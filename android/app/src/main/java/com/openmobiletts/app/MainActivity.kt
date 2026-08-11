package com.openmobiletts.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkInfo

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val TRUSTED_HOST = "127.0.0.1"
        private const val TRUSTED_SCHEME = "http"
        const val EXTRA_RESUME_MODEL_ID = "resume_model_id"
        const val EXTRA_RESUME_SECTION = "resume_section"
    }

    private var webView: WebView? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebViewPermissionRequest: android.webkit.PermissionRequest? = null

    private fun isTrustedAppUrl(uri: Uri?): Boolean = uri != null &&
        uri.scheme == TRUSTED_SCHEME &&
        uri.host == TRUSTED_HOST &&
        uri.port == OpenMobileTtsApp.PORT

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — generation proceeds either way */ }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingWebViewPermissionRequest
        pendingWebViewPermissionRequest = null
        if (granted && pending != null) {
            pending.grant(pending.resources)
        } else {
            pending?.deny()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        fileUploadCallback?.onReceiveValue(uris ?: emptyArray())
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15+ enforces edge-to-edge layouts for current target SDKs.
        // Keep the dark system bars, then place native/WebView content inside
        // the reported status, navigation, and display-cutout insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val app = application as OpenMobileTtsApp

        val ttsReady = app.isTtsModelDownloaded()
        val sttReady = app.isSttModelDownloaded()
        Log.i(TAG, "onCreate: TTS model=$ttsReady, STT model=$sttReady")

        if (ttsReady) {
            // TTS is the minimum requirement to start the app. Existing TTS-only
            // users choose whether to add STT from Settings; upgrades never start
            // a large network transfer without an explicit action.
            try {
                app.ensureServerRunning()
                Log.i(TAG, "Server started, alive = ${app.httpServer?.isAlive}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
            }
            showWebView()
        } else {
            showDownloadUI()
        }
    }

    // ---------- Download UI (programmatic, no Compose, no XML) ----------

    private fun showDownloadUI() {
        val modelDownloader = ModelDownloader(this)
        val ttsModel = modelDownloader.ttsModel
        val bgColor = Color.parseColor("#0a0c10")
        val textColor = Color.parseColor("#e2e8f0")
        val mutedColor = Color.parseColor("#94a3b8")
        val accentColor = Color.parseColor("#3b82f6")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        // Title
        val title = TextView(this).apply {
            text = AppMetadata.APP_NAME
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "On-device voice \u2194 text"
            setTextColor(mutedColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(48))
        }
        root.addView(subtitle)

        // Status text
        val statusText = TextView(this).apply {
            text = "Download the voice model to get started"
            setTextColor(mutedColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(statusText)

        // Progress bar (hidden initially)
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8),
            ).apply {
                bottomMargin = dp(16)
            }
        }
        root.addView(progressBar)

        // Progress percentage text (hidden initially)
        val progressText = TextView(this).apply {
            text = "0%"
            setTextColor(mutedColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(progressText)

        // Download button
        val downloadBtn = Button(this).apply {
            text = "Download Voice Model (${ttsModel.archiveSizeMb} MB)"
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(32), dp(16), dp(32), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        root.addView(downloadBtn)

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setTextColor(mutedColor)
            setBackgroundColor(Color.TRANSPARENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            visibility = View.GONE
        }
        root.addView(cancelBtn)

        setInsetContentView(root)

        fun resetDownloadUi(message: String) {
            statusText.text = message
            downloadBtn.isEnabled = true
            downloadBtn.text = "Download Voice Model (${ttsModel.archiveSizeMb} MB)"
            cancelBtn.visibility = View.GONE
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        }

        ModelDownloadWork.observe(this, this, DownloadModel.TTS) { work ->
            if (work == null) return@observe
            when (work.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    downloadBtn.isEnabled = false
                    downloadBtn.text = "Waiting for network..."
                    cancelBtn.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                    progressText.visibility = View.VISIBLE
                    progressText.text = "Waiting"
                    statusText.text = "The download will start when a network is available."
                }
                WorkInfo.State.RUNNING -> {
                    val downloaded = work.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                    val total = work.progress.getLong(
                        ModelDownloadWorker.KEY_TOTAL_BYTES,
                        ttsModel.archiveBytes,
                    )
                    val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                    downloadBtn.isEnabled = false
                    downloadBtn.text = "Downloading..."
                    cancelBtn.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = total <= 0L
                    progressBar.progress = percent
                    progressText.visibility = View.VISIBLE
                    progressText.text = if (total > 0L) "$percent%" else "${downloaded / 1024 / 1024} MB"
                    statusText.text = "Downloading voice model... ${downloaded / 1024 / 1024} MB"
                }
                WorkInfo.State.SUCCEEDED -> {
                    val app = application as OpenMobileTtsApp
                    if (app.isTtsModelDownloaded()) {
                        statusText.text = "Starting local voice engine..."
                        app.ensureServerRunning()
                        showWebView()
                    } else {
                        resetDownloadUi("Download finished but model validation failed. Retry the download.")
                    }
                }
                WorkInfo.State.FAILED -> {
                    val error = work.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                        ?: "Model download failed."
                    resetDownloadUi(error)
                }
                WorkInfo.State.CANCELLED -> resetDownloadUi("Download paused. Tap download to resume.")
            }
        }

        downloadBtn.setOnClickListener {
            val requiredBytes = modelDownloader.minimumTtsFreeBytes()
            if (filesDir.usableSpace < requiredBytes) {
                resetDownloadUi("Free at least ${requiredBytes / 1024 / 1024} MB and retry.")
                return@setOnClickListener
            }
            ModelDownloadWork.enqueue(this, DownloadModel.TTS)
        }

        cancelBtn.setOnClickListener {
            ModelDownloadWork.cancel(this, DownloadModel.TTS)
            cancelBtn.isEnabled = false
        }
    }

    // ---------- WebView ----------

    private fun showWebView() {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true       // Required for localStorage
            settings.databaseEnabled = true          // Required for IndexedDB (audio cache)
            settings.mediaPlaybackRequiresUserGesture = false  // Allow auto-play
            settings.allowContentAccess = true
            settings.allowFileAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE  // Always load fresh assets

            // Dark background to match the app while loading
            setBackgroundColor(Color.parseColor("#0a0c10"))

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val destination = request?.url
                    if (isTrustedAppUrl(destination)) return false
                    Log.w(TAG, "Blocked WebView navigation outside app origin: $destination")
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    val destination = url?.let(Uri::parse)
                    if (!isTrustedAppUrl(destination)) {
                        Log.w(TAG, "Stopped untrusted WebView page: $url")
                        view?.stopLoading()
                        return
                    }
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.i(TAG, "WebView page finished: $url")
                }

                override fun onReceivedError(
                    view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                ) {
                    Log.e(TAG, "WebView error: code=$errorCode, desc=$description, url=$failingUrl")
                    super.onReceivedError(view, errorCode, description, failingUrl)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    Log.e(TAG, "WebView HTTP error: ${request?.url} → ${errorResponse?.statusCode}")
                    super.onReceivedHttpError(view, request, errorResponse)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(TAG, "WebView [${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }

                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                    request?.let { req ->
                        if (!isTrustedAppUrl(req.origin)) {
                            Log.w(TAG, "Denied WebView permission from untrusted origin: ${req.origin}")
                            req.deny()
                            return@let
                        }
                        val resources = req.resources
                        Log.i(TAG, "WebView permission request: ${resources.joinToString()}")

                        // Grant audio capture for microphone access (STT dictation)
                        if (resources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            // Check Android runtime permission first
                            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                req.grant(resources)
                            } else {
                                // Request runtime permission, then grant WebView permission
                                pendingWebViewPermissionRequest = req
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            req.deny()
                        }
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (!isTrustedAppUrl(webView?.url?.let(Uri::parse))) {
                        Log.w(TAG, "Denied file chooser outside trusted app origin")
                        filePathCallback?.onReceiveValue(null)
                        return false
                    }
                    // Cancel any pending callback from a previous picker
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = filePathCallback
                    try {
                        // Don't use fileChooserParams?.createIntent() — it maps HTML accept
                        // extensions to MIME types, but Android's mapping is incomplete
                        // (.aac, .md, .m4a often unrecognized → greyed out in picker).
                        // Don't use EXTRA_MIME_TYPES either — Samsung/Xiaomi file pickers
                        // ignore it when type is */*. Instead, let the user pick ANY file
                        // and validate the extension after selection in the JS handler.
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "File chooser failed", e)
                        fileUploadCallback?.onReceiveValue(emptyArray())
                        fileUploadCallback = null
                        return false
                    }
                    return true
                }
            }
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")

        this.webView = webView
        setInsetContentView(webView)

        // Forward notification button presses to the WebView's player controls
        TtsService.playbackCommandCallback = { command ->
            runOnUiThread {
                webView.evaluateJavascript("window.__ttsControl?.${command}()", null)
            }
        }

        // Forward seek commands (with position in ms)
        TtsService.seekCallback = { positionMs ->
            runOnUiThread {
                webView.evaluateJavascript("window.__ttsControl?.seekTo($positionMs)", null)
            }
        }

        // Handle back navigation within WebView
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val appUrl = Uri.Builder()
            .scheme(TRUSTED_SCHEME)
            .encodedAuthority("$TRUSTED_HOST:${OpenMobileTtsApp.PORT}")
            .path("/")
            .apply {
                val resumeModelId = intent.getStringExtra(EXTRA_RESUME_MODEL_ID)?.take(100)
                val resumeSection = intent.getStringExtra(EXTRA_RESUME_SECTION)
                if (!resumeModelId.isNullOrEmpty() && resumeSection in setOf("models", "voice")) {
                    appendQueryParameter("resume_model", resumeModelId)
                    appendQueryParameter("resume_section", resumeSection)
                }
            }
            .build()
            .toString()
        intent.removeExtra(EXTRA_RESUME_MODEL_ID)
        intent.removeExtra(EXTRA_RESUME_SECTION)
        webView.loadUrl(appUrl)
    }

    /**
     * Hosts app content below system bars while the window remains edge-to-edge.
     * This is shared by first-run download UI and the WebView so neither can be
     * obscured by a status bar, camera cutout, or gesture navigation area.
     */
    private fun setInsetContentView(content: View) {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0a0c10"))
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            val params = content.layoutParams as FrameLayout.LayoutParams
            if (
                params.leftMargin != insets.left ||
                params.topMargin != insets.top ||
                params.rightMargin != insets.right ||
                params.bottomMargin != insets.bottom
            ) {
                params.setMargins(insets.left, insets.top, insets.right, insets.bottom)
                content.layoutParams = params
            }
            windowInsets
        }

        setContentView(container)
        ViewCompat.requestApplyInsets(container)
    }

    override fun onDestroy() {
        TtsService.playbackCommandCallback = null
        TtsService.seekCallback = null
        // Release any pending file upload callback to unblock the WebView file input
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    // ---------- JS ↔ Native Bridge ----------

    inner class AndroidBridge {

        @JavascriptInterface
        fun restartApp(modelId: String?, modelLabel: String?, destination: String?) {
            val safeModelId = modelId?.trim()?.take(100).orEmpty()
            val safeLabel = modelLabel?.trim()?.take(80).orEmpty()
            val safeDestination = destination?.takeIf { it in setOf("models", "voice") } ?: "models"
            Log.i(TAG, "Bridge: restartApp model=$safeModelId destination=$safeDestination")
            runOnUiThread {
                // A tiny translucent Activity runs in its own process, so it
                // survives this process exit and can relaunch the selected
                // model without Android blocking a background launch.
                startActivity(
                    Intent(this@MainActivity, AppRestartActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(AppRestartActivity.EXTRA_MODEL_ID, safeModelId)
                        .putExtra(AppRestartActivity.EXTRA_MODEL_LABEL, safeLabel)
                        .putExtra(AppRestartActivity.EXTRA_DESTINATION, safeDestination),
                )
                Process.killProcess(Process.myPid())
            }
        }

        @JavascriptInterface
        fun onGenerationStarted() {
            Log.d(TAG, "Bridge: onGenerationStarted")
            TtsService.start(this@MainActivity, "Generating speech...", wakeLock = true)
        }

        @JavascriptInterface
        fun onPlaybackStarted() {
            Log.d(TAG, "Bridge: onPlaybackStarted")
            TtsService.start(this@MainActivity, "Playing audio...", wakeLock = true, playbackMode = true)
        }

        @JavascriptInterface
        fun onPlaybackPaused() {
            Log.d(TAG, "Bridge: onPlaybackPaused")
            TtsService.instance?.updateNotification("Audio paused")
            TtsService.instance?.releaseWakeLock()
        }

        @JavascriptInterface
        fun onPlaybackStopped() {
            Log.d(TAG, "Bridge: onPlaybackStopped")
            TtsService.instance?.releaseWakeLock()
            TtsService.stop(this@MainActivity)
        }

        @JavascriptInterface
        fun updateGenerationProgress(current: Int, total: Int) {
            TtsService.instance?.updateProgress(current, total)
        }

        @JavascriptInterface
        fun updatePlaybackProgress(positionMs: Double, durationMs: Double) {
            TtsService.instance?.updatePlaybackProgress(positionMs.toLong(), durationMs.toLong())
        }

        @JavascriptInterface
        fun saveAudioFile(base64Data: String, filename: String, mimeType: String) {
            Log.d(TAG, "Bridge: saveAudioFile filename=$filename mime=$mimeType")
            // Dispatch heavy work (base64 decode + disk I/O) off the JavaBridge thread
            // to avoid blocking other bridge calls during the write.
            Thread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // API 29+: use MediaStore.Downloads
                        val resolver = contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, filename)
                            put(MediaStore.Downloads.MIME_TYPE, mimeType)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                out.write(bytes)
                            }
                        } else {
                            throw Exception("MediaStore insert returned null")
                        }
                    } else {
                        // API 26-28: write to app-scoped external storage (no permission needed)
                        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: throw Exception("External storage not available")
                        val file = java.io.File(downloadsDir, filename)
                        file.outputStream().use { out ->
                            out.write(bytes)
                        }
                    }

                    runOnUiThread {
                        if (!isDestroyed) {
                            Toast.makeText(applicationContext, "Saved to Downloads: $filename", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Log.i(TAG, "Saved audio: $filename (${bytes.size} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "saveAudioFile failed", e)
                    runOnUiThread {
                        if (!isDestroyed) {
                            Toast.makeText(applicationContext, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
