import { TaskResponse, StreamEvent } from '../types/agent';

const API_BASE = '/api/tasks';

export const taskApi = {
  async listTasks(): Promise<TaskResponse[]> {
    try {
      const res = await fetch(API_BASE);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      return (Array.isArray(data) ? data : []).map((t: any) => ({
        ...t,
        id: t.taskId || t.id,
        taskId: t.taskId || t.id,
      }));
    } catch (err) {
      console.warn('API connection failed, using local offline state:', err);
      return [];
    }
  },

  async getTask(taskId: string): Promise<TaskResponse | null> {
    try {
      const res = await fetch(`${API_BASE}/${taskId}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const t = await res.json();
      return {
        ...t,
        id: t.taskId || t.id,
        taskId: t.taskId || t.id,
      };
    } catch (err) {
      console.warn('Error fetching task:', err);
      return null;
    }
  },

  async interact(input: string, configId?: number): Promise<{ intent: 'CHAT' | 'KNOWLEDGE_QA' | 'AUTONOMOUS_TASK'; reply: string; taskId: string | null; isTask: boolean }> {
    const res = await fetch('/api/interact', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ input, configId }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: Failed to interact`);
    return await res.json();
  },

  async createTask(goal: string, configId?: number): Promise<TaskResponse> {
    const res = await fetch(API_BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ goal, configId }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: Failed to create task`);
    const t = await res.json();
    return {
      ...t,
      id: t.taskId || t.id,
      taskId: t.taskId || t.id,
    };
  },

  async replyToTask(taskId: string, reply: string): Promise<void> {
    const res = await fetch(`${API_BASE}/${taskId}/reply`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: reply,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: Failed to submit reply`);
  },

  async approveTask(taskId: string): Promise<void> {
    const res = await fetch(`${API_BASE}/${taskId}/approve`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: Failed to approve task`);
  },

  async getLlmConfig(): Promise<{ configId?: number; id: number; activeProvider: string; activeModel: string; availableProviders?: string[] }> {
    try {
      const res = await fetch('/api/config/llm');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (err) {
      console.warn('Failed to fetch LLM config:', err);
      return { id: -1, activeProvider: 'gemini', activeModel: 'gemini-3-flash-preview' };
    }
  },

  async setLlmProvider(provider: string): Promise<{ id: number; activeProvider: string; activeModel: string; availableProviders: string[] }> {
    const res = await fetch('/api/config/llm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: Failed to update LLM provider`);
    return await res.json();
  },

  /**
   * Subscribes to the live Server-Sent Events stream for a task.
   */
  subscribeToTaskStream(
    taskId: string,
    onEvent: (event: StreamEvent) => void,
    onError?: (error: any) => void
  ): () => void {
    const sseUrl = `${API_BASE}/${taskId}/events`;
    let eventSource: EventSource | null = null;

    try {
      eventSource = new EventSource(sseUrl);

      eventSource.onmessage = (e) => {
        try {
          const parsed: StreamEvent = JSON.parse(e.data);
          onEvent(parsed);
        } catch (err) {
          console.error('Failed to parse SSE payload', err, e.data);
        }
      };

      eventSource.onerror = (e) => {
        if (onError) onError(e);
      };
    } catch (err) {
      console.warn('SSE subscription failed to initialize', err);
      if (onError) onError(err);
    }

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  },
};
