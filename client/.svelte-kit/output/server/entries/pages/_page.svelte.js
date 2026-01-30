import "clsx";
import "@sveltejs/kit/internal";
import "../../chunks/exports.js";
import "../../chunks/utils.js";
import "@sveltejs/kit/internal/server";
import "../../chunks/state.svelte.js";
import "../../chunks/auth.js";
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    $$renderer2.push(`<div class="flex items-center justify-center min-h-screen"><div class="text-center"><h1 class="text-4xl font-bold text-primary-600 mb-4">Open Mobile TTS</h1> <p class="text-gray-600">Loading...</p></div></div>`);
  });
}
export {
  _page as default
};
