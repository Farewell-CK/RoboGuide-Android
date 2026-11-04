<template>
  <div class="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-end sm:items-center justify-center p-0 sm:p-4 animate-fade-in">
    <div class="bg-slate-900 border border-slate-700 w-full sm:max-w-md rounded-t-2xl sm:rounded-2xl p-5 shadow-2xl space-y-4 max-h-[90vh] overflow-y-auto">
      <!-- Modal Header -->
      <div class="flex items-center justify-between border-b border-slate-800 pb-3">
        <div class="flex items-center space-x-2">
          <div class="w-8 h-8 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
            ⚙️
          </div>
          <div>
            <h3 class="text-base font-bold text-white tracking-wide">环境与集群配置中心</h3>
            <p class="text-xs text-slate-400 font-mono">RoboGuide Node & Controller Setup</p>
          </div>
        </div>
        <button 
          @click="$emit('close')" 
          class="w-8 h-8 rounded-full bg-slate-800 active:bg-slate-700 text-slate-400 flex items-center justify-center text-sm"
        >
          ✕
        </button>
      </div>

      <!-- Profile Selector Cards -->
      <div class="space-y-2.5">
        <div class="text-xs font-semibold text-slate-300 flex items-center justify-between">
          <span>选择当前集群模式:</span>
          <span class="text-[10px] text-blue-400 font-mono">严格区分 REAL / DEMO</span>
        </div>

        <div class="grid grid-cols-1 gap-2">
          <div
            v-for="(cfg, key) in swarmState.profiles"
            :key="key"
            @click="selectedProfile = key"
            :class="[
              'p-3 rounded-xl border transition cursor-pointer flex items-start space-x-3',
              selectedProfile === key 
                ? 'bg-blue-950/40 border-blue-500 ring-1 ring-blue-500/50 shadow-lg shadow-blue-500/10' 
                : 'bg-slate-800/60 border-slate-700/80 active:bg-slate-800 text-slate-300'
            ]"
          >
            <div :class="[
              'w-8 h-6 rounded-md flex items-center justify-center text-xs font-bold mt-0.5 shrink-0',
              cfg.isSimulated ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' :
              'bg-blue-500/20 text-blue-400 border border-blue-500/30'
            ]">
              {{ cfg.badge }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-white">{{ cfg.name }}</span>
                <span v-if="selectedProfile === key" class="text-[10px] text-emerald-400 font-mono">● 已选定</span>
              </div>
              <p class="text-[11px] text-slate-400 mt-0.5 leading-snug">{{ cfg.description }}</p>
              <div class="text-[10px] font-mono text-slate-400 mt-1 truncate">
                HTTP: {{ cfg.controllerEndpoint }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Endpoint & Port Inputs -->
      <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2.5">
        <div>
          <label class="text-xs font-semibold text-slate-300">Controller HTTP 地址 (Inventory / Missions):</label>
          <input 
            v-model="customEndpoint"
            type="text" 
            placeholder="http://127.0.0.1:8080"
            class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
          />
        </div>

        <div>
          <label class="text-xs font-semibold text-slate-300">NodeProtocol gRPC 端口 (bidirectional NodeSession):</label>
          <input 
            v-model.number="customGrpcPort"
            type="number" 
            placeholder="50051"
            class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
          />
        </div>

        <div>
          <label class="text-xs font-semibold text-slate-300">视频流基准地址 (可选):</label>
          <input 
            v-model="customStreamUrl"
            type="text" 
            placeholder="http://127.0.0.1:8092"
            class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
          />
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="flex items-center space-x-2 pt-2">
        <button
          @click="$emit('close')"
          class="flex-1 py-2.5 bg-slate-800 border border-slate-700 active:bg-slate-700 text-slate-300 font-bold text-xs rounded-xl transition"
        >
          取消
        </button>
        <button
          @click="applyAndSave"
          class="flex-1 py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 active:from-blue-500 active:to-indigo-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-500/20 transition flex items-center justify-center space-x-1.5"
        >
          <span>✓ 应用并重新连接</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { swarmState, switchEnvironment } from '../services/telemetryService';
import type { EnvProfileType } from '../types/swarm';

const emit = defineEmits(['close']);

const selectedProfile = ref<EnvProfileType>(swarmState.currentProfile);
const customEndpoint = ref(swarmState.controllerEndpoint);
const customGrpcPort = ref(swarmState.grpcPort || 50051);
const customStreamUrl = ref(swarmState.videoStreamBaseUrl);

function applyAndSave() {
  switchEnvironment(selectedProfile.value, customEndpoint.value, customStreamUrl.value, customGrpcPort.value);
  emit('close');
}
</script>
