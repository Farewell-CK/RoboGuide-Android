import { reactive } from 'vue';
import type { 
  DeviceTelemetry, 
  CapabilitySummary,
  ActiveMission, 
  MapData, 
  EnvProfileType, 
  EnvProfileConfig, 
  TargetGoal, 
  ClarificationQuestion 
} from '../types/swarm';
import { PhoneNode, type NodeStatus, type ExecutionJournalRecord } from './phoneNodePlugin';

export interface SwarmState {
  currentProfile: EnvProfileType;
  profiles: Record<EnvProfileType, EnvProfileConfig>;
  modeType: 'DEMO' | 'REAL';
  controllerOnline: boolean;
  controllerEndpoint: string;
  grpcPort: number;
  videoStreamBaseUrl: string;
  phoneNodeId: string;
  nodeStatus: NodeStatus;
  journalRecords: ExecutionJournalRecord[];
  eStopTriggered: boolean;
  selectedDeviceId: string | null;
  followDeviceId: string | null;
  activeGoal: TargetGoal | null;
  activeMission: ActiveMission | null;
  activeClarification: ClarificationQuestion | null;
  mapData: MapData;
  devices: DeviceTelemetry[];
  humanPose: { x: number; y: number; theta: number };
  showEnvSettings: boolean;
  showFpvModal: boolean;
  fpvDeviceId: string | null;
  inventoryLastUpdatedMs: number;
}

const DEFAULT_PROFILES: Record<EnvProfileType, EnvProfileConfig> = {
  real_swarm: {
    id: 'real_swarm',
    name: '实机集群 (RoboGuide Controller)',
    badge: '实机',
    description: '通过 gRPC NodeSession 与 HTTP Control Plane 正式接入 RoboGuide',
    controllerEndpoint: 'http://127.0.0.1:8080',
    videoStreamBaseUrl: 'http://127.0.0.1:8092',
    artifactPort: 8090,
    isSimulated: false
  },
  isaac_sim: {
    id: 'isaac_sim',
    name: 'Isaac Sim 仿真 (DEMO 模拟演示)',
    badge: 'DEMO',
    description: 'NVIDIA Isaac Lab 园区数字孪生演示: 双足狗编队 + 电动叉车 (离线仿真)',
    controllerEndpoint: 'http://127.0.0.1:8080',
    videoStreamBaseUrl: 'http://127.0.0.1:8095',
    artifactPort: 8090,
    isSimulated: true
  },
  standalone_thor2: {
    id: 'standalone_thor2',
    name: '单机直连调试 (Robonix Host)',
    badge: '单机',
    description: '直连机载端 Robonix WebUI 端口 (127.0.0.1:8092)',
    controllerEndpoint: 'http://127.0.0.1:8092',
    videoStreamBaseUrl: 'http://127.0.0.1:8092',
    artifactPort: 8090,
    isSimulated: false
  },
  custom: {
    id: 'custom',
    name: '自定义中枢 (Custom Endpoint)',
    badge: '自定义',
    description: '手动指定局域网或 WSL DEAIOS Controller IP 与端口',
    controllerEndpoint: 'http://127.0.0.1:8080',
    videoStreamBaseUrl: 'http://127.0.0.1:8095',
    artifactPort: 8090,
    isSimulated: false
  }
};

const SIM_DEVICES: DeviceTelemetry[] = [
  {
    id: 'sim-dog-alpha',
    name: '[DEMO] 仿真领航犬 (Sim-Go2 Alpha)',
    roleTag: 'DEMO巡检',
    kind: 'legged_dog',
    status: 'ONLINE',
    ip: '127.0.0.1:8001',
    batteryPercent: 98,
    batteryVoltage: 25.2,
    pose: { x: 0.2, y: 1.5, theta: 0.4 },
    trajectory: [
      { x: -0.2, y: 1.0 },
      { x: 0.0, y: 1.2 },
      { x: 0.2, y: 1.5 }
    ],
    linearVelocity: 0.4,
    angularVelocity: 0.05,
    currentMode: 'patrol',
    hasActiveMap: true,
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.legged.terrain@v1', detail: 'Isaac Sim 强化学习步态', available: true },
      { kind: 'Observation', contract: 'sensor.camera.rgbd@v1', detail: 'Synthetic RGB-D + MID-360', available: true }
    ]
  },
  {
    id: 'sim-dog-beta',
    name: '[DEMO] 仿真协同犬 (Sim-Go2 Beta)',
    roleTag: 'DEMO伴随',
    kind: 'legged_dog',
    status: 'ONLINE',
    ip: '127.0.0.1:8002',
    batteryPercent: 91,
    batteryVoltage: 24.8,
    pose: { x: -1.4, y: 0.6, theta: 0.1 },
    trajectory: [
      { x: -1.6, y: 0.3 },
      { x: -1.5, y: 0.45 },
      { x: -1.4, y: 0.6 }
    ],
    linearVelocity: 0.35,
    angularVelocity: 0.02,
    currentMode: 'following',
    hasActiveMap: true,
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.legged.terrain@v1', detail: 'Isaac Sim 伴随协同编队', available: true }
    ]
  }
];

