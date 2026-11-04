<template>
  <div 
    v-if="swarmState.eStopTriggered" 
    class="fixed inset-0 z-50 bg-red-950/90 backdrop-blur-md flex flex-col items-center justify-center p-6 text-center animate-fade-in select-none"
  >
    <!-- Warning Icon -->
    <div class="w-20 h-20 rounded-full bg-red-600/30 border-4 border-red-500 flex items-center justify-center mb-4">
      <span class="text-4xl">🛑</span>
    </div>

    <!-- Mode conditional display -->
    <template v-if="swarmState.modeType === 'DEMO'">
      <span class="text-[10px] font-mono font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30 px-2 py-0.5 rounded uppercase mb-2">
        DEMO SIMULATION 仿真演示
      </span>
      <h2 class="text-xl font-black text-white tracking-widest uppercase">
        仿真集群急停演练
      </h2>
      <p class="text-xs text-red-200 mt-2 max-w-xs font-mono">
        [离线演示] 仿真机器人移动已暂停。
      </p>

      <button 
        @click="resetEmergencyStop" 
        class="mt-6 px-6 py-2.5 bg-slate-900 border-2 border-slate-600 active:border-emerald-500 text-slate-100 font-bold rounded-xl shadow-xl active:bg-emerald-600 transition text-xs"
      >
        恢复仿真演示 (DEMO RESET)
      </button>
    </template>

    <template v-else>
      <span class="text-[10px] font-mono font-bold bg-red-500/20 text-red-300 border border-red-500/30 px-2 py-0.5 rounded uppercase mb-2">
        REAL MODE 手机本地安全拦截
      </span>
      <h2 class="text-xl font-black text-white tracking-widest uppercase">
        实体硬件急停安全警告
      </h2>
      <div class="bg-black/50 border border-red-500/40 rounded-xl p-4 mt-3 max-w-sm text-left space-y-2 text-xs font-mono text-red-200">
        <p class="text-amber-300 font-bold">
          ⚠️ 注意：手机端未接通硬件级 E-Stop 继电器/高优先级急停通道。
        </p>
        <p class="text-slate-300 text-[11px] leading-relaxed">
          根据安全规范，手机应用不能单方面宣称已切断机器狗电机扭矩或刹停真机。<br><br>
          如现场发生危险，请立即按下机器狗本体物理急停按钮或机载遥控器安全开关！
        </p>
        <p class="text-slate-400 text-[10px]">
          安全占用解除权归属 RoboGuide 后端对账仲裁，手机端不具备直接解除权限。
        </p>
      </div>

      <button 
        @click="resetEmergencyStop" 
        class="mt-6 px-6 py-2.5 bg-slate-800 border border-slate-600 text-slate-200 font-bold rounded-xl text-xs active:bg-slate-700 transition"
      >
        关闭警示弹窗 (Dismiss Alert)
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { swarmState, resetEmergencyStop } from '../services/telemetryService';
</script>
