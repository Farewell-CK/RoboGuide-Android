import { reactive } from 'vue';
import type { DeviceTelemetry, ActiveMission, MapData } from '../types/swarm';

export interface SwarmState {
  controllerOnline: boolean;
  controllerEndpoint: string;
  phoneNodeId: string;
  eStopTriggered: boolean;
  selectedDeviceId: string | null;
  activeMission: ActiveMission | null;
  mapData: MapData;
  devices: DeviceTelemetry[];
  humanPose: { x: number; y: number; theta: number };
}

export const swarmState = reactive<SwarmState>({
  controllerOnline: true,
  controllerEndpoint: 'http://100.84.233.121:8080',
  phoneNodeId: 'node-phone-user',
  eStopTriggered: false,
  selectedDeviceId: null,
  activeMission: null,
  humanPose: { x: 0.5, y: -0.2, theta: 1.57 },
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
  devices: [
    {
      id: 'dog-thor-1',
      name: '1号狗 (Thor 1 领航犬)',
      kind: 'legged_dog',
      status: 'OFFLINE',
      ip: '192.168.2.47',
      tailscaleIp: '100.117.131.75',
      batteryPercent: 88,
      batteryVoltage: 24.2,
      pose: { x: -0.8, y: -0.5, theta: 0.2 },
      linearVelocity: 0.0,
      angularVelocity: 0.0,
      currentMode: 'standby',
      hasActiveMap: false,
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
      kind: 'legged_dog',
      status: 'ONLINE',
      ip: '192.168.2.42',
      tailscaleIp: '100.111.156.77',
      batteryPercent: 94,
      batteryVoltage: 25.1,
      pose: { x: 0.0, y: 0.0, theta: 0.005 },
      linearVelocity: 0.0,
      angularVelocity: 0.0,
      currentMode: 'mapping',
      hasActiveMap: true,
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
      name: '轮式重载运输车 (AGV-01)',
      kind: 'wheeled_agv',
      status: 'IDLE',
      ip: '192.168.2.105',
      tailscaleIp: '100.82.0.23',
      batteryPercent: 76,
      batteryVoltage: 48.0,
      pose: { x: 2.2, y: -1.8, theta: -1.2 },
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
      kind: 'fixed_camera',
      status: 'ONLINE',
      ip: '192.168.2.201',
      batteryPercent: 100,
      pose: { x: 1.5, y: 3.2, theta: -1.57 },
      currentMode: 'streaming',
      hasActiveMap: false,
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
  ]
});

// Periodic fetcher from real Thor 2 WebUI (100.111.156.77:8092/api/state)
let fetchTimer: number | null = null;

export function startTelemetryPolling() {
  if (fetchTimer) return;

  const poll = async () => {
    try {
      // Attempt to fetch from real Thor 2
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 1800);
      
      const res = await fetch('http://100.111.156.77:8092/api/state', {
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        const dog2 = swarmState.devices.find(d => d.id === 'dog-thor-2');
        if (dog2) {
          dog2.status = 'ONLINE';
          dog2.hasActiveMap = Boolean(data.has_map);
          dog2.currentMode = data.mode || 'mapping';
          if (data.pose) {
            dog2.pose = {
              x: Number(data.pose.x) || 0,
              y: Number(data.pose.y) || 0,
              theta: Number(data.pose.theta) || 0
            };
          }
        }
        if (data.has_map) {
          swarmState.mapData.has_map = true;
          swarmState.mapData.width = data.width || 282;
          swarmState.mapData.height = data.height || 179;
          swarmState.mapData.resolution = data.resolution || 0.05;
          swarmState.mapData.origin_x = data.origin_x || -5.33;
          swarmState.mapData.origin_y = data.origin_y || -4.51;
        }
      }
    } catch {
      // If Thor 2 or Tailscale is temporarily offline or CORS-blocked in browser, keep fallback
    }
  };

  poll();
  fetchTimer = window.setInterval(poll, 2500);
}

// Emergency stop trigger
export function triggerEmergencyStop(stopAll = true) {
  swarmState.eStopTriggered = true;
  if ('vibrate' in navigator) {
    navigator.vibrate([200, 100, 200, 100, 400]);
  }
  swarmState.devices.forEach(d => {
    if (d.kind !== 'human_terminal' && d.kind !== 'fixed_camera') {
      d.status = 'ESTOP';
      d.linearVelocity = 0;
      d.angularVelocity = 0;
    }
  });
  console.warn('[E-STOP] Emergency stop broadcasted to cluster! Target all:', stopAll);
}

export function resetEmergencyStop() {
  swarmState.eStopTriggered = false;
  swarmState.devices.forEach(d => {
    if (d.status === 'ESTOP') {
      d.status = d.id === 'dog-thor-1' ? 'OFFLINE' : 'ONLINE';
    }
  });
}

// Submit Natural Language Mission
export function submitMissionPrompt(promptText: string) {
  const missionId = `mission-${Date.now().toString().slice(-4)}`;
  
  // Intelligent matching simulation based on capabilities
  const isDelivery = promptText.includes('快递') || promptText.includes('文件') || promptText.includes('送');
  const isLead = promptText.includes('带我') || promptText.includes('带路') || promptText.includes('办公室');
  const isMapping = promptText.includes('建图') || promptText.includes('地图') || promptText.includes('同步');

  const tasks: ActiveMission['tasks'] = [];

  if (isMapping) {
    tasks.push({
      id: 'task-1',
      title: '狗 2 扫描并构建当前环境 3D 增量地图',
      assignedDeviceId: 'dog-thor-2',
      assignedDeviceName: '2号狗 (Thor 2)',
      requiredCapability: 'spatial.map.build@v0',
      status: 'running',
      progressPercent: 35
    });
    tasks.push({
      id: 'task-2',
      title: '发布地图不可变修订版至 Artifact CAS',
      assignedDeviceId: 'dog-thor-2',
      assignedDeviceName: '2号狗 (Thor 2)',
      requiredCapability: 'spatial.map.publish@v0',
      status: 'pending',
      progressPercent: 0
    });
    tasks.push({
      id: 'task-3',
      title: '轮式车拉取地图并更新全局路径代价',
      assignedDeviceId: 'agv-chassis-01',
      assignedDeviceName: '轮式重载运输车 (AGV-01)',
      requiredCapability: 'spatial.map.import@v0',
      status: 'pending',
      progressPercent: 0
    });
  } else if (isLead && isDelivery) {
    tasks.push({
      id: 'task-1',
      title: '狗 2 匹配领航意图，在人身前 1.5m 伴随导引',
      assignedDeviceId: 'dog-thor-2',
      assignedDeviceName: '2号狗 (Thor 2)',
      requiredCapability: 'mobility.legged.terrain@v1',
      status: 'running',
      progressPercent: 20
    });
    tasks.push({
      id: 'task-2',
      title: '走廊监控粗筛前台走道通行净空',
      assignedDeviceId: 'cam-hallway-03',
      assignedDeviceName: '走廊全景高位相机',
      requiredCapability: 'observation.camera.panoramic@v1',
      status: 'completed',
      progressPercent: 100
    });
    tasks.push({
      id: 'task-3',
      title: 'AGV 运载货物平地巡航至目标交接区',
      assignedDeviceId: 'agv-chassis-01',
      assignedDeviceName: '轮式重载运输车 (AGV-01)',
      requiredCapability: 'transport.payload.heavy@v1',
      status: 'pending',
      progressPercent: 0
    });
  } else {
    tasks.push({
      id: 'task-1',
      title: '异构能力匹配与意图分解',
      assignedDeviceId: 'node-phone-user',
      assignedDeviceName: '手机交互中枢',
      requiredCapability: 'human.intent.voice@v1',
      status: 'completed',
      progressPercent: 100
    });
    tasks.push({
      id: 'task-2',
      title: '双机协同巡查走廊指定点位',
      assignedDeviceId: 'dog-thor-2',
      assignedDeviceName: '2号狗 (Thor 2)',
      requiredCapability: 'mobility.legged.terrain@v1',
      status: 'running',
      progressPercent: 45
    });
  }

  swarmState.activeMission = {
    id: missionId,
    title: promptText,
    rawPrompt: promptText,
    createdAt: new Date().toLocaleTimeString(),
    status: 'EXECUTING',
    tasks
  };
}
