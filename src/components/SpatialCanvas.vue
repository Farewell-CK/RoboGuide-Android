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
    <div class="absolute top-3 left-3 bg-slate-900/85 backdrop-blur-md border border-slate-700/80 px-3 py-1.5 rounded-lg text-xs pointer-events-none flex items-center space-x-2 shadow-lg">
      <div class="w-2 h-2 rounded-full bg-blue-400 animate-pulse"></div>
      <span class="font-mono text-slate-300">
        {{ swarmState.currentProfile === 'isaac_sim' ? 'Isaac Lab 园区孪生' : 'RTAB-Map (0.05m)' }}
      </span>
      <span class="text-slate-500">|</span>
      <span class="text-slate-400 font-mono">{{ swarmState.mapData.width }}×{{ swarmState.mapData.height }}</span>
      <span v-if="followDeviceName" class="text-emerald-400 font-mono text-[11px] flex items-center space-x-1 pl-1">
        <span>🎯 追踪:</span>
        <span class="font-bold">{{ followDeviceName }}</span>
      </span>
    </div>

    <!-- Quick Map Controls (Zoom / Recenter / Follow-Cam) -->
    <div class="absolute right-3 top-3 flex flex-col space-y-1.5 z-10">
      <button 
        @click="zoomIn" 
        class="w-8 h-8 rounded-lg bg-slate-900/90 border border-slate-700 text-slate-200 text-lg flex items-center justify-center active:bg-blue-600 transition shadow"
      >
        +
      </button>
      <button 
        @click="zoomOut" 
        class="w-8 h-8 rounded-lg bg-slate-900/90 border border-slate-700 text-slate-200 text-lg flex items-center justify-center active:bg-blue-600 transition shadow"
      >
        −
      </button>
      <button 
        @click="toggleFollowCam" 
        :class="[
          'w-8 h-8 rounded-lg border text-xs flex items-center justify-center transition shadow',
          swarmState.followDeviceId ? 'bg-emerald-600 border-emerald-400 text-white animate-pulse' : 'bg-slate-900/90 border-slate-700 text-slate-200 active:bg-blue-600'
        ]"
        title="锁定跟随视角"
      >
        🎯
      </button>
      <button 
        @click="resetView" 
        class="w-8 h-8 rounded-lg bg-slate-900/90 border border-slate-700 text-slate-200 text-xs flex items-center justify-center active:bg-blue-600 transition shadow"
        title="全景复位"
      >
        🔄
      </button>
    </div>

    <!-- Active Navigation Goal Floating Bar -->
    <div 
      v-if="swarmState.activeGoal" 
      class="absolute top-14 left-3 right-3 bg-blue-950/90 backdrop-blur-md border border-blue-500/50 p-2.5 rounded-xl text-xs flex items-center justify-between shadow-xl z-20 animate-fade-in"
    >
      <div class="flex items-center space-x-2">
        <span class="text-base animate-bounce">🚩</span>
        <div>
          <div class="font-bold text-white flex items-center space-x-1.5">
            <span>{{ swarmState.activeGoal.label || '导航目标点' }}</span>
            <span class="font-mono text-[10px] text-blue-300">
              ({{ swarmState.activeGoal.x.toFixed(2) }}, {{ swarmState.activeGoal.y.toFixed(2) }})
            </span>
          </div>
          <p class="text-[10px] text-slate-300">点击地图任意空地可重新指点目标</p>
        </div>
      </div>
      <div class="flex items-center space-x-1.5">
        <button 
          @click="clearTargetGoal" 
          class="px-2.5 py-1 bg-slate-800 border border-slate-700 active:bg-slate-700 text-slate-300 rounded-lg text-xs font-bold"
        >
          取消
        </button>
      </div>
    </div>

    <!-- Tap-to-Goal Hint (when no goal) -->
    <div 
      v-else 
      class="absolute top-12 left-3 text-[10px] text-slate-500 pointer-events-none select-none font-mono"
    >
      💡 点击或轻触地图空白处即可指点导航 (Tap-to-Goal)
    </div>

    <!-- Dynamic Fleet Legend Footer -->
    <div class="absolute bottom-2 left-2 right-2 bg-slate-900/85 backdrop-blur border border-slate-700/80 px-3 py-1.5 rounded-lg flex items-center justify-around text-[10px] text-slate-300 shadow-lg">
      <div 
        v-for="d in displayedLegendDevices" 
        :key="d.id"
        @click="selectDeviceFromLegend(d.id)"
        class="flex items-center space-x-1 cursor-pointer active:scale-95 transition"
      >
        <span 
          :class="[
            'w-2.5 h-2.5 rounded-full',
            d.id === swarmState.selectedDeviceId ? 'ring-2 ring-white' : '',
            d.kind === 'legged_dog' ? 'bg-cyan-400' :
            d.kind === 'wheeled_agv' ? 'bg-purple-400' :
            d.kind === 'human_terminal' ? 'bg-emerald-400' : 'bg-amber-400'
          ]"
        ></span>
        <span :class="d.id === swarmState.selectedDeviceId ? 'font-bold text-white' : ''">
          {{ d.name.split(' ')[0] }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { swarmState, setTargetGoal, clearTargetGoal } from '../services/telemetryService';

const canvasRef = ref<HTMLCanvasElement | null>(null);

// Transform state
const scale = ref(38); // pixels per meter
const offsetX = ref(0);
const offsetY = ref(0);

let isPanning = false;
let startX = 0;
let startY = 0;
let hasDragged = false;
let touchDist = 0;
let animFrameId: number | null = null;
let pulseTimer = 0;

const followDeviceName = computed(() => {
  if (!swarmState.followDeviceId) return null;
  const d = swarmState.devices.find(dev => dev.id === swarmState.followDeviceId);
  return d ? d.name.split(' ')[0] : null;
});

const displayedLegendDevices = computed(() => {
  return swarmState.devices.filter(d => d.kind !== 'fixed_camera');
});

function selectDeviceFromLegend(id: string) {
  swarmState.selectedDeviceId = id;
  swarmState.followDeviceId = id;
}

function toggleFollowCam() {
  if (swarmState.followDeviceId) {
    swarmState.followDeviceId = null;
  } else {
    const defaultFollow = swarmState.selectedDeviceId || swarmState.devices.find(d => d.kind === 'legged_dog')?.id;
    if (defaultFollow) {
      swarmState.followDeviceId = defaultFollow;
    }
  }
}

function resetView() {
  if (!canvasRef.value) return;
  offsetX.value = canvasRef.value.width / 2;
  offsetY.value = canvasRef.value.height / 2;
  scale.value = 40;
  swarmState.followDeviceId = null;
}

function zoomIn() {
  scale.value = Math.min(100, scale.value * 1.25);
}

function zoomOut() {
  scale.value = Math.max(15, scale.value * 0.8);
}

// Convert screen (canvas) coordinates to world meters
function screenToWorld(sx: number, sy: number) {
  const wx = (sx - offsetX.value) / scale.value;
  const wy = -(sy - offsetY.value) / scale.value;
  return { x: wx, y: wy };
}

// Convert world meters to screen canvas pixels
function worldToScreen(wx: number, wy: number) {
  const sx = offsetX.value + wx * scale.value;
  const sy = offsetY.value - wy * scale.value;
  return { x: sx, y: sy };
}

function handleCanvasTap(screenX: number, screenY: number) {
  // Check if tap hit any device within 25px
  let clickedDevice: string | null = null;
  for (const d of swarmState.devices) {
    const s = worldToScreen(d.pose.x, d.pose.y);
    const dist = Math.hypot(screenX - s.x, screenY - s.y);
    if (dist <= 25) {
      clickedDevice = d.id;
      break;
    }
  }

  if (clickedDevice) {
    swarmState.selectedDeviceId = clickedDevice;
    swarmState.followDeviceId = clickedDevice;
  } else {
    // Tap on empty space -> set target navigation goal!
    const worldPos = screenToWorld(screenX, screenY);
    setTargetGoal(worldPos.x, worldPos.y, '指定目标点');
  }
}

// Touch & Mouse interaction handlers
function startPan(e: MouseEvent) {
  isPanning = true;
  hasDragged = false;
  startX = e.clientX - offsetX.value;
  startY = e.clientY - offsetY.value;
}

function doPan(e: MouseEvent) {
  if (!isPanning) return;
  hasDragged = true;
  swarmState.followDeviceId = null; // Drag breaks follow lock
  offsetX.value = e.clientX - startX;
  offsetY.value = e.clientY - startY;
}

function endPan(e: MouseEvent) {
  if (isPanning && !hasDragged) {
    const rect = canvasRef.value?.getBoundingClientRect();
    if (rect) {
      handleCanvasTap(e.clientX - rect.left, e.clientY - rect.top);
    }
  }
  isPanning = false;
}

function handleTouchStart(e: TouchEvent) {
  if (e.touches.length === 1) {
    isPanning = true;
    hasDragged = false;
    startX = e.touches[0].clientX - offsetX.value;
    startY = e.touches[0].clientY - offsetY.value;
  } else if (e.touches.length === 2) {
    isPanning = false;
    touchDist = Math.hypot(
      e.touches[0].clientX - e.touches[1].clientX,
      e.touches[0].clientY - e.touches[1].clientY
    );
  }
}

function handleTouchMove(e: TouchEvent) {
  if (isPanning && e.touches.length === 1) {
    hasDragged = true;
    swarmState.followDeviceId = null;
    offsetX.value = e.touches[0].clientX - startX;
    offsetY.value = e.touches[0].clientY - startY;
  } else if (e.touches.length === 2) {
    const newDist = Math.hypot(
      e.touches[0].clientX - e.touches[1].clientX,
      e.touches[0].clientY - e.touches[1].clientY
    );
    if (touchDist > 0) {
      const factor = newDist / touchDist;
      scale.value = Math.max(15, Math.min(100, scale.value * factor));
    }
    touchDist = newDist;
  }
}

function handleTouchEnd(e: TouchEvent) {
  if (isPanning && !hasDragged && e.changedTouches.length === 1) {
    const rect = canvasRef.value?.getBoundingClientRect();
    if (rect) {
      const t = e.changedTouches[0];
      handleCanvasTap(t.clientX - rect.left, t.clientY - rect.top);
    }
  }
  isPanning = false;
  touchDist = 0;
}

// ----------------- CANVAS RENDERING ENGINE -----------------

function render() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  pulseTimer += 0.05;

  // Handle Follow-Cam smooth interpolation
  if (swarmState.followDeviceId) {
    const target = swarmState.devices.find(d => d.id === swarmState.followDeviceId);
    if (target) {
      const desiredX = canvas.width / 2 - target.pose.x * scale.value;
      const desiredY = canvas.height / 2 + target.pose.y * scale.value;
      offsetX.value += (desiredX - offsetX.value) * 0.1;
      offsetY.value += (desiredY - offsetY.value) * 0.1;
    }
  }

  // Clear background
  ctx.fillStyle = '#080c14';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // 1. Draw coordinate grid & floor plan
  drawGridAndRooms(ctx, canvas);

  // 2. Draw Trajectory Trails (Breadcrumbs)
  drawDeviceTrails(ctx);

  // 3. Draw Target Navigation Goal (Beacon & Dotted Path)
  drawTargetGoal(ctx);

  // 4. Draw Devices (Dogs, AGV, Cameras, Human Node)
  drawDevices(ctx);

  animFrameId = requestAnimationFrame(render);
}

