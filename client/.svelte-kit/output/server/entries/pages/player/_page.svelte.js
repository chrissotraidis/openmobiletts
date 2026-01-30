import "clsx";
import "../../../chunks/auth.js";
import "@sveltejs/kit/internal";
import { w as writable } from "../../../chunks/exports.js";
import "../../../chunks/utils.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/state.svelte.js";
function createPlayerStore() {
  const { subscribe, set, update } = writable({
    text: "",
    timingData: [],
    audioUrl: null,
    audioBlob: null,
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
    setAudioBlob: (audioBlob) => update((state) => ({ ...state, audioBlob })),
    setPlaying: (isPlaying) => update((state) => ({ ...state, isPlaying })),
    setLoading: (isLoading) => update((state) => ({ ...state, isLoading })),
    setCurrentTime: (currentTime) => update((state) => ({ ...state, currentTime })),
    setDuration: (duration) => update((state) => ({ ...state, duration })),
    setError: (error) => update((state) => ({ ...state, error })),
    reset: () => set({
      text: "",
      timingData: [],
      audioUrl: null,
      audioBlob: null,
      isPlaying: false,
      isLoading: false,
      currentTime: 0,
      duration: 0,
      error: null
    })
  };
}
createPlayerStore();
function createHistoryStore() {
  const { subscribe, update } = writable({
    lastUpdated: Date.now()
  });
  return {
    subscribe,
    notifyUpdate: () => {
      update((state) => ({ ...state, lastUpdated: Date.now() }));
    }
  };
}
createHistoryStore();
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    {
      $$renderer2.push("<!--[!-->");
      $$renderer2.push(`<div class="flex items-center justify-center min-h-screen"><p class="text-gray-600">Checking authentication...</p></div>`);
    }
    $$renderer2.push(`<!--]-->`);
  });
}
export {
  _page as default
};
