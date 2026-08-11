export {};

declare global {
	interface Window {
		Android?: {
			restartApp?: (modelId?: string, modelLabel?: string, destination?: string) => void;
			onGenerationStarted?: () => void;
			onPlaybackStarted?: () => void;
			onPlaybackPaused?: () => void;
			onPlaybackStopped?: () => void;
			updateGenerationProgress?: (current: number, total: number) => void;
			updatePlaybackProgress?: (positionMs: number, durationMs: number) => void;
			saveAudioFile?: (base64Data: string, filename: string, mimeType: string) => void;
		};
		__ttsControl?: {
			play: () => void;
			pause: () => void;
			stop: () => void;
			seekTo: (positionMs: number) => void;
			next: () => void;
			previous: () => void;
		};
		webkitAudioContext?: typeof AudioContext;
	}
}
