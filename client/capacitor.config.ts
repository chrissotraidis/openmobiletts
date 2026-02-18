import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
	appId: 'com.openmobiletts.app',
	appName: 'Open Mobile TTS',
	webDir: 'build',
	android: {
		allowMixedContent: true,
	},
};

export default config;
