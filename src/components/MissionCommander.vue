<template>
  <div class="p-4 space-y-4 pb-24 overflow-y-auto h-full select-none">
    
    <!-- REAL MODE VIEW -->
    <template v-if="swarmState.modeType === 'REAL'">
      <!-- Phone Node Card -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-xl p-4 shadow-lg space-y-3">
        <div class="flex items-center justify-between pb-2 border-b border-slate-800">
          <div class="flex items-center space-x-2">
            <span class="text-base">📱</span>
            <span class="font-bold text-xs text-white">Android Phone Node (正式节点)</span>
          </div>
          <span 
            :class="[
              'text-[10px] font-mono px-2 py-0.5 rounded font-bold border',
              swarmState.nodeStatus.connected 
                ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30' 
                : 'bg-amber-500/20 text-amber-300 border-amber-500/30'
            ]"
          >
            {{ swarmState.nodeStatus.state }}
          </span>
        </div>

        <div class="grid grid-cols-2 gap-2 text-[11px] font-mono">
          <div class="bg-slate-950/60 p-2 rounded border border-slate-800/80">
            <span class="text-slate-400 block text-[10px]">持久节点 ID (Node ID):</span>
            <span class="text-slate-200 truncate block" :title="swarmState.phoneNodeId">
              {{ swarmState.phoneNodeId || '未初始化' }}
            </span>
          </div>

          <div class="bg-slate-950/60 p-2 rounded border border-slate-800/80">
            <span class="text-slate-400 block text-[10px]">租约会话 (Session ID):</span>
            <span class="text-slate-200 truncate block">
              {{ swarmState.nodeStatus.sessionId || '无活跃 Session' }}
            </span>
          </div>

          <div class="bg-slate-950/60 p-2 rounded border border-slate-800/80">
            <span class="text-slate-400 block text-[10px]">心跳租约 (Lease / HBs):</span>
            <span class="text-emerald-400 font-bold block">
              {{ swarmState.nodeStatus.heartbeatCount }} 次心跳 (seq ok)
            </span>
          </div>

          <div class="bg-slate-950/60 p-2 rounded border border-slate-800/80">
            <span class="text-slate-400 block text-[10px]">gRPC 目标端点:</span>
            <span class="text-blue-400 block truncate">
              {{ swarmState.nodeStatus.endpoint || '127.0.0.1:50051' }}
            </span>
          </div>
        </div>

        <!-- Camera Permission & Sensor Tools -->
        <div class="pt-1 flex items-center justify-between text-xs">
          <div class="flex items-center space-x-1.5">
            <span class="text-slate-400">相机权限:</span>
            <span :class="[swarmState.nodeStatus.hasCameraPermission ? 'text-emerald-400 font-bold' : 'text-amber-400']">
              {{ swarmState.nodeStatus.hasCameraPermission ? '✓ 已授权' : '⚠️ 未授权' }}
            </span>
          </div>

          <div class="flex items-center space-x-2">
            <button 
              v-if="!swarmState.nodeStatus.hasCameraPermission"
              @click="requestCamera"
              class="px-2.5 py-1 bg-amber-500/20 active:bg-amber-500 border border-amber-500/40 text-amber-300 font-bold text-[11px] rounded"
            >
              申请相机权限
            </button>
            <button 
              @click="testLocalCapture"
              :disabled="isCapturing"
              class="px-2.5 py-1 bg-blue-600/30 active:bg-blue-600 border border-blue-500/40 text-blue-300 font-bold text-[11px] rounded flex items-center space-x-1"
            >
              <span>{{ isCapturing ? '采图中...' : '📸 验证采图' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Real Missions Dispatch Card -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-xl p-4 shadow-lg space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-xs font-bold text-slate-300 flex items-center space-x-1.5">
            <span>🚀</span>
            <span>RoboGuide 任务闭环触发器</span>
          </h2>
          <span class="text-[10px] text-emerald-400 font-mono">Formal Mission Dispatch</span>
        </div>

        <p class="text-[11px] text-slate-400 leading-relaxed">
          由手机经由 HTTP API 向 Controller 提交 Mission Plan，由调度器完成能力匹配、资源绑定并下发真实 gRPC Execute 指令：
        </p>

        <div class="grid grid-cols-2 gap-2 pt-1">
          <button 
            @click="dispatchPingMission"
            :disabled="isSubmittingMission"
            class="p-3 bg-slate-800/80 hover:bg-slate-800 active:bg-blue-600 border border-slate-700 active:border-blue-400 rounded-xl text-left transition group shadow disabled:opacity-40"
          >
            <div class="text-base mb-1">📡</div>
            <div class="font-bold text-xs text-slate-200 group-active:text-white">1. 发起真实 Ping 任务</div>
            <div class="text-[10px] text-slate-400 mt-0.5 font-mono">system.ping@v1</div>
          </button>

          <button 
            @click="dispatchAcquireMission"
            :disabled="isSubmittingMission"
            class="p-3 bg-slate-800/80 hover:bg-slate-800 active:bg-blue-600 border border-slate-700 active:border-blue-400 rounded-xl text-left transition group shadow disabled:opacity-40"
          >
            <div class="text-base mb-1">📷</div>
            <div class="font-bold text-xs text-slate-200 group-active:text-white">2. 发起真实采图任务</div>
            <div class="text-[10px] text-slate-400 mt-0.5 font-mono">observation.acquire@v1</div>
          </button>
        </div>

        <div v-if="submissionFeedback" class="p-2.5 rounded-lg bg-slate-950/80 border border-slate-800 font-mono text-[11px]">
          <div :class="[submissionFeedback.success ? 'text-emerald-400 font-bold' : 'text-red-400 font-bold']">
            {{ submissionFeedback.success ? '✓ 任务已成功提交' : '✕ 提交失败' }}
          </div>
          <p class="text-slate-300 mt-1 break-all text-[10px]">
            {{ submissionFeedback.message }}
          </p>
        </div>
      </div>

      <!-- Execution Journal Table -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-xl p-4 shadow-lg space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-1.5">
            <span class="text-base">📋</span>
            <span class="text-xs font-bold text-slate-300">SQLite 执行日志与产物对账</span>
          </div>
          <button 
            @click="refreshJournal"
            class="text-[10px] font-mono text-blue-400 hover:text-blue-300 active:underline flex items-center space-x-1"
          >
            <span>刷新对账</span>
            <span>⟳</span>
          </button>
        </div>

        <div v-if="swarmState.journalRecords.length === 0" class="text-center py-6 text-slate-500 text-xs font-mono">
          暂无本地任务执行记录 (等待 Controller Execute 指令)
        </div>

        <div v-else class="space-y-2">
          <div 
            v-for="rec in swarmState.journalRecords" 
            :key="rec.execution_id"
            class="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800 text-xs font-mono space-y-1"
          >
            <div class="flex items-center justify-between">
              <span class="font-bold text-slate-200 text-[11px] truncate max-w-[180px]">
                {{ rec.operation || 'unknown' }}
              </span>
              <span 
                :class="[
                  'text-[10px] font-bold px-1.5 py-0.2 rounded uppercase',
                  rec.phase === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' :
                  rec.phase === 'FAILED' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                  rec.phase === 'CANCELLED' ? 'bg-slate-700 text-slate-400 border border-slate-600' :
                  'bg-blue-500/20 text-blue-400 border border-blue-500/30 animate-pulse'
                ]"
              >
                {{ rec.phase }}
              </span>
            </div>

            <div class="text-[10px] text-slate-400 flex items-center justify-between">
              <span class="truncate max-w-[200px]">ID: {{ rec.execution_id }}</span>
              <span>{{ formatTime(rec.created_at_ms) }}</span>
            </div>

            <!-- Artifact SHA-256 Digest if present -->
            <div v-if="rec.artifact_digest" class="mt-1 pt-1 border-t border-slate-800 text-[10px] text-sky-400">
              <span class="text-slate-400">SHA256: </span>
              <span class="break-all">{{ rec.artifact_digest }}</span>
            </div>

            <div v-if="rec.reason" class="text-[10px] text-slate-400 break-all">
              <span class="text-slate-400">回执: </span>{{ rec.reason }}
            </div>
          </div>
        </div>
      </div>
    </template>


    <!-- DEMO MODE VIEW -->
    <template v-else>
      <div class="bg-amber-950/30 border border-amber-500/40 rounded-xl p-3 flex items-center space-x-2.5">
        <span class="text-xl">⚠️</span>
        <div>
          <h3 class="text-xs font-bold text-amber-300">DEMO 离线仿真演示模式</h3>
          <p class="text-[11px] text-slate-300">当前处于数字孪生展示环境，状态与进度为仿真模拟生成。</p>
        </div>
      </div>

      <!-- Natural Language Command Box (Demo) -->
      <div class="bg-slate-900/90 border border-slate-800 rounded-xl p-4 shadow-lg space-y-3">
        <h2 class="text-xs font-bold text-slate-300">✨ 离线仿真自然语言派发</h2>
        <textarea 
          v-model="promptInput" 
          placeholder="例如：双足狗协同巡检，对货架 A 区进行点云重构..." 
          class="w-full bg-slate-950/80 border border-slate-700/80 rounded-lg p-3 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 min-h-[60px] resize-none"
        ></textarea>
        <button 
          @click="submitDemoPrompt"
          class="w-full py-2 rounded-lg text-xs font-bold bg-blue-600 hover:bg-blue-500 text-white transition shadow"
        >
          派发仿真任务演示
        </button>
      </div>

      <!-- Active Demo Mission DAG -->
      <div v-if="swarmState.activeMission" class="bg-slate-900/90 border border-slate-800 rounded-xl p-4 space-y-2">
        <div class="flex items-center justify-between pb-1 border-b border-slate-800">
          <span class="text-xs font-bold text-slate-200">仿真任务: {{ swarmState.activeMission.title }}</span>
          <span class="text-[10px] font-mono text-amber-400">[DEMO DAG]</span>
        </div>
        <div v-for="t in swarmState.activeMission.tasks" :key="t.id" class="p-2 rounded bg-slate-950/50 text-xs">
          <div class="flex justify-between text-slate-300">
            <span>{{ t.title }}</span>
            <span class="text-emerald-400 font-mono">{{ t.status }}</span>
          </div>
        </div>
      </div>
    </template>

  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { swarmState, refreshJournalRecords, refreshNodeStatus } from '../services/telemetryService';
import { PhoneNode } from '../services/phoneNodePlugin';

const promptInput = ref('');
const isCapturing = ref(false);
const isSubmittingMission = ref(false);
const submissionFeedback = ref<{ success: boolean; message: string } | null>(null);

function formatTime(ms: number) {
  if (!ms) return '';
  return new Date(ms).toLocaleTimeString();
}

async function refreshJournal() {
  await refreshNodeStatus();
  await refreshJournalRecords();
}

async function requestCamera() {
  try {
    const res = await PhoneNode.requestCameraPermission();
    await refreshNodeStatus();
  } catch (e: any) {
    alert('申请相机权限失败: ' + e?.message);
  }
}

async function testLocalCapture() {
  isCapturing.value = true;
  try {
    const res = await PhoneNode.manualCapture({ condition: 'manual-optical-verification' });
    if (res.success) {
      alert(`采图成功!\nSHA-256: ${res.digest}\n文件路径: ${res.filePath}\n文件大小: ${res.sizeBytes} 字节`);
    } else {
      alert(`采图失败: ${res.error}`);
    }
    await refreshNodeStatus();
    await refreshJournalRecords();
  } catch (e: any) {
    alert('采图异常: ' + e?.message);
  } finally {
    isCapturing.value = false;
  }
}

async function dispatchPingMission() {
  await submitMissionPlan('system.ping@v1', {}, 'Validate formal system ping on phone node');
}

async function dispatchAcquireMission() {
  await submitMissionPlan('observation.acquire@v1', { expected: 'semantic site observation' }, 'Acquire photographic evidence via mobile camera');
}

async function submitMissionPlan(capabilityContract: string, parameters: Record<string, any>, objective: string) {
  isSubmittingMission.value = true;
  submissionFeedback.value = null;

  const missionId = `mission-phone-${Date.now()}`;
  const taskId = `task-${capabilityContract.replace(/[@.]/g, '-')}-${Date.now()}`;
  const [qualifiedName, version] = capabilityContract.split('@');
  const [namespace, name] = qualifiedName.split('.');

  const plan = {
    schema_version: 'roboguide.mission-plan/v0.3',
    mission: {
      id: missionId,
      objective: objective
    },
    contexts: [{
      id: 'phone-context',
      roles: [{ id: 'phone-role', actor: 'phone-actor' }],
      relations: []
    }],
    tasks: [{
      id: taskId,
      description: objective,
      context_id: 'phone-context',
      depends_on: [],
      roles: [{
        id: 'phone-exec-role',
        actor: 'phone-actor',
        capability: capabilityContract.startsWith('observation') ? 'observation' : 'compute',
        contract: {
          namespace: namespace,
          name: name,
          version: version
        },
        resource_kind: 'compute',
        context_role: 'phone-role',
        resource_scope: 'task',
        execution: {
          capability_contract: {
            namespace: namespace,
            name: name,
            version: version
          },
          parameters: parameters
        }
      }]
    }]
  };

  try {
    const url = `${swarmState.controllerEndpoint.replace(/\/$/, '')}/v1/missions`;
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(plan)
    });

    const bodyText = await res.text();
    if (res.ok) {
      submissionFeedback.value = {
        success: true,
        message: `Mission ${missionId} 提交成功: ${bodyText}`
      };
      // Poll journal for execution receipt
      setTimeout(refreshJournal, 500);
      setTimeout(refreshJournal, 1500);
      setTimeout(refreshJournal, 3000);
    } else {
      submissionFeedback.value = {
        success: false,
        message: `Controller 返回 HTTP ${res.status}: ${bodyText}`
      };
    }
  } catch (e: any) {
    submissionFeedback.value = {
      success: false,
      message: `网络请求失败: ${e.message} (请检查 Controller 地址是否可达)`
    };
  } finally {
    isSubmittingMission.value = false;
  }
}

function submitDemoPrompt() {
  if (!promptInput.value) return;
  swarmState.activeMission = {
    id: 'demo-mission-' + Date.now(),
    title: promptInput.value,
    rawPrompt: promptInput.value,
    createdAt: new Date().toLocaleTimeString(),
    status: 'EXECUTING',
    tasks: [
      {
        id: 'demo-t1',
        title: '双足狗强化学习巡检',
        assignedDeviceId: 'sim-dog-alpha',
        assignedDeviceName: '仿真领航犬 (Alpha)',
        requiredCapability: 'mobility.legged.terrain@v1',
        status: 'running',
        progressPercent: 60
      }
    ]
  };
  promptInput.value = '';
}
</script>
