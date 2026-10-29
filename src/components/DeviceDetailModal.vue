<template>
  <div 
    v-if="device" 
    class="fixed inset-0 z-40 bg-black/75 backdrop-blur-sm flex flex-col justify-end sm:justify-center sm:items-center p-0 sm:p-4"
  >
    <div class="bg-deaios-card border border-deaios-border rounded-t-2xl sm:rounded-2xl w-full sm:max-w-md max-h-[88vh] overflow-y-auto flex flex-col shadow-2xl animate-slide-up">
      <!-- Modal Header -->
      <div class="p-4 border-b border-deaios-border flex items-center justify-between sticky top-0 bg-deaios-card/95 z-10">
        <div class="flex items-center space-x-2">
          <span class="text-2xl">{{ getDeviceIcon(device.kind) }}</span>
          <div>
            <h2 class="font-bold text-sm text-slate-100">{{ device.name }}</h2>
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
        <!-- Live Video View / Camera Feed Placeholder -->
        <div class="relative w-full aspect-video bg-slate-950 rounded-xl overflow-hidden border border-deaios-border flex items-center justify-center">
          <div class="absolute top-2 left-2 px-2 py-0.5 rounded bg-black/60 text-[10px] font-mono text-emerald-400 flex items-center space-x-1">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            <span>LIVE CAM (Orbbec/RTSP)</span>
          </div>

          <!-- Video placeholder graphics -->
          <div class="text-center text-slate-600">
            <div class="text-3xl mb-1">📹</div>
            <div class="font-mono text-[11px]">低延迟 WebRTC 视频流</div>
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
              {{ device.hardwareDetails?.temperature || 45 }}°C <span class="text-xs text-slate-400">(Thor Orin)</span>
            </div>
          </div>
        </div>

        <!-- Manual Joystick Control (for mobile dogs / AGV) -->
        <div v-if="device.kind === 'legged_dog' || device.kind === 'wheeled_agv'" class="bg-slate-950/60 p-3 rounded-xl border border-slate-800">
          <div class="text-slate-400 font-bold mb-2 flex items-center justify-between">
            <span>🕹️ 人工遥控微调 (Manual Override)</span>
            <span class="text-[10px] text-slate-500 font-mono">0.3 m/s</span>
          </div>
          
          <div class="grid grid-cols-3 gap-2 max-w-[200px] mx-auto py-2">
            <div></div>
            <button @click="sendCmd(0.3, 0)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm">▲</button>
            <div></div>
            <button @click="sendCmd(0, 0.4)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm">◀</button>
            <button @click="sendCmd(0, 0)" class="h-10 rounded-lg bg-red-600/30 text-red-400 border border-red-500/40 font-bold flex items-center justify-center text-xs">停</button>
            <button @click="sendCmd(0, -0.4)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm">▶</button>
            <div></div>
            <button @click="sendCmd(-0.2, 0)" class="h-10 rounded-lg bg-slate-800 active:bg-blue-600 text-slate-100 font-bold flex items-center justify-center text-sm">▼</button>
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
              class="flex items-center justify-between bg-slate-900/80 px-2.5 py-1.5 rounded font-mono text-[10px]"
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
import { swarmState } from '../services/telemetryService';
import type { DeviceKind } from '../types/swarm';

const props = defineProps<{ deviceId: string | null }>();
defineEmits(['close']);

const device = computed(() => {
  return swarmState.devices.find(d => d.id === props.deviceId);
});

function getDeviceIcon(kind?: DeviceKind) {
  switch (kind) {
    case 'legged_dog': return '🐕';
    case 'wheeled_agv': return '🚗';
    case 'fixed_camera': return '📹';
    case 'manipulator': return '🦾';
    default: return '📱';
  }
}

function sendCmd(linear: number, angular: number) {
  if ('vibrate' in navigator) navigator.vibrate(20);
  console.log(`[TELEOP] Sent cmd_vel to ${props.deviceId}: vx=${linear}, wz=${angular}`);
}
</script>