function drawGridAndRooms(ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement) {
  ctx.save();

  // Grid
  const gridSize = 1.0 * scale.value;
  const startGridX = offsetX.value % gridSize;
  const startGridY = offsetY.value % gridSize;

  ctx.strokeStyle = '#151d2d';
  ctx.lineWidth = 1;

  ctx.beginPath();
  for (let x = startGridX; x < canvas.width; x += gridSize) {
    ctx.moveTo(x, 0);
    ctx.lineTo(x, canvas.height);
  }
  for (let y = startGridY; y < canvas.height; y += gridSize) {
    ctx.moveTo(0, y);
    ctx.lineTo(canvas.width, y);
  }
  ctx.stroke();

  // Axes origin marker
  ctx.strokeStyle = '#1e2d48';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(0, offsetY.value);
  ctx.lineTo(canvas.width, offsetY.value);
  ctx.moveTo(offsetX.value, 0);
  ctx.lineTo(offsetX.value, canvas.height);
  ctx.stroke();

  // Draw simulated Industrial Warehouse walls / rooms
  const rooms = [
    { x: -4.0, y: -3.0, w: 8.0, h: 6.5, name: '工业物流园区大厅 (Central Atrium)' },
    { x: -3.8, y: 1.0, w: 3.5, h: 2.2, name: '立体货架存储区 (Racks A)' },
    { x: 0.5, y: 1.0, w: 3.2, h: 2.2, name: '自动打包分拣区 (Sort B)' },
    { x: -3.8, y: -2.8, w: 7.5, h: 1.8, name: '高速 AGV 巡航通道 (Transit Corridor)' }
  ];

  for (const r of rooms) {
    const s = worldToScreen(r.x, r.y + r.h);
    const sw = r.w * scale.value;
    const sh = r.h * scale.value;

    ctx.fillStyle = 'rgba(15, 23, 42, 0.45)';
    ctx.fillRect(s.x, s.y, sw, sh);

    ctx.strokeStyle = 'rgba(56, 189, 248, 0.25)';
    ctx.lineWidth = 1.5;
    ctx.strokeRect(s.x, s.y, sw, sh);

    ctx.fillStyle = 'rgba(148, 163, 184, 0.4)';
    ctx.font = '10px monospace';
    ctx.fillText(r.name, s.x + 8, s.y + 16);
  }

  ctx.restore();
}

