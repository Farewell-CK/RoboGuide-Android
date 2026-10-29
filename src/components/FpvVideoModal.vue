<template>
  <div class="fixed inset-0 z-50 bg-black/90 backdrop-blur-md flex flex-col justify-between p-3 animate-fade-in select-none">
    <!-- Top Bar -->
    <div class="flex items-center justify-between bg-slate-900/90 border border-slate-700/80 px-3 py-2 rounded-xl">
      <div class="flex items-center space-x-2">
        <span class="w-2.5 h-2.5 rounded-full bg-red-500 animate-ping"></span>
        <span class="text-xs font-bold text-white tracking-wide">
          {{ targetDevice ? targetDevice.name : '实时机载第一人称视锥 (FPV)' }}
        </span>
        <span class="text-[10px] bg-red-500/20 text-red-400 font-mono px-1.5 py-0.5 rounded border border-red-500/30">
          LIVE
        </span>
      </div>
      <button 
        @click="$emit('close')"
        class="w-7 h-7 rounded-full bg-slate-800 active:bg-slate-700 text-slate-300 flex items-center justify-center text-xs font-bold"
      >
        ✕
      </button>
    </div>

    <!-- Center Video Viewport with HUD -->
    <div class="relative flex-1 my-3 bg-black rounded-xl overflow-hidden border border-slate-800 flex items-center justify-center">
      <!-- Simulated Camera Canvas -->
      <canvas ref="fpvCanvas" class="w-full h-full block object-cover"></canvas>

      <!-- FPV HUD Crosshair & Overlays -->
      <div class="absolute inset-0 pointer-events-none p-3 flex flex-col justify-between">
        <!-- Top HUD Metrics -->
        <div class="flex items-center justify-between text-[11px] font-mono text-cyan-400 bg-slate-950/60 backdrop-blur px-2.5 py-1 rounded-lg border border-cyan-500/30">
          <div class="flex items-center space-x-3">
            <span>RES: 1080p</span>
            <span>FPS: 30</span>
            <span>LATENCY: 42ms</span>
          </div>
          <div class="flex items-center space-x-2">
            <span>BAT: {{ targetDevice?.batteryPercent || 95 }}%</span>
            <span>SPD: {{ (targetDevice?.linearVelocity || 0).toFixed(2) }} m/s</span>
          </div>
        </div>

        <!-- Center Crosshair Target -->
        <div class="self-center flex flex-col items-center justify-center opacity-70">
          <div class="w-16 h-16 border border-cyan-400/50 rounded-lg flex items-center justify-center relative">
            <div class="w-2 h-2 bg-cyan-400 rounded-full"></div>
            <!-- Brackets -->
            <div class="absolute -top-1 -left-1 w-3 h-3 border-t-2 border-l-2 border-cyan-400"></div>
            <div class="absolute -top-1 -right-1 w-3 h-3 border-t-2 border-r-2 border-cyan-400"></div>
            <div class="absolute -bottom-1 -left-1 w-3 h-3 border-b-2 border-l-2 border-cyan-400"></div>
            <div class="absolute -bottom-1 -right-1 w-3 h-3 border-b-2 border-r-2 border-cyan-400"></div>
          </div>
          <span class="text-[9px] font-mono text-cyan-300 mt-1 bg-black/60 px-1 rounded">AIM: FORWARD</span>
        </div>

        <!-- Bottom Compass Pitch/Roll Tape -->
        <div class="self-center bg-slate-950/70 border border-slate-700/80 px-4 py-1 rounded-full text-[10px] font-mono text-slate-300">
          YAW: {{ ((targetDevice?.pose.theta || 0) * (180 / Math.PI)).toFixed(1) }}° | PITCH: 0.2° | ROLL: -0.1°
        </div>
      </div>
    </div>

    <!-- Bottom Controls & Quick Driving -->
    <div class="bg-slate-900/90 border border-slate-700/80 p-3 rounded-xl flex items-center justify-between">
      <div class="text-xs text-slate-400">
        <span class="font-bold text-white">推流协议:</span>
        <span class="font-mono text-blue-400 ml-1">WebRTC (H.264)</span>
      </div>

      <div class="flex items-center space-x-2">
        <button 
          @click="toggleNightVision"
          :class="[
            'px-2.5 py-1.5 rounded-lg text-xs font-bold border transition',
            nightVision ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' : 'bg-slate-800 text-slate-300 border-slate-700'
          ]"
        >
          👁️ 夜视增强
        </button>
        <button 
          @click="$emit('open-teleop', targetDevice?.id)"
          class="px-3 py-1.5 bg-blue-600 active:bg-blue-500 text-white rounded-lg text-xs font-bold transition shadow-lg shadow-blue-500/20"
        >
          🎮 打开操作摇杆
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { swarmState } from '../services/telemetryService';

