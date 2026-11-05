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
  connect(options: { host: string; port: number; artifactPort?: number }): Promise<{ status: string; endpoint: string; artifactPort?: number }>;
  disconnect(): Promise<{ status: string }>;
  getJournalRecords(options?: { limit?: number }): Promise<{ records: ExecutionJournalRecord[] }>;
  requestCameraPermission(): Promise<{ granted: boolean }>;
  manualCapture(options?: { condition?: string }): Promise<ManualCaptureResult>;
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
    return { status: 'web_preview_connect', endpoint: `${options.host}:${options.port}` };
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
  async addListener() {
    return { remove: () => {} };
  },
};

export const PhoneNode = registerPlugin<PhoneNodePluginInterface>('PhoneNode', {
  web: () => WebPhoneNode,
});
