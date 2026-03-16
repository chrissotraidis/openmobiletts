# Unified Input — Edge Cases

## Mic Tap During Active TTS Generation
**Scenario:** User taps the mic button while TTS audio is being generated (stream in progress).
**Why it matters:** Recording audio while the device speaker is playing TTS output would capture the TTS audio, creating a feedback loop of nonsense transcription.
**How we handle it:**
1. Mic button is disabled (grayed out) during active TTS generation
2. Visual indicator: mic button shows a tooltip or is visually dimmed
3. Once generation completes or is cancelled, mic button re-enables
**Status:** 🔵 Not built

---

## Generate Tap During Active Recording
**Scenario:** User taps "Generate Speech" while actively recording via microphone.
**Why it matters:** Generating speech during recording would play audio that gets captured by the mic.
**How we handle it:**
1. Generate button is disabled during active recording
2. User must stop recording first, then can generate speech from the transcribed text
**Status:** 🔵 Not built

---

## Text Area Has Content When Mic Is Tapped
**Scenario:** User has existing text in the text area (typed, pasted, or from a previous transcription) and taps mic.
**Why it matters:** Should the new transcription replace or append to existing text?
**How we handle it:**
1. New transcription **appends** to existing text (separated by a newline)
2. User can always select-all and delete before recording if they want a fresh start
3. This matches the "scratchpad" concept — content accumulates
**Status:** 🔵 Not built

---

## Upload During Active Recording
**Scenario:** User taps Upload while the mic is actively recording.
**Why it matters:** Upload could be for a document (TTS path) or audio file (STT path), and either could conflict with the active recording.
**How we handle it:**
1. Upload button is disabled during active recording
2. User must stop recording first
**Status:** 🔵 Not built

---

## Export With Empty Text Area
**Scenario:** User taps Export when the text area is empty.
**Why it matters:** Exporting an empty document is pointless and could create a confusing file.
**How we handle it:**
1. Export button is disabled when text area is empty
2. If somehow triggered with empty text, show "Nothing to export"
**Status:** 🔵 Not built

---

## Switching Tabs During Recording
**Scenario:** User is recording via mic on Generate tab, then switches to History or Settings tab.
**Why it matters:** Recording should stop to avoid capturing unintended audio while user browses other tabs.
**How we handle it:**
1. Switching tabs while recording stops the recording
2. If audio was captured (>0.5s), it's automatically submitted for transcription
3. Result appears in text area when user returns to Generate tab
**Status:** 🔵 Not built

---

## Rapid Mic Button Toggling
**Scenario:** User rapidly taps mic on/off multiple times.
**Why it matters:** Could create multiple overlapping MediaRecorder sessions or fire multiple transcription requests.
**How we handle it:**
1. Debounce mic button (ignore taps within 500ms of state change)
2. Only one recording session active at a time
3. Only one transcription request in flight at a time
**Status:** 🔵 Not built
