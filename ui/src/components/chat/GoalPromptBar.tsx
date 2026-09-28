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

  const [configs, setConfigs] = useState<any[]>([]);

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
    
    try {
      const res = await fetch('/api/config/llm/all');
      const latestConfigs = await res.json();
      setConfigs(latestConfigs);

      const currentIndex = latestConfigs.findIndex((c: any) => c.configName === activeProvider);
      const nextIndex = (currentIndex + 1) % latestConfigs.length;
      const next = latestConfigs[nextIndex];
      
      addRawLog({ level: 'INFO', message: `Switching active LLM profile to: ${next.configName}` });
      setActiveProvider(next.configName, next.id);
      setActiveModel(next.activeModel || '');

      await fetch('/api/config/llm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: next.id,
          configName: next.configName,
          providerType: next.providerType,
          model: next.activeModel,
          baseUrl: next.baseUrl,
          apiKeys: next.apiKeys,
          isDefault: true
        })
      });
    } catch (e) {
      console.error("Failed to switch provider", e);
    }
  };

  const handleModelChange = async (newModel: string) => {
    setActiveModel(newModel);
    addRawLog({ level: 'INFO', message: `Switched model for profile ${activeProvider} to: ${newModel}` });

    try {
      const res = await fetch('/api/config/llm/all');
      const latestConfigs = await res.json();
      const current = latestConfigs.find((c: any) => c.configName === activeProvider);
      if (!current) return;

      await fetch('/api/config/llm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: current.id,
          configName: current.configName,
          providerType: current.providerType,
          model: newModel,
          baseUrl: current.baseUrl,
          apiKeys: current.apiKeys,
          isDefault: true
        })
      });
      setConfigs(latestConfigs);
    } catch (e) {
      console.error("Failed to sync model change with backend", e);
    }
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

    // === FIRE-AND-FORGET PATTERN ===
    // Everything is now unified as a Task. The backend will instantly return a Task ID in CREATED state.
    // The backend thread handles classifying it as a chat or autonomous task.
    taskApi.interact(taskGoal, activeConfigId)
      .then((interaction) => {
        addRawLog({ level: 'INFO', message: `Goal dispatched. Tracking as Task ID: ${interaction.taskId}` });
        if (interaction.taskId) {
          // Poll the task to track its lifecycle (CREATED -> PLANNING/COMPLETED)
          taskApi.getTask(interaction.taskId).then((task) => {
            if (task) setActiveTask(task);
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
        {/* Provider / Model Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          {/* Provider Badge (Cycles Profile) */}
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
            <span>{(configs.find(c => c.configName === activeProvider)?.providerType || activeProvider).toUpperCase()}</span>
            <span style={{ fontSize: '10px', opacity: 0.7, marginLeft: '2px' }}>⟳</span>
          </div>

          {/* Model Select Dropdown */}
          <select
            value={activeModel || ''}
            onChange={(e) => handleModelChange(e.target.value)}
            title="Active Model for this profile"
            style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              padding: '4px 6px',
              fontSize: '11px',
              outline: 'none',
              width: '140px',
              fontFamily: 'var(--font-mono)',
              cursor: 'pointer',
              borderRadius: '4px'
            }}
          >
            <option value={activeModel || ''} style={{ background: 'var(--bg-card)' }}>{activeModel || 'Select Model'}</option>
            {configs.find(c => c.configName === activeProvider)?.providerType === 'openrouter' && (
              <>
                <option value="google/gemma-4-31b-it:free" style={{ background: 'var(--bg-card)' }}>google/gemma-4-31b-it:free</option>
                <option value="openrouter/free" style={{ background: 'var(--bg-card)' }}>openrouter/free</option>
                <option value="nvidia/nemotron-3-super-120b-a12b:free" style={{ background: 'var(--bg-card)' }}>nvidia/nemotron-3-super-120b-a12b:free</option>
                <option value="google/gemma-4-26b-a4b-it:free" style={{ background: 'var(--bg-card)' }}>google/gemma-4-26b-a4b-it:free</option>
                <option value="openai/gpt-4o" style={{ background: 'var(--bg-card)' }}>openai/gpt-4o</option>
                <option value="anthropic/claude-3.5-sonnet" style={{ background: 'var(--bg-card)' }}>anthropic/claude-3.5-sonnet</option>
                <option value="qwen/qwen-3-72b-instruct" style={{ background: 'var(--bg-card)' }}>qwen/qwen-3-72b-instruct</option>
                <option value="meta-llama/llama-4-70b-instruct" style={{ background: 'var(--bg-card)' }}>meta-llama/llama-4-70b-instruct</option>
              </>
            )}
            {configs.find(c => c.configName === activeProvider)?.providerType === 'groq' && (
              <>
                <option value="qwen3.6-27b" style={{ background: 'var(--bg-card)' }}>qwen3.6-27b</option>
                <option value="llama-4-scout" style={{ background: 'var(--bg-card)' }}>llama-4-scout</option>
                <option value="deepseek-v4-flash" style={{ background: 'var(--bg-card)' }}>deepseek-v4-flash</option>
                <option value="gemma-3-31b-it" style={{ background: 'var(--bg-card)' }}>gemma-3-31b-it</option>
                <option value="mixtral-8x7b-32768" style={{ background: 'var(--bg-card)' }}>mixtral-8x7b-32768</option>
                <option value="llama-3.1-70b-versatile" style={{ background: 'var(--bg-card)' }}>llama-3.1-70b-versatile</option>
                <option value="llama-3.1-8b-instant" style={{ background: 'var(--bg-card)' }}>llama-3.1-8b-instant</option>
              </>
            )}
            {configs.find(c => c.configName === activeProvider)?.providerType === 'gemini' && (
              <>
                <option value="gemini-3.6-flash" style={{ background: 'var(--bg-card)' }}>gemini-3.6-flash</option>
                <option value="gemini-3.1-pro" style={{ background: 'var(--bg-card)' }}>gemini-3.1-pro</option>
              </>
            )}
          </select>
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



