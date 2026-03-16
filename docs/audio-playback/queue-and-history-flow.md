# Queue and History Flow

> **Note:** This documents existing v2.0 functionality that is already implemented. Created retroactively for completeness.

## Who

A user who has generated multiple TTS audio items and wants to manage playback and review past generations.

## The Happy Path — Queue Playback

1. User generates TTS audio from text → audio appears in bottom player bar
2. User navigates to **History tab** — sees list of past generations
3. User taps **Queue** button on a history card → item is added to the playback queue
4. User can queue multiple items
5. Bottom player bar shows current track + queue indicator
6. When current track finishes, `handleEnded` in player.js auto-plays next item in queue
7. User can tap the **queue toggle** in the expanded player view to see/manage the queue
8. In queue view: reorder (by tapping an item to play it immediately), remove individual items, or "Clear All"

## The Happy Path — History Replay

1. User navigates to **History tab**
2. History cards show: title (preview text), voice, speed, date, status badge
3. Status badges: "Audio ready" (cached in IndexedDB) or "Text only" (no cached audio)
4. User taps a card → **detail/reader view** opens
5. Detail view shows full text with synchronized sentence highlighting
6. If audio is cached: auto-plays immediately
7. If audio is not cached: text is displayed but no auto-play (avoids triggering expensive on-device regeneration)
8. User can tap **Play** to regenerate, **Download** to save, or **Delete** to remove
9. **Android back button** works via `popstate` — returns from detail view to history list

## The Happy Path — Expanded Player

1. User taps the bottom player bar → expanded "Now Playing" view opens
2. Shows: album art area (waveform visualization), track info, seek bar, playback controls
3. Queue toggle button (top-right, ListMusic icon) switches between Now Playing and Queue view
4. **Close** (ChevronDown) returns to the collapsed player bar
5. On Android: back button also closes expanded view (via parent `popstate` handler)
6. Mobile bottom padding (72px) prevents content from being obscured by navigation bar

## What Could Go Wrong

### Audio cache evicted by browser
- **When:** Browser's IndexedDB storage is cleared (user cleared site data, storage pressure)
- **What happens:** History card shows "Text only" instead of "Audio ready". Audio must be regenerated.
- **Recovery:** User taps Play to regenerate. Text is always preserved in history metadata.

### Queue item's audio no longer cached
- **When:** A queued item's IndexedDB cache was evicted between queueing and playback
- **What happens:** When auto-play reaches this item, it skips to the next item with cached audio (or stops if none remain)
- **Recovery:** User can regenerate the skipped item from History.

### History grows very large
- **When:** User generates hundreds of items over time
- **What happens:** History list loads all items from localStorage. Performance may degrade with 500+ items.
- **Recovery:** v3.0 project storage with auto-cleanup will address this. In v2.0, user can manually delete old items.

## Acceptance Criteria

> These criteria document existing behavior — they are already met.

- [x] Queue button adds items to playback queue
- [x] Queue auto-advances to next item when current finishes
- [x] Expanded player shows queue list with remove/clear controls
- [x] History cards show correct cache status (Audio ready / Text only)
- [x] Detail view auto-plays only if audio is cached
- [x] Android back button navigates from detail view to history list
- [x] Download button saves audio to device
- [x] Delete removes item from history

## Related

- See: [Audio Playback Overview](audio-playback-overview.md) for technical rules
- See: [Project Storage](../project-storage/project-storage-overview.md) for v3.0 replacement of localStorage history
