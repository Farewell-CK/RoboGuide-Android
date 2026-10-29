<template>
  <div 
    v-if="swarmState.eStopTriggered" 
    class="fixed inset-0 z-50 bg-red-950/90 backdrop-blur-md flex flex-col items-center justify-center p-6 text-center animate-fade-in"
  >
    <!-- Warning Icon -->
    <div class="w-24 h-24 rounded-full bg-red-600/30 border-4 border-red-500 flex items-center justify-center mb-4 animate-ping">
      <span class="text-5xl">🛑</span>
    </div>

    <h2 class="text-2xl font-black text-white tracking-widest uppercase">
      集群硬件急停已触发
    </h2>
    <p class="text-sm text-red-200 mt-2 max-w-xs font-mono">
      EMERGENCY STOP BROADCASTED<br>
      全集群移动底盘电机扭矩已切断，锁死刹车！
    </p>

    <!-- Affected devices list -->
    <div class="mt-6 w-full max-w-xs bg-black/40 border border-red-500/40 rounded-xl p-3 text-xs text-left space-y-1.5 font-mono">
      <div v-for="dev in affectedDevices" :key="dev.id" class="flex items-center justify-between text-red-300">
        <span>{{ dev.name }}</span>
        <span class="font-bold text-red-400">BRAKED [0.0m/s]</span>
      </div>
    </div>

    <!-- Reset E-Stop Button -->
    <button 
      @click="resetEmergencyStop" 
      class="mt-8 px-8 py-3.5 bg-slate-900 border-2 border-slate-600 active:border-emerald-500 text-slate-100 font-bold rounded-xl shadow-xl active:bg-emerald-600 transition tracking-wide text-sm"
    >
      解除急停并恢复系统 (RESET)
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { swarmState, resetEmergencyStop } from '../services/telemetryService';

const affectedDevices = computed(() => {
  return swarmState.devices.filter(d => d.kind === 'legged_dog' || d.kind === 'wheeled_agv');
});
</script>
