export type DeviceKind = 'legged_dog' | 'wheeled_agv' | 'manipulator' | 'fixed_camera' | 'human_terminal';

export type DeviceStatus = 'ONLINE' | 'BUSY' | 'IDLE' | 'OFFLINE' | 'ESTOP';

export type EnvProfileType = 'real_swarm' | 'isaac_sim' | 'standalone_thor2' | 'custom';

export interface EnvProfileConfig {
  id: EnvProfileType;
  name: string;
  badge: string;
  description: string;
  controllerEndpoint: string;
  videoStreamBaseUrl: string;
  isSimulated: boolean;
}

export interface CapabilitySummary {
  kind: 'Mobility' | 'Transport' | 'Compute' | 'Observation' | 'HumanInteraction' | 'Manipulation';
  contract: string;
  detail: string;
  available: boolean;
}

export interface DeviceTelemetry {
  id: string;
  name: string;
  roleTag?: string; // e.g. '领航犬' | '协同犬' | '重载运输' | '广角视锥'
  kind: DeviceKind;
  status: DeviceStatus;
  ip: string;
  tailscaleIp?: string;
  batteryPercent: number;
  batteryVoltage?: number;
  pose: {
    x: number;
    y: number;
    theta: number; // yaw in radians
  };
  trajectory?: Array<{ x: number; y: number }>; // Breadcrumb history (last 15 points)
  linearVelocity?: number;
  angularVelocity?: number;
  currentMode: string; // 'mapping' | 'localization' | 'navigating' | 'stand' | 'idle'
  hasActiveMap: boolean;
  cameraStreamUrl?: string;
  capabilities: CapabilitySummary[];
  hardwareDetails?: {
    platform: string;
    temperature?: number;
    cpuLoad?: number;
  };
}

export interface MapData {
  has_map: boolean;
  mode: string;
  width: number;
  height: number;
  resolution: number;
  origin_x: number;
  origin_y: number;
  pose?: { x: number; y: number; theta: number };
  grid_base64?: string;
}

export interface TargetGoal {
  x: number;
  y: number;
  label?: string;
  assignedDeviceId?: string;
}

export interface ClarificationQuestion {
  id: string;
  question: string;
  options: string[];
  selectedOption?: string;
}

export interface MissionTaskDAGItem {
  id: string;
  title: string;
  assignedDeviceId: string;
  assignedDeviceName: string;
  requiredCapability: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  progressPercent: number;
}

export interface ActiveMission {
  id: string;
  title: string;
  rawPrompt: string;
  createdAt: string;
  status: 'SUBMITTING' | 'MATCHING' | 'EXECUTING' | 'COMPLETED' | 'CANCELLED';
  clarifications?: ClarificationQuestion[];
  tasks: MissionTaskDAGItem[];
}
