<template>
  <header class="bg-deaios-card/90 backdrop-blur-md border-b border-deaios-border px-4 py-2.5 flex items-center justify-between sticky top-0 z-30 select-none">
    <div class="flex items-center space-x-2.5">
      <div 
        @click="swarmState.showEnvSettings = true"
        class="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-blue-500/20 cursor-pointer active:scale-95 transition"
      >
        <span class="text-base">🤖</span>
      </div>
      <div>
        <div class="flex items-center space-x-1.5">
          <h1 class="font-bold text-sm tracking-wide text-white">DEAIOS Hub</h1>
          <button 
            @click="swarmState.showEnvSettings = true"
            :class="[
              'text-[10px] font-mono font-bold px-1.5 py-0.5 rounded border transition flex items-center space-x-1',
              swarmState.currentProfile === 'real_swarm' ? 'bg-amber-500/20 text-amber-300 border-amber-500/30' :
              swarmState.currentProfile === 'isaac_sim' ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30' :
              'bg-blue-500/20 text-blue-300 border-blue-500/30'
            ]"
          >
            <span>{{ activeProfileBadge }}</span>
            <span class="text-[9px] opacity-70">⚙️</span>
          </button>
        </div>
        <p 
          @click="swarmState.showEnvSettings = true"
          class="text-[11px] text-slate-400 font-mono flex items-center space-x-1 cursor-pointer"
        >
          <span 
            :class="[
              'w-1.5 h-1.5 rounded-full',
              swarmState.controllerOnline ? 'bg-emerald-400 animate-pulse' : 'bg-red-400'
            ]"
          ></span>
          <span class="truncate max-w-[140px]">{{ endpointHost }}</span>
        </p>
      </div>
    </div>

    <!-- Right status cluster -->
    <div class="flex items-center space-x-2">
      <!-- Fleet count -->
      <div class="bg-slate-800/80 border border-slate-700 px-2 py-1 rounded-md text-[11px] flex items-center space-x-1.5">
        <span class="text-slate-400">集群:</span>
        <span class="font-semibold text-emerald-400">{{ onlineCount }}/{{ totalCount }}</span>
      </div>

      <!-- Quick Emergency Stop button in header -->
      <button 
        @click="$emit('trigger-estop')"
        class="bg-red-500/20 active:bg-red-500 border border-red-500/40 text-red-400 active:text-white px-2.5 py-1 rounded-md text-xs font-bold transition flex items-center space-x-1 shadow-sm"
      >
        <span class="w-2 h-2 rounded-full bg-red-500 animate-ping"></span>
        <span>急停</span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { swarmState } from '../services/telemetryService';

defineEmits(['trigger-estop']);

const activeProfileBadge = computed(() => {
  const current = swarmState.profiles[swarmState.currentProfile];
  return current ? current.badge : '默认';
});

const endpointHost = computed(() => {
  try {
    const url = new URL(swarmState.controllerEndpoint);
    return `${url.hostname}:${url.port || '80'}`;
  } catch {
    return swarmState.controllerEndpoint.replace('http://', '');
  }
});

const onlineCount = computed(() => {
  return swarmState.devices.filter(d => d.status === 'ONLINE' || d.status === 'BUSY' || d.status === 'IDLE').length;
});

const totalCount = computed(() => swarmState.devices.length);
</script>
