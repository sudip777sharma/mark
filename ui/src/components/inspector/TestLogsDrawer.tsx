import React, { useState, useRef, useEffect } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { Terminal, Trash2, Copy, Check, ChevronDown, ChevronUp, X, Filter } from 'lucide-react';

export const TestLogsDrawer: React.FC = () => {
  const isTestMode = useAgentStore((s) => s.isTestMode);
  const setIsTestMode = useAgentStore((s) => s.setIsTestMode);
  const rawLogs = useAgentStore((s) => s.rawLogs);
  const clearRawLogs = useAgentStore((s) => s.clearRawLogs);

  const [filterText, setFilterText] = useState('');
  const [selectedLevel, setSelectedLevel] = useState<string>('ALL');
  const [copied, setCopied] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const logEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (autoScroll && logEndRef.current) {
      logEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [rawLogs, autoScroll]);

  if (!isTestMode) return null;

  const filteredLogs = rawLogs.filter((log) => {
    const matchesLevel = selectedLevel === 'ALL' || log.level === selectedLevel;
    const matchesText =
      !filterText ||
      log.message.toLowerCase().includes(filterText.toLowerCase()) ||
      (log.payload && JSON.stringify(log.payload).toLowerCase().includes(filterText.toLowerCase()));
    return matchesLevel && matchesText;
  });

  const handleCopyLogs = () => {
    navigator.clipboard.writeText(JSON.stringify(rawLogs, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const levelColors: Record<string, { bg: string; text: string }> = {
    INFO: { bg: 'rgba(6, 182, 212, 0.15)', text: 'var(--accent-cyan)' },
    WARN: { bg: 'rgba(245, 158, 11, 0.15)', text: 'var(--accent-amber)' },
    ERROR: { bg: 'rgba(244, 63, 94, 0.15)', text: 'var(--accent-rose)' },
    DEBUG: { bg: 'rgba(139, 92, 246, 0.15)', text: 'var(--accent-violet)' },
  };

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        height: isMinimized ? '40px' : '280px',
        backgroundColor: 'rgba(7, 10, 18, 0.98)',
        backdropFilter: 'blur(16px)',
        borderTop: '1px solid var(--border-focus)',
        boxShadow: '0 -4px 24px rgba(0, 0, 0, 0.6)',
        display: 'flex',
        flexDirection: 'column',
        zIndex: 500,
        transition: 'height 0.2s ease-in-out',
        fontFamily: 'var(--font-mono)',
      }}
    >
      {/* Top Bar / Controls */}
      <div
        style={{
          height: '40px',
          padding: '0 16px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: isMinimized ? 'none' : '1px solid var(--border-subtle)',
          backgroundColor: 'rgba(255, 255, 255, 0.02)',
          userSelect: 'none',
        }}
      >
        {/* Left: Title & Count */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              color: 'var(--accent-amber)',
              fontSize: '12px',
              fontWeight: 600,
            }}
          >
            <Terminal size={15} />
            <span>TEST MODE ACTIVE TELEMETRY</span>
          </div>
          <span
            style={{
              fontSize: '11px',
              padding: '1px 8px',
              borderRadius: '9999px',
              background: 'rgba(245, 158, 11, 0.15)',
              color: 'var(--accent-amber)',
              fontWeight: 600,
            }}
          >
            {rawLogs.length} events
          </span>
        </div>

        {/* Center: Search & Filter */}
        {!isMinimized && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                padding: '3px 8px',
                borderRadius: '6px',
                background: 'rgba(255, 255, 255, 0.04)',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <Filter size={12} color="var(--text-dim)" />
              <input
                type="text"
                placeholder="Filter logs..."
                value={filterText}
                onChange={(e) => setFilterText(e.target.value)}
                style={{
                  fontSize: '11px',
                  color: 'var(--text-primary)',
                  width: '130px',
                  background: 'transparent',
                }}
              />
            </div>

            {/* Level Selector */}
            <div style={{ display: 'flex', gap: '3px' }}>
              {['ALL', 'INFO', 'WARN', 'ERROR'].map((lvl) => (
                <button
                  key={lvl}
                  onClick={() => setSelectedLevel(lvl)}
                  style={{
                    padding: '2px 7px',
                    fontSize: '10px',
                    fontWeight: 600,
                    borderRadius: '4px',
                    background: selectedLevel === lvl ? 'var(--accent-cyan)' : 'transparent',
                    color: selectedLevel === lvl ? '#07090e' : 'var(--text-dim)',
                    border: '1px solid transparent',
                  }}
                >
                  {lvl}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Right: Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <button
            onClick={() => setAutoScroll(!autoScroll)}
            style={{
              fontSize: '11px',
              color: autoScroll ? 'var(--accent-cyan)' : 'var(--text-dim)',
              padding: '2px 6px',
            }}
            title="Auto-scroll"
          >
            {autoScroll ? '● Auto-scroll' : '○ Scroll lock'}
          </button>

          <button
            onClick={handleCopyLogs}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
              fontSize: '11px',
              color: copied ? 'var(--accent-emerald)' : 'var(--text-secondary)',
              padding: '4px 8px',
              borderRadius: '4px',
              background: 'rgba(255, 255, 255, 0.04)',
            }}
            title="Copy logs as JSON"
          >
            {copied ? <Check size={12} /> : <Copy size={12} />}
            <span>{copied ? 'Copied' : 'Copy'}</span>
          </button>

          <button
            onClick={clearRawLogs}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
              fontSize: '11px',
              color: 'var(--text-muted)',
              padding: '4px 8px',
              borderRadius: '4px',
              background: 'rgba(255, 255, 255, 0.04)',
            }}
            title="Clear all telemetry logs"
          >
            <Trash2 size={12} />
            <span>Clear</span>
          </button>

          <button
            onClick={() => setIsMinimized(!isMinimized)}
            style={{
              color: 'var(--text-dim)',
              padding: '4px',
            }}
            title={isMinimized ? 'Expand drawer' : 'Minimize drawer'}
          >
            {isMinimized ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
          </button>

          <button
            onClick={() => setIsTestMode(false)}
            style={{
              color: 'var(--text-dim)',
              padding: '4px',
            }}
            title="Close test mode"
          >
            <X size={15} />
          </button>
        </div>
      </div>

      {/* Log Feed */}
      {!isMinimized && (
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '8px 16px',
            fontSize: '11px',
            lineHeight: 1.5,
          }}
        >
          {filteredLogs.length === 0 ? (
            <div style={{ color: 'var(--text-dim)', textAlign: 'center', padding: '30px' }}>
              No telemetry events recorded yet. Assign a goal or interact with MARK to inspect full runtime execution.
            </div>
          ) : (
            filteredLogs.map((log) => {
              const lvl = levelColors[log.level] || levelColors.INFO;
              return (
                <div
                  key={log.id}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '10px',
                    padding: '3px 0',
                    borderBottom: '1px solid rgba(255, 255, 255, 0.02)',
                  }}
                >
                  <span style={{ color: 'var(--text-dim)', whiteSpace: 'nowrap', fontSize: '10px' }}>
                    {log.timestamp}
                  </span>
                  <span
                    style={{
                      padding: '1px 5px',
                      borderRadius: '3px',
                      background: lvl.bg,
                      color: lvl.text,
                      fontWeight: 600,
                      fontSize: '10px',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {log.level}
                  </span>
                  <span style={{ color: 'var(--text-primary)', wordBreak: 'break-all', flex: 1 }}>
                    {log.message}
                    {log.payload && (
                      <pre
                        style={{
                          margin: '4px 0 0 0',
                          padding: '6px 8px',
                          borderRadius: '4px',
                          background: 'rgba(0, 0, 0, 0.4)',
                          color: 'var(--accent-cyan)',
                          fontSize: '10px',
                          overflowX: 'auto',
                          whiteSpace: 'pre-wrap',
                        }}
                      >
                        {typeof log.payload === 'string' ? log.payload : JSON.stringify(log.payload, null, 2)}
                      </pre>
                    )}
                  </span>
                </div>
              );
            })
          )}
          <div ref={logEndRef} />
        </div>
      )}
    </div>
  );
};
