package com.openmobiletts.app

import android.app.Application
import android.util.Log

class OpenMobileTtsApp : Application() {

    companion object {
        private const val TAG = "OpenMobileTTS"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sherpa-onnx-jni")
            Log.i(TAG, "sherpa-onnx-jni loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load sherpa-onnx-jni: ${e.message}")
        }
    }
}
