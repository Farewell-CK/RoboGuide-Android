<template>
  <div 
    v-if="device" 
    class="fixed inset-0 z-40 bg-black/75 backdrop-blur-sm flex flex-col justify-end sm:justify-center sm:items-center p-0 sm:p-4 animate-fade-in select-none"
  >
    <div class="bg-slate-900 border border-slate-700 rounded-t-2xl sm:rounded-2xl w-full sm:max-w-md max-h-[88vh] overflow-y-auto flex flex-col shadow-2xl animate-slide-up">
      <!-- Modal Header -->
      <div class="p-4 border-b border-slate-800 flex items-center justify-between sticky top-0 bg-slate-900/95 z-10">
        <div class="flex items-center space-x-2">
          <span class="text-2xl">{{ getDeviceIcon(device.kind) }}</span>
          <div>
            <div class="flex items-center space-x-2">
              <h2 class="font-bold text-sm text-slate-100">{{ device.name }}</h2>
              <span v-if="device.roleTag" class="text-[9px] bg-blue-500/20 text-blue-300 px-1.5 py-0.2 rounded border border-blue-500/30">
                {{ device.roleTag }}
              </span>
            </div>
            <p class="text-[11px] font-mono text-slate-400">{{ device.id }} | {{ device.tailscaleIp || device.ip }}</p>
          </div>
        </div>
        <button 
          @click="$emit('close')"
          class="w-7 h-7 rounded-full bg-slate-800 text-slate-400 flex items-center justify-center text-sm font-bold active:bg-slate-700"
        >
          ✕
        </button>
      </div>

      <!-- Modal Body -->
      <div class="p-4 space-y-4 text-xs">
        <!-- Live Video View Banner & FPV Launch -->
        <div 
          @click="openFpv(device.id)"
          class="relative w-full aspect-video bg-slate-950 rounded-xl overflow-hidden border border-slate-800 flex items-center justify-center cursor-pointer group shadow"
        >
          <div class="absolute top-2 left-2 px-2 py-0.5 rounded bg-black/70 text-[10px] font-mono text-emerald-400 flex items-center space-x-1 border border-emerald-500/30">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            <span>FPV CAM STREAM (Orbbec/Synthetic)</span>
          </div>

          <div class="text-center group-hover:scale-105 transition">
            <div class="text-3xl mb-1">📹</div>
            <div class="font-bold text-xs text-blue-400">点击进入全屏 FPV 视锥与 HUD</div>
            <div class="text-[10px] text-slate-500 font-mono mt-0.5">低延迟 WebRTC 视频流</div>
          </div>
        </div>

        <!-- Telemetry Details Grid -->
        <div class="grid grid-cols-2 gap-2">
          <div class="bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
            <span class="text-[10px] text-slate-500">电池与电压</span>
            <div class="font-mono font-bold text-sm text-emerald-400 mt-0.5">
              {{ device.batteryPercent }}% <span class="text-xs text-slate-400">({{ device.batteryVoltage || 24.5 }}V)</span>
            </div>
          </div>
          <div class="bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
            <span class="text-[10px] text-slate-500">机载算力温度</span>
            <div class="font-mono font-bold text-sm text-blue-400 mt-0.5">
              {{ device.hardwareDetails?.temperature || 45 }}°C <span class="text-xs text-slate-400">({{ device.hardwareDetails?.platform?.split(' ')[0] || 'GPU' }})</span>
            </div>
          </div>
        </div>

        <!-- Gait & Posture Controls (for quadruped dogs) -->
        <div v-if="device.kind === 'legged_dog'" class="space-y-1.5">
          <div class="text-slate-400 font-bold text-[11px]">🐕 具身步态与姿态控制</div>
          <div class="grid grid-cols-4 gap-1.5">
            <button 
              @click="setGait('stand')" 
              class="py-1.5 bg-slate-800 active:bg-blue-600 border border-slate-700 text-slate-200 rounded-lg text-xs font-bold transition"
            >
              立正站立
            </button>
            <button 
              @click="setGait('sit')" 
              class="py-1.5 bg-slate-800 active:bg-blue-600 border border-slate-700 text-slate-200 rounded-lg text-xs font-bold transition"
            >
              屈膝待命
            </button>
            <button 
              @click="setGait('trot')" 
              class="py-1.5 bg-slate-800 active:bg-blue-600 border border-slate-700 text-slate-200 rounded-lg text-xs font-bold transition"
            >
              小跑步态
            </button>
            <button 
              @click="setGait('brake')" 
              class="py-1.5 bg-red-950/40 active:bg-red-600 border border-red-500/40 text-red-300 rounded-lg text-xs font-bold transition"
            >
              高PD刹车
            </button>
          </div>
        </div>

        <!-- Manual Joystick Control -->
        <div v-if="device.kind === 'legged_dog' || device.kind === 'wheeled_agv'" class="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
          <div class="text-slate-400 font-bold mb-2 flex items-center justify-between">
            <span>🕹️ 人工遥控微调 (Manual Override)</span>
            <span class="text-[10px] text-slate-500 font-mono">步长: 0.35 m/s</span>
          </div>
          
          <div class="grid grid-cols-3 gap-2 max-w-[200px] mx-auto py-1">
            <div></div>
            <button @click="sendCmd(0.35, 0)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm shadow">▲</button>
            <div></div>
            <button @click="sendCmd(0, 0.45)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm shadow">◀</button>
            <button @click="sendCmd(0, 0)" class="h-10 rounded-lg bg-red-600/30 text-red-400 border border-red-500/40 font-bold flex items-center justify-center text-xs active:bg-red-600 active:text-white">停</button>
            <button @click="sendCmd(0, -0.45)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm shadow">▶</button>
            <div></div>
            <button @click="sendCmd(-0.25, 0)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm shadow">▼</button>
            <div></div>
          </div>
        </div>

        <!-- Capability Contract list -->
        <div>
          <h4 class="text-slate-400 font-bold mb-1.5">📜 注册能力契约 (Capabilities)</h4>
          <div class="space-y-1">
            <div 
              v-for="cap in device.capabilities" 
              :key="cap.contract"
              class="flex items-center justify-between bg-slate-950/80 px-2.5 py-1.5 rounded font-mono text-[10px] border border-slate-800/60"
            >
              <span class="text-blue-300 font-semibold">{{ cap.contract }}</span>
              <span class="text-slate-400">{{ cap.detail }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { swarmState, openFpvStream } from '../services/telemetryService';
import type { DeviceKind } from '../types/swarm';

const props = defineProps<{ deviceId: string | null }>();
const emit = defineEmits(['close']);

const device = computed(() => {
  return swarmState.devices.find(d => d.id === props.deviceId);
});

function openFpv(id: string) {
  emit('close');
  openFpvStream(id);
}

function setGait(mode: string) {
  if (device.value) {
    device.value.currentMode = mode;
  }
}

function sendCmd(vx: number, vtheta: number) {
  if (!device.value) return;
  device.value.linearVelocity = vx;
  device.value.angularVelocity = vtheta;

  // Advance pose slightly for instant feedback
  device.value.pose.x += vx * 0.1 * Math.cos(device.value.pose.theta);
  device.value.pose.y += vx * 0.1 * Math.sin(device.value.pose.theta);
  device.value.pose.theta += vtheta * 0.1;
}

function getDeviceIcon(kind: DeviceKind) {
  switch (kind) {
    case 'legged_dog': return '🐕';
    case 'wheeled_agv': return '🚗';
    case 'manipulator': return '🦾';
    case 'fixed_camera': return '📹';
    case 'human_terminal': return '📱';
    default: return '🤖';
  }
}
</script>
