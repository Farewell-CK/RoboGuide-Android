import { reactive } from 'vue';
import type { 
  DeviceTelemetry, 
  ActiveMission, 
  MapData, 
  EnvProfileType, 
  EnvProfileConfig, 
  TargetGoal, 
  ClarificationQuestion 
} from '../types/swarm';

export interface SwarmState {
  currentProfile: EnvProfileType;
  profiles: Record<EnvProfileType, EnvProfileConfig>;
  controllerOnline: boolean;
  controllerEndpoint: string;
  videoStreamBaseUrl: string;
  phoneNodeId: string;
  eStopTriggered: boolean;
  selectedDeviceId: string | null;
  followDeviceId: string | null; // Follow-Cam lock
  activeGoal: TargetGoal | null;
  activeMission: ActiveMission | null;
  activeClarification: ClarificationQuestion | null;
  mapData: MapData;
  devices: DeviceTelemetry[];
  humanPose: { x: number; y: number; theta: number };
  showEnvSettings: boolean;
  showFpvModal: boolean;
  fpvDeviceId: string | null;
}

const DEFAULT_PROFILES: Record<EnvProfileType, EnvProfileConfig> = {
  real_swarm: {
    id: 'real_swarm',
    name: '实机外场集群 (Real Fleet)',
    badge: '实机',
    description: 'Thor 1 (领航犬) + Thor 2 (协同犬) + AGV-01 实体硬件',
    controllerEndpoint: 'http://100.84.233.121:8080',
    videoStreamBaseUrl: 'http://100.111.156.77:8092',
    isSimulated: false
  },
  isaac_sim: {
    id: 'isaac_sim',
    name: 'Isaac Sim 园区仿真 (Digital Twin)',
    badge: '仿真',
    description: 'NVIDIA Isaac Lab 园区: 双足狗编队 + 电动叉车 + 鹰眼高位视锥',
    controllerEndpoint: 'http://100.84.233.121:8080',
    videoStreamBaseUrl: 'http://100.84.233.121:8095',
    isSimulated: true
  },
  standalone_thor2: {
    id: 'standalone_thor2',
    name: '单机直连调试 (Thor 2 Robonix)',
    badge: '单机',
    description: '直连 2号机载端 Robonix WebUI 端口 (100.111.156.77:8092)',
    controllerEndpoint: 'http://100.111.156.77:8092',
    videoStreamBaseUrl: 'http://100.111.156.77:8092',
    isSimulated: false
  },
  custom: {
    id: 'custom',
    name: '自定义中枢 (Custom Endpoint)',
    badge: '自定义',
    description: '手动指定局域网或公网 DEAIOS Controller IP 与端口',
    controllerEndpoint: 'http://192.168.1.28:8080',
    videoStreamBaseUrl: 'http://192.168.1.28:8095',
    isSimulated: false
  }
};

