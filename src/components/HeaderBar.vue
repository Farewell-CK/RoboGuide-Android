<template>
  <header class="bg-deaios-card/90 backdrop-blur-md border-b border-deaios-border px-4 py-2.5 flex items-center justify-between sticky top-0 z-30 select-none">
    <div class="flex items-center space-x-2.5">
      <div class="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-blue-500/20">
        <span class="text-lg">🤖</span>
      </div>
      <div>
        <div class="flex items-center space-x-1.5">
          <h1 class="font-bold text-sm tracking-wide text-white">DEAIOS Hub</h1>
          <span class="text-[10px] bg-blue-500/20 text-blue-400 font-mono px-1.5 py-0.5 rounded border border-blue-500/30">v1.0</span>
        </div>
        <p class="text-[11px] text-slate-400 font-mono flex items-center space-x-1">
          <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
          <span>100.84.233.121 (中控)</span>
        </p>
      </div>
    </div>

    <!-- Right status cluster -->
    <div class="flex items-center space-x-2">
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

const onlineCount = computed(() => {
  return swarmState.devices.filter(d => d.status === 'ONLINE' || d.status === 'BUSY' || d.status === 'IDLE').length;
});

const totalCount = computed(() => swarmState.devices.length);
</script>
