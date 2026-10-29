<template>
  <div class="p-4 space-y-3 pb-24 overflow-y-auto h-full select-none">
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
            : 'bg-slate-900 border border-slate-800 text-slate-400 active:bg-slate-800'
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
        class="bg-slate-900/90 border border-slate-800 rounded-xl p-3.5 transition relative overflow-hidden shadow-lg"
      >
        <!-- Left color stripe by kind -->
        <div :class="['absolute left-0 top-0 bottom-0 w-1.5', getDeviceColor(dev.kind)]"></div>

        <div class="flex items-start justify-between">
          <div class="flex items-center space-x-2.5">
            <span class="text-2xl">{{ getDeviceIcon(dev.kind) }}</span>
            <div>
              <div class="flex items-center space-x-2">
                <h3 class="font-bold text-sm text-slate-100">{{ dev.name }}</h3>
                <span v-if="dev.roleTag" class="text-[9px] bg-blue-500/20 text-blue-400 border border-blue-500/30 px-1.5 py-0.2 rounded font-mono">
                  {{ dev.roleTag }}
                </span>
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
        <div class="mt-2.5 pt-2 border-t border-slate-800 flex flex-wrap gap-1">
          <span 
            v-for="cap in dev.capabilities.slice(0, 3)" 
            :key="cap.contract"
            :class="[
              'text-[9px] px-1.5 py-0.5 rounded font-mono',
              cap.available ? 'bg-slate-800 text-slate-300 border border-slate-700' : 'bg-slate-950 text-slate-600 line-through'
            ]"
          >
            {{ cap.detail }}
          </span>
          <span v-if="dev.capabilities.length > 3" class="text-[9px] text-slate-500 px-1">
            +{{ dev.capabilities.length - 3 }}
          </span>
        </div>

        <!-- Coordinates bar -->
        <div class="mt-2 flex items-center justify-between text-[10px] text-slate-400 font-mono bg-slate-950/60 px-2.5 py-1 rounded-lg">
          <span>位姿 (X, Y): ({{ dev.pose.x.toFixed(2) }}, {{ dev.pose.y.toFixed(2) }})</span>
          <span>偏航 θ: {{ ((dev.pose.theta * 180) / Math.PI).toFixed(1) }}°</span>
        </div>

        <!-- Quick Action Toolbar -->
        <div class="mt-2.5 pt-2 border-t border-slate-800 flex items-center justify-end space-x-1.5">
          <button 
            @click.stop="toggleFollow(dev.id)"
            :class="[
              'px-2.5 py-1 rounded-lg text-xs font-bold border transition flex items-center space-x-1',
              swarmState.followDeviceId === dev.id 
                ? 'bg-emerald-600 border-emerald-400 text-white' 
                : 'bg-slate-800 border-slate-700 active:bg-slate-700 text-slate-300'
            ]"
          >
            <span>🎯</span>
            <span>{{ swarmState.followDeviceId === dev.id ? '取消锁定' : '视角锁定' }}</span>
          </button>

          <button 
            v-if="dev.kind === 'legged_dog' || dev.kind === 'fixed_camera'"
            @click.stop="openFpv(dev.id)"
            class="px-2.5 py-1 bg-slate-800 border border-slate-700 active:bg-slate-700 text-cyan-300 rounded-lg text-xs font-bold transition flex items-center space-x-1"
          >
            <span>👁️</span>
            <span>FPV 画面</span>
          </button>

          <button 
            v-if="dev.kind === 'legged_dog' || dev.kind === 'wheeled_agv'"
            @click.stop="$emit('select-device', dev.id)"
            class="px-3 py-1 bg-blue-600 active:bg-blue-500 text-white rounded-lg text-xs font-bold transition shadow shadow-blue-500/20 flex items-center space-x-1"
          >
            <span>🎮</span>
            <span>遥控详情</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { swarmState, openFpvStream } from '../services/telemetryService';
import type { DeviceKind } from '../types/swarm';

defineEmits(['select-device']);

const selectedCategory = ref<string>('all');

const categories = [
  { key: 'all', label: '全部' },
  { key: 'legged_dog', label: '🐕 四足狗' },
  { key: 'wheeled_agv', label: '🚗 轮式车' },
  { key: 'fixed_camera', label: '📹 监控' },
  { key: 'human_terminal', label: '📱 本机' },
];

function toggleFollow(id: string) {
  if (swarmState.followDeviceId === id) {
    swarmState.followDeviceId = null;
  } else {
    swarmState.followDeviceId = id;
    swarmState.selectedDeviceId = id;
  }
}

function openFpv(id: string) {
  openFpvStream(id);
}

function getCategoryCount(key: string) {
  if (key === 'all') return swarmState.devices.length;
  return swarmState.devices.filter(d => d.kind === key).length;
}

const filteredDevices = computed(() => {
  if (selectedCategory.value === 'all') {
    return swarmState.devices;
  }
  return swarmState.devices.filter(d => d.kind === selectedCategory.value);
});

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

function getDeviceColor(kind: DeviceKind) {
  switch (kind) {
    case 'legged_dog': return 'bg-cyan-500';
    case 'wheeled_agv': return 'bg-purple-500';
    case 'manipulator': return 'bg-amber-500';
    case 'fixed_camera': return 'bg-blue-500';
    case 'human_terminal': return 'bg-emerald-500';
    default: return 'bg-slate-500';
  }
}

function getStatusBadge(status: string) {
  switch (status) {
    case 'ONLINE': return 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30';
    case 'BUSY': return 'bg-blue-500/20 text-blue-400 border-blue-500/30';
    case 'IDLE': return 'bg-amber-500/20 text-amber-400 border-amber-500/30';
    case 'ESTOP': return 'bg-red-500/20 text-red-400 border-red-500/30';
    case 'OFFLINE': default: return 'bg-slate-800 text-slate-500 border-slate-700';
  }
}
</script>
