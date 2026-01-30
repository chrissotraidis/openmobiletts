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
      set({ token, isAuthenticated: true });
    },
    clearToken: () => {
      set({ token: null, isAuthenticated: false });
    }
  };
}
createAuthStore();
