export const manifest = (() => {
function __memo(fn) {
	let value;
	return () => value ??= (value = fn());
}

return {
	appDir: "_app",
	appPath: "_app",
	assets: new Set(["favicon.png","icon-192.png","icon-512.png","manifest.json","sw.js","test.html"]),
	mimeTypes: {".png":"image/png",".json":"application/json",".js":"text/javascript",".html":"text/html"},
	_: {
		client: {start:"_app/immutable/entry/start.CtarZU8n.js",app:"_app/immutable/entry/app.BGEUMFkv.js",imports:["_app/immutable/entry/start.CtarZU8n.js","_app/immutable/chunks/DEeGOvap.js","_app/immutable/chunks/_ZM0HBor.js","_app/immutable/chunks/CAdreZCq.js","_app/immutable/chunks/BUApaBEI.js","_app/immutable/entry/app.BGEUMFkv.js","_app/immutable/chunks/_ZM0HBor.js","_app/immutable/chunks/_fpMHx7C.js","_app/immutable/chunks/a1OpiWwP.js","_app/immutable/chunks/CAdreZCq.js","_app/immutable/chunks/CevPxRnU.js","_app/immutable/chunks/DdNNHlwz.js","_app/immutable/chunks/CPdKxBGB.js"],stylesheets:[],fonts:[],uses_env_dynamic_public:false},
		nodes: [
			__memo(() => import('./nodes/0.js')),
			__memo(() => import('./nodes/1.js')),
			__memo(() => import('./nodes/2.js')),
			__memo(() => import('./nodes/3.js')),
			__memo(() => import('./nodes/4.js'))
		],
		remotes: {
			
		},
		routes: [
			{
				id: "/",
				pattern: /^\/$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 2 },
				endpoint: null
			},
			{
				id: "/login",
				pattern: /^\/login\/?$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 3 },
				endpoint: null
			},
			{
				id: "/player",
				pattern: /^\/player\/?$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 4 },
				endpoint: null
			}
		],
		prerendered_routes: new Set([]),
		matchers: async () => {
			
			return {  };
		},
		server_assets: {}
	}
}
})();
