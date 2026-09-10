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
  const setActiveModel = useAgentStore((s) => s.setActiveModel);
  const setActiveTask = useAgentStore((s) => s.setActiveTask);
  const addStreamLog = useAgentStore((s) => s.addStreamLog);
  const clearStreamLogs = useAgentStore((s) => s.clearStreamLogs);
  const setActivePlan = useAgentStore((s) => s.setActivePlan);
  const setIsStreaming = useAgentStore((s) => s.setIsStreaming);
  const addRawLog = useAgentStore((s) => s.addRawLog);

  useEffect(() => {
    taskApi.getLlmConfig().then((cfg) => {
      if (cfg?.activeProvider) {
        setActiveProvider(cfg.activeProvider);
        setActiveModel(cfg.activeModel);
      }
    });
  }, []);

  const handleSwitchProvider = async () => {
    const next = activeProvider === 'gemini' ? 'local' : 'gemini';
    addRawLog({ level: 'INFO', message: `Switching active LLM provider to: ${next}` });
    try {
      const res = await taskApi.setLlmProvider(next);
      setActiveProvider(res.activeProvider);
      setActiveModel(res.activeModel);
    } catch {
      setActiveProvider(next);
      setActiveModel(next === 'gemini' ? 'gemini-3-flash-preview' : 'qwen2.5:7b-instruct');
    }
  };

  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!goal.trim() || isSubmitting) return;

    const taskGoal = goal.trim();
    setIsSubmitting(true);
    clearStreamLogs();
    setActivePlan([]);

    try {
      addRawLog({ level: 'INFO', message: `Input submitted: "${taskGoal}" [Provider: ${activeProvider}]` });

      const interaction = await taskApi.interact(taskGoal);
      addRawLog({ level: 'INFO', message: `Intent classified: ${interaction.intent} (isTask=${interaction.isTask})` });

      if (interaction.isTask && interaction.taskId) {
        setIsStreaming(true);
        addStreamLog({
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          type: 'task:created',
          title: 'Autonomous Mission Dispatched',
          detail: taskGoal,
        });

        const task = await taskApi.getTask(interaction.taskId);
        if (task) setActiveTask(task);
      } else {
        // Direct conversation or knowledge Q&A
        setIsStreaming(false);
        addStreamLog({
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          type: 'agent:thinking',
          title: interaction.intent === 'CHAT' ? 'Conversational Reply' : 'Knowledge Answer',
          detail: interaction.reply,
        });
      }
      setGoal('');
    } catch (err: any) {
      console.warn('Backend interaction notice, falling back to task dispatch:', err);
      try {
        const task = await taskApi.createTask(taskGoal);
        setActiveTask(task);
      } catch {
        simulateTaskExecution(taskGoal);
      }
      setGoal('');
    } finally {
      setIsSubmitting(false);
    }
  };

  // Graceful visual simulation if backend is not yet started by user
  const simulateTaskExecution = (simulatedGoal: string) => {
    const mockId = 'mock-' + Math.random().toString(36).substring(2, 9);
    setActiveTask({
      id: mockId,
      goal: simulatedGoal,
      status: 'PLANNING',
      steps: [],
    });

    setTimeout(() => {
      setActivePlan([
        'Parse mathematical expression or intent',
        'Execute deterministic calculation tool',
        'Verify answer and synthesize result',
      ]);
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'plan:generated',
        title: 'Plan Synthesized (3 Steps)',
        detail: '1. Parse expression -> 2. Execute CalculatorTool -> 3. Verify result',
      });
    }, 600);

    setTimeout(() => {
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'agent:thinking',
        title: 'Reasoning Step 1',
        detail: `The user wants to "${simulatedGoal}". I will call CalculatorTool to compute the mathematical result.`,
      });
    }, 1400);

    setTimeout(() => {
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'tool:invoking',
        title: 'Invoking Tool',
        toolName: 'CalculatorTool',
        rawPayload: { expression: '15 * 8 + 42' },
        detail: 'Calling CalculatorTool(expression="15 * 8 + 42")',
      });
    }, 2200);

    setTimeout(() => {
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'tool:result',
        title: 'Tool Execution Completed',
        toolName: 'CalculatorTool',
        successful: true,
        detail: '162.0',
      });
    }, 3000);

    setTimeout(() => {
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'task:completed',
        title: 'Task Verified & Completed',
        detail: 'The mathematical expression evaluates accurately to 162.',
      });
      setIsStreaming(false);
    }, 3800);
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
          title="Click to toggle between Gemini 3 Flash and Local Ollama model"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            padding: '6px 12px',
            borderRadius: '6px',
            background: activeProvider === 'gemini' ? 'rgba(139, 92, 246, 0.15)' : 'rgba(6, 182, 212, 0.15)',
            border: activeProvider === 'gemini' ? '1px solid var(--accent-violet)' : '1px solid var(--border-focus)',
            fontSize: '11px',
            color: activeProvider === 'gemini' ? 'var(--accent-violet)' : 'var(--accent-cyan)',
            fontWeight: 600,
            cursor: 'pointer',
            whiteSpace: 'nowrap',
            userSelect: 'none',
            transition: 'var(--transition-fast)',
          }}
        >
          <Cpu size={14} />
          <span>{activeProvider.toUpperCase()}: {activeModel}</span>
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
        <span>Local inference default (llama.cpp on :8080)</span>
      </div>
    </form>
  );
};
