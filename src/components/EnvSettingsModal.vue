<template>
  <div class="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-end sm:items-center justify-center p-0 sm:p-4 animate-fade-in">
    <div class="bg-slate-900 border border-slate-700 w-full sm:max-w-lg rounded-t-2xl sm:rounded-2xl p-5 shadow-2xl space-y-4 max-h-[92vh] overflow-y-auto">
      <!-- Modal Header -->
      <div class="flex items-center justify-between border-b border-slate-800 pb-3">
        <div class="flex items-center space-x-2">
          <div class="w-8 h-8 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
            ⚙️
          </div>
          <div>
            <h3 class="text-base font-bold text-white tracking-wide">环境与安全接入配置</h3>
            <p class="text-xs text-slate-400 font-mono">RoboGuide Node & Operator mTLS</p>
          </div>
        </div>
        <button 
          @click="$emit('close')" 
          class="w-8 h-8 rounded-full bg-slate-800 active:bg-slate-700 text-slate-400 flex items-center justify-center text-sm"
        >
          ✕
        </button>
      </div>

      <!-- Settings Sub-Tabs -->
      <div class="flex border-b border-slate-800 text-xs font-semibold">
        <button
          @click="activeSubTab = 'endpoints'"
          :class="[
            'pb-2 px-3 transition border-b-2',
            activeSubTab === 'endpoints' ? 'border-blue-500 text-blue-400 font-bold' : 'border-transparent text-slate-400'
          ]"
        >
          🌐 集群与端点
        </button>
        <button
          @click="activeSubTab = 'security'"
          :class="[
            'pb-2 px-3 transition border-b-2 flex items-center space-x-1',
            activeSubTab === 'security' ? 'border-blue-500 text-blue-400 font-bold' : 'border-transparent text-slate-400'
          ]"
        >
          <span>🔐 安全凭据 (mTLS)</span>
          <span v-if="securityConfig.useMtls" class="w-2 h-2 rounded-full bg-emerald-400"></span>
        </button>
      </div>

      <!-- Tab 1: Endpoints & Topology -->
      <div v-show="activeSubTab === 'endpoints'" class="space-y-3.5">
        <!-- Quick Presets -->
        <div>
          <label class="text-xs font-semibold text-slate-300">快速场景预设:</label>
          <div class="grid grid-cols-3 gap-2 mt-1.5">
            <button
              @click="applyPreset('thinkpad')"
              type="button"
              class="p-2 bg-slate-800 border border-slate-700 rounded-lg text-left active:border-blue-500 transition"
            >
              <div class="text-[11px] font-bold text-white">ThinkPad 现场</div>
              <div class="text-[9px] text-blue-400 font-mono mt-0.5">192.168.2.20 (mTLS)</div>
            </button>
            <button
              @click="applyPreset('loopback')"
              type="button"
              class="p-2 bg-slate-800 border border-slate-700 rounded-lg text-left active:border-blue-500 transition"
            >
              <div class="text-[11px] font-bold text-white">受控本地隧道</div>
              <div class="text-[9px] text-emerald-400 font-mono mt-0.5">127.0.0.1 (ADB)</div>
            </button>
            <button
              @click="applyPreset('demo')"
              type="button"
              class="p-2 bg-slate-800 border border-slate-700 rounded-lg text-left active:border-blue-500 transition"
            >
              <div class="text-[11px] font-bold text-white">Isaac Lab</div>
              <div class="text-[9px] text-amber-400 font-mono mt-0.5">离线 DEMO 演示</div>
            </button>
          </div>
        </div>

        <!-- Node ID and generation -->
        <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-semibold text-slate-300">本机独立 Node 身份 (Node ID):</label>
            <button
              @click="generateNewIdentity"
              type="button"
              class="text-[10px] bg-blue-600/30 text-blue-300 border border-blue-500/40 px-2 py-0.5 rounded font-mono active:bg-blue-600/50"
            >
              ⚡ 生成独立身份与密钥
            </button>
          </div>
          <input
            v-model="securityConfig.nodeId"
            type="text"
            placeholder="phone-node-xxxx"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
          />
          <p class="text-[10px] text-slate-400">
            每部手机拥有独立的物理身份标识，连接与重连时严格对账，不与其他手机冲突。
          </p>
        </div>

        <!-- Endpoint Inputs -->
        <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2.5">
          <div class="grid grid-cols-3 gap-2">
            <div class="col-span-2">
              <label class="text-xs font-semibold text-slate-300">Node gRPC 主机:</label>
              <input
                v-model="securityConfig.nodeGrpcHost"
                type="text"
                placeholder="192.168.2.20"
                class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
              />
            </div>
            <div>
              <label class="text-xs font-semibold text-slate-300">gRPC 端口:</label>
              <input
                v-model.number="securityConfig.nodeGrpcPort"
                type="number"
                placeholder="50051"
                class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label class="text-xs font-semibold text-slate-300">Operator HTTPS 控制面入口:</label>
            <input
              v-model="securityConfig.operatorHttpsUrl"
              type="text"
              placeholder="https://192.168.2.20:8080"
              class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-slate-300">Artifact HTTPS 产物中心地址:</label>
            <input
              v-model="securityConfig.artifactHttpsUrl"
              type="text"
              placeholder="https://192.168.2.20:8090"
              class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-slate-300">视频流基准地址 (可选):</label>
            <input
              v-model="customStreamUrl"
              type="text"
              placeholder="http://192.168.2.41:8092"
              class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <label class="text-xs font-semibold text-slate-300">TLS 服务名覆写 (可选):</label>
            <input
              v-model="securityConfig.tlsServerName"
              type="text"
              placeholder="留空使用目标 IP，或输入 server.roboguide.local"
              class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
            />
          </div>
        </div>
      </div>

      <!-- Tab 2: Security & PKI Credentials -->
      <div v-show="activeSubTab === 'security'" class="space-y-3.5">
        <!-- mTLS Toggle Switch -->
        <div class="flex items-center justify-between bg-slate-800/80 border border-slate-700 rounded-xl p-3">
          <div>
            <div class="text-xs font-bold text-white">启用原生 mTLS 双向认证</div>
            <p class="text-[10px] text-slate-400">gRPC NodeSession 与 HTTPS 数据面使用客户端证书鉴权</p>
          </div>
          <label class="relative inline-flex items-center cursor-pointer">
            <input type="checkbox" v-model="securityConfig.useMtls" class="sr-only peer" />
            <div class="w-11 h-6 bg-slate-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
          </label>
        </div>

        <!-- Server CA Root -->
        <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-1.5">
          <label class="text-xs font-semibold text-slate-300 flex items-center justify-between">
            <span>服务端信任根 CA 证书 (ca.crt):</span>
            <span v-if="securityConfig.serverCaCertPem" class="text-[10px] text-emerald-400 font-mono">✓ 已配置</span>
          </label>
          <textarea
            v-model="securityConfig.serverCaCertPem"
            rows="3"
            placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500"
          ></textarea>
        </div>

        <!-- Node Credentials -->
        <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-bold text-blue-300">1. 本机 Node 客户端证书 (Node.crt):</label>
            <span v-if="securityConfig.nodeCertFingerprint" class="text-[9px] text-cyan-400 font-mono">
              SHA-256 计算完成
            </span>
          </div>
          <textarea
            v-model="securityConfig.nodeCertPem"
            rows="3"
            placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500"
          ></textarea>
          
          <div v-if="securityConfig.nodeCertFingerprint" class="p-2 bg-slate-950 rounded border border-cyan-500/30">
            <div class="text-[10px] text-slate-400 font-semibold">Node 证书指纹 (供服务端白名单配置):</div>
            <div class="text-[10px] text-cyan-300 font-mono break-all select-all mt-0.5">
              {{ securityConfig.nodeCertFingerprint }}
            </div>
          </div>

          <label class="text-xs font-semibold text-slate-300 flex items-center justify-between">
            <span>本机 Node 客户端私钥 (Node.key):</span>
            <span v-if="securityConfig.hasNodeKey || securityConfig.nodeKeyPem" class="text-[10px] text-emerald-400 font-mono">✓ 密钥已就绪</span>
          </label>
          <textarea
            v-model="securityConfig.nodeKeyPem"
            rows="3"
            placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500"
          ></textarea>
        </div>

        <!-- Operator Credentials -->
        <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-bold text-indigo-300">2. 操作员凭据 (admin.crt / viewer.crt):</label>
            <select
              v-model="securityConfig.operatorRole"
              class="bg-slate-900 border border-slate-700 text-[10px] text-white rounded px-2 py-0.5 font-mono"
            >
              <option value="viewer">角色: 观察员 (viewer)</option>
              <option value="operator">角色: 操作员 (operator)</option>
              <option value="recovery-admin">角色: 管理员 (recovery-admin)</option>
            </select>
          </div>
          <textarea
            v-model="securityConfig.operatorCertPem"
            rows="2"
            placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500"
          ></textarea>

          <label class="text-xs font-semibold text-slate-300 flex items-center justify-between">
            <span>操作员私钥 (admin.key / viewer.key):</span>
            <span v-if="securityConfig.hasOperatorKey || securityConfig.operatorKeyPem" class="text-[10px] text-emerald-400 font-mono">✓ 密钥已就绪</span>
          </label>
          <textarea
            v-model="securityConfig.operatorKeyPem"
            rows="2"
            placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500"
          ></textarea>
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
          @click="saveAndApply"
          class="flex-1 py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 active:from-blue-500 active:to-indigo-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-blue-500/20 transition flex items-center justify-center space-x-1.5"
        >
          <span>✓ 保存并重新连接</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { 
  swarmState, 
  loadClusterSecurityConfig, 
  saveClusterSecurityConfig, 
  generateDeviceIdentity,
  switchEnvironment 
} from '../services/telemetryService';
import type { ClusterSecurityConfig } from '../types/swarm';

