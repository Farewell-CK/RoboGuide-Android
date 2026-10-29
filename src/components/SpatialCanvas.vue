<template>
  <div class="relative w-full h-full bg-[#080b10] overflow-hidden select-none touch-none">
    <!-- Main Canvas -->
    <canvas 
      ref="canvasRef" 
      class="w-full h-full block cursor-grab active:cursor-grabbing"
      @mousedown="startPan"
      @mousemove="doPan"
      @mouseup="endPan"
      @mouseleave="endPan"
      @touchstart="handleTouchStart"
      @touchmove="handleTouchMove"
      @touchend="handleTouchEnd"
    ></canvas>

    <!-- Top Overlay HUD -->
    <div class="absolute top-3 left-3 bg-deaios-card/85 backdrop-blur-md border border-deaios-border px-3 py-1.5 rounded-lg text-xs pointer-events-none flex items-center space-x-2">
      <div class="w-2 h-2 rounded-full bg-blue-400 animate-pulse"></div>
      <span class="font-mono text-slate-300">RTAB-Map (0.05m)</span>
      <span class="text-slate-500">|</span>
      <span class="text-slate-400 font-mono">{{ swarmState.mapData.width }}×{{ swarmState.mapData.height }}</span>
    </div>

    <!-- Quick Map Controls (Zoom / Recenter) -->
    <div class="absolute right-3 top-3 flex flex-col space-y-1.5 z-10">
      <button 
        @click="zoomIn" 
        class="w-8 h-8 rounded-lg bg-deaios-card/90 border border-deaios-border text-slate-200 text-lg flex items-center justify-center active:bg-blue-600 transition shadow"
      >
        +
      </button>
      <button 
        @click="zoomOut" 
        class="w-8 h-8 rounded-lg bg-deaios-card/90 border border-deaios-border text-slate-200 text-lg flex items-center justify-center active:bg-blue-600 transition shadow"
      >
        −
      </button>
      <button 
        @click="resetView" 
        class="w-8 h-8 rounded-lg bg-deaios-card/90 border border-deaios-border text-slate-200 text-xs flex items-center justify-center active:bg-blue-600 transition shadow"
        title="复位"
      >
        🎯
      </button>
    </div>

    <!-- Map Legend Footer -->
    <div class="absolute bottom-2 left-2 right-2 bg-deaios-card/80 backdrop-blur border border-deaios-border/70 px-3 py-1.5 rounded-lg flex items-center justify-around text-[10px] text-slate-300">
      <div class="flex items-center space-x-1">
        <span class="w-2.5 h-2.5 rounded-full bg-cyan-400"></span>
        <span>狗2 (Thor)</span>
      </div>
      <div class="flex items-center space-x-1">
        <span class="w-2.5 h-2.5 rounded-full bg-blue-400 opacity-60"></span>
        <span>狗1 (待机)</span>
      </div>
      <div class="flex items-center space-x-1">
        <span class="w-2.5 h-2.5 rounded-sm bg-purple-400"></span>
        <span>AGV 车</span>
      </div>
      <div class="flex items-center space-x-1">
        <span class="w-2.5 h-2.5 rounded-full bg-emerald-400 ring-2 ring-emerald-400/30"></span>
        <span>持机人</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { swarmState } from '../services/telemetryService';

const canvasRef = ref<HTMLCanvasElement | null>(null);

// Pan & Zoom transform state
const scale = ref(38); // pixels per meter
const offsetX = ref(0);
const offsetY = ref(0);

let isPanning = false;
let startX = 0;
let startY = 0;
let touchDist = 0;
let animFrameId: number | null = null;

function resetView() {
  if (!canvasRef.value) return;
  offsetX.value = canvasRef.value.width / 2;
  offsetY.value = canvasRef.value.height / 2;
  scale.value = 40;
}

function zoomIn() {
  scale.value = Math.min(100, scale.value * 1.25);
}

function zoomOut() {
  scale.value = Math.max(15, scale.value * 0.8);
}

// Mouse events
function startPan(e: MouseEvent) {
  isPanning = true;
  startX = e.clientX - offsetX.value;
  startY = e.clientY - offsetY.value;
}

function doPan(e: MouseEvent) {
  if (!isPanning) return;
  offsetX.value = e.clientX - startX;
  offsetY.value = e.clientY - startY;
}

