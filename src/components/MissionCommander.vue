<template>
  <div class="p-4 space-y-4 pb-24 overflow-y-auto h-full select-none">
    <!-- Natural Language Command Box -->
    <div class="bg-slate-900/90 border border-slate-800 rounded-xl p-4 shadow-lg">
      <div class="flex items-center justify-between mb-2">
        <h2 class="text-xs font-bold text-slate-300 flex items-center space-x-1.5">
          <span>✨</span>
          <span>集群自然语言任务下发</span>
        </h2>
        <span class="text-[10px] text-blue-400 font-mono">RoboGuide DAG 编译器</span>
      </div>

      <div class="relative">
        <textarea 
          v-model="promptInput" 
          placeholder="例如：小狗领航导览至 302 会议室，协同小车在后方伴随跟随..." 
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

    <!-- RoboGuide Human-in-the-Loop (HITL) Clarification Dialogue Bubble -->
    <div 
      v-if="swarmState.activeClarification" 
      class="bg-amber-950/40 border border-amber-500/50 rounded-xl p-4 shadow-xl animate-fade-in space-y-2.5"
    >
      <div class="flex items-center space-x-2">
        <span class="text-lg">💬</span>
        <div class="flex-1">
          <div class="flex items-center justify-between">
            <h4 class="text-xs font-bold text-amber-300">RoboGuide 交互澄清 (open_question)</h4>
            <span class="text-[9px] bg-amber-500/20 text-amber-400 font-mono px-1.5 py-0.5 rounded border border-amber-500/30">
              BLOCKING UNCERTAINTY
            </span>
          </div>
          <p class="text-xs text-slate-200 mt-1 font-medium">
            {{ swarmState.activeClarification.question }}
          </p>
        </div>
      </div>

      <!-- Clarification Clickable Options Chips -->
      <div class="flex flex-wrap gap-2 pt-1">
        <button
          v-for="opt in swarmState.activeClarification.options"
          :key="opt"
          @click="chooseClarification(opt)"
          class="px-3 py-1.5 bg-amber-500/20 active:bg-amber-500 hover:bg-amber-500/30 border border-amber-500/40 text-amber-200 text-xs font-bold rounded-lg transition active:scale-95 shadow-sm"
        >
          {{ opt }}
        </button>
      </div>
    </div>

    <!-- Quick Action Presets -->
    <div>
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-xs font-bold text-slate-400">⚡ 快速多机协同预设</h3>
        <span class="text-[10px] text-slate-500 font-mono">
          {{ swarmState.currentProfile === 'isaac_sim' ? '仿真环境方案' : '实机集群方案' }}
        </span>
      </div>
      <div class="grid grid-cols-2 gap-2">
        <button 
          v-for="preset in presets" 
          :key="preset.title"
          @click="submitPrompt(preset.prompt)"
          class="bg-slate-900/90 border border-slate-800 p-3 rounded-lg text-left active:border-blue-500 transition group shadow"
        >
          <div class="text-lg mb-1">{{ preset.icon }}</div>
          <div class="font-bold text-xs text-slate-200 group-active:text-blue-400">{{ preset.title }}</div>
          <div class="text-[10px] text-slate-400 mt-0.5 line-clamp-1">{{ preset.desc }}</div>
        </button>
      </div>
    </div>

    <!-- Active Mission DAG Stepper Card -->
    <div v-if="swarmState.activeMission" class="bg-slate-900/90 border border-blue-500/30 rounded-xl p-4 shadow-lg relative overflow-hidden">
      <div class="flex items-center justify-between pb-2 border-b border-slate-800">
        <div>
          <div class="flex items-center space-x-2">
            <span class="w-2 h-2 rounded-full bg-blue-500 animate-ping"></span>
            <span class="font-bold text-xs text-blue-400">多机协同执行 Task DAG</span>
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
          class="bg-slate-950/60 border border-slate-800 rounded-lg p-2.5 text-xs shadow-inner"
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
            <span>执行者: {{ task.assignedDeviceName }}</span>
            <span class="font-mono text-slate-500">{{ task.requiredCapability }}</span>
          </div>

          <!-- Progress Bar -->
          <div class="w-full bg-slate-800 rounded-full h-1 mt-2 overflow-hidden">
            <div 
              class="bg-blue-500 h-1 rounded-full transition-all duration-300"
              :style="{ width: `${task.progressPercent}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { swarmState, answerClarification } from '../services/telemetryService';
import type { ActiveMission } from '../types/swarm';

const promptInput = ref('');
const isRecording = ref(false);

