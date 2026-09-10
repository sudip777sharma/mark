export type TaskStatus = 'PLANNING' | 'EXECUTING' | 'WAITING_USER' | 'COMPLETED' | 'FAILED';

export interface AgentStep {
  stepNumber: number;
  description: string;
  toolName: string;
  outcome: string;
  provider?: string;
}

export interface TaskResponse {
  taskId?: string;
  id?: string;
  goal: string;
  status: TaskStatus;
  finalAnswer?: string | null;
  plan?: string[] | null;
  steps: AgentStep[];
}

export interface ToolCallPayload {
  toolName: string;
  arguments: Record<string, unknown>;
  stepNumber?: number;
}

export interface ToolResultPayload {
  toolName: string;
  successful: boolean;
  observation: string;
  metadata?: Record<string, unknown>;
}

export type StreamEventType =
  | 'task:created'
  | 'plan:generated'
  | 'agent:thinking'
  | 'tool:invoking'
  | 'tool:result'
  | 'agent:critique'
  | 'user:input_required'
  | 'task:completed'
  | 'task:failed';

export interface StreamEvent {
  type: StreamEventType;
  taskId: string;
  timestamp: string;
  data: Record<string, any>;
}

export interface WorldState {
  activeWindowTitle?: string;
  activeBrowserUrl?: string;
  lastCapturedText?: string;
  systemMetrics?: {
    cpuLoad?: string;
    ramUsage?: string;
  };
}

export type VoiceModeState = 'idle' | 'listening' | 'thinking' | 'speaking';
export type AppViewMode = 'voice' | 'canvas';
