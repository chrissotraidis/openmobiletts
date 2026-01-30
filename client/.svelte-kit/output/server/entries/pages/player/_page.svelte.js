import "clsx";
import "../../../chunks/auth.js";
import { w as writable, i as get } from "../../../chunks/exports.js";
import "@sveltejs/kit/internal";
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
const playerStore = createPlayerStore();
function createQueueStore() {
  const { subscribe, set, update } = writable({
    items: [],
    // Array of { id, text, audioBlob, timingData, voice, speed, timestamp }
    currentIndex: -1
  });
  return {
    subscribe,
    /** Add an item to the end of the queue */
    add(item) {
      update((state) => ({
        ...state,
        items: [...state.items, { ...item, queueId: Date.now() + Math.random() }]
      }));
    },
    /** Remove an item by its queueId */
    remove(queueId) {
      update((state) => {
        const idx = state.items.findIndex((i) => i.queueId === queueId);
        if (idx === -1) return state;
        const items = state.items.filter((i) => i.queueId !== queueId);
        let currentIndex = state.currentIndex;
        if (idx < currentIndex) {
          currentIndex--;
        } else if (idx === currentIndex) {
          currentIndex = Math.min(currentIndex, items.length - 1);
        }
        return { items, currentIndex };
      });
    },
    /** Reorder: move item from fromIndex to toIndex */
    reorder(fromIndex, toIndex) {
      update((state) => {
        const items = [...state.items];
        const [moved] = items.splice(fromIndex, 1);
        items.splice(toIndex, 0, moved);
        let currentIndex = state.currentIndex;
        if (state.currentIndex === fromIndex) {
          currentIndex = toIndex;
        } else if (fromIndex < state.currentIndex && toIndex >= state.currentIndex) {
          currentIndex--;
        } else if (fromIndex > state.currentIndex && toIndex <= state.currentIndex) {
          currentIndex++;
        }
        return { items, currentIndex };
      });
    },
    /** Play a specific item in the queue by index */
    playIndex(index) {
      const state = get({ subscribe });
      if (index < 0 || index >= state.items.length) return;
      const item = state.items[index];
      const audioUrl = URL.createObjectURL(item.audioBlob);
      playerStore.setAudioBlob(item.audioBlob);
      playerStore.setAudioUrl(audioUrl);
      playerStore.setTimingData(item.timingData || []);
      playerStore.setText(item.text);
      update((s) => ({ ...s, currentIndex: index }));
      setTimeout(() => playerStore.setPlaying(true), 100);
    },
    /** Play the next item in queue (called when current track ends) */
    playNext() {
      const state = get({ subscribe });
      const nextIndex = state.currentIndex + 1;
      if (nextIndex < state.items.length) {
        this.playIndex(nextIndex);
        return true;
      }
      return false;
    },
    /** Clear the entire queue */
    clear() {
      set({ items: [], currentIndex: -1 });
    }
  };
}
createQueueStore();
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
const defaults = {
  defaultVoice: "af_heart",
  defaultSpeed: 1,
  autoPlay: true
};
function loadSettings() {
  return defaults;
}
function createSettingsStore() {
  const { subscribe, set, update } = writable(loadSettings());
  return {
    subscribe,
    update(key, value) {
      update((state) => {
        const next = { ...state, [key]: value };
        return next;
      });
    },
    reset() {
      set(defaults);
    }
  };
}
createSettingsStore();
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    {
      $$renderer2.push("<!--[!-->");
      $$renderer2.push(`<div class="flex items-center justify-center min-h-screen bg-[#0a0c10]"><p class="text-slate-500">Checking authentication...</p></div>`);
    }
    $$renderer2.push(`<!--]-->`);
  });
}
export {
  _page as default
};