const REAL_DEVICES: DeviceTelemetry[] = [
  {
    id: 'dog-thor-1',
    name: '1号狗 (Thor 1 领航犬)',
    roleTag: '领航探路',
    kind: 'legged_dog',
    status: 'OFFLINE',
    ip: '192.168.2.47',
    tailscaleIp: '100.117.131.75',
    batteryPercent: 88,
    batteryVoltage: 24.2,
    pose: { x: -0.8, y: -0.5, theta: 0.2 },
    trajectory: [
      { x: -1.0, y: -0.7 },
      { x: -0.9, y: -0.6 },
      { x: -0.8, y: -0.5 }
    ],
    linearVelocity: 0.0,
    angularVelocity: 0.0,
    currentMode: 'standby',
    hasActiveMap: false,
    cameraStreamUrl: 'http://100.117.131.75:8092/camera/rgb',
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.legged.terrain@v1', detail: '四足越障/楼梯通行', available: false },
      { kind: 'Compute', contract: 'spatial.map.build@v0', detail: 'RTAB-Map 3D建图与闭环', available: false },
      { kind: 'Observation', contract: 'sensor.lidar.mid360@v1', detail: 'Livox MID-360 激光雷达', available: false },
      { kind: 'Observation', contract: 'sensor.camera.rgbd@v1', detail: 'Orbbec Gemini 336L 深度相机', available: false }
    ],
    hardwareDetails: {
      platform: 'NVIDIA Jetson Thor (JetPack 6)',
      temperature: 42,
      cpuLoad: 12
    }
  },
  {
    id: 'dog-thor-2',
    name: '2号狗 (Thor 2 协同犬)',
    roleTag: '建图协同',
    kind: 'legged_dog',
    status: 'ONLINE',
    ip: '192.168.2.42',
    tailscaleIp: '100.111.156.77',
    batteryPercent: 94,
    batteryVoltage: 25.1,
    pose: { x: 0.0, y: 0.0, theta: 0.005 },
    trajectory: [
      { x: -0.2, y: -0.1 },
      { x: -0.1, y: -0.05 },
      { x: 0.0, y: 0.0 }
    ],
    linearVelocity: 0.0,
    angularVelocity: 0.0,
    currentMode: 'mapping',
    hasActiveMap: true,
    cameraStreamUrl: 'http://100.111.156.77:8092/camera/rgb',
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.legged.terrain@v1', detail: '四足全地形敏捷步态', available: true },
      { kind: 'Compute', contract: 'spatial.map.import@v0', detail: '不可变地图导入与定位', available: true },
      { kind: 'Observation', contract: 'sensor.lidar.mid360@v1', detail: 'Livox MID-360 激光点云', available: true },
      { kind: 'Observation', contract: 'sensor.camera.rgbd@v1', detail: 'Orbbec 3D RGB-D', available: true }
    ],
    hardwareDetails: {
      platform: 'NVIDIA Jetson Thor (JetPack 6)',
      temperature: 45,
      cpuLoad: 28
    }
  },
  {
    id: 'agv-chassis-01',
    name: '轮式重载车 (AGV-01)',
    roleTag: '重载物流',
    kind: 'wheeled_agv',
    status: 'IDLE',
    ip: '192.168.2.105',
    tailscaleIp: '100.82.0.23',
    batteryPercent: 76,
    batteryVoltage: 48.0,
    pose: { x: 2.2, y: -1.8, theta: -1.2 },
    trajectory: [
      { x: 2.0, y: -1.7 },
      { x: 2.1, y: -1.75 },
      { x: 2.2, y: -1.8 }
    ],
    linearVelocity: 0.0,
    angularVelocity: 0.0,
    currentMode: 'docked',
    hasActiveMap: true,
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.wheeled.cruise@v1', detail: '平地高速巡航 (2.5m/s)', available: true },
      { kind: 'Transport', contract: 'transport.payload.heavy@v1', detail: '大载荷平移 (额定 50kg)', available: true },
      { kind: 'Observation', contract: 'sensor.lidar.2d@v1', detail: '360° 单线防撞激光', available: true }
    ],
    hardwareDetails: {
      platform: 'Industrial x86 PLC Controller',
      temperature: 38,
      cpuLoad: 8
    }
  },
  {
    id: 'cam-hallway-03',
    name: '走廊全景高位相机 (CAM-302)',
    roleTag: '全局视锥',
    kind: 'fixed_camera',
    status: 'ONLINE',
    ip: '192.168.2.201',
    batteryPercent: 100,
    pose: { x: 1.5, y: 3.2, theta: -1.57 },
    currentMode: 'streaming',
    hasActiveMap: false,
    cameraStreamUrl: 'http://100.84.233.121:8095/cam302',
    capabilities: [
      { kind: 'Observation', contract: 'observation.camera.panoramic@v1', detail: '4K 全局广角视锥监控', available: true },
      { kind: 'Compute', contract: 'compute.cv.human_detect@v1', detail: '边缘人员与障碍粗筛', available: true }
    ],
    hardwareDetails: {
      platform: 'PoE IP Panoramic Fisheye',
      temperature: 36
    }
  },
  {
    id: 'node-phone-user',
    name: '手机交互中枢 (本机节点)',
    roleTag: '人类意图',
    kind: 'human_terminal',
    status: 'ONLINE',
    ip: '127.0.0.1',
    batteryPercent: 85,
    pose: { x: 0.5, y: -0.2, theta: 1.57 },
    currentMode: 'listening',
    hasActiveMap: true,
    capabilities: [
      { kind: 'HumanInteraction', contract: 'human.intent.voice@v1', detail: '麦克风原生降噪意图输入', available: true },
      { kind: 'Observation', contract: 'spatial.human_pose@v1', detail: '人类持有者 6-DoF 移动空间锚点', available: true },
      { kind: 'HumanInteraction', contract: 'human.guardian.estop@v1', detail: '物理音量键双重急停守卫', available: true }
    ],
    hardwareDetails: {
      platform: 'Android 14 / Capacitor Bridge',
      cpuLoad: 5
    }
  }
];

