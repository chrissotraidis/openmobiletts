package com.openmobiletts.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var webView: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — generation proceeds either way */ }

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

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val app = application as OpenMobileTtsApp

        Log.i(TAG, "onCreate: model downloaded = ${app.isModelDownloaded()}")

        if (app.isModelDownloaded()) {
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
            text = "Open Mobile TTS"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "On-device text-to-speech"
            setTextColor(mutedColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(48))
        }
        root.addView(subtitle)

        // Status text
        val statusText = TextView(this).apply {
            text = "Download the TTS model to get started"
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
            text = "Download Model (~350 MB)"
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

        setContentView(root)

        // Download action
        downloadBtn.setOnClickListener {
            downloadBtn.isEnabled = false
            downloadBtn.text = "Downloading..."
            progressBar.visibility = View.VISIBLE
            progressText.visibility = View.VISIBLE
            statusText.text = "Downloading model..."

            scope.launch {
                try {
                    val downloader = ModelDownloader()
                    withContext(Dispatchers.IO) {
                        downloader.download(filesDir) { bytesRead, totalBytes ->
                            val percent = if (totalBytes > 0) {
                                ((bytesRead * 100) / totalBytes).toInt()
                            } else {
                                -1
                            }
                            launch(Dispatchers.Main) {
                                if (percent >= 0) {
                                    progressBar.progress = percent
                                    progressText.text = "$percent%"
                                    statusText.text = "Downloading model... ${bytesRead / 1024 / 1024} MB"
                                } else {
                                    progressBar.isIndeterminate = true
                                    progressText.text = "${bytesRead / 1024 / 1024} MB"
                                }
                            }
                        }
                    }

                    statusText.text = "Starting server..."
                    val app = application as OpenMobileTtsApp
                    app.ensureServerRunning()

                    showWebView()
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed", e)
                    statusText.text = "Download failed: ${e.message}"
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Retry Download"
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                }
            }
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
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE  // Always load fresh assets

            // Dark background to match the app while loading
            setBackgroundColor(Color.parseColor("#0a0c10"))

            webViewClient = object : WebViewClient() {
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

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // Cancel any pending callback from a previous picker
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = filePathCallback
                    try {
                        val intent = fileChooserParams?.createIntent()
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
        setContentView(webView)

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

        webView.loadUrl("http://127.0.0.1:${OpenMobileTtsApp.PORT}/")
    }

    override fun onDestroy() {
        TtsService.playbackCommandCallback = null
        TtsService.seekCallback = null
        scope.cancel()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    // ---------- JS ↔ Native Bridge ----------

    inner class AndroidBridge {

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
        fun updatePlaybackProgress(positionMs: Int, durationMs: Int) {
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
                        Toast.makeText(this@MainActivity, "Saved to Downloads: $filename", Toast.LENGTH_SHORT).show()
                    }
                    Log.i(TAG, "Saved audio: $filename (${bytes.size} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "saveAudioFile failed", e)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
