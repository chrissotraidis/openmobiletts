<script>
	import { onMount, onDestroy } from 'svelte';

	let { stream = null } = $props();

	let canvas;
	let animationId;
	let analyser;
	let dataArray;
	let audioContext;
	let canvasW = 0;
	let canvasH = 0;

	onMount(() => {
		if (!stream || !canvas) return;

		audioContext = new (window.AudioContext || window.webkitAudioContext)();
		const source = audioContext.createMediaStreamSource(stream);
		analyser = audioContext.createAnalyser();
		analyser.fftSize = 256;
		analyser.smoothingTimeConstant = 0.7;
		source.connect(analyser);

		const bufferLength = analyser.frequencyBinCount;
		dataArray = new Uint8Array(bufferLength);

		// Size canvas once after layout is stable
		requestAnimationFrame(() => {
			sizeCanvas();
			draw();
		});

		return () => {
			if (animationId) cancelAnimationFrame(animationId);
			analyser = null; // stops the draw loop if a frame fires after cleanup
			if (audioContext) audioContext.close();
		};
	});

	function sizeCanvas() {
		if (!canvas) return;
		const dpr = window.devicePixelRatio || 1;
		canvasW = canvas.clientWidth;
		canvasH = canvas.clientHeight;
		canvas.width = canvasW * dpr;
		canvas.height = canvasH * dpr;
		const ctx = canvas.getContext('2d');
		ctx.scale(dpr, dpr);
	}

	function draw() {
		if (!canvas || !analyser) return;

		animationId = requestAnimationFrame(draw);
		analyser.getByteFrequencyData(dataArray);

		const ctx = canvas.getContext('2d');
		ctx.clearRect(0, 0, canvasW, canvasH);

		if (canvasW === 0 || canvasH === 0) {
			sizeCanvas(); // retry sizing — layout may not have been ready on first call
			if (canvasW === 0 || canvasH === 0) return;
		}

		// Draw frequency bars — centered vertically
		const barCount = 40;
		const gap = 2;
		const barWidth = (canvasW - (barCount - 1) * gap) / barCount;
		const step = Math.floor(dataArray.length / barCount);

		for (let i = 0; i < barCount; i++) {
			const value = dataArray[i * step] / 255;
			const barHeight = Math.max(2, value * (canvasH * 0.8));

			// Blue to purple gradient based on position
			const ratio = i / barCount;
			const r = Math.round(59 + ratio * 80);
			const g = Math.round(130 - ratio * 30);
			const b = Math.round(246 + ratio * 9);

			ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${0.6 + value * 0.4})`;

			const x = i * (barWidth + gap);
			const y = (canvasH - barHeight) / 2;

			// Rounded bars
			const radius = Math.min(barWidth / 2, 3);
			ctx.beginPath();
			ctx.moveTo(x + radius, y);
			ctx.lineTo(x + barWidth - radius, y);
			ctx.quadraticCurveTo(x + barWidth, y, x + barWidth, y + radius);
			ctx.lineTo(x + barWidth, y + barHeight - radius);
			ctx.quadraticCurveTo(x + barWidth, y + barHeight, x + barWidth - radius, y + barHeight);
			ctx.lineTo(x + radius, y + barHeight);
			ctx.quadraticCurveTo(x, y + barHeight, x, y + barHeight - radius);
			ctx.lineTo(x, y + radius);
			ctx.quadraticCurveTo(x, y, x + radius, y);
			ctx.fill();
		}
	}
</script>

<canvas
	bind:this={canvas}
	class="w-full h-12 rounded-lg"
></canvas>