function endPan() {
  isPanning = false;
}

// Touch events for mobile
function handleTouchStart(e: TouchEvent) {
  if (e.touches.length === 1) {
    isPanning = true;
    startX = e.touches[0].clientX - offsetX.value;
    startY = e.touches[0].clientY - offsetY.value;
  } else if (e.touches.length === 2) {
    isPanning = false;
    const dx = e.touches[0].clientX - e.touches[1].clientX;
    const dy = e.touches[0].clientY - e.touches[1].clientY;
    touchDist = Math.hypot(dx, dy);
  }
}

function handleTouchMove(e: TouchEvent) {
  if (e.touches.length === 1 && isPanning) {
    offsetX.value = e.touches[0].clientX - startX;
    offsetY.value = e.touches[0].clientY - startY;
  } else if (e.touches.length === 2) {
    const dx = e.touches[0].clientX - e.touches[1].clientX;
    const dy = e.touches[0].clientY - e.touches[1].clientY;
    const dist = Math.hypot(dx, dy);
    if (touchDist > 0) {
      const factor = dist / touchDist;
      scale.value = Math.max(15, Math.min(100, scale.value * factor));
    }
    touchDist = dist;
  }
}

function handleTouchEnd() {
  isPanning = false;
  touchDist = 0;
}

// Rendering Loop
function render() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const w = canvas.width;
  const h = canvas.height;

  // Clear background
  ctx.fillStyle = '#0a0d14';
  ctx.fillRect(0, 0, w, h);

  ctx.save();
  ctx.translate(offsetX.value, offsetY.value);

  // 1. Draw Grid lines
  ctx.strokeStyle = '#151c28';
  ctx.lineWidth = 1;
  const gridSize = scale.value; // 1 meter grid
  const minX = -offsetX.value;
  const maxX = w - offsetX.value;
  const minY = -offsetY.value;
  const maxY = h - offsetY.value;

  ctx.beginPath();
  for (let x = Math.floor(minX / gridSize) * gridSize; x < maxX; x += gridSize) {
    ctx.moveTo(x, minY);
    ctx.lineTo(x, maxY);
  }
  for (let y = Math.floor(minY / gridSize) * gridSize; y < maxY; y += gridSize) {
    ctx.moveTo(minX, y);
    ctx.lineTo(maxX, y);
  }
  ctx.stroke();

  // 2. Draw Office Corridor Walls & Free Space (from RTAB-Map room geometry)
  drawOfficeMap(ctx);

  // 3. Draw Camera Frustum
  drawCameraFrustum(ctx);

  // 4. Draw All Swarm Devices
  swarmState.devices.forEach(dev => {
    drawDevice(ctx, dev);
  });

  // 5. Draw Human Location
  drawHuman(ctx, swarmState.humanPose);

  ctx.restore();

  animFrameId = requestAnimationFrame(render);
}

function drawOfficeMap(ctx: CanvasRenderingContext2D) {
  const s = scale.value;

  // Free space (light gray / clean hallway corridor)
  ctx.fillStyle = '#1e2638';
  ctx.beginPath();
  // Main corridor
  ctx.rect(-6 * s, -1.8 * s, 12 * s, 3.6 * s);
  // Branch corridor (towards office)
  ctx.rect(1 * s, 1.8 * s, 3 * s, 4 * s);
  ctx.fill();

  // Corridor walls (obstacle boundary)
  ctx.strokeStyle = '#38bdf8';
  ctx.lineWidth = 2.5;
  ctx.shadowColor = '#0284c7';
  ctx.shadowBlur = 6;
  ctx.strokeRect(-6 * s, -1.8 * s, 12 * s, 3.6 * s);

  // Branch walls
  ctx.beginPath();
  ctx.moveTo(1 * s, 1.8 * s);
  ctx.lineTo(1 * s, 5.8 * s);
  ctx.lineTo(4 * s, 5.8 * s);
  ctx.lineTo(4 * s, 1.8 * s);
  ctx.stroke();

  ctx.shadowBlur = 0; // reset shadow

  // Doorway landmark markers
  ctx.fillStyle = '#f59e0b';
  ctx.font = '10px monospace';
  ctx.fillText('🚪 王义老师办公室', 1.2 * s, 5.4 * s);
  ctx.fillText('🚪 前台接待处', -5.8 * s, -1.2 * s);
  ctx.fillText('🚪 302 会议室', 3.8 * s, -1.2 * s);
}

