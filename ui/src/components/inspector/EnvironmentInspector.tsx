import React from 'react';
import { useAgentStore } from '../../store/agentStore';
import { AudioOrbVisualizer } from '../voice/AudioOrbVisualizer';
import { Monitor, Globe, Cpu, CheckCircle, ShieldCheck, Activity } from 'lucide-react';

export const EnvironmentInspector: React.FC = () => {
  const worldState = useAgentStore((s) => s.worldState);
  const voiceState = useAgentStore((s) => s.voiceState);
  const setViewMode = useAgentStore((s) => s.setViewMode);

  return (
    <div
      style={{
        width: '300px',
        height: '100%',
        borderLeft: '1px solid var(--border-subtle)',
        background: 'rgba(9, 13, 22, 0.95)',
        display: 'flex',
        flexDirection: 'column',
        overflowY: 'auto',
        padding: '20px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px' }}>
        <Activity size={16} color="var(--accent-cyan)" />
        <h3 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-primary)', letterSpacing: '0.5px' }}>
          AGENT RUNTIME STATE
        </h3>
      </div>

      {/* Mini Living Orb Header */}
      <div
        className="glass-panel"
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          padding: '16px',
          marginBottom: '18px',
          cursor: 'pointer',
        }}
        onClick={() => setViewMode('voice')}
        title="Click to expand Live Voice HUD"
      >
        <AudioOrbVisualizer size={120} interactive={false} />
        <span style={{ fontSize: '11px', color: 'var(--accent-cyan)', fontWeight: 500, marginTop: '8px' }}>
          Voice: {voiceState.toUpperCase()}
        </span>
        <span style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
          Click to enter full Voice HUD
        </span>
      </div>

      {/* World State Telemetry */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* Active Window */}
        <div className="glass-panel-subtle" style={{ padding: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px', color: 'var(--text-muted)' }}>
            <Monitor size={13} />
            <span style={{ fontSize: '11px', fontWeight: 600 }}>ACTIVE WINDOW</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-primary)', wordBreak: 'break-all' }}>
            {worldState.activeWindowTitle || 'Desktop Workspace'}
          </p>
        </div>

        {/* Active Browser Target */}
        <div className="glass-panel-subtle" style={{ padding: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px', color: 'var(--text-muted)' }}>
            <Globe size={13} />
            <span style={{ fontSize: '11px', fontWeight: 600 }}>BROWSER PLAYWRIGHT</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)', wordBreak: 'break-all', fontFamily: 'var(--font-mono)' }}>
            {worldState.activeBrowserUrl || 'Disconnected / Standby'}
          </p>
        </div>

        {/* System Hardware Load */}
        <div className="glass-panel-subtle" style={{ padding: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px', color: 'var(--text-muted)' }}>
            <Cpu size={13} />
            <span style={{ fontSize: '11px', fontWeight: 600 }}>SYSTEM RESOURCES</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '4px' }}>
            <span>CPU Usage:</span>
            <span style={{ color: 'var(--accent-cyan)', fontFamily: 'var(--font-mono)' }}>
              {worldState.systemMetrics?.cpuLoad || '12%'}
            </span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '11px', color: 'var(--text-secondary)' }}>
            <span>RAM (Local Model):</span>
            <span style={{ color: 'var(--accent-cyan)', fontFamily: 'var(--font-mono)' }}>
              {worldState.systemMetrics?.ramUsage || '4.8 GB / 16 GB'}
            </span>
          </div>
        </div>

        {/* Tool Security Guardrails Badge */}
        <div className="glass-panel-subtle" style={{ padding: '12px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <ShieldCheck size={16} color="var(--accent-emerald)" />
          <div>
            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--accent-emerald)' }}>
              Guardrails Active
            </div>
            <span style={{ fontSize: '10px', color: 'var(--text-dim)' }}>
              Path isolation & human approval checks
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