// Restore saved configuration
const savedProfileId = (localStorage.getItem('deaios_profile') as EnvProfileType) || 'real_swarm';
const savedEndpoint = localStorage.getItem('deaios_custom_endpoint');
const savedStreamUrl = localStorage.getItem('deaios_custom_stream_url');
const savedGrpcPort = parseInt(localStorage.getItem('deaios_grpc_port') || '50051', 10);

const activeCfg = DEFAULT_PROFILES[savedProfileId] || DEFAULT_PROFILES.real_swarm;
if (savedEndpoint) activeCfg.controllerEndpoint = savedEndpoint;
if (savedStreamUrl) activeCfg.videoStreamBaseUrl = savedStreamUrl;

export const swarmState = reactive<SwarmState>({
  currentProfile: activeCfg.id,
  profiles: DEFAULT_PROFILES,
  modeType: activeCfg.isSimulated ? 'DEMO' : 'REAL',
  controllerOnline: false,
  controllerEndpoint: activeCfg.controllerEndpoint,
  grpcPort: savedGrpcPort,
  videoStreamBaseUrl: activeCfg.videoStreamBaseUrl,
  phoneNodeId: 'node-phone-initializing',
  nodeStatus: {
    connected: false,
    state: 'DISCONNECTED',
    detail: 'Initializing native NodeSession',
    nodeId: '',
    sessionId: '',
    leaseId: '',
    heartbeatCount: 0,
    lastHeartbeatAckMs: 0,
    endpoint: '',
    hasCameraPermission: false,
  },
  journalRecords: [],
  eStopTriggered: false,
  selectedDeviceId: null,
  followDeviceId: null,
  activeGoal: null,
  activeMission: null,
  activeClarification: null,
  humanPose: { x: 0.5, y: -0.2, theta: 1.57 },
  showEnvSettings: false,
  showFpvModal: false,
  fpvDeviceId: null,
  inventoryLastUpdatedMs: 0,
  mapData: {
    has_map: false,
    mode: 'standby',
    width: 200,
    height: 200,
    resolution: 0.05,
    origin_x: -5.0,
    origin_y: -5.0,
    pose: { x: 0.0, y: 0.0, theta: 0.0 }
  },
  devices: []
});

