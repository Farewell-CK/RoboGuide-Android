import { registerPlugin } from '@capacitor/core';

export interface NodeStatus {
  connected: boolean;
  state: string;
  detail: string;
  nodeId: string;
  sessionId: string;
  leaseId: string;
  heartbeatCount: number;
  lastHeartbeatAckMs: number;
  endpoint: string;
  hasCameraPermission: boolean;
}

export interface ExecutionJournalRecord {
  execution_id: string;
  command_id: string;
  session_id: string;
  operation: string;
  parameters_json: string;
  phase: string;
  reason: string;
  artifact_digest?: string;
  artifact_path?: string;
  artifact_size?: number;
  created_at_ms: number;
  updated_at_ms: number;
}

export interface ManualCaptureResult {
  success: boolean;
  filePath?: string;
  digest?: string;
  sizeBytes?: number;
  mimeType?: string;
  timestampMs?: number;
  error?: string;
}

export interface PhoneNodePluginInterface {
  getNodeStatus(): Promise<NodeStatus>;
  connect(options: { host?: string; port?: number; artifactPort?: number; useMtls?: boolean; config?: any }): Promise<{ status: string; endpoint: string; useMtls?: boolean; nodeId?: string; artifactPort?: number }>;
  disconnect(): Promise<{ status: string }>;
  getJournalRecords(options?: { limit?: number }): Promise<{ records: ExecutionJournalRecord[] }>;
  requestCameraPermission(): Promise<{ granted: boolean }>;
  manualCapture(options?: { condition?: string }): Promise<ManualCaptureResult>;
  saveConfig(options: { config: any }): Promise<any>;
  getConfig(): Promise<any>;
  generateDeviceIdentity(options?: { algorithm?: 'EC' | 'RSA'; nodeId?: string }): Promise<{ nodeId: string; publicKeyPem: string; algorithm: string }>;
  operatorRequest(options: { method: 'GET' | 'POST' | 'PUT'; path: string; body?: any }): Promise<{ status: number; data: any; rawBody?: string }>;
  addListener(eventName: 'nodeStatusChanged', listenerFunc: (status: Partial<NodeStatus>) => void): Promise<any>;
  addListener(eventName: 'executionEvent', listenerFunc: (event: { executionId: string; phase: string; reason: string }) => void): Promise<any>;
}

// Fallback for non-Android browser development
const WebPhoneNode: PhoneNodePluginInterface = {
  async getNodeStatus() {
    return {
      connected: false,
      state: 'WEB_PREVIEW',
      detail: 'Running in browser without Android native runtime',
      nodeId: 'node-phone-web-preview',
      sessionId: '',
      leaseId: '',
      heartbeatCount: 0,
      lastHeartbeatAckMs: 0,
      endpoint: '127.0.0.1:50051',
      hasCameraPermission: false,
    };
  },
  async connect(options) {
    return { status: 'web_preview_connect', endpoint: `${options.host || '127.0.0.1'}:${options.port || 50051}`, useMtls: options.useMtls };
  },
  async disconnect() {
    return { status: 'web_preview_disconnect' };
  },
  async getJournalRecords() {
    return { records: [] };
  },
  async requestCameraPermission() {
    return { granted: false };
  },
  async manualCapture() {
    return { success: false, error: 'Camera not available on web preview' };
  },
  async saveConfig(options) {
    return options.config;
  },
  async getConfig() {
    return {};
  },
  async generateDeviceIdentity(options) {
    return { nodeId: options?.nodeId || 'node-phone-mock', publicKeyPem: '-----BEGIN PUBLIC KEY-----\nMOCK\n-----END PUBLIC KEY-----', algorithm: options?.algorithm || 'EC' };
  },
  async operatorRequest(options) {
    const res = await fetch(`http://127.0.0.1:8080${options.path}`, {
      method: options.method,
      headers: { 'Content-Type': 'application/json' },
      body: options.body ? JSON.stringify(options.body) : undefined
    }).catch(() => null);
    if (!res) return { status: 0, data: null };
    const data = await res.json().catch(() => null);
    return { status: res.status, data };
  },
  async addListener() {
    return { remove: () => {} };
  },
};

export const PhoneNode = registerPlugin<PhoneNodePluginInterface>('PhoneNode', {
  web: () => WebPhoneNode,
});
