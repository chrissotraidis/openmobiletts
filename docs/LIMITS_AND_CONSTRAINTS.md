# Limits and Constraints

## Quick Reference

| Limit | Value | Reason |
|-------|-------|--------|
| **Model Type** | Local (Kokoro 82M) | No API, fully private |
| **Max tokens per chunk** | 510 | Kokoro model limit |
| **Actual chunk size** | 175-250 tokens | Safe buffer + natural breaks |
| **Max total text length** | Unlimited | Automatically chunked |
| **Audio format** | MP3, 64kbps, 22050Hz | Good quality, small size |
| **Concurrent generations** | 1 per user | Server processes sequentially |
| **Model storage** | ~/.cache/kokoro/ (~320MB) | Downloaded once, cached |
| **CPU generation speed** | 2-5x real-time | GPU would be 35-210x |

## Detailed Limits

### 1. Token Limit per Chunk

**Why 510 tokens?**

Kokoro TTS has a hard architectural limit of 510 tokens per generation. Exceeding this causes the model to:
- Generate garbled audio
- Crash with out-of-memory errors
- Produce silence or truncated output

**Our solution:**

```python
# text_preprocessor.py
MAX_CHUNK_TOKENS = 250  # Conservative limit

# Text is split at sentence boundaries
# Ensures we never hit the 510 token ceiling
```

**What this means for you:**

- Short texts (< 250 tokens / ~200 words): Generated as one chunk
- Long texts: Split into multiple chunks, streamed sequentially
- No limit on total text length
- Natural pauses between chunks (at sentence boundaries)

**Example:**
```
Input: 1,000 word article
Chunking: ~4-5 chunks of 200-250 words each
Generation time: 4-5x longer than a single paragraph
Result: Seamless audio with natural pauses
```

### 2. Processing Speed

**Current setup (CPU):**

| Text Length | Generation Time | Audio Duration |
|-------------|----------------|----------------|
| 50 words | ~2-3 seconds | ~20 seconds |
| 200 words | ~8-12 seconds | ~80 seconds |
| 1,000 words | ~40-60 seconds | ~7 minutes |
| 5,000 words | ~3-5 minutes | ~35 minutes |

**Factors affecting speed:**

1. **CPU vs GPU**:
   - Your Mac M1/M2: ~2-5x real-time (CPU)
   - NVIDIA GPU: ~35-210x real-time
   - TPU: Even faster

2. **Voice selection**:
   - All voices take roughly same time
   - Slight variance (< 5%) between voices

3. **Speed parameter**:
   - Slower speed (0.5x): Generates faster (less audio to encode)
   - Normal speed (1.0x): Baseline
   - Faster speed (2.0x): Generates slower (more audio to encode)

4. **Text complexity**:
   - Simple words: Faster
   - Technical terms, numbers: Slightly slower
   - Non-English characters: May be slower

### 3. Memory Requirements

**Server (Python):**

```
Model loading:     ~500MB RAM
Active generation: +200-500MB RAM
Peak usage:        ~1GB RAM
```

**Client (Browser):**

```
Page load:         ~50MB RAM
Audio playback:    +50-100MB RAM per audio
IndexedDB:         Variable (GBs possible)
Peak usage:        ~200-500MB RAM
```

**Disk space:**

```
Model files:       ~320MB (one-time)
Python deps:       ~400MB
Client build:      ~10MB
Audio cache:       Grows with usage
```

### 4. Concurrent Usage

**Single-user mode (current):**
- One generation at a time per server
- Multiple browser tabs share same queue
- New request waits for previous to complete

**Why sequential?**

1. Memory constraints (loading multiple models = OOM)
2. CPU contention (parallel = slower for all)
3. Simpler implementation

**Future multi-user:**
- Implement request queue
- Load balancing across multiple workers
- Rate limiting per user

### 5. Text Length Limits

**No hard limit exists, but practical considerations:**

| Text Length | Chunks | Generation Time | Recommendation |
|-------------|--------|-----------------|----------------|
| < 500 words | 1-2 | < 30 seconds | ✅ Ideal |
| 500-2,000 words | 3-8 | 30 sec - 2 min | ✅ Good |
| 2,000-10,000 words | 8-40 | 2-10 minutes | ⚠️ Works, be patient |
| 10,000+ words | 40+ | 10+ minutes | ⚠️ Consider splitting |

**Recommended workflow for very long texts:**

1. Split into chapters/sections
2. Generate each section separately
3. Use history to access all sections
4. Download individually or combine offline

### 6. Browser Limits

**IndexedDB storage:**
- Chrome/Edge: Up to 80% of available disk space
- Firefox: Up to 50% of available disk space
- Safari: ~1GB typically

**Blob URL limits:**
- Chrome: Unlimited blob URLs
- Other browsers: May have limits (100-1000s)
- We revoke old blob URLs to avoid memory leaks

