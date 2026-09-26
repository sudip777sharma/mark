import React, { useState, useEffect } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { taskApi } from '../../api/taskClient';
import { Send, Cpu, Loader2 } from 'lucide-react';

export const GoalPromptBar: React.FC = () => {
  const [goal, setGoal] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const activeProvider = useAgentStore((s) => s.activeProvider);
  const activeModel = useAgentStore((s) => s.activeModel);
  const setActiveProvider = useAgentStore((s) => s.setActiveProvider);
  const activeConfigId = useAgentStore((s) => s.activeConfigId);
  const setActiveModel = useAgentStore((s) => s.setActiveModel);
  const setActiveTask = useAgentStore((s) => s.setActiveTask);
  const addStreamLog = useAgentStore((s) => s.addStreamLog);
  const clearStreamLogs = useAgentStore((s) => s.clearStreamLogs);
  const setActivePlan = useAgentStore((s) => s.setActivePlan);
  const setIsStreaming = useAgentStore((s) => s.setIsStreaming);
  const addRawLog = useAgentStore((s) => s.addRawLog);

  const [configs, setConfigs] = useState<{id: number, configName: string, providerType: string, activeModel: string}[]>([]);

  useEffect(() => {
    taskApi.getLlmConfig().then((cfg) => {
      if (cfg?.activeProvider) {
        setActiveProvider(cfg.activeProvider, cfg.id || -1);
        setActiveModel(cfg.activeModel);
      }
    });
    fetch('/api/config/llm/all').then(res => res.json()).then(data => {
       setConfigs(data);
    }).catch(e => console.error(e));
  }, []);

  const handleSwitchProvider = async () => {
    if (configs.length === 0) return;
    const currentIndex = configs.findIndex(c => c.configName === activeProvider);
    const nextIndex = (currentIndex + 1) % configs.length;
    const next = configs[nextIndex];
    
    addRawLog({ level: 'INFO', message: `Switching active LLM profile to: ${next.configName}` });
    setActiveProvider(next.configName, next.id);
    setActiveModel(next.activeModel || '');
  };

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!goal.trim() || isSubmitting) return;

    const taskGoal = goal.trim();
    setGoal('');
    setIsSubmitting(true);

    // === INSTANT UI FEEDBACK ===
    // Show streaming UI immediately, don't wait for backend
    clearStreamLogs();
    setActivePlan([]);
    setIsStreaming(true);

    addStreamLog({
      id: 'mission-init-' + Date.now(),
      timestamp: new Date().toLocaleTimeString(),
      type: 'task:created',
      title: 'Mission Initialized',
      detail: taskGoal,
    });

    addStreamLog({
      id: 'intent-analysis-' + Date.now(),
      timestamp: new Date().toLocaleTimeString(),
      type: 'agent:thinking',
      title: 'Analyzing Intent & Classifying Goal',
      detail: 'Sending to LLM for intent classification. Waiting for response...',
    });

    addRawLog({ level: 'INFO', message: `Input submitted: "${taskGoal}" [Provider: ${activeProvider}]` });

    // === FIRE-AND-FORGET PATTERN ===
    // Don't await - let the backend work in the background while UI stays responsive.
    // Use createTask directly as fallback to avoid the synchronous intent classification bottleneck.
    taskApi.interact(taskGoal, activeConfigId)
      .then((interaction) => {
        addRawLog({ level: 'INFO', message: `Intent classified: ${interaction.intent} (isTask=${interaction.isTask})` });

        if (interaction.isTask && interaction.taskId) {
          // Task was created, poll it
          taskApi.getTask(interaction.taskId).then((task) => {
            if (task) setActiveTask(task);
          });
        } else {
          // Direct conversation or knowledge Q&A
          setIsStreaming(false);
          addStreamLog({
            id: 'reply-' + Date.now(),
            timestamp: new Date().toLocaleTimeString(),
            type: 'agent:thinking',
            title: interaction.intent === 'CHAT' ? 'Conversational Reply' : 'Knowledge Answer',
            detail: interaction.reply,
          });
        }
      })
      .catch((err: any) => {
        console.warn('Backend interaction failed, falling back to direct task creation:', err);
        addRawLog({ level: 'WARN', message: `Interact call failed: ${err?.message || err}. Falling back to direct createTask.` });
        // Fallback: create the task directly, skipping intent classification entirely
        taskApi.createTask(taskGoal, activeConfigId)
          .then((task) => {
            if (task) setActiveTask(task);
          })
          .catch(() => {
            setIsStreaming(false);
            addStreamLog({
              id: 'error-' + Date.now(),
              timestamp: new Date().toLocaleTimeString(),
              type: 'task:failed',
              title: 'Connection Failed',
              detail: 'Could not reach backend. Is the server running?',
            });
          });
      })
      .finally(() => {
        setIsSubmitting(false);
      });
  };

  return (
    <form
      onSubmit={handleSubmit}
      style={{
        width: '100%',
        padding: '16px 20px',
        borderTop: '1px solid var(--border-subtle)',
        background: 'rgba(7, 9, 14, 0.95)',
        backdropFilter: 'blur(12px)',
      }}
    >
      <div
        className="glass-panel"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          padding: '8px 14px',
          borderRadius: '12px',
          background: 'var(--bg-card)',
          border: '1px solid var(--border-card)',
          boxShadow: 'var(--shadow-md)',
        }}
      >
        {/* Model Selector badge */}
        <div
          onClick={handleSwitchProvider}
          title="Click to cycle through available LLM providers"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            padding: '6px 12px',
            borderRadius: '6px',
            background: 'rgba(6, 182, 212, 0.15)',
            border: '1px solid var(--border-focus)',
            fontSize: '11px',
            color: 'var(--accent-cyan)',
            fontWeight: 600,
            cursor: 'pointer',
            whiteSpace: 'nowrap',
            userSelect: 'none',
            transition: 'var(--transition-fast)',
          }}
        >
          <Cpu size={14} />
          <span>
            {(configs.find(c => c.configName === activeProvider)?.providerType || activeProvider).toUpperCase()}: {activeModel || 'Not Configured'}
          </span>
          <span style={{ fontSize: '10px', opacity: 0.7, marginLeft: '2px' }}>⇄</span>
        </div>

        {/* Text Input */}
        <input
          type="text"
          placeholder="Assign an autonomous goal to MARK (e.g. Calculate (15 * 8) + 42, inspect window, or browse)..."
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          disabled={isSubmitting}
          style={{
            flex: 1,
            fontSize: '14px',
            color: 'var(--text-primary)',
            padding: '4px 0',
          }}
        />

        {/* Submit button */}
        <button
          type="submit"
          disabled={isSubmitting || !goal.trim()}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '36px',
            height: '36px',
            borderRadius: '8px',
            background: goal.trim() ? 'var(--accent-cyan)' : 'rgba(255, 255, 255, 0.05)',
            color: goal.trim() ? '#07090e' : 'var(--text-muted)',
            cursor: goal.trim() && !isSubmitting ? 'pointer' : 'default',
            transition: 'var(--transition-fast)',
            boxShadow: goal.trim() ? '0 0 14px rgba(6, 182, 212, 0.4)' : 'none',
          }}
        >
          {isSubmitting ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
        </button>
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginTop: '8px',
          padding: '0 4px',
          fontSize: '11px',
          color: 'var(--text-dim)',
        }}
      >
        <span>Press <strong>Enter</strong> to dispatch goal</span>
        <span>Configure models in Settings (⚙️)</span>
      </div>
    </form>
  );
};