const props = defineProps<{
  deviceId: string | null;
}>();

defineEmits(['close', 'open-teleop']);

const fpvCanvas = ref<HTMLCanvasElement | null>(null);
const nightVision = ref(false);
let animId: number | null = null;
let t = 0;

const targetDevice = computed(() => {
  return swarmState.devices.find(d => d.id === props.deviceId) || swarmState.devices[0];
});

function toggleNightVision() {
  nightVision.value = !nightVision.value;
}

function renderFpv() {
  const canvas = fpvCanvas.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  t += 0.04;

  // Background synthetic warehouse camera view
  const grad = ctx.createLinearGradient(0, 0, 0, canvas.height);
  if (nightVision.value) {
    grad.addColorStop(0, '#021a08');
    grad.addColorStop(0.5, '#042e10');
    grad.addColorStop(1, '#021808');
  } else {
    grad.addColorStop(0, '#0c1524');
    grad.addColorStop(0.5, '#1e293b');
    grad.addColorStop(1, '#0f172a');
  }
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Perspective floor grid lines (moving with robot velocity)
  ctx.save();
  ctx.strokeStyle = nightVision.value ? 'rgba(34, 197, 94, 0.25)' : 'rgba(56, 189, 248, 0.2)';
  ctx.lineWidth = 1.5;

  const horizon = canvas.height * 0.45;
  const speed = targetDevice.value?.linearVelocity || 0.1;
  const offset = (t * speed * 80) % 40;

  // Horizontal floor lines
  for (let y = horizon; y < canvas.height; y += 25) {
    const curY = y + offset;
    if (curY > horizon && curY < canvas.height) {
      ctx.beginPath();
      ctx.moveTo(0, curY);
      ctx.lineTo(canvas.width, curY);
      ctx.stroke();
    }
  }

  // Vanishing lines to horizon
  const centerX = canvas.width / 2;
  for (let x = -canvas.width; x <= canvas.width * 2; x += 60) {
    ctx.beginPath();
    ctx.moveTo(centerX, horizon);
    ctx.lineTo(x, canvas.height);
    ctx.stroke();
  }

  // Draw simulated obstacle / pallet in view
  const boxX = centerX + Math.sin(t * 0.5) * 80;
  const boxY = horizon + 40;
  const boxW = 70;
  const boxH = 50;

  ctx.fillStyle = nightVision.value ? 'rgba(34, 197, 94, 0.3)' : 'rgba(234, 179, 8, 0.25)';
  ctx.fillRect(boxX - boxW / 2, boxY, boxW, boxH);
  ctx.strokeStyle = nightVision.value ? '#22c55e' : '#eab308';
  ctx.strokeRect(boxX - boxW / 2, boxY, boxW, boxH);

  // Detection Bounding Box Tag
  ctx.fillStyle = nightVision.value ? '#22c55e' : '#eab308';
  ctx.font = 'bold 9px monospace';
  ctx.fillText('DETECT: PALLET_RACK (0.94)', boxX - boxW / 2, boxY - 4);

  ctx.restore();

  animId = requestAnimationFrame(renderFpv);
}

onMounted(() => {
  if (fpvCanvas.value) {
    fpvCanvas.value.width = fpvCanvas.value.clientWidth || 400;
    fpvCanvas.value.height = fpvCanvas.value.clientHeight || 260;
  }
  renderFpv();
});

onUnmounted(() => {
  if (animId) cancelAnimationFrame(animId);
});
</script>
