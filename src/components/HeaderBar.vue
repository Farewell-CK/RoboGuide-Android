<template>
  <header class="bg-deaios-card/90 backdrop-blur-md border-b border-deaios-border px-4 py-2.5 flex items-center justify-between sticky top-0 z-30 select-none">
    <div class="flex items-center space-x-2.5">
      <div 
        @click="swarmState.showEnvSettings = true"
        class="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-blue-500/20 cursor-pointer active:scale-95 transition"
      >
        <span class="text-base">📱</span>
      </div>
      <div>
        <div class="flex items-center space-x-1.5">
          <h1 class="font-bold text-sm tracking-wide text-white">RoboGuide Mobile Node</h1>
          <button 
            @click="swarmState.showEnvSettings = true"
            :class="[
              'text-[10px] font-mono font-bold px-1.5 py-0.5 rounded border transition flex items-center space-x-1',
              swarmState.modeType === 'DEMO' ? 'bg-amber-500/20 text-amber-300 border-amber-500/30' :
              'bg-blue-500/20 text-blue-300 border-blue-500/30'
            ]"
          >
            <span>{{ swarmState.modeType === 'DEMO' ? 'DEMO 演示' : 'REAL 正式节点' }}</span>
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
              swarmState.nodeStatus.connected ? 'bg-emerald-400 animate-pulse' : 'bg-red-400'
            ]"
          ></span>
          <span class="truncate max-w-[150px]">
            {{ swarmState.nodeStatus.connected ? `gRPC 在线: ${shortNodeId}` : (swarmState.nodeStatus.state || '未连接') }}
          </span>
        </p>
      </div>
    </div>

    <!-- Right status cluster -->
    <div class="flex items-center space-x-2">
      <!-- Inventory Nodes count -->
      <div class="bg-slate-800/80 border border-slate-700 px-2 py-1 rounded-md text-[11px] flex items-center space-x-1.5">
        <span class="text-slate-400">中枢节点:</span>
        <span :class="['font-semibold', swarmState.controllerOnline ? 'text-emerald-400' : 'text-slate-400']">
          {{ swarmState.controllerOnline ? swarmState.devices.length : '离线' }}
        </span>
      </div>

      <!-- Emergency Stop button in header -->
      <button 
        @click="$emit('trigger-estop')"
        class="bg-red-500/20 active:bg-red-500 border border-red-500/40 text-red-400 active:text-white px-2.5 py-1 rounded-md text-xs font-bold transition flex items-center space-x-1 shadow-sm"
      >
        <span class="w-2 h-2 rounded-full bg-red-500"></span>
        <span>急停</span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { swarmState } from '../services/telemetryService';

defineEmits(['trigger-estop']);

const shortNodeId = computed(() => {
  const id = swarmState.phoneNodeId;
  if (!id) return 'node-phone';
  return id.length > 16 ? id.slice(0, 16) + '...' : id;
});
</script>
