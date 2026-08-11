package com.openmobiletts.app

/** Product and model identity shared by Android API responses. */
object AppMetadata {
    const val APP_NAME = "Open Mobile TTS"
    const val SHERPA_ONNX_VERSION = "1.13.4"
    const val CAPABILITIES_JSON = """{"schema_version":1,"platform":"android","features":{"tts":true,"stt":true,"batch_transcription":false,"engine_switching":true,"document_import":true,"audio_import":true,"model_download":true,"model_catalog":true,"project_storage":true,"exports":true,"logs":true}}"""
}