function parseHostAndPort(endpointUrl: string): { host: string; port: number } {
  try {
    const url = new URL(endpointUrl);
    return {
      host: url.hostname || '127.0.0.1',
      port: parseInt(url.port || '8080', 10)
    };
  } catch {
    const clean = endpointUrl.replace(/^https?:\/\//, '');
    const parts = clean.split(':');
    return {
      host: parts[0] || '127.0.0.1',
      port: parts[1] ? parseInt(parts[1], 10) : 8080
    };
  }
}

export function switchEnvironment(profileId: EnvProfileType, customEndpoint?: string, customStreamUrl?: string, customGrpcPort?: number) {
  swarmState.currentProfile = profileId;
  const cfg = swarmState.profiles[profileId];

  if (customEndpoint) cfg.controllerEndpoint = customEndpoint;
  if (customStreamUrl) cfg.videoStreamBaseUrl = customStreamUrl;
  if (customGrpcPort) swarmState.grpcPort = customGrpcPort;

  swarmState.controllerEndpoint = cfg.controllerEndpoint;
  swarmState.videoStreamBaseUrl = cfg.videoStreamBaseUrl;
  swarmState.modeType = cfg.isSimulated ? 'DEMO' : 'REAL';

  localStorage.setItem('deaios_profile', profileId);
  if (customEndpoint) localStorage.setItem('deaios_custom_endpoint', customEndpoint);
  if (customStreamUrl) localStorage.setItem('deaios_custom_stream_url', customStreamUrl);
  if (customGrpcPort) localStorage.setItem('deaios_grpc_port', String(customGrpcPort));

  if (profileId === 'isaac_sim') {
    swarmState.devices = JSON.parse(JSON.stringify(SIM_DEVICES));
    swarmState.mapData.has_map = true;
  } else {
    // In real mode, clear synthetic devices and trigger inventory refresh
    swarmState.devices = [];
    swarmState.mapData.has_map = false;
    connectNativeNode();
    fetchControllerInventory();
  }
}

export async function connectNativeNode() {
  try {
    const { host } = parseHostAndPort(swarmState.controllerEndpoint);
    const grpcPort = swarmState.grpcPort || 50051;
    const artifactPort = swarmState.profiles[swarmState.currentProfile]?.artifactPort || 8090;
    await PhoneNode.connect({ host, port: grpcPort, artifactPort });
    await refreshNodeStatus();
  } catch (e) {
    console.error('Failed to initiate native PhoneNode connection', e);
  }
}

export async function refreshNodeStatus() {
  try {
    const status = await PhoneNode.getNodeStatus();
    swarmState.nodeStatus = status;
    if (status.nodeId) {
      swarmState.phoneNodeId = status.nodeId;
    }
  } catch (e) {
    console.error('Failed to query node status', e);
  }
}

export async function refreshJournalRecords() {
  try {
    const res = await PhoneNode.getJournalRecords({ limit: 50 });
    swarmState.journalRecords = res.records || [];
  } catch (e) {
    console.error('Failed to fetch journal records', e);
  }
}

let isPollingInventory = false;

export async function fetchControllerInventory() {
  if (isPollingInventory || swarmState.modeType === 'DEMO') return;
  isPollingInventory = true;

  try {
    const url = `${swarmState.controllerEndpoint.replace(/\/$/, '')}/v1/inventory`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2500);

    const res = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (res.ok) {
      const data = await res.json();
      swarmState.controllerOnline = true;
      swarmState.inventoryLastUpdatedMs = Date.now();

      if (Array.isArray(data.nodes)) {
        const mappedDevices: DeviceTelemetry[] = data.nodes.map((node: any) => {
          const isPhone = node.node_id === swarmState.phoneNodeId || node.node_id?.startsWith('node-phone-');
          const isDog = node.node_id?.includes('dog') || node.node_id?.includes('thor');
          
          // Formal RG Inventory Contract: reported_health ("Online" | "Degraded" | "Offline" | "SafeStopped")
          // and liveness ("Reachable" | "Unreachable") are independent facts.
          let devStatus: 'ONLINE' | 'BUSY' | 'IDLE' | 'OFFLINE' | 'ESTOP' = 'OFFLINE';
          const rh = String(node.reported_health || node.status || '').toLowerCase();
          const liveness = String(node.liveness || '').toLowerCase();

          // SafeStopped takes absolute precedence: ESTOP
          if (rh === 'safestopped' || rh === 'estop') {
            devStatus = 'ESTOP';
          } else if (liveness === 'unreachable') {
            // Unreachable means communication lost; cannot be displayed as ONLINE regardless of stale health report
            devStatus = 'OFFLINE';
          } else if (rh === 'degraded' || rh === 'busy') {
            devStatus = 'BUSY';
          } else if (rh === 'online' || rh === 'healthy') {
            devStatus = 'ONLINE';
          } else {
            devStatus = 'OFFLINE';
          }

          // Parse capabilities (categories) and contracts (concrete interfaces) independently
          let caps: CapabilitySummary[] = [];

          if (Array.isArray(node.capability_profiles)) {
            // Explicit profiles from gRPC where each entry directly binds contract and kind
            caps = node.capability_profiles.map((cp: any) => {
              const kindUpper = cp.kind ? (cp.kind.charAt(0).toUpperCase() + cp.kind.slice(1)) : 'Compute';
              return {
                kind: kindUpper as any,
                contract: cp.contract || 'unknown',
                detail: `Ready: ${cp.ready}`,
                available: Boolean(cp.ready)
              };
            });
          } else {
            // RG Inventory v0.1: capabilities (categories) and contracts are independent arrays
            if (Array.isArray(node.capabilities)) {
              for (const c of node.capabilities) {
                const kindUpper = c.kind ? (c.kind.charAt(0).toUpperCase() + c.kind.slice(1)) : 'Compute';
                caps.push({
                  kind: kindUpper as any,
                  contract: `category:${c.kind}`,
                  detail: `Category: ${c.kind} (${c.available ? 'available' : 'unavailable'})`,
                  available: Boolean(c.available)
                });
              }
            }

            if (Array.isArray(node.contracts)) {
              for (const contractStr of node.contracts) {
                let inferredKind = 'Compute';
                if (contractStr.startsWith('observation.') || contractStr.startsWith('sensor.')) {
                  inferredKind = 'Observation';
                } else if (contractStr.startsWith('mobility.')) {
                  inferredKind = 'Mobility';
                } else if (contractStr.startsWith('transport.')) {
                  inferredKind = 'Transport';
                }
                caps.push({
                  kind: inferredKind as any,
                  contract: contractStr,
                  detail: 'Registered contract',
                  available: true
                });
              }
            }
          }

          // Pose: only present if genuine coordinates are provided by localization; otherwise leave undefined (Unknown)
          const genuinePose = (node.pose && typeof node.pose.x === 'number' && !isNaN(node.pose.x))
            ? { x: node.pose.x, y: node.pose.y, theta: node.pose.theta || 0 }
            : undefined;

          const modeDisplay = node.reported_health
            ? `${node.reported_health} (${node.liveness || 'Unknown'})`
            : (node.status || 'unknown');

          return {
            id: node.node_id || 'unknown-node',
            name: isPhone ? `手机终端 (${node.node_id})` : (node.name || node.node_id),
            roleTag: isPhone ? '交互与采图' : (isDog ? '自主机器狗' : '计算节点'),
            kind: isPhone ? 'human_terminal' : (isDog ? 'legged_dog' : 'fixed_camera'),
            status: devStatus,
            ip: node.endpoint || 'N/A',
            batteryPercent: undefined,
            pose: genuinePose,
            currentMode: modeDisplay,
            hasActiveMap: false,
            capabilities: caps
          };
        });
        swarmState.devices = mappedDevices;
      }
    } else {
      swarmState.controllerOnline = false;
    }
  } catch {
    swarmState.controllerOnline = false;
  } finally {
    isPollingInventory = false;
  }
}