function drawDeviceTrails(ctx: CanvasRenderingContext2D) {
  ctx.save();
  for (const d of swarmState.devices) {
    if (!d.trajectory || d.trajectory.length < 2) continue;

    ctx.beginPath();
    const first = worldToScreen(d.trajectory[0].x, d.trajectory[0].y);
    ctx.moveTo(first.x, first.y);

    for (let i = 1; i < d.trajectory.length; i++) {
      const p = worldToScreen(d.trajectory[i].x, d.trajectory[i].y);
      ctx.lineTo(p.x, p.y);
    }
    const curr = worldToScreen(d.pose.x, d.pose.y);
    ctx.lineTo(curr.x, curr.y);

    ctx.lineWidth = 2;
    ctx.setLineDash([4, 4]);
    if (d.kind === 'legged_dog') {
      ctx.strokeStyle = 'rgba(56, 189, 248, 0.4)';
    } else if (d.kind === 'wheeled_agv') {
      ctx.strokeStyle = 'rgba(192, 132, 252, 0.4)';
    } else {
      ctx.strokeStyle = 'rgba(52, 211, 153, 0.4)';
    }
    ctx.stroke();
    ctx.setLineDash([]);
  }
  ctx.restore();
}

function drawTargetGoal(ctx: CanvasRenderingContext2D) {
  if (!swarmState.activeGoal) return;
  const g = swarmState.activeGoal;
  const s = worldToScreen(g.x, g.y);

  ctx.save();

  // Radar pulse ring
  const pulseRadius = 15 + Math.sin(pulseTimer * 3) * 6;
  ctx.beginPath();
  ctx.arc(s.x, s.y, pulseRadius, 0, Math.PI * 2);
  ctx.strokeStyle = 'rgba(56, 189, 248, 0.7)';
  ctx.lineWidth = 2;
  ctx.stroke();

  // Connecting line from assigned device to goal
  if (g.assignedDeviceId) {
    const dev = swarmState.devices.find(d => d.id === g.assignedDeviceId);
    if (dev) {
      const ds = worldToScreen(dev.pose.x, dev.pose.y);
      ctx.beginPath();
      ctx.moveTo(ds.x, ds.y);
      ctx.lineTo(s.x, s.y);
      ctx.strokeStyle = 'rgba(56, 189, 248, 0.8)';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([6, 6]);
      ctx.stroke();
      ctx.setLineDash([]);
    }
  }

  // Pin point
  ctx.beginPath();
  ctx.arc(s.x, s.y, 4, 0, Math.PI * 2);
  ctx.fillStyle = '#38bdf8';
  ctx.fill();

  // Flag text
  ctx.font = 'bold 11px sans-serif';
  ctx.fillStyle = '#ffffff';
  ctx.fillText('🚩 ' + (g.label || '目标点'), s.x + 8, s.y - 8);

  ctx.restore();
}