const SIM_DEVICES: DeviceTelemetry[] = [
  {
    id: 'sim-dog-alpha',
    name: '仿真领航犬 (Sim-Go2 Alpha)',
    roleTag: '仿真领航',
    kind: 'legged_dog',
    status: 'ONLINE',
    ip: '127.0.0.1:8001',
    tailscaleIp: '100.84.233.121',
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
    cameraStreamUrl: 'http://100.84.233.121:8095/sim/dog1',
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.legged.terrain@v1', detail: 'Isaac Sim 强化学习步态', available: true },
      { kind: 'Observation', contract: 'sensor.camera.rgbd@v1', detail: 'Synthetic RGB-D + MID-360', available: true },
      { kind: 'Compute', contract: 'spatial.map.build@v0', detail: '虚拟环境占据栅格推流', available: true }
    ],
    hardwareDetails: {
      platform: 'Isaac Lab / RTX GPU Substrate',
      temperature: 52,
      cpuLoad: 45
    }
  },
  {
    id: 'sim-dog-beta',
    name: '仿真协同犬 (Sim-Go2 Beta)',
    roleTag: '仿真协同',
    kind: 'legged_dog',
    status: 'ONLINE',
    ip: '127.0.0.1:8002',
    tailscaleIp: '100.84.233.121',
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
    cameraStreamUrl: 'http://100.84.233.121:8095/sim/dog2',
    capabilities: [
      { kind: 'Mobility', contract: 'mobility.legged.terrain@v1', detail: 'Isaac Sim 伴随协同编队', available: true },
      { kind: 'Compute', contract: 'spatial.map.import@v0', detail: '共享高精地图对齐', available: true }
    ],
    hardwareDetails: {
      platform: 'Isaac Lab / RTX GPU Substrate',
      temperature: 50,
      cpuLoad: 38
    }
  },
  {
    id: 'sim-forklift-01',
    name: '仿真电动叉车 (Sim-Forklift)',
    roleTag: '智能搬运',
    kind: 'wheeled_agv',
    status: 'IDLE',
    ip: '127.0.0.1:8003',
    tailscaleIp: '100.84.233.121',
    batteryPercent: 100,
    batteryVoltage: 48.0,
    pose: { x: 2.8, y: -1.2, theta: 3.14 },
    trajectory: [
      { x: 3.0, y: -1.2 },
      { x: 2.9, y: -1.2 },
      { x: 2.8, y: -1.2 }
    ],
    linearVelocity: 0.0,
    angularVelocity: 0.0,
    currentMode: 'parked',
    hasActiveMap: true,
    capabilities: [
      { kind: 'Transport', contract: 'transport.payload.heavy@v1', detail: '仓库货架托盘搬运', available: true },
      { kind: 'Mobility', contract: 'mobility.wheeled.cruise@v1', detail: '阿克曼/差速平地导航', available: true }
    ],
    hardwareDetails: {
      platform: 'Isaac Sim Rigid Body PhysX',
      temperature: 46
    }
  },
  {
    id: 'sim-cam-highview',
    name: '仿真园区鹰眼视锥 (Sim-EagleEye)',
    roleTag: '园区全局',
    kind: 'fixed_camera',
    status: 'ONLINE',
    ip: '127.0.0.1:8004',
    batteryPercent: 100,
    pose: { x: 0.0, y: 4.5, theta: -1.57 },
    currentMode: 'streaming',
    hasActiveMap: false,
    cameraStreamUrl: 'http://100.84.233.121:8095/sim/highcam',
    capabilities: [
      { kind: 'Observation', contract: 'observation.camera.panoramic@v1', detail: '园区俯视全局孪生', available: true }
    ],
    hardwareDetails: {
      platform: 'Isaac Synthetic Camera Prim'
    }
  },
  {
    id: 'node-phone-user',
    name: '手机交互中枢 (本机节点)',
    roleTag: '人类意图',
    kind: 'human_terminal',
    status: 'ONLINE',
    ip: '127.0.0.1',
    batteryPercent: 85,
    pose: { x: 0.5, y: -0.2, theta: 1.57 },
    currentMode: 'listening',
    hasActiveMap: true,
    capabilities: [
      { kind: 'HumanInteraction', contract: 'human.intent.voice@v1', detail: '麦克风原生降噪意图输入', available: true },
      { kind: 'Observation', contract: 'spatial.human_pose@v1', detail: '人类持有者 6-DoF 移动空间锚点', available: true }
    ],
    hardwareDetails: {
      platform: 'Android 14 / Capacitor Bridge',
      cpuLoad: 5
    }
  }
];

// Restore saved profile
const savedProfileId = (localStorage.getItem('deaios_profile') as EnvProfileType) || 'real_swarm';
const activeCfg = DEFAULT_PROFILES[savedProfileId] || DEFAULT_PROFILES.real_swarm;