function drawCameraFrustum(ctx: CanvasRenderingContext2D) {
  const cam = swarmState.devices.find(d => d.kind === 'fixed_camera');
  if (!cam) return;
  const s = scale.value;
  const cx = cam.pose.x * s;
  const cy = -cam.pose.y * s;

  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(cam.pose.theta);

  // Frustum cone
  ctx.fillStyle = 'rgba(236, 72, 153, 0.12)';
  ctx.strokeStyle = 'rgba(236, 72, 153, 0.4)';
  ctx.beginPath();
  ctx.moveTo(0, 0);
  ctx.arc(0, 0, 3.5 * s, -Math.PI / 4, Math.PI / 4);
  ctx.closePath();
  ctx.fill();
  ctx.stroke();

  // Camera dot
  ctx.fillStyle = '#ec4899';
  ctx.beginPath();
  ctx.arc(0, 0, 5, 0, Math.PI * 2);
  ctx.fill();

  ctx.restore();
}

function drawDevice(ctx: CanvasRenderingContext2D, dev: any) {
  const s = scale.value;
  const x = dev.pose.x * s;
  const y = -dev.pose.y * s; // invert Y for canvas
  const theta = dev.pose.theta;

  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(-theta);

  if (dev.kind === 'legged_dog') {
    // Dog Body Rectangle
    const isLive = dev.status === 'ONLINE';
    ctx.fillStyle = isLive ? '#0284c7' : '#334155';
    ctx.strokeStyle = isLive ? '#38bdf8' : '#64748b';
    ctx.lineWidth = 2;

    const len = 0.6 * s;
    const wid = 0.35 * s;
    ctx.beginPath();
    ctx.roundRect(-len / 2, -wid / 2, len, wid, 4);
    ctx.fill();
    ctx.stroke();

    // Direction arrow
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.moveTo(len / 2 + 6, 0);
    ctx.lineTo(len / 2 - 2, -4);
    ctx.lineTo(len / 2 - 2, 4);
    ctx.closePath();
    ctx.fill();

    // 4 Feet markers
    ctx.fillStyle = isLive ? '#38bdf8' : '#475569';
    const fx = len * 0.35;
    const fy = wid * 0.45;
    [-fx, fx].forEach(px => {
      [-fy, fy].forEach(py => {
        ctx.beginPath();
        ctx.arc(px, py, 2.5, 0, Math.PI * 2);
        ctx.fill();
      });
    });

  } else if (dev.kind === 'wheeled_agv') {
    // Wheeled chassis
    const len = 0.8 * s;
    const wid = 0.5 * s;
    ctx.fillStyle = '#7e22ce';
    ctx.strokeStyle = '#c084fc';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.roundRect(-len / 2, -wid / 2, len, wid, 6);
    ctx.fill();
    ctx.stroke();

    // Payload area pattern
    ctx.strokeStyle = 'rgba(255,255,255,0.2)';
    ctx.strokeRect(-len * 0.3, -wid * 0.3, len * 0.6, wid * 0.6);
  }

  ctx.restore();

  // Name tag
  ctx.fillStyle = '#e2e8f0';
  ctx.font = '10px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText(dev.name.split(' ')[0], x, y - 0.5 * s);
}

function drawHuman(ctx: CanvasRenderingContext2D, pose: any) {
  const s = scale.value;
  const x = pose.x * s;
  const y = -pose.y * s;

  // Pulse ring
  ctx.strokeStyle = 'rgba(16, 185, 129, 0.4)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(x, y, 12, 0, Math.PI * 2);
  ctx.stroke();

  // Center human dot
  ctx.fillStyle = '#10b981';
  ctx.beginPath();
  ctx.arc(x, y, 6, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 9px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('我 (持机人)', x, y + 20);
}

function resizeCanvas() {
  if (!canvasRef.value) return;
  canvasRef.value.width = canvasRef.value.clientWidth * window.devicePixelRatio;
  canvasRef.value.height = canvasRef.value.clientHeight * window.devicePixelRatio;
  resetView();
}

onMounted(() => {
  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();
  render();
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeCanvas);
  if (animFrameId) cancelAnimationFrame(animFrameId);
});
</script>
