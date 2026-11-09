<template>
  <div class="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
    <div class="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full max-h-[92vh] flex flex-col shadow-2xl overflow-hidden">
      <!-- Modal Header -->
      <div class="p-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/60">
        <div class="flex items-center space-x-2">
          <span class="text-base">🛡️</span>
          <h2 class="text-sm font-bold text-white tracking-wide">RoboGuide 集群与原生 mTLS 配置</h2>
        </div>
        <button
          @click="$emit('close')"
          class="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
        >
          ✕
        </button>
      </div>

      <!-- Navigation Sub-tabs -->
      <div class="flex border-b border-slate-800 bg-slate-950/30 text-xs font-semibold">
        <button
          @click="activeSubTab = 'endpoints'"
          :class="[
            'flex-1 py-2.5 text-center transition border-b-2',
            activeSubTab === 'endpoints'
              ? 'text-blue-400 border-blue-500 bg-blue-500/10'
              : 'text-slate-400 border-transparent hover:text-slate-300'
          ]"
        >
          🌐 通信端点设置
        </button>
        <button
          @click="activeSubTab = 'security'"
          :class="[
            'flex-1 py-2.5 text-center transition border-b-2',
            activeSubTab === 'security'
              ? 'text-blue-400 border-blue-500 bg-blue-500/10'
              : 'text-slate-400 border-transparent hover:text-slate-300'
          ]"
        >
          🔐 PKI 证书与原生凭据
        </button>
      </div>

      <!-- Modal Body (Scrollable) -->
      <div class="flex-1 overflow-y-auto p-4 space-y-4">
        <!-- Tab 1: Endpoints -->
        <div v-show="activeSubTab === 'endpoints'" class="space-y-3.5">
          <!-- Preset Profiles -->
          <div>
            <label class="text-[11px] font-bold text-slate-400 uppercase tracking-wider">快捷预置模板</label>
            <div class="grid grid-cols-3 gap-2 mt-1.5">
              <button
                @click="applyPreset('thinkpad')"
                class="py-2 px-2.5 bg-slate-800/80 hover:bg-slate-800 border border-slate-700 rounded-xl text-left transition"
              >
                <div class="text-xs font-bold text-white">ThinkPad 现场</div>
                <div class="text-[10px] text-slate-400 font-mono">192.168.2.20 mTLS</div>
              </button>
              <button
                @click="applyPreset('loopback')"
                class="py-2 px-2.5 bg-slate-800/80 hover:bg-slate-800 border border-slate-700 rounded-xl text-left transition"
              >
                <div class="text-xs font-bold text-white">本地直连</div>
                <div class="text-[10px] text-slate-400 font-mono">127.0.0.1 明文</div>
              </button>
              <button
                @click="applyPreset('demo')"
                class="py-2 px-2.5 bg-slate-800/80 hover:bg-slate-800 border border-slate-700 rounded-xl text-left transition"
              >
                <div class="text-xs font-bold text-amber-400">DEMO 演示</div>
                <div class="text-[10px] text-slate-400 font-mono">本地仿真数据</div>
              </button>
            </div>
          </div>

          <div class="space-y-3 pt-1">
            <div>
              <label class="text-xs font-semibold text-slate-300">集群标识 (Cluster ID):</label>
              <input
                v-model="securityConfig.clusterId"
                type="text"
                placeholder="roboguide-field-cluster"
                class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
              />
            </div>

            <div>
              <div class="flex items-center justify-between">
                <label class="text-xs font-semibold text-slate-300">本机 Node 身份 (Node ID):</label>
                <button
                  type="button"
                  @click="generateNewNodeIdentity"
                  class="text-[10px] text-blue-400 hover:text-blue-300 font-medium flex items-center space-x-1"
                >
                  <span>⚡ 原生生成独立身份与密钥</span>
                </button>
              </div>
              <input
                v-model="securityConfig.nodeId"
                type="text"
                placeholder="phone-node-01"
                class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
              />
            </div>

            <div class="grid grid-cols-3 gap-2">
              <div class="col-span-2">
                <label class="text-xs font-semibold text-slate-300">Node gRPC 主机 (Host):</label>
                <input
                  v-model="securityConfig.nodeGrpcHost"
                  type="text"
                  placeholder="192.168.2.20"
                  class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label class="text-xs font-semibold text-slate-300">端口:</label>
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
              <label class="text-xs font-semibold text-slate-300">Artifact HTTPS 产物中心入口:</label>
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

            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="text-xs font-semibold text-slate-300">Node/Artifact TLS 期望服务名:</label>
                <input
                  v-model="securityConfig.tlsServerName"
                  type="text"
                  placeholder="rg-controller"
                  class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label class="text-xs font-semibold text-slate-300">Operator TLS 期望服务名:</label>
                <input
                  v-model="securityConfig.operatorTlsServerName"
                  type="text"
                  placeholder="rg-field-controller"
                  class="mt-1 w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 2: Security & PKI Credentials -->
        <div v-show="activeSubTab === 'security'" class="space-y-3.5">
          <!-- mTLS Toggle Switch -->
          <div class="flex items-center justify-between bg-slate-800/80 border border-slate-700 rounded-xl p-3">
            <div>
              <div class="text-xs font-bold text-white">启用原生 mTLS 双向认证</div>
              <p class="text-[10px] text-slate-400">gRPC NodeSession 与 HTTPS 双通道严格校验服务端证书与客户端身份</p>
            </div>
            <label class="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" v-model="securityConfig.useMtls" class="sr-only peer" />
              <div class="w-11 h-6 bg-slate-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>

          <!-- Server CAs -->
          <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2">
            <div class="text-xs font-bold text-amber-300">服务端 CA 根证书 (Trust Root)</div>
            <div>
              <label class="text-[11px] font-semibold text-slate-300 flex items-center justify-between">
                <span>Node & Artifact CA (ca.crt):</span>
                <span v-if="securityConfig.serverCaCertPem" class="text-[10px] text-emerald-400 font-mono">✓ 已配置</span>
              </label>
              <textarea
                v-model="securityConfig.serverCaCertPem"
                rows="2"
                placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500 mt-1"
              ></textarea>
            </div>
            <div>
              <label class="text-[11px] font-semibold text-slate-300 flex items-center justify-between">
                <span>Operator CA (operator-ca.crt):</span>
                <span v-if="securityConfig.operatorCaCertPem" class="text-[10px] text-emerald-400 font-mono">✓ 已配置</span>
              </label>
              <textarea
                v-model="securityConfig.operatorCaCertPem"
                rows="2"
                placeholder="留空则复用 Node CA，若现场独立则填入 Operator CA"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500 mt-1"
              ></textarea>
            </div>
          </div>

          <!-- Section 1: Node Identity -->
          <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2.5">
            <div class="flex items-center justify-between">
              <div class="text-xs font-bold text-blue-300">1. 本机 Node 凭据 (Node.crt / 硬件私钥)</div>
              <span v-if="securityConfig.hasNodeKey" class="text-[10px] text-emerald-400 font-mono flex items-center space-x-1">
                <span>●</span> <span>私钥已在原生安全保护中</span>
              </span>
              <span v-else class="text-[10px] text-rose-400 font-mono">未生成本机私钥</span>
            </div>

            <div class="flex items-center space-x-2">
              <button
                type="button"
                @click="generateNewNodeIdentity"
                class="flex-1 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-medium transition"
              >
                ⚡ 原生生成 Node 密钥对
              </button>
            </div>

            <!-- Exported Node Public Key Dialog/Box -->
            <div v-if="exportedNodePublicKey" class="p-2.5 bg-slate-950 rounded-lg border border-blue-500/30 space-y-1">
              <div class="flex items-center justify-between">
                <span class="text-[10px] text-blue-300 font-semibold">导出的 Node 公钥 (提供给管理员签发证书):</span>
                <button
                  type="button"
                  @click="copyText(exportedNodePublicKey)"
                  class="text-[10px] text-blue-400 hover:text-white"
                >
                  📋 复制公钥
                </button>
              </div>
              <textarea
                readonly
                :value="exportedNodePublicKey"
                rows="3"
                class="w-full bg-slate-900/80 border border-slate-800 rounded p-1.5 text-[9px] font-mono text-slate-300 select-all"
              ></textarea>
            </div>

            <div>
              <label class="text-[11px] font-semibold text-slate-300">导入签发的 Node 证书 (PEM):</label>
              <textarea
                v-model="inputNodeCertPem"
                rows="2"
                placeholder="粘贴由 Node CA 签发的客户端证书..."
                class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500 mt-1"
              ></textarea>
              <button
                type="button"
                @click="doImportNodeCert"
                :disabled="!inputNodeCertPem"
                class="mt-1.5 w-full py-1.5 bg-slate-800 hover:bg-slate-700 border border-slate-600 disabled:opacity-50 text-slate-200 rounded-lg text-xs font-medium transition"
              >
                📥 导入并原生校验 Node 证书
              </button>
            </div>

            <div v-if="securityConfig.nodeCertFingerprint" class="p-2 bg-slate-950 rounded border border-cyan-500/30">
              <div class="text-[10px] text-slate-400 font-semibold">当前生效 Node 证书指纹 (SHA-256):</div>
              <div class="text-[10px] text-cyan-300 font-mono break-all select-all mt-0.5">
                {{ securityConfig.nodeCertFingerprint }}
              </div>
            </div>
          </div>

          <!-- Section 2: Operator Identity -->
          <div class="bg-slate-800/80 border border-slate-700 rounded-xl p-3 space-y-2.5">
            <div class="flex items-center justify-between">
              <div class="text-xs font-bold text-indigo-300">2. 操作员凭据 (admin.crt / viewer.crt)</div>
              <select
                v-model="securityConfig.operatorRole"
                class="bg-slate-900 border border-slate-700 text-[10px] text-white rounded px-2 py-0.5 font-mono"
              >
                <option value="viewer">角色: 观察员 (viewer)</option>
                <option value="operator">角色: 操作员 (operator)</option>
                <option value="recovery-admin">角色: 管理员 (recovery-admin)</option>
              </select>
            </div>

            <div class="flex items-center justify-between text-[10px]">
              <span v-if="securityConfig.hasOperatorKey" class="text-emerald-400 font-mono flex items-center space-x-1">
                <span>●</span> <span>操作员私钥已在原生保护中</span>
              </span>
              <span v-else class="text-rose-400 font-mono">未生成操作员私钥</span>
            </div>

            <div class="flex items-center space-x-2">
              <button
                type="button"
                @click="generateNewOperatorIdentity"
                class="flex-1 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-medium transition"
              >
                ⚡ 原生生成 Operator 密钥对
              </button>
            </div>

            <div v-if="exportedOpPublicKey" class="p-2.5 bg-slate-950 rounded-lg border border-indigo-500/30 space-y-1">
              <div class="flex items-center justify-between">
                <span class="text-[10px] text-indigo-300 font-semibold">导出的 Operator 公钥:</span>
                <button
                  type="button"
                  @click="copyText(exportedOpPublicKey)"
                  class="text-[10px] text-indigo-400 hover:text-white"
                >
                  📋 复制公钥
                </button>
              </div>
              <textarea
                readonly
                :value="exportedOpPublicKey"
                rows="3"
                class="w-full bg-slate-900/80 border border-slate-800 rounded p-1.5 text-[9px] font-mono text-slate-300 select-all"
              ></textarea>
            </div>

            <div>
              <label class="text-[11px] font-semibold text-slate-300">导入签发的 Operator 证书 (PEM):</label>
              <textarea
                v-model="inputOpCertPem"
                rows="2"
                placeholder="粘贴由 Operator CA 签发的操作员证书..."
                class="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-[10px] font-mono text-white focus:outline-none focus:border-blue-500 mt-1"
              ></textarea>
              <button
                type="button"
                @click="doImportOperatorCert"
                :disabled="!inputOpCertPem"
                class="mt-1.5 w-full py-1.5 bg-slate-800 hover:bg-slate-700 border border-slate-600 disabled:opacity-50 text-slate-200 rounded-lg text-xs font-medium transition"
              >
                📥 导入并原生校验 Operator 证书
              </button>
            </div>

            <div v-if="securityConfig.operatorCertFingerprint" class="p-2 bg-slate-950 rounded border border-indigo-500/30">
              <div class="text-[10px] text-slate-400 font-semibold">当前生效 Operator 证书指纹:</div>
              <div class="text-[10px] text-indigo-300 font-mono break-all select-all mt-0.5">
                {{ securityConfig.operatorCertFingerprint }}
              </div>
            </div>
          </div>

          <!-- Explicit Reset Credentials Action -->
          <div class="pt-1">
            <button
              type="button"
              @click="confirmClearCredentials"
              class="w-full py-2 bg-rose-950/40 hover:bg-rose-900/60 border border-rose-800/50 text-rose-300 text-xs font-medium rounded-xl transition"
            >
              🗑️ 显式清除原生私钥与凭据
            </button>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="p-4 border-t border-slate-800 bg-slate-950/60 flex items-center space-x-2">
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
  importNodeCertificate,
  generateOperatorIdentity,
  importOperatorCertificate,
  clearCredentials,
  switchEnvironment 
} from '../services/telemetryService';
import type { ClusterSecurityConfig } from '../types/swarm';