export function setTargetGoal(x: number, y: number, label = '目标点') {
  if (x === undefined || y === undefined || isNaN(x) || isNaN(y)) return;
  swarmState.activeGoal = { x, y, label };
}

export function clearTargetGoal() {
  swarmState.activeGoal = null;
}

export function openFpvStream(deviceId: string) {
  swarmState.fpvDeviceId = deviceId;
  swarmState.showFpvModal = true;
}

export function closeFpvModal() {
  swarmState.showFpvModal = false;
  swarmState.fpvDeviceId = null;
}

export function answerClarification(questionId: string, answer: string) {
  if (swarmState.activeClarification && swarmState.activeClarification.id === questionId) {
    swarmState.activeClarification.selectedOption = answer;
    setTimeout(() => {
      swarmState.activeClarification = null;
    }, 1000);
  }
}

export function triggerEmergencyStop() {
  swarmState.eStopTriggered = true;
  // Safety rule: In real mode, local phone cannot unilaterally claim physical torque cutoff on real dogs!
  if (swarmState.modeType === 'DEMO') {
    for (const d of swarmState.devices) {
      if (d.kind === 'legged_dog') {
        d.status = 'ESTOP';
        d.linearVelocity = 0.0;
        d.angularVelocity = 0.0;
        d.currentMode = 'estop_brake';
      }
    }
  }
}

export function resetEmergencyStop() {
  swarmState.eStopTriggered = false;
  if (swarmState.modeType === 'DEMO') {
    for (const d of swarmState.devices) {
      if (d.status === 'ESTOP') {
        d.status = 'ONLINE';
        d.currentMode = 'stand';
      }
    }
  }
}

let loopTimer: number | null = null;
let simStep = 0;

export function startTelemetryPolling() {
  if (loopTimer) return;

  // Initialize native node listeners
  PhoneNode.addListener('nodeStatusChanged', (status) => {
    Object.assign(swarmState.nodeStatus, status);
    if (status.nodeId) swarmState.phoneNodeId = status.nodeId;
  });

  PhoneNode.addListener('executionEvent', () => {
    refreshJournalRecords();
  });

  refreshNodeStatus();
  refreshJournalRecords();

  if (swarmState.modeType === 'REAL') {
    connectNativeNode();
  }

  const loop = async () => {
    if (swarmState.modeType === 'REAL') {
      await refreshNodeStatus();
      await fetchControllerInventory();
    } else {
      // Isaac Sim DEMO simulation stepping
      if (!swarmState.eStopTriggered) {
        simStep += 0.05;
        const dogAlpha = swarmState.devices.find(d => d.id === 'sim-dog-alpha');
        if (dogAlpha && dogAlpha.pose) {
          dogAlpha.pose.x = 0.8 * Math.cos(simStep * 0.6) + 0.5;
          dogAlpha.pose.y = 1.2 * Math.sin(simStep * 0.6) + 1.2;
          dogAlpha.pose.theta = Math.atan2(
            1.2 * Math.cos(simStep * 0.6) * 0.6,
            -0.8 * Math.sin(simStep * 0.6) * 0.6
          );
        }
      }
    }
  };

  loopTimer = window.setInterval(loop, 2000);
}
