# Open Mobile TTS - PWA Client

Progressive Web App for streaming TTS playback with synchronized text highlighting.

Built with **Svelte + SvelteKit** for optimal mobile performance.

## Overview

This is the frontend PWA client for Open Mobile TTS. It provides:
- Audio player with real-time streaming
- Sentence-level text synchronization and highlighting
- Document upload interface (PDF, DOCX)
- Direct text input for TTS
- Service worker for offline shell
- MediaSession API for lock screen controls
- Mobile-first responsive design

## Why Svelte/SvelteKit?

Svelte was chosen for maximum mobile performance:
- **Smallest bundle size**: ~3KB framework overhead (vs 45KB React, 35KB Vue)
- **No virtual DOM**: Compiles to vanilla JS, direct DOM manipulation
- **Faster initial load**: Critical for mobile networks and older devices
- **Lower memory usage**: Better for mobile device constraints
- **Built-in PWA support**: Via SvelteKit adapters

## Installation

### Prerequisites
- Node.js 18+ and npm/yarn/pnpm

### Initial Setup (First Time)

1. **Create SvelteKit project:**
   ```bash
   npm create svelte@latest .
   # Select: Skeleton project, TypeScript optional, Prettier + ESLint recommended
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Install additional dependencies:**
   ```bash
   npm install -D @sveltejs/adapter-static
   npm install -D tailwindcss postcss autoprefixer
   npx tailwindcss init -p
   ```

2. **Configure API endpoint:**
   Create `.env.local`:
   ```bash
   VITE_API_URL=http://localhost:8000  # Development
   # or
   VITE_API_URL=https://your-server.com  # Production
   ```

## Running

### Development
```bash
npm run dev
```
Visit `http://localhost:5173` (or shown port)

### Build for Production
```bash
npm run build
```

### Preview Production Build
```bash
npm run preview
```

## Project Structure

```
client/
├── public/
│   ├── manifest.json         # PWA manifest
│   ├── sw.js                # Service worker
│   ├── icon-192.png         # App icon
│   └── icon-512.png         # App icon
├── src/
│   ├── components/          # UI components
│   │   ├── AudioPlayer.jsx
│   │   ├── TextDisplay.jsx
│   │   ├── FileUpload.jsx
│   │   ├── VoiceSelector.jsx
│   │   └── LoginForm.jsx
│   ├── services/            # Business logic
│   │   ├── api.js           # API client
│   │   ├── auth.js          # Authentication
│   │   ├── audioPlayer.js   # Audio streaming and playback
│   │   └── syncController.js # Text synchronization
│   ├── styles/              # CSS/Tailwind
│   ├── App.jsx              # Root component
│   └── main.jsx             # Entry point
├── package.json
└── README.md                # This file
```

## Features

### Audio Streaming
- Progressive audio loading (no wait for full generation)
- Chunked transfer encoding from server
- Automatic buffering and playback

### Text Synchronization
- Sentence-level highlighting based on timing metadata
- Smooth scroll-into-view for current segment
- Visual indication of playback position

### Offline Support
- Service worker caches static assets
- App shell loads offline
- Audio requires network (dynamically generated)

### Mobile Optimizations
- Large touch targets (44×44px minimum)
- Responsive layout (mobile-first)
- MediaSession API for lock screen controls (Android)
- iOS fallback messaging (no background audio)

## Development

### Recommended Stack
- **Framework**: React, Vue, or Svelte (your choice)
- **Build tool**: Vite (fast, modern)
- **Styling**: Tailwind CSS (utility-first, mobile-friendly)
- **State management**: Context API or Zustand (keep it simple)
- **HTTP client**: Fetch API (native)

### Code Organization

**Components** should be:
- Self-contained and reusable
- Mobile-first (responsive by default)
- Accessible (ARIA labels, keyboard nav)

**Services** should handle:
- API communication
- Audio playback logic
- Text synchronization
- Authentication state

### Key Implementation Details

**Audio Streaming Pattern:**
```javascript
async function streamTTS(text, voice) {
  const response = await fetch(`${API_URL}/api/tts/stream?text=${text}&voice=${voice}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });

  const reader = response.body.getReader();
  const audioChunks = [];
  const timingData = [];

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const text = new TextDecoder().decode(value);
    if (text.startsWith('TIMING:')) {
      const timing = JSON.parse(text.slice(7));
      timingData.push(timing);
    } else {
      audioChunks.push(value);
    }
  }

  const blob = new Blob(audioChunks, { type: 'audio/mpeg' });
  return { audioUrl: URL.createObjectURL(blob), timingData };
}
```

**Text Highlighting:**
```javascript
audio.addEventListener('timeupdate', () => {
  const currentTime = audio.currentTime;
  timingData.forEach((segment, index) => {
    const element = document.getElementById(`segment-${index}`);
    if (currentTime >= segment.start && currentTime < segment.end) {
      element.classList.add('highlighted');
      element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } else {
      element.classList.remove('highlighted');
    }
  });
});
```

## PWA Configuration

### Manifest (`public/manifest.json`)
```json
{
  "name": "Open Mobile TTS",
  "short_name": "TTS Reader",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#1a1a1a",
  "theme_color": "#4a90d9",
  "icons": [
    { "src": "/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

### Service Worker Strategy
- **Static assets**: Cache-first (HTML, CSS, JS, images)
- **API calls**: Network-only (dynamic content)
- **Audio files**: Network-first (can cache completed downloads)

## Mobile Testing

### Android
- Chrome DevTools > Device mode
- Physical device via USB debugging
- Test background audio and lock screen controls

### iOS
- Safari Web Inspector (macOS required)
- Physical device (iOS Simulator has limitations)
- Test foreground audio playback
- Verify "Add to Home Screen" flow

## Deployment

### Static Hosting (Recommended)
- **Vercel**: `vercel --prod`
- **Netlify**: `netlify deploy --prod`
- **Cloudflare Pages**: Connect GitHub repo

### Environment Variables
Set `VITE_API_URL` in your hosting provider's dashboard to point to your production server.

### HTTPS Required
PWAs require HTTPS for service workers and many APIs. Use your hosting provider's SSL or Let's Encrypt.

## Browser Support

| Feature | Chrome/Edge | Firefox | Safari (iOS) |
|---------|-------------|---------|--------------|
| Basic PWA | ✅ | ✅ | ✅ |
| Background audio | ✅ | ✅ | ❌ |
| MediaSession API | ✅ | ✅ | ⚠️ (limited) |
| Add to home screen | ✅ | ✅ | ✅ |
| Service worker | ✅ | ✅ | ✅ |

## Troubleshooting

**Audio won't play on mobile**
- Check HTTPS (required for audio autoplay)
- Verify user gesture (click/tap) triggered playback
- Test CORS headers from server

**Text sync is off**
- Verify timing metadata format from server
- Check `timeupdate` event frequency
- Ensure audio and timing arrays align

**Service worker not updating**
- Clear browser cache
- Unregister old service worker
- Use "Update on reload" in DevTools

**iOS: Audio stops when app minimized**
- This is a PWA limitation on iOS
- Display warning to users
- Consider native iOS app for full background audio

## References

- See `docs/technical-architecture.md` for detailed patterns
- PWA Best Practices: https://web.dev/progressive-web-apps/
- MediaSession API: https://developer.mozilla.org/en-US/docs/Web/API/MediaSession
- Service Workers: https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
