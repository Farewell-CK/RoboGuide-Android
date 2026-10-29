export type DeviceKind = 'legged_dog' | 'wheeled_agv' | 'manipulator' | 'fixed_camera' | 'human_terminal';

export type DeviceStatus = 'ONLINE' | 'BUSY' | 'IDLE' | 'OFFLINE' | 'ESTOP';

export interface CapabilitySummary {
  kind: 'Mobility' | 'Transport' | 'Compute' | 'Observation' | 'HumanInteraction' | 'Manipulation';
  contract: string;
  detail: string;
  available: boolean;
}

export interface DeviceTelemetry {
  id: string;
  name: string;
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
  linearVelocity?: number;
  angularVelocity?: number;
  currentMode: string; // 'mapping' | 'localization' | 'navigating' | 'stand' | 'idle'
  hasActiveMap: boolean;
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
  grid_base64?: string; // Occupancy grid binary/image if available
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
  tasks: MissionTaskDAGItem[];
}
