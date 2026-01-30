import * as universal from '../entries/pages/_page.js';

export const index = 2;
let component_cache;
export const component = async () => component_cache ??= (await import('../entries/pages/_page.svelte.js')).default;
export { universal };
export const universal_id = "src/routes/+page.js";
export const imports = ["_app/immutable/nodes/2.CN0LmzHJ.js","_app/immutable/chunks/BUApaBEI.js","_app/immutable/chunks/a1OpiWwP.js","_app/immutable/chunks/_ZM0HBor.js","_app/immutable/chunks/CEhDTjoP.js"];
export const stylesheets = [];
export const fonts = [];