const emit = defineEmits(['close']);

const activeSubTab = ref<'endpoints' | 'security'>('endpoints');
const customStreamUrl = ref(swarmState.videoStreamBaseUrl);

const inputNodeCertPem = ref('');
const inputOpCertPem = ref('');
const exportedNodePublicKey = ref('');
const exportedOpPublicKey = ref('');

const securityConfig = ref<ClusterSecurityConfig>({
  clusterId: 'roboguide-field-cluster',
  nodeId: swarmState.phoneNodeId || 'phone-node-01',
  nodeGrpcHost: '192.168.2.20',
  nodeGrpcPort: 50051,
  operatorHttpsUrl: 'https://192.168.2.20:8080',
  artifactHttpsUrl: 'https://192.168.2.20:8090',
  tlsServerName: 'rg-controller',
  operatorTlsServerName: 'rg-field-controller',
  useMtls: false,
  serverCaCertPem: '',
  operatorCaCertPem: '',
  nodeCertPem: '',
  operatorCertPem: '',
  operatorRole: 'viewer'
});

onMounted(async () => {
  try {
    const loaded = await loadClusterSecurityConfig();
    if (loaded && loaded.nodeGrpcHost) {
      securityConfig.value = { ...securityConfig.value, ...loaded };
      if (loaded.nodeCertPem) {
        inputNodeCertPem.value = loaded.nodeCertPem;
      }
      if (loaded.operatorCertPem) {
        inputOpCertPem.value = loaded.operatorCertPem;
      }
    }
  } catch (e) {
    console.warn('Could not load security config', e);
  }
});

