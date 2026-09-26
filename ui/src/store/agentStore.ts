import { create } from 'zustand';
import { AppViewMode, TaskResponse, VoiceModeState, WorldState } from '../types/agent';

export interface StreamLogItem {
  id: string;
  timestamp: string;
  type: string;
  title: string;
  detail?: string;
  toolName?: string;
  successful?: boolean;
  rawPayload?: any;
}

interface AgentStoreState {
  // Navigation & Modes
  viewMode: AppViewMode;
  setViewMode: (mode: AppViewMode) => void;

  // Voice Interaction State
  voiceState: VoiceModeState;
  setVoiceState: (state: VoiceModeState) => void;
  audioLevel: number;
  setAudioLevel: (level: number) => void;
  voiceTranscript: string;
  setVoiceTranscript: (text: string) => void;
  agentSpeechText: string;
  setAgentSpeechText: (text: string) => void;

  // Task & Canvas State
  tasks: TaskResponse[];
  setTasks: (tasks: TaskResponse[]) => void;
  activeTaskId: string | null;
  activeTask: TaskResponse | null;
  setActiveTaskId: (id: string | null) => void;
  setActiveTask: (task: TaskResponse | null) => void;

  // Streaming & Live Reasoning
  isStreaming: boolean;
  setIsStreaming: (streaming: boolean) => void;
  streamLogs: StreamLogItem[];
  setStreamLogs: (logs: StreamLogItem[]) => void;
  addStreamLog: (item: StreamLogItem) => void;
  clearStreamLogs: () => void;
  activePlan: string[];
  setActivePlan: (plan: string[]) => void;

  // Human in the loop
  waitingUserInput: { taskId: string; prompt: string } | null;
  setWaitingUserInput: (input: { taskId: string; prompt: string } | null) => void;

  // World State Telemetry
  worldState: WorldState;
  setWorldState: (state: Partial<WorldState>) => void;

  // Active LLM Config
  activeProvider: string; activeConfigId: number;
  activeModel: string;
  setActiveProvider: (provider: string, configId: number) => void;
  setActiveModel: (model: string) => void;

  // Test Mode & Unfiltered Logs
  isTestMode: boolean;
  setIsTestMode: (testMode: boolean) => void;
  rawLogs: Array<{ id: string; timestamp: string; level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'; message: string; payload?: any }>;
  addRawLog: (log: { level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'; message: string; payload?: any }) => void;
  clearRawLogs: () => void;
}

export const useAgentStore = create<AgentStoreState>((set) => ({
  viewMode: 'voice',
  setViewMode: (viewMode) => set({ viewMode }),

  voiceState: 'idle',
  setVoiceState: (voiceState) => set({ voiceState }),
  audioLevel: 0,
  setAudioLevel: (audioLevel) => set({ audioLevel }),
  voiceTranscript: '',
  setVoiceTranscript: (voiceTranscript) => set({ voiceTranscript }),
  agentSpeechText: '',
  setAgentSpeechText: (agentSpeechText) => set({ agentSpeechText }),

  tasks: [],
  setTasks: (tasks) => set({ tasks }),
  activeTaskId: null,
  activeTask: null,
  setActiveTaskId: (activeTaskId) => set({ activeTaskId }),
  setActiveTask: (activeTask) =>
    set({
      activeTask,
      activeTaskId: activeTask ? activeTask.taskId || activeTask.id || null : null,
    }),

  isStreaming: false,
  setIsStreaming: (isStreaming) => set({ isStreaming }),
  streamLogs: [],
  setStreamLogs: (logs) => set({ streamLogs: logs }),
  addStreamLog: (item) => set((state) => ({ streamLogs: [...state.streamLogs, item] })),
  clearStreamLogs: () => set({ streamLogs: [] }),
  activePlan: [],
  setActivePlan: (activePlan) => set({ activePlan }),

  waitingUserInput: null,
  setWaitingUserInput: (waitingUserInput) => set({ waitingUserInput }),

  worldState: {
    activeWindowTitle: 'Visual Studio Code',
    activeBrowserUrl: 'http://localhost:8080/v1/chat/completions',
    systemMetrics: {
      cpuLoad: '12%',
      ramUsage: '4.8 GB / 16 GB',
    },
  },
  setWorldState: (updates) =>
    set((state) => ({ worldState: { ...state.worldState, ...updates } })),

  activeProvider: 'gemini', activeConfigId: -1,
  activeModel: 'gemini-3-flash-preview',
  setActiveProvider: (activeProvider, activeConfigId) => set({ activeProvider, activeConfigId }),
  setActiveModel: (activeModel) => set({ activeModel }),

  isTestMode: false,
  setIsTestMode: (isTestMode) => set({ isTestMode }),
  rawLogs: [],
  addRawLog: (log) =>
    set((state) => ({
      rawLogs: [
        ...state.rawLogs,
        {
          id: Math.random().toString(36).substring(2, 9),
          timestamp: new Date().toLocaleTimeString() + '.' + String(new Date().getMilliseconds()).padStart(3, '0'),
          ...log,
        },
      ],
    })),
  clearRawLogs: () => set({ rawLogs: [] }),
}));