**Audio player limits:**
- HTML5 audio: No duration limit
- MP3 decoding: Handled by browser
- Seeking: Instant (blob URLs are local)

### 7. Network Limits

**Local communication (no actual network):**
- Client → Server: localhost, no bandwidth limit
- Typical streaming rate: 50-500 KB/s
- No timeout (can stream for hours if needed)

**Initial model download:**
- Size: ~320MB
- Source: HuggingFace CDN
- One-time only
- Can be pre-downloaded

### 8. File Upload Limits

**Document processing:**

```python
# config.py
MAX_UPLOAD_SIZE_MB = 10  # Default limit

Supported formats:
- PDF: ✅ (via PyMuPDF)
- DOCX: ✅ (via python-docx)
- TXT: ✅ (direct read)
```

**Why the limit?**
- Prevents memory exhaustion
- Typical documents < 10MB
- Can be increased if needed

### 9. Voice Limits

**Built-in voices:** 11 total

```python
American Female: af_heart, af_nova, af_sky, af_bella, af_sarah
American Male:   am_adam, am_michael
British Female:  bf_emma, bf_isabella
British Male:    bm_george, bm_lewis
```

**Custom voices:**
- Unlimited (with training)
- Requires fine-tuning Kokoro model
- Not currently exposed in UI

### 10. API Rate Limits

**Current: None**

Why no rate limiting?
- Single-user setup
- Local execution
- No cost per request

**Future considerations:**
- Add rate limiting for multi-user deployment
- Prevent abuse in shared environments
- Fair queue management

## Overcoming Limits

### Want faster generation?

1. **Use a GPU**:
   ```bash
   # Install CUDA toolkit
   # Install torch with CUDA
   pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
   ```

2. **Use a cloud GPU**:
   - AWS P3 instances
   - Google Colab (free GPU)
   - Lambda Labs

3. **Pre-generate common phrases**:
   - Cache frequently used text
   - Build a library of audio snippets

### Need to process very long texts?

1. **Split into sections**:
   - Process chapter by chapter
   - Combine audio files offline (ffmpeg)

2. **Batch processing**:
   - Queue multiple texts
   - Generate overnight

3. **Use streaming effectively**:
   - Start listening while generating
   - Don't wait for completion

### Running out of storage?

1. **Clear IndexedDB cache**:
   - Browser DevTools → Application → IndexedDB
   - Delete old audio

2. **Download and delete**:
   - Download important audio
   - Clear from browser cache

3. **Increase browser quota**:
   - Chrome settings → Site settings → Storage
   - Allow more storage for localhost

## Technical Constraints

### Kokoro Model Constraints

1. **Architecture**: Transformer-based, requires sequential token processing
2. **Training data**: English-only (en-us, en-gb)
3. **Sampling rate**: Fixed 24kHz output (downsampled to 22050Hz for MP3)
4. **Voice embeddings**: Fixed set (requires retraining for new voices)

### Server Constraints

1. **Single-threaded generation**: Python GIL + PyTorch
2. **Memory-bound**: Model must fit in RAM
3. **CPU-bound**: No async during generation

### Client Constraints

1. **Browser APIs**: Limited to Web Audio API, IndexedDB
2. **Streaming**: Must parse custom protocol
3. **Memory**: Large audio files can cause issues on mobile

## Comparison to Cloud TTS APIs

| Feature | Open Mobile TTS | Google Cloud TTS | AWS Polly |
|---------|----------------|------------------|-----------|
| **Privacy** | 100% local | Sent to Google | Sent to AWS |
| **Cost** | Free (electricity) | $4/1M chars | $4/1M chars |
| **Speed (CPU)** | 2-5x real-time | ~10x real-time | ~15x real-time |
| **Speed (GPU)** | 35-210x real-time | ~10x real-time | ~15x real-time |
| **Quality** | State-of-the-art | Excellent | Excellent |
| **Voices** | 11 built-in | 400+ voices | 60+ voices |
| **Offline** | ✅ Yes | ❌ No | ❌ No |
| **Custom voices** | ✅ Yes (with training) | ✅ Yes (paid) | ✅ Yes (paid) |
| **Rate limits** | None (local) | 300 req/min | 100 req/min |

## Summary

**Key takeaway**: Open Mobile TTS has NO hard text length limit and runs 100% locally on your hardware. The only practical limits are:

1. **Processing time**: Long texts take longer (but you can listen while generating)
2. **Memory**: Very large texts may need chunking (handled automatically)
3. **Storage**: Browser cache can fill up (easily cleared)

Everything else is a feature, not a limitation:
- ✅ Unlimited usage (no API costs)
- ✅ Complete privacy (no data leaves your machine)
- ✅ No internet required (after initial setup)
- ✅ No rate limits (use as much as you want)