function copyText(txt: string) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(txt);
    alert('已复制到剪贴板！');
  } else {
    alert(txt);
  }
}

function applyPreset(preset: 'thinkpad' | 'loopback' | 'demo') {
  if (preset === 'thinkpad') {
    securityConfig.value.clusterId = 'rg-field-thinkpad';
    securityConfig.value.nodeGrpcHost = '192.168.2.20';
    securityConfig.value.nodeGrpcPort = 50051;
    securityConfig.value.operatorHttpsUrl = 'https://192.168.2.20:8080';
    securityConfig.value.artifactHttpsUrl = 'https://192.168.2.20:8090';
    securityConfig.value.tlsServerName = 'rg-controller';
    securityConfig.value.operatorTlsServerName = 'rg-field-controller';
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

async function generateNewNodeIdentity() {
  try {
    const generated = await generateDeviceIdentity('EC', securityConfig.value.nodeId);
    if (generated && generated.nodeId) {
      securityConfig.value.nodeId = generated.nodeId;
      securityConfig.value.hasNodeKey = true;
      exportedNodePublicKey.value = generated.publicKeyPem;
      alert(`已成功在原生沙箱生成 ${generated.nodeId} 的 EC 密钥对！\n私钥安全存入本地，公钥已导出供管理员签发。`);
    }
  } catch (e: any) {
    alert('生成失败: ' + (e?.message || e));
  }
}

async function doImportNodeCert() {
  if (!inputNodeCertPem.value.trim()) return;
  try {
    const res = await importNodeCertificate(inputNodeCertPem.value.trim());
    if (res && res.success) {
      securityConfig.value.nodeCertPem = inputNodeCertPem.value.trim();
      securityConfig.value.nodeCertFingerprint = res.nodeCertFingerprint;
      alert(`Node 证书导入并验证成功！\n指纹: ${res.nodeCertFingerprint}`);
    }
  } catch (e: any) {
    alert('导入失败 (密钥不匹配或格式错误): ' + (e?.message || e));
  }
}

async function generateNewOperatorIdentity() {
  try {
    const generated = await generateOperatorIdentity('EC');
    if (generated) {
      securityConfig.value.hasOperatorKey = true;
      exportedOpPublicKey.value = generated.publicKeyPem;
      alert('已成功生成 Operator EC 密钥对！\n私钥安全存入本地，公钥已导出供管理员签发。');
    }
  } catch (e: any) {
    alert('生成失败: ' + (e?.message || e));
  }
}

async function doImportOperatorCert() {
  if (!inputOpCertPem.value.trim()) return;
  try {
    const res = await importOperatorCertificate(inputOpCertPem.value.trim());
    if (res && res.success) {
      securityConfig.value.operatorCertPem = inputOpCertPem.value.trim();
      securityConfig.value.operatorCertFingerprint = res.operatorCertFingerprint;
      alert(`Operator 证书导入并验证成功！\n指纹: ${res.operatorCertFingerprint}`);
    }
  } catch (e: any) {
    alert('导入失败 (密钥不匹配或格式错误): ' + (e?.message || e));
  }
}

async function confirmClearCredentials() {
  if (confirm('确定要清除本地保存的所有私钥和客户端证书吗？此操作不可逆。')) {
    await clearCredentials('all');
    securityConfig.value.hasNodeKey = false;
    securityConfig.value.hasOperatorKey = false;
    securityConfig.value.nodeCertPem = '';
    securityConfig.value.operatorCertPem = '';
    securityConfig.value.nodeCertFingerprint = '';
    securityConfig.value.operatorCertFingerprint = '';
    inputNodeCertPem.value = '';
    inputOpCertPem.value = '';
    exportedNodePublicKey.value = '';
    exportedOpPublicKey.value = '';
    alert('本地凭据已安全清除。');
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
