import React, { useState } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { taskApi } from '../../api/taskClient';
import { HelpCircle, Check, Send, AlertTriangle } from 'lucide-react';

export const HumanInTheLoopModal: React.FC = () => {
  const waitingUserInput = useAgentStore((s) => s.waitingUserInput);
  const setWaitingUserInput = useAgentStore((s) => s.setWaitingUserInput);
  const addStreamLog = useAgentStore((s) => s.addStreamLog);

  const [replyText, setReplyText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!waitingUserInput) return null;

  const handleSendReply = async () => {
    if (!replyText.trim() || isSubmitting) return;
    setIsSubmitting(true);
    try {
      await taskApi.replyToTask(waitingUserInput.taskId, replyText.trim());
      addStreamLog({
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        type: 'user:replied',
        title: 'User Provided Clarification',
        detail: replyText.trim(),
      });
      setWaitingUserInput(null);
      setReplyText('');
    } catch (err) {
      console.error('Failed to submit reply:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleApprove = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await taskApi.approveTask(waitingUserInput.taskId);
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
      setIsSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(3, 7, 18, 0.75)',
        backdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 999,
        padding: '20px',
      }}
    >
      <div
        className="glass-panel"
        style={{
          width: '100%',
          maxWidth: '540px',
          border: '1px solid var(--accent-amber)',
          boxShadow: '0 0 35px rgba(245, 158, 11, 0.25)',
          padding: '24px',
          borderRadius: '16px',
          animation: 'gentleBreathe 4s infinite',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
          <div
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '8px',
              background: 'rgba(245, 158, 11, 0.15)',
              color: 'var(--accent-amber)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <AlertTriangle size={20} />
          </div>
          <div>
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
              Action Requires Your Guidance
            </h3>
            <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
              MARK has paused execution to confirm or request information.
            </span>
          </div>
        </div>

        <div
          style={{
            padding: '14px',
            borderRadius: '10px',
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid var(--border-subtle)',
            marginBottom: '18px',
            fontSize: '14px',
            lineHeight: 1.6,
            color: 'var(--text-primary)',
          }}
        >
          {waitingUserInput.prompt}
        </div>

        <div style={{ marginBottom: '16px' }}>
          <input
            type="text"
            placeholder="Type your response or clarification here..."
            value={replyText}
            onChange={(e) => setReplyText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSendReply();
            }}
            autoFocus
            style={{
              width: '100%',
              padding: '12px 16px',
              borderRadius: '8px',
              border: '1px solid var(--border-card)',
              background: 'var(--bg-input)',
              fontSize: '14px',
              color: 'var(--text-primary)',
              transition: 'var(--transition-fast)',
            }}
          />
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button
            onClick={handleApprove}
            disabled={isSubmitting}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '10px 18px',
              borderRadius: '8px',
              background: 'rgba(16, 185, 129, 0.15)',
              border: '1px solid var(--accent-emerald)',
              color: 'var(--accent-emerald)',
              fontSize: '13px',
              fontWeight: 600,
            }}
          >
            <Check size={16} /> Approve & Continue
          </button>

          <button
            onClick={handleSendReply}
            disabled={isSubmitting || !replyText.trim()}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '10px 20px',
              borderRadius: '8px',
              background: 'var(--accent-cyan)',
              color: '#07090e',
              fontSize: '13px',
              fontWeight: 600,
              boxShadow: 'var(--shadow-glow-cyan)',
              opacity: !replyText.trim() ? 0.5 : 1,
            }}
          >
            <Send size={15} /> Send Reply
          </button>
        </div>
      </div>
    </div>
  );
};
