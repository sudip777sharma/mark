import React, { useEffect, useRef } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { taskApi } from '../../api/taskClient';
import { ToolCallCard } from './ToolCallCard';
import { InjectedPromptInspector } from './InjectedPromptInspector';
import {
  Brain,
  ListTodo,
  CheckCircle2,
  AlertCircle,
  Clock,
  Sparkles,
  Layers,
  Zap,
  AlertTriangle,
  Send,
  Check,
  Code,
} from 'lucide-react';

const AnimatedEllipsis: React.FC = () => {
  const [dots, setDots] = React.useState('');
  useEffect(() => {
    const interval = setInterval(() => {
      setDots(prev => prev.length >= 3 ? '' : prev + '.');
    }, 400);
    return () => clearInterval(interval);
  }, []);
  // Use a fixed width span to prevent layout jitter when dots change
  return <span style={{ display: 'inline-block', width: '1em', textAlign: 'left' }}>{dots}</span>;
};

export const LiveAgentCanvas: React.FC = () => {
  const activeTask = useAgentStore((s) => s.activeTask);
  const streamLogs = useAgentStore((s) => s.streamLogs);
  const activePlan = useAgentStore((s) => s.activePlan);
  const isStreaming = useAgentStore((s) => s.isStreaming);
  const llmThoughtStream = useAgentStore((s) => s.llmThoughtStream);
  const waitingUserInput = useAgentStore((s) => s.waitingUserInput);
  const setWaitingUserInput = useAgentStore((s) => s.setWaitingUserInput);
  const setActiveTask = useAgentStore((s) => s.setActiveTask);
  const addStreamLog = useAgentStore((s) => s.addStreamLog);
  const llmInteractionLogs = useAgentStore((s) => s.llmInteractionLogs);

  const [humanReplyText, setHumanReplyText] = React.useState('');
  const [isSubmittingReply, setIsSubmittingReply] = React.useState(false);
  const [showPromptInspector, setShowPromptInspector] = React.useState(false);

  const handleSendHumanReply = async () => {
    const tId = activeTask?.taskId || activeTask?.id || waitingUserInput?.taskId;
    if (!tId || !humanReplyText.trim() || isSubmittingReply) return;
    setIsSubmittingReply(true);
    try {
      if (activeTask) {
        setActiveTask({ ...activeTask, status: 'EXECUTING', currentAction: 'User reply submitted. Resuming task...' });
      }
      await taskApi.replyToTask(tId, humanReplyText.trim());
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'user:replied',
        title: 'User Provided Clarification',
        detail: humanReplyText.trim(),
      });
      setWaitingUserInput(null);
      setHumanReplyText('');
    } catch (err) {
      console.error('Failed to submit reply:', err);
    } finally {
      setIsSubmittingReply(false);
    }
  };

  const handleApproveHumanAction = async () => {
    const tId = activeTask?.taskId || activeTask?.id || waitingUserInput?.taskId;
    if (!tId || isSubmittingReply) return;
    setIsSubmittingReply(true);
    try {
      if (activeTask) {
        setActiveTask({ ...activeTask, status: 'EXECUTING', currentAction: 'Action approved. Resuming task...' });
      }
      await taskApi.approveTask(tId);
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'user:approved',
        title: 'Action Approved by User',
        detail: 'User granted authorization to proceed.',
      });
      setWaitingUserInput(null);
    } catch (err) {
      console.error('Failed to approve:', err);
    } finally {
      setIsSubmittingReply(false);
    }
  };
  
  const [expandAllSignal, setExpandAllSignal] = React.useState(0);
  const [collapseAllSignal, setCollapseAllSignal] = React.useState(0);
  const [cooldownTimer, setCooldownTimer] = React.useState(0);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [streamLogs]);

  useEffect(() => {
    if (activeTask?.status === 'COOLDOWN') {
      setCooldownTimer(60);
      const interval = setInterval(() => {
        setCooldownTimer((prev) => (prev > 0 ? prev - 1 : 0));
      }, 1000);
      return () => clearInterval(interval);
    } else {
      setCooldownTimer(0);
    }
  }, [activeTask?.status]);

  if (!activeTask) {
    return (
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--text-muted)',
          padding: '40px',
          textAlign: 'center',
        }}
      >
        <div
          style={{
            width: '64px',
            height: '64px',
            borderRadius: '16px',
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid var(--border-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: '16px',
            color: 'var(--accent-cyan)',
          }}
        >
          <Sparkles size={28} />
        </div>
        <h3 style={{ fontSize: '18px', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '8px' }}>
          Autonomous Agent Standby
        </h3>
        <p style={{ fontSize: '14px', maxWidth: '420px', lineHeight: 1.6 }}>
          Submit a goal in the prompt bar below or switch to <strong>Live Voice Mode</strong> to talk directly with MARK.
        </p>
      </div>
    );
  }

  const statusColors: Record<string, { color: string; bg: string }> = {
    PLANNING: { color: 'var(--accent-violet)', bg: 'rgba(139, 92, 246, 0.15)' },
    EXECUTING: { color: 'var(--accent-cyan)', bg: 'rgba(6, 182, 212, 0.15)' },
    WAITING_USER: { color: 'var(--accent-amber)', bg: 'rgba(245, 158, 11, 0.15)' },
    COMPLETED: { color: 'var(--accent-emerald)', bg: 'rgba(16, 185, 129, 0.15)' },
    FAILED: { color: 'var(--accent-rose)', bg: 'rgba(244, 63, 94, 0.15)' },
    COOLDOWN: { color: '#fb923c', bg: 'rgba(251, 146, 60, 0.15)' }, // Orange
    QUOTA_EXHAUSTED: { color: '#dc2626', bg: 'rgba(220, 38, 38, 0.15)' }, // Red
  };

  const currentStatus = statusColors[activeTask.status] || statusColors.PLANNING;

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflowY: 'auto',
      }}
    >
      {/* Top Active Task Header Wrapper */}
      <div
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 10,
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          background: 'rgba(9, 13, 22, 0.85)',
          padding: '24px 28px 20px 28px',
          borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
        }}
      >
        <div
          className="glass-panel"
          style={{
            padding: '16px 20px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '16px',
          }}
        >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
            <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.5px' }}>
              CURRENT MISSION
            </span>
            <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
              ID: {(activeTask.taskId || activeTask.id || 'mission').substring(0, 8)}...
            </span>
          </div>
          <h2 style={{ fontSize: '17px', fontWeight: 600, color: 'var(--text-primary)', lineHeight: 1.4 }}>
            {activeTask.goal}
          </h2>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {activeTask.llmRequestCount !== undefined && activeTask.llmRequestCount > 0 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                padding: '6px 14px',
                borderRadius: '9999px',
                background: 'rgba(234, 179, 8, 0.15)',
                border: '1px solid var(--accent-amber)',
                color: 'var(--accent-amber)',
                fontSize: '12px',
                fontWeight: 600,
                whiteSpace: 'nowrap',
              }}
            >
              <Zap size={13} />
              <span>LLM Calls: {activeTask.llmRequestCount}</span>
            </div>
          )}
          
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 14px',
              borderRadius: '9999px',
              background: currentStatus.bg,
              border: `1px solid ${currentStatus.color}`,
              color: currentStatus.color,
              fontSize: '12px',
              fontWeight: 600,
              whiteSpace: 'nowrap',
            }}
          >
          {isStreaming ? (
            <div
              style={{
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                backgroundColor: currentStatus.color,
                animation: 'pulseGlow 1.5s infinite',
              }}
            />
          ) : (
            <CheckCircle2 size={13} />
          )}
          <span>
            {activeTask.status}
            {activeTask.status === 'COOLDOWN' && cooldownTimer > 0 && ` (${cooldownTimer}s)`}
          </span>
        </div>
        </div>
      </div>
      </div>

      <div style={{ padding: '0 28px 24px 28px', display: 'flex', flexDirection: 'column' }}>

      {/* Plan Roadmap Visualizer */}
      {activePlan.length > 0 && (
        <div
          className="glass-panel"
          style={{
            padding: '14px 18px',
            marginBottom: '20px',
            background: 'rgba(15, 23, 42, 0.65)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <ListTodo size={15} color="var(--accent-cyan)" />
            <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-primary)', letterSpacing: '0.5px' }}>
              REASONING & EXECUTION PLAN ({activePlan.length} STEPS)
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {activePlan.map((step, idx) => (
              <div
                key={idx}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  fontSize: '13px',
                  color: 'var(--text-secondary)',
                }}
              >
                <div
                  style={{
                    width: '20px',
                    height: '20px',
                    borderRadius: '50%',
                    background: 'rgba(255, 255, 255, 0.05)',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '11px',
                    fontWeight: 600,
                    fontFamily: 'var(--font-mono)',
                    color: 'var(--accent-cyan)',
                  }}
                >
                  {idx + 1}
                </div>
                <span>{step}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Live Stream of Events, Thoughts & Tool Executions */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)' }}>Execution Trace</span>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <button
              onClick={() => setShowPromptInspector((prev) => !prev)}
              className="glass-button"
              style={{
                fontSize: '11px',
                padding: '4px 10px',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                color: showPromptInspector ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                borderColor: showPromptInspector ? 'var(--accent-cyan)' : undefined,
                background: showPromptInspector ? 'rgba(6, 182, 212, 0.15)' : undefined,
              }}
              title="View the exact prompt injected into the LLM"
            >
              <Code size={13} />
              <span>Injected Prompts {llmInteractionLogs.length > 0 ? `(${llmInteractionLogs.length})` : ''}</span>
            </button>
            <button
              onClick={() => setExpandAllSignal(prev => prev + 1)}
              className="glass-button"
              style={{ fontSize: '11px', padding: '4px 8px' }}
            >
              Expand All
            </button>
            <button
              onClick={() => setCollapseAllSignal(prev => prev + 1)}
              className="glass-button"
              style={{ fontSize: '11px', padding: '4px 8px' }}
            >
              Collapse All
            </button>
          </div>
        </div>

        {showPromptInspector && (
          <InjectedPromptInspector
            logs={llmInteractionLogs}
            onClose={() => setShowPromptInspector(false)}
          />
        )}
        
        {streamLogs.map((log) => {
          if (log.type === 'tool:invoking' || log.type === 'tool:result') {
            return (
              <ToolCallCard
                key={log.id}
                expandAllSignal={expandAllSignal}
                collapseAllSignal={collapseAllSignal}
                toolName={log.toolName || 'Tool'}
                argumentsMap={log.rawPayload}
                observation={log.detail}
                successful={log.successful}
                timestamp={log.timestamp}
                isExecuting={log.type === 'tool:invoking'}
              />
            );
          }

          if (log.type === 'agent:thinking') {
            return (
              <div
                key={log.id}
                className="glass-panel"
                style={{
                  padding: '14px 16px',
                  borderLeft: '3px solid var(--accent-violet)',
                  background: 'rgba(139, 92, 246, 0.05)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                  <Brain size={15} color="var(--accent-violet)" />
                  <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--accent-violet)' }}>
                    Agent Reasoning
                  </span>
                  <button
                    onClick={() => setShowPromptInspector(true)}
                    style={{
                      background: 'transparent',
                      border: '1px solid rgba(139, 92, 246, 0.3)',
                      borderRadius: '4px',
                      color: 'var(--accent-violet)',
                      fontSize: '10px',
                      padding: '2px 8px',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      marginLeft: '6px',
                    }}
                    title="Inspect the prompt injected into the LLM for this step"
                  >
                    <Code size={11} />
                    <span>Injected Prompt</span>
                  </button>
                  <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)', marginLeft: 'auto' }}>
                    {log.timestamp}
                  </span>
                </div>
                <p style={{ fontSize: '13px', color: 'var(--text-primary)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
                  {log.detail}
                </p>
              </div>
            );
          }

          if (log.type === 'task:completed') {
            return (
              <div
                key={log.id}
                className="glass-panel"
                style={{
                  padding: '16px 18px',
                  borderLeft: '3px solid var(--accent-emerald)',
                  background: 'rgba(16, 185, 129, 0.08)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                  <CheckCircle2 size={16} color="var(--accent-emerald)" />
                  <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--accent-emerald)' }}>
                    Mission Accomplished & Verified
                  </span>
                </div>
                <p style={{ fontSize: '14px', color: 'var(--text-primary)', lineHeight: 1.6 }}>
                  {log.detail}
                </p>
              </div>
            );
          }

          return (
            <div
              key={log.id}
              className="glass-panel-subtle"
              style={{
                padding: '10px 14px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Layers size={13} color="var(--text-muted)" />
                <span style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                  {log.title}
                </span>
                {log.detail && (
                  <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Ã¢â‚¬â€ {log.detail}</span>
                )}
              </div>
              <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
                {log.timestamp}
              </span>
            </div>
          );
        })}

        {(waitingUserInput || activeTask?.status === 'NEEDS_INPUT') && (
          <div
            className="glass-panel"
            style={{
              margin: '12px 0',
              padding: '16px 18px',
              border: '1px solid var(--accent-amber)',
              borderRadius: '8px',
              background: 'rgba(245, 158, 11, 0.08)',
              boxShadow: '0 0 20px rgba(245, 158, 11, 0.15)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
              <AlertTriangle size={18} color="var(--accent-amber)" />
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--accent-amber)' }}>
                Action Requires Your Guidance (Human-in-the-Loop)
              </span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--text-primary)', marginBottom: '12px', lineHeight: 1.5 }}>
              {waitingUserInput?.prompt || activeTask?.currentAction || 'MARK requires clarification to proceed.'}
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <textarea
                value={humanReplyText}
                onChange={(e) => setHumanReplyText(e.target.value)}
                placeholder="Type your response, clarification, or instruction here..."
                rows={2}
                style={{
                  width: '100%',
                  background: 'rgba(0, 0, 0, 0.4)',
                  border: '1px solid var(--border)',
                  borderRadius: '6px',
                  padding: '8px 12px',
                  color: 'var(--text-primary)',
                  fontSize: '13px',
                  resize: 'none',
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSendHumanReply();
                  }
                }}
              />
              <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                <button
                  onClick={handleApproveHumanAction}
                  disabled={isSubmittingReply}
                  className="btn-secondary"
                  style={{
                    padding: '6px 12px',
                    fontSize: '12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    cursor: 'pointer'
                  }}
                >
                  <Check size={14} />
                  <span>Approve & Continue</span>
                </button>
                <button
                  onClick={handleSendHumanReply}
                  disabled={!humanReplyText.trim() || isSubmittingReply}
                  className="btn-primary"
                  style={{
                    padding: '6px 14px',
                    fontSize: '12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    cursor: humanReplyText.trim() ? 'pointer' : 'not-allowed',
                    opacity: humanReplyText.trim() ? 1 : 0.6
                  }}
                >
                  <Send size={14} />
                  <span>Send Reply</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {llmThoughtStream && (
          <div style={{
            margin: '8px 0',
            padding: '12px 14px',
            background: 'rgba(0, 0, 0, 0.5)',
            borderRadius: '6px',
            border: '1px solid var(--border)',
            color: 'var(--text-secondary)',
            fontFamily: 'monospace',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            width: '100%',
            fontSize: '11px',
            maxHeight: '260px',
            overflowY: 'auto',
            boxSizing: 'border-box'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                <Brain size={14} color="var(--accent-cyan)" />
                <span>{isStreaming ? '🧠 LLM Thought (Live Streaming):' : '🧠 LLM Thought / Reasoning (Persisted):'}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <button
                  onClick={() => setShowPromptInspector((prev) => !prev)}
                  style={{
                    background: 'rgba(6, 182, 212, 0.1)',
                    border: '1px solid rgba(6, 182, 212, 0.3)',
                    borderRadius: '4px',
                    color: 'var(--accent-cyan)',
                    fontSize: '10px',
                    padding: '2px 8px',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                  title="Inspect the full prompt and system instructions sent to the LLM"
                >
                  <Code size={11} />
                  <span>{showPromptInspector ? 'Hide Prompt' : 'View Injected Prompt'}</span>
                </button>
                <span style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                  {isStreaming ? 'Live' : 'Saved in DB'}
                </span>
              </div>
            </div>
            <div style={{ lineHeight: 1.5, color: 'var(--text-muted)' }}>
              {llmThoughtStream}
              {isStreaming && (
                <span className="animate-pulse" style={{ color: 'var(--accent-cyan)', marginLeft: '4px' }}>■</span>
              )}
            </div>
          </div>
        )}

        {isStreaming && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '10px 14px',
              color: 'var(--accent-cyan)',
              fontSize: '12px',
              width: '100%',
              boxSizing: 'border-box'
            }}
          >
            <Clock size={14} className="animate-spin" />
            <span>
              {activeTask?.currentAction ? (
                <span>
                  {activeTask.currentAction.replace(/\.+$/, '')}
                  <AnimatedEllipsis />
                </span>
              ) : (
                <span>
                  Autonomous agent executing next step<AnimatedEllipsis />
                </span>
              )}
            </span>
          </div>
        )}
        <div ref={bottomRef} />
      </div>
      </div>
    </div>
  );
};

