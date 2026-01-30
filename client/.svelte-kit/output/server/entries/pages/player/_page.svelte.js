import "clsx";
import "../../../chunks/auth.js";
import "@sveltejs/kit/internal";
import { w as writable } from "../../../chunks/exports.js";
import "../../../chunks/utils.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/state.svelte.js";
import { _ as store_get, $ as attr_style, a0 as stringify, a1 as unsubscribe_stores, a2 as ensure_array_like, a3 as attr_class } from "../../../chunks/index.js";
import { a as ssr_context } from "../../../chunks/context.js";
import { a as attr } from "../../../chunks/attributes.js";
import { e as escape_html } from "../../../chunks/escaping.js";
function onDestroy(fn) {
  /** @type {SSRContext} */
  ssr_context.r.on_destroy(fn);
}
function createPlayerStore() {
  const { subscribe, set, update } = writable({
    text: "",
    timingData: [],
    audioUrl: null,
    isPlaying: false,
    isLoading: false,
    currentTime: 0,
    duration: 0,
    error: null
  });
  return {
    subscribe,
    setText: (text) => update((state) => ({ ...state, text })),
    setTimingData: (timingData) => update((state) => ({ ...state, timingData })),
    setAudioUrl: (audioUrl) => update((state) => ({ ...state, audioUrl })),
    setPlaying: (isPlaying) => update((state) => ({ ...state, isPlaying })),
    setLoading: (isLoading) => update((state) => ({ ...state, isLoading })),
    setCurrentTime: (currentTime) => update((state) => ({ ...state, currentTime })),
    setDuration: (duration) => update((state) => ({ ...state, duration })),
    setError: (error) => update((state) => ({ ...state, error })),
    reset: () => set({
      text: "",
      timingData: [],
      audioUrl: null,
      isPlaying: false,
      isLoading: false,
      currentTime: 0,
      duration: 0,
      error: null
    })
  };
}
const playerStore = createPlayerStore();
function AudioPlayer($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    var $$store_subs;
    function formatTime(seconds) {
      const mins = Math.floor(seconds / 60);
      const secs = Math.floor(seconds % 60);
      return `${mins}:${secs.toString().padStart(2, "0")}`;
    }
    onDestroy(() => {
      if (store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl) {
        URL.revokeObjectURL(store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl);
      }
    });
    if (
      // Update current segment based on time
      // Scroll segment into view
      // Setup MediaSession API for lock screen controls (Android)
      store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl
    ) {
      $$renderer2.push("<!--[-->");
      $$renderer2.push(`<div class="bg-white rounded-xl shadow-lg p-6 space-y-4 sticky bottom-0"><h3 class="text-lg font-semibold text-gray-900">Audio Player</h3> <audio preload="auto"></audio> <div class="space-y-2"><div class="relative h-2 bg-gray-200 rounded-full cursor-pointer" role="progressbar" tabindex="0"${attr("aria-valuenow", store_get($$store_subs ??= {}, "$playerStore", playerStore).currentTime)} aria-valuemin="0"${attr("aria-valuemax", store_get($$store_subs ??= {}, "$playerStore", playerStore).duration)}><div class="absolute h-full bg-primary-600 rounded-full"${attr_style(`width: ${stringify(store_get($$store_subs ??= {}, "$playerStore", playerStore).currentTime / store_get($$store_subs ??= {}, "$playerStore", playerStore).duration * 100 || 0)}%`)}></div></div> <div class="flex justify-between text-sm text-gray-600"><span>${escape_html(formatTime(store_get($$store_subs ??= {}, "$playerStore", playerStore).currentTime))}</span> <span>${escape_html(formatTime(store_get($$store_subs ??= {}, "$playerStore", playerStore).duration))}</span></div></div> <div class="flex items-center justify-center gap-4"><button class="btn btn-secondary"${attr("disabled", !store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl, true)}>⏪ 10s</button> `);
      if (store_get($$store_subs ??= {}, "$playerStore", playerStore).isPlaying) {
        $$renderer2.push("<!--[-->");
        $$renderer2.push(`<button class="btn btn-primary">⏸️ Pause</button>`);
      } else {
        $$renderer2.push("<!--[!-->");
        $$renderer2.push(`<button class="btn btn-primary"${attr("disabled", !store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl, true)}>▶️ Play</button>`);
      }
      $$renderer2.push(`<!--]--> <button class="btn btn-secondary"${attr("disabled", !store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl, true)}>10s ⏩</button></div> `);
      if (store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl) {
        $$renderer2.push("<!--[-->");
        $$renderer2.push(`<a${attr("href", store_get($$store_subs ??= {}, "$playerStore", playerStore).audioUrl)} download="tts-audio.mp3" class="btn btn-secondary w-full text-center block">⬇️ Download MP3</a>`);
      } else {
        $$renderer2.push("<!--[!-->");
      }
      $$renderer2.push(`<!--]--></div>`);
    } else {
      $$renderer2.push("<!--[!-->");
    }
    $$renderer2.push(`<!--]-->`);
    if ($$store_subs) unsubscribe_stores($$store_subs);
  });
}
function TextInput($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    var $$store_subs;
    let text = "";
    let voice = "af_heart";
    let speed = 1;
    $$renderer2.push(`<div class="bg-white rounded-xl shadow-lg p-6 space-y-6"><h2 class="text-xl font-semibold text-gray-900">Generate Speech</h2> <div><label for="text" class="block text-sm font-medium text-gray-700 mb-2">Enter Text or Upload Document</label> <textarea id="text" rows="6" class="input resize-none" placeholder="Type or paste text here...">`);
    const $$body = escape_html(text);
    if ($$body) {
      $$renderer2.push(`${$$body}`);
    }
    $$renderer2.push(`</textarea></div> <div><label class="block text-sm font-medium text-gray-700 mb-2">Upload Document (PDF, DOCX, TXT)</label> <input type="file" accept=".pdf,.docx,.txt" class="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-primary-50 file:text-primary-700 hover:file:bg-primary-100 file:cursor-pointer cursor-pointer"/></div> <div class="grid grid-cols-1 sm:grid-cols-2 gap-4"><div><label for="voice" class="block text-sm font-medium text-gray-700 mb-2">Voice</label> `);
    $$renderer2.select({ id: "voice", value: voice, class: "input" }, ($$renderer3) => {
      $$renderer3.option({ value: "af_heart" }, ($$renderer4) => {
        $$renderer4.push(`Female (Heart)`);
      });
      $$renderer3.option({ value: "af_nova" }, ($$renderer4) => {
        $$renderer4.push(`Female (Nova)`);
      });
      $$renderer3.option({ value: "af_sky" }, ($$renderer4) => {
        $$renderer4.push(`Female (Sky)`);
      });
      $$renderer3.option({ value: "af_bella" }, ($$renderer4) => {
        $$renderer4.push(`Female (Bella)`);
      });
      $$renderer3.option({ value: "af_sarah" }, ($$renderer4) => {
        $$renderer4.push(`Female (Sarah)`);
      });
      $$renderer3.option({ value: "am_adam" }, ($$renderer4) => {
        $$renderer4.push(`Male (Adam)`);
      });
      $$renderer3.option({ value: "am_michael" }, ($$renderer4) => {
        $$renderer4.push(`Male (Michael)`);
      });
      $$renderer3.option({ value: "bf_emma" }, ($$renderer4) => {
        $$renderer4.push(`British Female (Emma)`);
      });
      $$renderer3.option({ value: "bf_isabella" }, ($$renderer4) => {
        $$renderer4.push(`British Female (Isabella)`);
      });
      $$renderer3.option({ value: "bm_george" }, ($$renderer4) => {
        $$renderer4.push(`British Male (George)`);
      });
      $$renderer3.option({ value: "bm_lewis" }, ($$renderer4) => {
        $$renderer4.push(`British Male (Lewis)`);
      });
    });
    $$renderer2.push(`</div> <div><label for="speed" class="block text-sm font-medium text-gray-700 mb-2">Speed: ${escape_html(speed)}x</label> <input id="speed" type="range"${attr("value", speed)} min="0.5" max="2.0" step="0.1" class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"/></div></div> <button${attr("disabled", store_get($$store_subs ??= {}, "$playerStore", playerStore).isLoading || !text.trim(), true)} class="btn btn-primary w-full">${escape_html(store_get($$store_subs ??= {}, "$playerStore", playerStore).isLoading ? "Generating..." : "Generate Speech")}</button> `);
    if (store_get($$store_subs ??= {}, "$playerStore", playerStore).error) {
      $$renderer2.push("<!--[-->");
      $$renderer2.push(`<div class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">${escape_html(store_get($$store_subs ??= {}, "$playerStore", playerStore).error)}</div>`);
    } else {
      $$renderer2.push("<!--[!-->");
    }
    $$renderer2.push(`<!--]--></div>`);
    if ($$store_subs) unsubscribe_stores($$store_subs);
  });
}
function TextDisplay($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    var $$store_subs;
    function isHighlighted(segment, currentTime) {
      return currentTime >= segment.start && currentTime < segment.end;
    }
    if (store_get($$store_subs ??= {}, "$playerStore", playerStore).text && store_get($$store_subs ??= {}, "$playerStore", playerStore).timingData.length > 0) {
      $$renderer2.push("<!--[-->");
      $$renderer2.push(`<div class="bg-white rounded-xl shadow-lg p-6"><h3 class="text-lg font-semibold text-gray-900 mb-4">Text (Synchronized)</h3> <div class="prose max-w-none"><!--[-->`);
      const each_array = ensure_array_like(store_get($$store_subs ??= {}, "$playerStore", playerStore).timingData);
      for (let index = 0, $$length = each_array.length; index < $$length; index++) {
        let segment = each_array[index];
        $$renderer2.push(`<span${attr("id", `segment-${stringify(index)}`)}${attr_class("text-segment", void 0, {
          "highlighted": isHighlighted(segment, store_get($$store_subs ??= {}, "$playerStore", playerStore).currentTime)
        })}>${escape_html(segment.text)} </span>`);
      }
      $$renderer2.push(`<!--]--></div></div>`);
    } else {
      $$renderer2.push("<!--[!-->");
    }
    $$renderer2.push(`<!--]-->`);
    if ($$store_subs) unsubscribe_stores($$store_subs);
  });
}
function AudioHistory($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    let history = [];
    let loading = false;
    function formatDate(timestamp) {
      const date = new Date(timestamp);
      const now = /* @__PURE__ */ new Date();
      const diffMs = now - date;
      const diffMins = Math.floor(diffMs / 6e4);
      const diffHours = Math.floor(diffMs / 36e5);
      const diffDays = Math.floor(diffMs / 864e5);
      if (diffMins < 1) return "Just now";
      if (diffMins < 60) return `${diffMins}m ago`;
      if (diffHours < 24) return `${diffHours}h ago`;
      if (diffDays < 7) return `${diffDays}d ago`;
      return date.toLocaleDateString();
    }
    function truncateText(text, maxLength = 60) {
      if (text.length <= maxLength) return text;
      return text.slice(0, maxLength) + "...";
    }
    $$renderer2.push(`<div class="bg-white rounded-xl shadow-lg p-6 space-y-4"><div class="flex justify-between items-center"><h3 class="text-lg font-semibold text-gray-900">History</h3> <button class="text-sm text-primary-600 hover:text-primary-700"${attr("disabled", loading, true)}>${escape_html("🔄 Refresh")}</button></div> `);
    {
      $$renderer2.push("<!--[!-->");
      if (history.length === 0) {
        $$renderer2.push("<!--[-->");
        $$renderer2.push(`<div class="text-center py-8 text-gray-500">No saved audio yet. Generate some speech to see it here!</div>`);
      } else {
        $$renderer2.push("<!--[!-->");
        $$renderer2.push(`<div class="space-y-3 max-h-96 overflow-y-auto"><!--[-->`);
        const each_array = ensure_array_like(history);
        for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
          let item = each_array[$$index];
          $$renderer2.push(`<div class="border border-gray-200 rounded-lg p-4 hover:bg-gray-50 transition-colors"><div class="flex items-start justify-between gap-3"><div class="flex-1 min-w-0"><p class="text-sm text-gray-900 mb-1">${escape_html(truncateText(item.text))}</p> <div class="flex items-center gap-3 text-xs text-gray-500"><span>${escape_html(formatDate(item.timestamp))}</span> <span>•</span> <span>${escape_html(item.voice)}</span> <span>•</span> <span>${escape_html(item.speed)}x</span></div></div> <div class="flex gap-2"><button class="px-3 py-1 bg-primary-600 text-white text-sm rounded-lg hover:bg-primary-700">▶️ Play</button> <button class="px-3 py-1 bg-red-50 text-red-600 text-sm rounded-lg hover:bg-red-100">🗑️</button></div></div></div>`);
        }
        $$renderer2.push(`<!--]--></div>`);
      }
      $$renderer2.push(`<!--]-->`);
    }
    $$renderer2.push(`<!--]--></div>`);
  });
}
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    $$renderer2.push(`<div class="min-h-screen flex flex-col"><header class="bg-white shadow-sm border-b border-gray-200"><div class="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center"><h1 class="text-2xl font-bold text-primary-600">Open Mobile TTS</h1> <button class="btn btn-secondary text-sm">Logout</button></div></header> <main class="flex-1 max-w-7xl mx-auto w-full px-4 py-6 space-y-6">`);
    TextInput($$renderer2);
    $$renderer2.push(`<!----> `);
    TextDisplay($$renderer2);
    $$renderer2.push(`<!----> `);
    AudioPlayer($$renderer2);
    $$renderer2.push(`<!----> `);
    AudioHistory($$renderer2);
    $$renderer2.push(`<!----></main> `);
    if (/iPhone|iPad|iPod/.test(navigator.userAgent)) {
      $$renderer2.push("<!--[-->");
      $$renderer2.push(`<div class="bg-yellow-50 border-t border-yellow-200 p-4"><p class="text-sm text-yellow-800 text-center">📱 iOS Limitation: Audio will stop when app is minimized or screen locks.
				Keep app in foreground during playback.</p></div>`);
    } else {
      $$renderer2.push("<!--[!-->");
    }
    $$renderer2.push(`<!--]--></div>`);
  });
}
export {
  _page as default
};