export const swarmState = reactive<SwarmState>({
  currentProfile: activeCfg.id,
  profiles: DEFAULT_PROFILES,
  controllerOnline: true,
  controllerEndpoint: activeCfg.controllerEndpoint,
  videoStreamBaseUrl: activeCfg.videoStreamBaseUrl,
  phoneNodeId: 'node-phone-user',
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
  mapData: {
    has_map: true,
    mode: 'mapping',
    width: 282,
    height: 179,
    resolution: 0.05,
    origin_x: -5.33,
    origin_y: -4.51,
    pose: { x: 0.0, y: 0.0, theta: 0.005 }
  },
  devices: activeCfg.id === 'isaac_sim' ? SIM_DEVICES : REAL_DEVICES
});

export function switchEnvironment(profileId: EnvProfileType, customEndpoint?: string) {
  swarmState.currentProfile = profileId;
  const cfg = swarmState.profiles[profileId];
  if (customEndpoint) {
    cfg.controllerEndpoint = customEndpoint;
  }
  swarmState.controllerEndpoint = cfg.controllerEndpoint;
  swarmState.videoStreamBaseUrl = cfg.videoStreamBaseUrl;

  localStorage.setItem('deaios_profile', profileId);
  if (customEndpoint) {
    localStorage.setItem('deaios_custom_endpoint', customEndpoint);
  }

  // Switch fleet roster
  if (profileId === 'isaac_sim') {
    swarmState.devices = JSON.parse(JSON.stringify(SIM_DEVICES));
    swarmState.mapData.width = 320;
    swarmState.mapData.height = 200;
    swarmState.mapData.origin_x = -6.0;
    swarmState.mapData.origin_y = -5.0;
  } else {
    swarmState.devices = JSON.parse(JSON.stringify(REAL_DEVICES));
    swarmState.mapData.width = 282;
    swarmState.mapData.height = 179;
    swarmState.mapData.origin_x = -5.33;
    swarmState.mapData.origin_y = -4.51;
  }
}

export function setTargetGoal(x: number, y: number, label = '导航目标点') {
  const targetDevice = swarmState.selectedDeviceId 
    ? swarmState.devices.find(d => d.id === swarmState.selectedDeviceId && d.kind === 'legged_dog')
    : swarmState.devices.find(d => d.status === 'ONLINE' && d.kind === 'legged_dog');

  swarmState.activeGoal = {
    x,
    y,
    label,
    assignedDeviceId: targetDevice?.id
  };

  // If a mission is active or can be triggered
  if (targetDevice) {
    targetDevice.currentMode = 'navigating';
  }
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
    // Advance mission
    if (swarmState.activeMission) {
      swarmState.activeMission.status = 'EXECUTING';
      const runningTask = swarmState.activeMission.tasks.find(t => t.status === 'pending');
      if (runningTask) {
        runningTask.status = 'running';
        runningTask.progressPercent = 35;
      }
    }
    setTimeout(() => {
      swarmState.activeClarification = null;
    }, 1200);
  }
}

export function triggerEmergencyStop() {
  swarmState.eStopTriggered = true;
  for (const d of swarmState.devices) {
    if (d.kind === 'legged_dog' || d.kind === 'wheeled_agv') {
      d.status = 'ESTOP';
      d.linearVelocity = 0.0;
      d.angularVelocity = 0.0;
      d.currentMode = 'estop_brake';
    }
  }
}

export function resetEmergencyStop() {
  swarmState.eStopTriggered = false;
  for (const d of swarmState.devices) {
    if (d.status === 'ESTOP') {
      d.status = 'ONLINE';
      d.currentMode = 'stand';
    }
  }
}

// Background simulation & polling loop
let loopTimer: number | null = null;
let simStep = 0;