const emit = defineEmits(['close']);

const activeSubTab = ref<'endpoints' | 'security'>('endpoints');
const customStreamUrl = ref(swarmState.videoStreamBaseUrl);

const securityConfig = ref<ClusterSecurityConfig>({
  clusterId: 'roboguide-field-cluster',
  nodeId: swarmState.phoneNodeId || 'phone-node-01',
  nodeGrpcHost: '192.168.2.20',
  nodeGrpcPort: 50051,
  operatorHttpsUrl: 'https://192.168.2.20:8080',
  artifactHttpsUrl: 'https://192.168.2.20:8090',
  tlsServerName: '',
  useMtls: false,
  serverCaCertPem: '',
  nodeCertPem: '',
  nodeKeyPem: '',
  operatorCertPem: '',
  operatorKeyPem: '',
  operatorRole: 'viewer'
});

onMounted(async () => {
  try {
    const loaded = await loadClusterSecurityConfig();
    if (loaded && loaded.nodeGrpcHost) {
      securityConfig.value = { ...securityConfig.value, ...loaded };
    }
  } catch (e) {
    console.warn('Could not load security config', e);
  }
});

function applyPreset(preset: 'thinkpad' | 'loopback' | 'demo') {
  if (preset === 'thinkpad') {
    securityConfig.value.clusterId = 'rg-field-thinkpad';
    securityConfig.value.nodeGrpcHost = '192.168.2.20';
    securityConfig.value.nodeGrpcPort = 50051;
    securityConfig.value.operatorHttpsUrl = 'https://192.168.2.20:8080';
    securityConfig.value.artifactHttpsUrl = 'https://192.168.2.20:8090';
    securityConfig.value.useMtls = true;
    customStreamUrl.value = 'http://192.168.2.41:8092';
    switchEnvironment('custom', 'https://192.168.2.20:8080', customStreamUrl.value, 50051);
  } else if (preset === 'loopback') {
    securityConfig.value.clusterId = 'rg-loopback-tunnel';
    securityConfig.value.nodeGrpcHost = '127.0.0.1';
    securityConfig.value.nodeGrpcPort = 50051;
    securityConfig.value.operatorHttpsUrl = 'http://127.0.0.1:8080';
    securityConfig.value.artifactHttpsUrl = 'http://127.0.0.1:8090';
    securityConfig.value.useMtls = false;
    customStreamUrl.value = 'http://127.0.0.1:8092';
    switchEnvironment('real_swarm', 'http://127.0.0.1:8080', customStreamUrl.value, 50051);
  } else if (preset === 'demo') {
    securityConfig.value.useMtls = false;
    switchEnvironment('isaac_sim', 'http://127.0.0.1:8080', 'http://127.0.0.1:8095', 50051);
  }
}

async function generateNewIdentity() {
  try {
    const generated = await generateDeviceIdentity('EC', securityConfig.value.nodeId);
    if (generated && generated.nodeId) {
      securityConfig.value.nodeId = generated.nodeId;
      securityConfig.value.hasNodeKey = true;
      alert(`已成功生成独立身份 ${generated.nodeId} 的 EC 密钥对！\n公钥已导出，私钥已安全存入本地密钥存储。`);
    }
  } catch (e: any) {
    alert('生成失败: ' + e.message);
  }
}

async function saveAndApply() {
  await saveClusterSecurityConfig(securityConfig.value);
  switchEnvironment(
    securityConfig.value.useMtls ? 'custom' : 'real_swarm',
    securityConfig.value.operatorHttpsUrl,
    customStreamUrl.value,
    securityConfig.value.nodeGrpcPort
  );
  emit('close');
}
</script>
