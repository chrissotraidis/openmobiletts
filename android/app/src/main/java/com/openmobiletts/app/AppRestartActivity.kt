package com.openmobiletts.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Foreground bridge that relaunches the app after the TTS process exits. */
class AppRestartActivity : Activity() {
    companion object {
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_LABEL = "model_label"
        const val EXTRA_DESTINATION = "destination"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0a0c10")
        window.navigationBarColor = Color.parseColor("#0a0c10")

        val modelLabel = intent.getStringExtra(EXTRA_MODEL_LABEL).orEmpty()
        val modelId = intent.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val destination = intent.getStringExtra(EXTRA_DESTINATION)
            ?.takeIf { it == "models" || it == "voice" }
            ?: "models"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
            setBackgroundColor(Color.parseColor("#0a0c10"))
        }
        root.addView(
            ImageView(this).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = null
            },
            LinearLayout.LayoutParams(dp(88), dp(88)).apply {
                bottomMargin = dp(24)
            },
        )
        root.addView(
            TextView(this).apply {
                text = "Switching voice model"
                setTextColor(Color.parseColor("#e2e8f0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            },
        )
        if (modelLabel.isNotEmpty()) {
            root.addView(
                TextView(this).apply {
                    text = modelLabel
                    setTextColor(Color.parseColor("#94a3b8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                },
            )
        }
        root.addView(
            ProgressBar(this).apply {
                isIndeterminate = true
                contentDescription = "Switching voice model"
            },
            LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                topMargin = dp(28)
            },
        )
        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed(
            {
                packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    ?.putExtra(MainActivity.EXTRA_RESUME_MODEL_ID, modelId)
                    ?.putExtra(MainActivity.EXTRA_RESUME_SECTION, destination)
                    ?.let(::startActivity)
                finishAndRemoveTask()
            },
            900L,
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