function drawDevices(ctx: CanvasRenderingContext2D) {
  for (const d of swarmState.devices) {
    const s = worldToScreen(d.pose.x, d.pose.y);
    const isSelected = d.id === swarmState.selectedDeviceId;

    ctx.save();
    ctx.translate(s.x, s.y);

    // Selected highlight halo
    if (isSelected) {
      ctx.beginPath();
      ctx.arc(0, 0, 22 + Math.sin(pulseTimer * 4) * 2, 0, Math.PI * 2);
      ctx.strokeStyle = '#38bdf8';
      ctx.lineWidth = 2.5;
      ctx.stroke();
    }

    if (d.kind === 'legged_dog') {
      // Quadruped robot body
      ctx.rotate(-d.pose.theta);

      // Body box
      ctx.fillStyle = d.status === 'ONLINE' ? '#0ea5e9' : d.status === 'ESTOP' ? '#ef4444' : '#475569';
      ctx.fillRect(-12, -7, 24, 14);

      // 4 feet
      ctx.fillStyle = '#38bdf8';
      ctx.fillRect(8, -10, 4, 3);
      ctx.fillRect(8, 7, 4, 3);
      ctx.fillRect(-12, -10, 4, 3);
      ctx.fillRect(-12, 7, 4, 3);

      // Head direction arrow
      ctx.beginPath();
      ctx.moveTo(12, 0);
      ctx.lineTo(19, 0);
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 2.5;
      ctx.stroke();

    } else if (d.kind === 'wheeled_agv') {
      ctx.rotate(-d.pose.theta);
      ctx.fillStyle = '#8b5cf6';
      ctx.fillRect(-14, -10, 28, 20);
      ctx.strokeStyle = '#c084fc';
      ctx.lineWidth = 2;
      ctx.strokeRect(-14, -10, 28, 20);

      // Wheels
      ctx.fillStyle = '#1e1b4b';
      ctx.fillRect(-10, -13, 6, 3);
      ctx.fillRect(4, -13, 6, 3);
      ctx.fillRect(-10, 10, 6, 3);
      ctx.fillRect(4, 10, 6, 3);

    } else if (d.kind === 'fixed_camera') {
      // Camera FoV cone
      ctx.rotate(-d.pose.theta);
      ctx.fillStyle = 'rgba(56, 189, 248, 0.15)';
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.arc(0, 0, 60, -Math.PI / 4, Math.PI / 4);
      ctx.closePath();
      ctx.fill();

      // Camera body
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(0, 0, 6, 0, Math.PI * 2);
      ctx.fill();

    } else if (d.kind === 'human_terminal') {
      // Human position & viewing direction
      ctx.rotate(-d.pose.theta);

      // Vision cone
      ctx.fillStyle = 'rgba(52, 211, 153, 0.2)';
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.arc(0, 0, 45, -Math.PI / 6, Math.PI / 6);
      ctx.closePath();
      ctx.fill();

      // Human avatar circle
      ctx.fillStyle = '#10b981';
      ctx.beginPath();
      ctx.arc(0, 0, 8, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    ctx.restore();

    // Name tag & Battery label
    ctx.save();
    ctx.font = 'bold 10px sans-serif';
    ctx.fillStyle = isSelected ? '#38bdf8' : '#e2e8f0';
    const tag = `${d.name.split(' ')[0]} (${d.batteryPercent}%)`;
    ctx.fillText(tag, s.x - 25, s.y + 22);
    ctx.restore();
  }
}

function handleResize() {
  if (!canvasRef.value) return;
  canvasRef.value.width = canvasRef.value.clientWidth;
  canvasRef.value.height = canvasRef.value.clientHeight;
  if (offsetX.value === 0 && offsetY.value === 0) {
    resetView();
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize);
  setTimeout(() => {
    handleResize();
    render();
  }, 50);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  if (animFrameId) {
    cancelAnimationFrame(animFrameId);
  }
});
</script>
