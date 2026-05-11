# Batch Export Flow

## Who

A user with a folder of call recordings who wants individual Markdown transcripts for each audio file.

## The Happy Path

1. User opens the Generate tab.
2. User clicks **Batch Upload**.
3. File picker accepts multiple audio files.
4. User selects many WAV/MP3/M4A files.
5. App creates a background batch transcription job.
6. Server validates each file extension.
7. Server initializes Moonshine STT if needed.
8. Server transcribes files sequentially.
9. Browser polls job status and shows per-file progress.
10. Server writes one Markdown transcript per audio file.
11. Server writes `summary.md` and `manifest.json`.
12. Browser downloads a ZIP file when the job completes.
13. User opens the ZIP and gets one Markdown file per original recording.

## What Could Go Wrong

### Unsupported File In Batch
- **When:** User selects a file that is not an audio format.
- **What happens:** The Generate tab rejects the selection before upload. The API also validates server-side and marks unsupported files as failed if called directly.
- **Recovery:** User can remove or convert the failed file and rerun the batch.

### STT Model Missing
- **When:** Moonshine STT model is not installed locally.
- **What happens:** Batch request fails before processing begins.
- **Recovery:** User installs the STT model from Settings or local setup and retries.

### One File Fails
- **When:** ffmpeg cannot decode a corrupt file or transcription fails.
- **What happens:** Batch continues with the next file.
- **Recovery:** ZIP includes the failure reason in `manifest.json` and `summary.md`.

### Duplicate Filenames
- **When:** Multiple files normalize to the same Markdown filename.
- **What happens:** App appends a number to keep filenames unique.
- **Recovery:** User receives all transcripts without overwrites.

## Acceptance Criteria

- [x] Batch Upload accepts multiple audio files.
- [x] Batch Upload does not accept documents.
- [x] Batch Upload creates a background job with status polling.
- [x] Files are processed sequentially.
- [x] Each successful audio file produces one Markdown file.
- [x] ZIP includes `summary.md`.
- [x] ZIP includes `manifest.json`.
- [x] Failed files are reported without aborting successful files.
- [x] Original audio filenames are preserved in transcript metadata.
- [x] Browser automatically downloads the ZIP when processing completes.

## Related

- See: [Batch Transcription Overview](batch-transcription-overview.md)
- See: [Audio Import](../audio-import/audio-import-overview.md)
- Depends on: [STT](../stt/stt-overview.md)
