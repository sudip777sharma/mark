import React, { useState } from 'react';
import { LlmInteractionLog } from '../../store/agentStore';
import { Code, Copy, Check, ChevronDown, ChevronRight, Terminal, Sparkles, MessageSquare, Brain } from 'lucide-react';

interface InjectedPromptInspectorProps {
  logs: LlmInteractionLog[];
  onClose?: () => void;
}

export const InjectedPromptInspector: React.FC<InjectedPromptInspectorProps> = ({ logs, onClose }) => {
  const [selectedIndex, setSelectedIndex] = useState<number>(() => Math.max(0, logs.length - 1));
  const [copied, setCopied] = useState(false);
  const [expandedSections, setExpandedSections] = useState<{ system: boolean; history: boolean; response: boolean }>({
    system: false,
    history: false,
    response: true,
  });

  if (!logs || logs.length === 0) {
    return (
      <div
        className="glass-panel"
        style={{
          padding: '16px 20px',
          margin: '12px 0',
          borderRadius: '8px',
          border: '1px solid var(--border-subtle)',
          color: 'var(--text-muted)',
          fontSize: '13px',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
        }}
      >
        <Terminal size={15} color="var(--accent-cyan)" />
        <span>No LLM prompt interactions logged for this mission yet.</span>
      </div>
    );
  }

  const activeIndex = Math.min(selectedIndex, logs.length - 1);
  const activeLog = logs[activeIndex] || logs[0];

  const handleCopyPrompt = async () => {
    try {
      await navigator.clipboard.writeText(activeLog.prompt || '');
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy prompt:', err);
    }
  };

  const toggleSection = (section: 'system' | 'history' | 'response') => {
    setExpandedSections((prev) => ({ ...prev, [section]: !prev[section] }));
  };

  // Helper to extract sections from the combined prompt
  const parsePromptSections = (rawPrompt: string) => {
    let system = '';
    let history = '';
    let activeDirective = '';

    const sysIdx = rawPrompt.indexOf('=== SYSTEM PROMPT ===');
    const histIdx = rawPrompt.indexOf('=== CONVERSATION HISTORY ===');
    const activeIdx = rawPrompt.indexOf('=== ACTIVE INJECTED USER PROMPT ===');

    if (sysIdx !== -1) {
      const sysEnd = histIdx !== -1 ? histIdx : activeIdx !== -1 ? activeIdx : rawPrompt.length;
      system = rawPrompt.substring(sysIdx + '=== SYSTEM PROMPT ==='.length, sysEnd).trim();
    }

    if (histIdx !== -1) {
      const histEnd = activeIdx !== -1 ? activeIdx : rawPrompt.length;
      history = rawPrompt.substring(histIdx + '=== CONVERSATION HISTORY ==='.length, histEnd).trim();
    }

    if (activeIdx !== -1) {
      activeDirective = rawPrompt.substring(activeIdx + '=== ACTIVE INJECTED USER PROMPT ==='.length).trim();
    } else if (sysIdx === -1 && histIdx === -1) {
      activeDirective = rawPrompt.trim();
    }

    return { system, history, activeDirective };
  };

  const { system, history, activeDirective } = parsePromptSections(activeLog.prompt || '');

  return (
    <div
      className="glass-panel"
      style={{
        margin: '12px 0',
        padding: '16px 20px',
        borderRadius: '8px',
        border: '1px solid var(--border)',
        background: 'rgba(15, 23, 42, 0.75)',
        boxShadow: '0 4px 20px rgba(0, 0, 0, 0.3)',
      }}
    >
      {/* Header & Phase Switcher */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '10px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Code size={16} color="var(--accent-cyan)" />
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-primary)', letterSpacing: '0.3px' }}>
            Injected LLM Prompts & Context
          </span>
          <span style={{ fontSize: '11px', color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
            ({logs.length} {logs.length === 1 ? 'call' : 'calls'})
          </span>
        </div>

        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <button
            onClick={handleCopyPrompt}
            className="glass-button"
            style={{
              fontSize: '11px',
              padding: '4px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              color: copied ? 'var(--accent-emerald)' : 'var(--text-secondary)',
            }}
          >
            {copied ? <Check size={13} color="var(--accent-emerald)" /> : <Copy size={13} />}
            <span>{copied ? 'Copied Full Prompt!' : 'Copy Full Prompt'}</span>
          </button>
          {onClose && (
            <button
              onClick={onClose}
              className="glass-button"
              style={{ fontSize: '11px', padding: '4px 8px', color: 'var(--text-muted)' }}
            >
              ✕ Close
            </button>
          )}
        </div>
      </div>

      {/* Phase Navigation Pills */}
      <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', paddingBottom: '8px', marginBottom: '14px' }}>
        {logs.map((logItem, idx) => {
          const isSelected = idx === activeIndex;
          const label = logItem.phase || `Call #${idx + 1}`;
          return (
            <button
              key={idx}
              onClick={() => setSelectedIndex(idx)}
              style={{
                fontSize: '11px',
                padding: '4px 10px',
                borderRadius: '6px',
                border: isSelected ? '1px solid var(--accent-cyan)' : '1px solid var(--border-subtle)',
                background: isSelected ? 'rgba(6, 182, 212, 0.15)' : 'rgba(255, 255, 255, 0.03)',
                color: isSelected ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                fontWeight: isSelected ? 600 : 400,
                transition: 'var(--transition-fast)',
              }}
            >
              <span>{label}</span>
              <span style={{ fontSize: '10px', opacity: 0.6 }}>#{idx + 1}</span>
            </button>
          );
        })}
      </div>

      {/* Section 1: Active Injected Directive (Highlighted) */}
      {activeDirective && (
        <div
          style={{
            marginBottom: '12px',
            padding: '12px 14px',
            borderRadius: '6px',
            background: 'rgba(6, 182, 212, 0.08)',
            border: '1px solid rgba(6, 182, 212, 0.3)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px' }}>
            <Sparkles size={14} color="var(--accent-cyan)" />
            <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--accent-cyan)' }}>
              Active Injected Directive / User Prompt
            </span>
          </div>
          <pre
            style={{
              margin: 0,
              fontSize: '12px',
              fontFamily: 'var(--font-mono)',
              color: 'var(--text-primary)',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              lineHeight: 1.5,
            }}
          >
            {activeDirective}
          </pre>
        </div>
      )}

      {/* Section 2: System Prompt & Injected Tool Rules (Collapsible) */}
      {system && (
        <div
          style={{
            marginBottom: '8px',
            borderRadius: '6px',
            border: '1px solid var(--border-subtle)',
            background: 'rgba(255, 255, 255, 0.02)',
            overflow: 'hidden',
          }}
        >
          <button
            onClick={() => toggleSection('system')}
            style={{
              width: '100%',
              padding: '10px 14px',
              background: 'transparent',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              color: 'var(--text-secondary)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Terminal size={14} color="var(--text-muted)" />
              <span style={{ fontSize: '12px', fontWeight: 500 }}>System Prompt & Tool Definitions</span>
              <span style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                ({system.length} chars)
              </span>
            </div>
            {expandedSections.system ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
          {expandedSections.system && (
            <div style={{ padding: '12px 14px', borderTop: '1px solid var(--border-subtle)', background: 'rgba(0, 0, 0, 0.3)' }}>
              <pre
                style={{
                  margin: 0,
                  fontSize: '11px',
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--text-secondary)',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  maxHeight: '260px',
                  overflowY: 'auto',
                  lineHeight: 1.5,
                }}
              >
                {system}
              </pre>
            </div>
          )}
        </div>
      )}

      {/* Section 3: Conversation History / Multi-turn Context (Collapsible) */}
      {history && (
        <div
          style={{
            marginBottom: '8px',
            borderRadius: '6px',
            border: '1px solid var(--border-subtle)',
            background: 'rgba(255, 255, 255, 0.02)',
            overflow: 'hidden',
          }}
        >
          <button
            onClick={() => toggleSection('history')}
            style={{
              width: '100%',
              padding: '10px 14px',
              background: 'transparent',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              color: 'var(--text-secondary)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <MessageSquare size={14} color="var(--text-muted)" />
              <span style={{ fontSize: '12px', fontWeight: 500 }}>Multi-Turn Context & Step History</span>
              <span style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
                ({history.length} chars)
              </span>
            </div>
            {expandedSections.history ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
          {expandedSections.history && (
            <div style={{ padding: '12px 14px', borderTop: '1px solid var(--border-subtle)', background: 'rgba(0, 0, 0, 0.3)' }}>
              <pre
                style={{
                  margin: 0,
                  fontSize: '11px',
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--text-secondary)',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  maxHeight: '260px',
                  overflowY: 'auto',
                  lineHeight: 1.5,
                }}
              >
                {history}
              </pre>
            </div>
          )}
        </div>
      )}

      {/* Section 4: Model Output & Generated Tool Calls (Collapsible) */}
      {(activeLog.thinking || activeLog.response) && (
        <div
          style={{
            borderRadius: '6px',
            border: '1px solid var(--border-subtle)',
            background: 'rgba(255, 255, 255, 0.02)',
            overflow: 'hidden',
          }}
        >
          <button
            onClick={() => toggleSection('response')}
            style={{
              width: '100%',
              padding: '10px 14px',
              background: 'transparent',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              color: 'var(--text-secondary)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Brain size={14} color="var(--accent-violet)" />
              <span style={{ fontSize: '12px', fontWeight: 500 }}>Model Output & Tool Calls</span>
            </div>
            {expandedSections.response ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
          {expandedSections.response && (
            <div style={{ padding: '12px 14px', borderTop: '1px solid var(--border-subtle)', background: 'rgba(0, 0, 0, 0.3)' }}>
              {activeLog.thinking && (
                <div style={{ marginBottom: '8px' }}>
                  <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--accent-violet)' }}>Thought:</span>
                  <pre style={{ margin: '4px 0 0 0', fontSize: '11px', fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap' }}>
                    {activeLog.thinking}
                  </pre>
                </div>
              )}
              {activeLog.response && (
                <div>
                  <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--accent-emerald)' }}>Response / Action:</span>
                  <pre style={{ margin: '4px 0 0 0', fontSize: '11px', fontFamily: 'var(--font-mono)', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
                    {activeLog.response}
                  </pre>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