const presets = computed(() => {
  if (swarmState.currentProfile === 'isaac_sim') {
    return [
      {
        icon: '🏭',
        title: '双足狗协同巡检 (Isaac)',
        desc: '领航犬 Alpha 探路，Beta 犬侧翼伴随扫描货架',
        prompt: '在 Isaac 园区中启动双足机器狗协同巡检，对立体货架 A 区进行点云重构。'
      },
      {
        icon: '📦',
        title: '园区异构搬运 (Isaac)',
        desc: '鹰眼监控粗筛，小狗引导，叉车搬运托盘',
        prompt: '鹰眼监控检测到货架托盘就位，派遣仿真小狗确认路径，通知电动叉车前往搬运。'
      },
      {
        icon: '🗺️',
        title: '全景子图闭环拼图',
        desc: '双机分别建图并推送到 CAS 空间仓库',
        prompt: '触发双机局部子图共享，执行位姿图优化闭环拼板。'
      },
      {
        icon: '🚨',
        title: '全员避障待命',
        desc: '广播进入待命站立刚性锁定',
        prompt: '检测到前方临时施工，所有仿真设备就近停靠在通道两侧。'
      }
    ];
  }

  return [
    {
      icon: '🗺️',
      title: '分布式地图共享接力',
      desc: '双狗实地建图，发布至 CAS 仓库并交叉验证',
      prompt: '执行分布式地图共享：1号狗探路建图并发布，2号狗导入验证不可变地图。'
    },
    {
      icon: '🚶',
      title: '双机伴随导览',
      desc: '狗在人身前 1.5m 领航导引至 302 会议室',
      prompt: '领航狗在持机人身前 1.5 米处伴随导览，目标为 302 会议室。'
    },
    {
      icon: '📦',
      title: '异构协同取件',
      desc: '高位相机粗筛 ➔ 狗探路 ➔ AGV 搬运货物',
      prompt: '走廊监控识别障碍，小狗前出确认地面通行度，AGV 运输重载货物。'
    },
    {
      icon: '🔋',
      title: '一键低电量轮替',
      desc: '低电量节点自动交接上下文并前往充电桩',
      prompt: '检查全集群电量，若小于 30% 则将当前任务角色移交给备选节点。'
    }
  ];
});

function startVoice() {
  isRecording.value = true;
}

function stopVoice() {
  if (isRecording.value) {
    isRecording.value = false;
    promptInput.value = '领航狗在身前伴随导览至会议室，协同设备在后方随行。';
  }
}

function chooseClarification(opt: string) {
  if (swarmState.activeClarification) {
    answerClarification(swarmState.activeClarification.id, opt);
  }
}

function submitPrompt(text: string) {
  if (!text.trim()) return;

  const isSim = swarmState.currentProfile === 'isaac_sim';

  // Trigger clarification for demonstration of RoboGuide HITL
  swarmState.activeClarification = {
    id: 'clarify-' + Date.now(),
    question: isSim 
      ? '检测到园区有两个待巡检货架（A区与B区），请确认首选编队方向：' 
      : '检测到 302 会议室有东门与西门两个入口，请指定领航接入点：',
    options: isSim 
      ? ['优先立体货架 A 区 (Alpha领航)', '优先自动分拣 B 区 (Beta领航)'] 
      : ['东侧主入口通道 (平地宽敞)', '西侧连廊通道 (需过减速带)']
  };

  const newMission: ActiveMission = {
    id: 'mission-' + Date.now(),
    title: text.length > 25 ? text.slice(0, 25) + '...' : text,
    rawPrompt: text,
    createdAt: new Date().toLocaleTimeString(),
    status: 'MATCHING',
    tasks: isSim ? [
      {
        id: 't-1',
        title: '解析意图与多机能力匹配',
        assignedDeviceId: 'controller',
        assignedDeviceName: 'RoboGuide 中枢',
        requiredCapability: 'compute.dag.compiler@v1',
        status: 'completed',
        progressPercent: 100
      },
      {
        id: 't-2',
        title: '领航犬 Alpha 强化学习步态巡检',
        assignedDeviceId: 'sim-dog-alpha',
        assignedDeviceName: '仿真领航犬 (Alpha)',
        requiredCapability: 'mobility.legged.terrain@v1',
        status: 'pending',
        progressPercent: 0
      },
      {
        id: 't-3',
        title: '协同犬 Beta 伴随扫描与点云融合',
        assignedDeviceId: 'sim-dog-beta',
        assignedDeviceName: '仿真协同犬 (Beta)',
        requiredCapability: 'spatial.map.build@v0',
        status: 'pending',
        progressPercent: 0
      }
    ] : [
      {
        id: 't-1',
        title: '能力契约与空间时序联合调度',
        assignedDeviceId: 'controller',
        assignedDeviceName: 'RoboGuide 控制平面',
        requiredCapability: 'compute.dag.compiler@v1',
        status: 'completed',
        progressPercent: 100
      },
      {
        id: 't-2',
        title: '2号狗领航前出导航',
        assignedDeviceId: 'dog-thor-2',
        assignedDeviceName: '2号狗 (Thor 2)',
        requiredCapability: 'mobility.legged.terrain@v1',
        status: 'pending',
        progressPercent: 0
      },
      {
        id: 't-3',
        title: 'AGV 平地巡航伴随重载物料',
        assignedDeviceId: 'agv-chassis-01',
        assignedDeviceName: '轮式重载车 (AGV-01)',
        requiredCapability: 'transport.payload.heavy@v1',
        status: 'pending',
        progressPercent: 0
      }
    ]
  };

  swarmState.activeMission = newMission;
  promptInput.value = '';
}

function getTaskStatusClass(status: string) {
  switch (status) {
    case 'completed': return 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
    case 'running': return 'bg-blue-500/20 text-blue-400 border border-blue-500/30 animate-pulse';
    case 'failed': return 'bg-red-500/20 text-red-400 border border-red-500/30';
    default: return 'bg-slate-800 text-slate-500 border border-slate-700';
  }
}
</script>
