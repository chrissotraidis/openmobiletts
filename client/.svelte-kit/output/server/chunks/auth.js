import { w as writable } from "./exports.js";
function createAuthStore() {
  const storedToken = null;
  const { subscribe, set, update } = writable({
    token: storedToken,
    isAuthenticated: false
  });
  return {
    subscribe,
    setToken: (token) => {
      update(() => ({ token, isAuthenticated: true }));
    },
    clearToken: () => {
      update(() => ({ token: null, isAuthenticated: false }));
    }
  };
}
createAuthStore();
