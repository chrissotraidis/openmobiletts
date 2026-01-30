import { b as attr } from "../../../chunks/attributes.js";
import { e as escape_html } from "../../../chunks/escaping.js";
import "@sveltejs/kit/internal";
import "../../../chunks/exports.js";
import "../../../chunks/utils.js";
import "@sveltejs/kit/internal/server";
import "../../../chunks/state.svelte.js";
import "../../../chunks/auth.js";
function _page($$renderer, $$props) {
  $$renderer.component(($$renderer2) => {
    let username = "";
    let password = "";
    let loading = false;
    $$renderer2.push(`<div class="min-h-screen flex items-center justify-center px-4 bg-gradient-to-br from-primary-50 to-blue-100"><div class="w-full max-w-md"><div class="bg-white rounded-2xl shadow-xl p-8"><div class="text-center mb-8"><h1 class="text-3xl font-bold text-primary-600 mb-2">Open Mobile TTS</h1> <p class="text-gray-600">Sign in to continue</p></div> <form class="space-y-6">`);
    {
      $$renderer2.push("<!--[!-->");
    }
    $$renderer2.push(`<!--]--> <div><label for="username" class="block text-sm font-medium text-gray-700 mb-2">Username</label> <input id="username" type="text"${attr("value", username)} required${attr("disabled", loading, true)} class="input" placeholder="admin"/></div> <div><label for="password" class="block text-sm font-medium text-gray-700 mb-2">Password</label> <input id="password" type="password"${attr("value", password)} required${attr("disabled", loading, true)} class="input" placeholder="testpassword123"/></div> <button type="submit"${attr("disabled", loading, true)} class="btn btn-primary w-full">${escape_html("Sign In")}</button> <button type="button" class="btn btn-secondary w-full">Test API Connection</button></form> `);
    {
      $$renderer2.push("<!--[!-->");
    }
    $$renderer2.push(`<!--]--></div></div></div>`);
  });
}
export {
  _page as default
};
