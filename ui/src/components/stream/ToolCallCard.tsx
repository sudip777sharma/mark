import React, { useState } from 'react';
import {
  Calculator,
  Globe,
  Monitor,
  FileText,
  Terminal,
  UserCheck,
  CheckCircle2,
  XCircle,
  Clock,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
} from 'lucide-react';

interface ToolCallCardProps {
  toolName: string;
  argumentsMap?: Record<string, unknown>;
  observation?: string;
  successful?: boolean;
  timestamp?: string;
  isExecuting?: boolean;
  expandAllSignal?: number;
  collapseAllSignal?: number;
}

export const ToolCallCard: React.FC<ToolCallCardProps> = ({
  toolName,
  argumentsMap = {},
  observation,
  successful,
  timestamp,
  isExecuting = false,
  expandAllSignal = 0,
  collapseAllSignal = 0,
}) => {
  const [expanded, setExpanded] = useState(false);
  const [copied, setCopied] = useState(false);

  React.useEffect(() => {
    if (expandAllSignal > 0) setExpanded(true);
  }, [expandAllSignal]);

  React.useEffect(() => {
    if (collapseAllSignal > 0) setExpanded(false);
  }, [collapseAllSignal]);

  const getToolMeta = () => {
    const name = toolName.toLowerCase();
    if (name.includes('calculator')) {
      return { icon: <Calculator size={15} />, label: 'Calculator', color: 'var(--accent-cyan)' };
    }
    if (name.includes('browser')) {
      return { icon: <Globe size={15} />, label: 'Browser Engine', color: 'var(--accent-indigo)' };
    }
    if (name.includes('desktop') || name.includes('inspect') || name.includes('ocr')) {
      return { icon: <Monitor size={15} />, label: 'Desktop Automation', color: 'var(--accent-violet)' };
    }
    if (name.includes('file') || name.includes('directory')) {
      return { icon: <FileText size={15} />, label: 'File System', color: 'var(--accent-emerald)' };
    }
    if (name.includes('command') || name.includes('terminal')) {
      return { icon: <Terminal size={15} />, label: 'System Terminal', color: 'var(--accent-amber)' };
    }
    if (name.includes('askuser')) {
      return { icon: <UserCheck size={15} />, label: 'Human Clarification', color: 'var(--accent-rose)' };
    }
    return { icon: <Terminal size={15} />, label: toolName, color: 'var(--text-secondary)' };
  };

  const meta = getToolMeta();

  const handleCopy = () => {
    navigator.clipboard.writeText(
      JSON.stringify({ tool: toolName, arguments: argumentsMap, observation }, null, 2)
    );
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div
      className="glass-panel"
      style={{
        width: '100%',
        marginBottom: '10px',
        borderLeft: `3px solid ${meta.color}`,
        overflow: 'hidden',
        transition: 'var(--transition-fast)',
      }}
    >
      {/* Header bar */}
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 14px',
          cursor: 'pointer',
          background: 'rgba(255, 255, 255, 0.015)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '26px',
              height: '26px',
              borderRadius: '6px',
              background: 'rgba(255, 255, 255, 0.06)',
              color: meta.color,
            }}
          >
            {meta.icon}
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-primary)' }}>
                {toolName}
              </span>
              <span
                style={{
                  fontSize: '10px',
                  fontWeight: 600,
                  padding: '2px 6px',
                  borderRadius: '4px',
                  background: 'rgba(255, 255, 255, 0.05)',
                  color: meta.color,
                }}
              >
                {meta.label}
              </span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {isExecuting && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--accent-amber)', fontSize: '11px' }}>
              <Clock size={13} className="animate-spin" />
              <span>Executing...</span>
            </div>
          )}

          {!isExecuting && successful !== undefined && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px', color: successful ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
              {successful ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
              <span>{successful ? 'Success' : 'Failed'}</span>
            </div>
          )}

          {timestamp && (
            <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
              {timestamp}
            </span>
          )}

          {expanded ? <ChevronUp size={14} color="var(--text-muted)" /> : <ChevronDown size={14} color="var(--text-muted)" />}
        </div>
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div
          style={{
            padding: '12px 14px',
            borderTop: '1px solid var(--border-subtle)',
            background: 'rgba(0, 0, 0, 0.25)',
          }}
        >
          {/* Tool Arguments */}
          <div style={{ marginBottom: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
              <span style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.5px' }}>
                PARAMETERS
              </span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleCopy();
                }}
                style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '10px', color: 'var(--text-muted)' }}
              >
                {copied ? <Check size={11} color="var(--accent-emerald)" /> : <Copy size={11} />}
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>
            <pre
              style={{
                fontSize: '11px',
                padding: '8px',
                borderRadius: '6px',
                background: 'rgba(0, 0, 0, 0.4)',
                color: 'var(--text-secondary)',
                overflowX: 'auto',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
              }}
            >
              {JSON.stringify(argumentsMap, null, 2)}
            </pre>
          </div>

          {/* Observation Output */}
          {observation && (
            <div>
              <span style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.5px', display: 'block', marginBottom: '4px' }}>
                OBSERVATION OUTPUT
              </span>
              <pre
                style={{
                  fontSize: '11px',
                  padding: '8px',
                  borderRadius: '6px',
                  background: 'rgba(0, 0, 0, 0.4)',
                  color: successful ? 'var(--text-primary)' : 'var(--accent-rose)',
                  maxHeight: '160px',
                  overflowY: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {observation}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
