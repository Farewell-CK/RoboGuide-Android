<template>
  <div class="p-4 space-y-3 pb-24 overflow-y-auto h-full">
    <!-- Filter Pills -->
    <div class="flex items-center space-x-1.5 overflow-x-auto pb-1 text-xs">
      <button 
        v-for="cat in categories" 
        :key="cat.key"
        @click="selectedCategory = cat.key"
        :class="[
          'px-3 py-1.5 rounded-full font-medium transition whitespace-nowrap',
          selectedCategory === cat.key 
            ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/30' 
            : 'bg-deaios-card border border-deaios-border text-slate-400 active:bg-slate-800'
        ]"
      >
        {{ cat.label }} ({{ getCategoryCount(cat.key) }})
      </button>
    </div>

    <!-- Device Cards Grid -->
    <div class="space-y-2.5">
      <div 
        v-for="dev in filteredDevices" 
        :key="dev.id"
        @click="$emit('select-device', dev.id)"
        class="bg-deaios-card border border-deaios-border rounded-xl p-3.5 active:border-blue-500 transition cursor-pointer relative overflow-hidden"
      >
        <!-- Left color stripe by kind -->
        <div :class="['absolute left-0 top-0 bottom-0 w-1', getDeviceColor(dev.kind)]"></div>

        <div class="flex items-start justify-between">
          <div class="flex items-center space-x-2.5">
            <span class="text-2xl">{{ getDeviceIcon(dev.kind) }}</span>
            <div>
              <div class="flex items-center space-x-2">
                <h3 class="font-bold text-sm text-slate-100">{{ dev.name }}</h3>
              </div>
              <p class="text-[11px] font-mono text-slate-400 mt-0.5">
                {{ dev.tailscaleIp || dev.ip }}
              </p>
            </div>
          </div>

          <!-- Status badge -->
          <div class="flex flex-col items-end space-y-1">
            <span :class="['text-[10px] font-mono px-2 py-0.5 rounded-full border', getStatusBadge(dev.status)]">
              {{ dev.status }}
            </span>
            <div v-if="dev.batteryPercent !== undefined" class="text-[10px] text-slate-400 flex items-center space-x-1">
              <span>⚡ {{ dev.batteryPercent }}%</span>
            </div>
          </div>
        </div>

        <!-- Capability pills -->
        <div class="mt-2.5 pt-2 border-t border-deaios-border/50 flex flex-wrap gap-1">
          <span 
            v-for="cap in dev.capabilities.slice(0, 3)" 
            :key="cap.contract"
            :class="[
              'text-[9px] px-1.5 py-0.5 rounded font-mono',
              cap.available ? 'bg-slate-800 text-slate-300 border border-slate-700' : 'bg-slate-900 text-slate-600 line-through'
            ]"
          >
            {{ cap.detail }}
          </span>
          <span v-if="dev.capabilities.length > 3" class="text-[9px] text-slate-500 px-1">
            +{{ dev.capabilities.length - 3 }}
          </span>
        </div>

        <!-- Pose coordinates -->
        <div class="mt-2 flex items-center justify-between text-[10px] text-slate-400 font-mono bg-slate-950/40 px-2 py-1 rounded">
          <span>位姿 (X, Y): ({{ dev.pose.x.toFixed(2) }}, {{ dev.pose.y.toFixed(2) }})</span>
          <span>偏航 θ: {{ (dev.pose.theta * 180 / Math.PI).toFixed(1) }}°</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { swarmState } from '../services/telemetryService';
import type { DeviceKind } from '../types/swarm';

defineEmits(['select-device']);

const selectedCategory = ref<string>('all');

const categories = [
  { key: 'all', label: '全部设备' },
  { key: 'legged_dog', label: '🐕 四足狗' },
  { key: 'wheeled_agv', label: '🚗 轮式车' },
  { key: 'fixed_camera', label: '📹 监控' },
  { key: 'human_terminal', label: '📱 本机' },
];

function getCategoryCount(key: string) {
  if (key === 'all') return swarmState.devices.length;
  return swarmState.devices.filter(d => d.kind === key).length;
}

const filteredDevices = computed(() => {
  if (selectedCategory.value === 'all') return swarmState.devices;
  return swarmState.devices.filter(d => d.kind === selectedCategory.value);
});

function getDeviceIcon(kind: DeviceKind) {
  switch (kind) {
    case 'legged_dog': return '🐕';
    case 'wheeled_agv': return '🚗';
    case 'fixed_camera': return '📹';
    case 'manipulator': return '🦾';
    case 'human_terminal': return '📱';
  }
}

function getDeviceColor(kind: DeviceKind) {
  switch (kind) {
    case 'legged_dog': return 'bg-cyan-500';
    case 'wheeled_agv': return 'bg-purple-500';
    case 'fixed_camera': return 'bg-pink-500';
    case 'manipulator': return 'bg-orange-500';
    case 'human_terminal': return 'bg-emerald-500';
  }
}

function getStatusBadge(status: string) {
  switch (status) {
    case 'ONLINE': return 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400';
    case 'BUSY': return 'bg-blue-500/15 border-blue-500/30 text-blue-400';
    case 'IDLE': return 'bg-slate-500/15 border-slate-500/30 text-slate-400';
    case 'OFFLINE': return 'bg-red-500/15 border-red-500/30 text-red-400';
    case 'ESTOP': return 'bg-red-600 text-white font-bold animate-pulse';
    default: return 'bg-slate-800 text-slate-300';
  }
}
</script>
