<template>
  <div class="p-4 space-y-4 pb-24 overflow-y-auto h-full">
    <!-- Natural Language Command Box -->
    <div class="bg-deaios-card border border-deaios-border rounded-xl p-4 shadow-lg">
      <div class="flex items-center justify-between mb-2">
        <h2 class="text-xs font-bold text-slate-300 flex items-center space-x-1.5">
          <span>✨</span>
          <span>集群自然语言任务下发</span>
        </h2>
        <span class="text-[10px] text-blue-400 font-mono">LLM/VLM DAG 编译器</span>
      </div>

      <div class="relative">
        <textarea 
          v-model="promptInput" 
          placeholder="例如：小狗带我去王老师办公室，让AGV去前台取快递..." 
          class="w-full bg-slate-950/80 border border-slate-700/80 rounded-lg p-3 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 min-h-[75px] resize-none"
        ></textarea>
      </div>

      <div class="mt-3 flex items-center justify-between">
        <!-- Hold-to-Talk Voice Button -->
        <button 
          @mousedown="startVoice"
          @mouseup="stopVoice"
          @touchstart="startVoice"
          @touchend="stopVoice"
          :class="[
            'px-3.5 py-2 rounded-lg text-xs font-semibold flex items-center space-x-2 transition',
            isRecording 
              ? 'bg-red-500 text-white animate-pulse shadow-md shadow-red-500/40' 
              : 'bg-slate-800 text-slate-300 border border-slate-700 active:bg-slate-700'
          ]"
        >
          <span>{{ isRecording ? '🔴 正在录音中...' : '🎙️ 按住说话' }}</span>
        </button>

        <!-- Submit Button -->
        <button 
          @click="submitPrompt(promptInput)" 
          :disabled="!promptInput.trim()"
          class="px-4 py-2 rounded-lg text-xs font-bold bg-blue-600 hover:bg-blue-500 active:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed text-white transition shadow-sm shadow-blue-500/30 flex items-center space-x-1"
        >
          <span>派发任务</span>
          <span>➔</span>
        </button>
      </div>
    </div>

    <!-- Quick Action Presets -->
    <div>
      <h3 class="text-xs font-bold text-slate-400 mb-2">⚡ 快速多机协同预设</h3>
      <div class="grid grid-cols-2 gap-2">
        <button 
          v-for="preset in presets" 
          :key="preset.title"
          @click="submitPrompt(preset.prompt)"
          class="bg-deaios-card border border-deaios-border p-3 rounded-lg text-left active:border-blue-500 transition group"
        >
          <div class="text-lg mb-1">{{ preset.icon }}</div>
          <div class="font-bold text-xs text-slate-200 group-active:text-blue-400">{{ preset.title }}</div>
          <div class="text-[10px] text-slate-400 mt-0.5 line-clamp-1">{{ preset.desc }}</div>
        </button>
      </div>
    </div>

    <!-- Active Mission DAG Stepper Card -->
    <div v-if="swarmState.activeMission" class="bg-deaios-card border border-blue-500/30 rounded-xl p-4 shadow-lg relative overflow-hidden">
      <div class="flex items-center justify-between pb-2 border-b border-deaios-border">
        <div>
          <div class="flex items-center space-x-2">
            <span class="w-2 h-2 rounded-full bg-blue-500 animate-ping"></span>
            <span class="font-bold text-xs text-blue-400">执行中任务 DAG</span>
          </div>
          <p class="text-xs text-slate-200 font-medium mt-1">"{{ swarmState.activeMission.title }}"</p>
        </div>
        <span class="text-[10px] font-mono bg-blue-500/20 text-blue-300 px-2 py-0.5 rounded border border-blue-500/30">
          {{ swarmState.activeMission.status }}
        </span>
      </div>

      <!-- Task DAG Steps -->
      <div class="mt-3 space-y-2.5">
        <div 
          v-for="(task, idx) in swarmState.activeMission.tasks" 
          :key="task.id"
          class="bg-slate-950/50 border border-slate-800 rounded-lg p-2.5 text-xs"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <span class="w-5 h-5 rounded-full bg-slate-800 flex items-center justify-center font-mono text-[10px] text-slate-300">
                {{ idx + 1 }}
              </span>
              <span class="font-medium text-slate-200">{{ task.title }}</span>
            </div>
            <span :class="['text-[10px] font-mono px-1.5 py-0.5 rounded', getTaskStatusClass(task.status)]">
              {{ task.status.toUpperCase() }}
            </span>
          </div>

          <div class="mt-2 flex items-center justify-between text-[10px] text-slate-400">
            <span>分配执行: <strong class="text-slate-300">{{ task.assignedDeviceName }}</strong></span>
            <span class="font-mono text-blue-400">{{ task.requiredCapability }}</span>
          </div>

          <!-- Progress Bar -->
          <div class="w-full bg-slate-800 h-1 rounded-full mt-2 overflow-hidden">
            <div 
              class="bg-blue-500 h-full transition-all duration-500" 
              :style="{ width: task.progressPercent + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { swarmState, submitMissionPrompt } from '../services/telemetryService';

const promptInput = ref('');
const isRecording = ref(false);

const presets = [
  {
    icon: '🗺️',
    title: '分布式地图共享接力',
    desc: '狗2扫描 ➔ 发布CAS ➔ 异构节点同步',
    prompt: '对当前走廊执行3D建图，并将不可变地图增量发布到CAS仓库'
  },
  {
    icon: '🚶',
    title: '双机伴随导览',
    desc: '狗2身前1.5m领航导引至办公室',
    prompt: '带领我去王义老师办公室'
  },
  {
    icon: '📦',
    title: '异构协同取件',
    desc: '高位相机粗筛 ➔ AGV前往取快递',
    prompt: '让AGV前往前台取快递，高位相机协同确认路况'
  },
  {
    icon: '🛑',
    title: '全机回充停泊',
    desc: '所有移动设备自主导航回Dock',
    prompt: '所有移动设备停止作业，返回停泊区'
  }
];

function submitPrompt(text: string) {
  if (!text.trim()) return;
  submitMissionPrompt(text);
  promptInput.value = '';
}

function startVoice() {
  isRecording.value = true;
  if ('vibrate' in navigator) navigator.vibrate(50);
}

function stopVoice() {
  if (!isRecording.value) return;
  isRecording.value = false;
  if ('vibrate' in navigator) navigator.vibrate([50, 50]);
  // Mock simulated ASR speech-to-text
  promptInput.value = '带领我去王义老师办公室，顺便让AGV把前台快递取过来';
}

function getTaskStatusClass(status: string) {
  switch (status) {
    case 'running': return 'bg-blue-500/20 text-blue-400 border border-blue-500/30 animate-pulse';
    case 'completed': return 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
    case 'failed': return 'bg-red-500/20 text-red-400 border border-red-500/30';
    default: return 'bg-slate-800 text-slate-400';
  }
}
</script>