export function startTelemetryPolling() {
  if (loopTimer) return;

  const loop = async () => {
    // 1. If currently in Real mode, attempt to query Thor 2 live state
    if (swarmState.currentProfile === 'real_swarm' || swarmState.currentProfile === 'standalone_thor2') {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 1800);
        const res = await fetch('http://100.111.156.77:8092/api/state', { signal: controller.signal });
        clearTimeout(timeoutId);

        if (res.ok) {
          const data = await res.json();
          swarmState.controllerOnline = true;
          const dog2 = swarmState.devices.find(d => d.id === 'dog-thor-2');
          if (dog2) {
            dog2.status = 'ONLINE';
            dog2.hasActiveMap = Boolean(data.has_map);
            if (data.mode) dog2.currentMode = data.mode;
            if (data.pose) {
              const nx = Number(data.pose.x) || dog2.pose.x;
              const ny = Number(data.pose.y) || dog2.pose.y;
              const nth = Number(data.pose.theta) || dog2.pose.theta;
              
              if (Math.hypot(nx - dog2.pose.x, ny - dog2.pose.y) > 0.02) {
                if (!dog2.trajectory) dog2.trajectory = [];
                dog2.trajectory.push({ x: dog2.pose.x, y: dog2.pose.y });
                if (dog2.trajectory.length > 25) dog2.trajectory.shift();
              }
              dog2.pose = { x: nx, y: ny, theta: nth };
            }
          }
        }
      } catch {
        // Fallback gracefully
      }
    }

    // 2. If in Isaac Sim mode, step simulated robots smoothly
    if (swarmState.currentProfile === 'isaac_sim' && !swarmState.eStopTriggered) {
      simStep += 0.05;
      const dogAlpha = swarmState.devices.find(d => d.id === 'sim-dog-alpha');
      const dogBeta = swarmState.devices.find(d => d.id === 'sim-dog-beta');

      // Alpha follows goal or patrols in ellipse
      if (dogAlpha) {
        let tx = dogAlpha.pose.x;
        let ty = dogAlpha.pose.y;

        if (swarmState.activeGoal && swarmState.activeGoal.assignedDeviceId === dogAlpha.id) {
          const gx = swarmState.activeGoal.x;
          const gy = swarmState.activeGoal.y;
          const dx = gx - dogAlpha.pose.x;
          const dy = gy - dogAlpha.pose.y;
          const dist = Math.hypot(dx, dy);

          if (dist > 0.1) {
            dogAlpha.pose.theta = Math.atan2(dy, dx);
            tx += Math.cos(dogAlpha.pose.theta) * 0.04;
            ty += Math.sin(dogAlpha.pose.theta) * 0.04;
            dogAlpha.linearVelocity = 0.4;
          } else {
            dogAlpha.linearVelocity = 0.0;
            dogAlpha.currentMode = 'arrived';
          }
        } else {
          // Patrol ellipse
          tx = 0.8 * Math.cos(simStep * 0.6) + 0.5;
          ty = 1.2 * Math.sin(simStep * 0.6) + 1.2;
          dogAlpha.pose.theta = Math.atan2(
            1.2 * Math.cos(simStep * 0.6) * 0.6,
            -0.8 * Math.sin(simStep * 0.6) * 0.6
          );
          dogAlpha.linearVelocity = 0.35;
        }

        if (!dogAlpha.trajectory) dogAlpha.trajectory = [];
        dogAlpha.trajectory.push({ x: dogAlpha.pose.x, y: dogAlpha.pose.y });
        if (dogAlpha.trajectory.length > 30) dogAlpha.trajectory.shift();
        dogAlpha.pose.x = tx;
        dogAlpha.pose.y = ty;
      }

      // Beta follows Alpha closely with 0.8m offset
      if (dogBeta && dogAlpha) {
        const targetX = dogAlpha.pose.x - Math.cos(dogAlpha.pose.theta) * 0.8;
        const targetY = dogAlpha.pose.y - Math.sin(dogAlpha.pose.theta) * 0.8;
        const dx = targetX - dogBeta.pose.x;
        const dy = targetY - dogBeta.pose.y;
        
        dogBeta.pose.theta = Math.atan2(dy, dx);
        dogBeta.pose.x += dx * 0.08;
        dogBeta.pose.y += dy * 0.08;
        dogBeta.linearVelocity = Math.hypot(dx, dy) * 1.5;

        if (!dogBeta.trajectory) dogBeta.trajectory = [];
        dogBeta.trajectory.push({ x: dogBeta.pose.x, y: dogBeta.pose.y });
        if (dogBeta.trajectory.length > 30) dogBeta.trajectory.shift();
      }
    }

    // Follow-Cam lock
    if (swarmState.followDeviceId) {
      const lockedDevice = swarmState.devices.find(d => d.id === swarmState.followDeviceId);
      if (lockedDevice) {
        // SpatialCanvas will read this
      }
    }

    // 3. Increment mission progress if executing
    if (swarmState.activeMission && swarmState.activeMission.status === 'EXECUTING') {
      const runningTask = swarmState.activeMission.tasks.find(t => t.status === 'running');
      if (runningTask) {
        runningTask.progressPercent = Math.min(100, runningTask.progressPercent + 2);
        if (runningTask.progressPercent >= 100) {
          runningTask.status = 'completed';
          const nextTask = swarmState.activeMission.tasks.find(t => t.status === 'pending');
          if (nextTask) {
            nextTask.status = 'running';
          } else {
            swarmState.activeMission.status = 'COMPLETED';
          }
        }
      }
    }
  };

  loopTimer = window.setInterval(loop, 200);
}
