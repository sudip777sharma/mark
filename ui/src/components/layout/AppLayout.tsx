import React from 'react';
import { useAgentStore } from '../../store/agentStore';
import { useTaskPoller } from '../../hooks/useTaskPoller';
import { LiveVoiceHud } from '../voice/LiveVoiceHud';
import { TaskSidebar } from '../sidebar/TaskSidebar';
import { LiveAgentCanvas } from '../stream/LiveAgentCanvas';
import { GoalPromptBar } from '../chat/GoalPromptBar';
import { EnvironmentInspector } from '../inspector/EnvironmentInspector';
import { HumanInTheLoopModal } from '../stream/HumanInTheLoopModal';
import { TestLogsDrawer } from '../inspector/TestLogsDrawer';
import { Mic, LayoutDashboard, Sparkles, FlaskConical } from 'lucide-react';

export const AppLayout: React.FC = () => {
  useTaskPoller();
  const viewMode = useAgentStore((s) => s.viewMode);
  const setViewMode = useAgentStore((s) => s.setViewMode);
  const isTestMode = useAgentStore((s) => s.isTestMode);
  const setIsTestMode = useAgentStore((s) => s.setIsTestMode);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        width: '100vw',
        overflow: 'hidden',
        background: 'var(--bg-app)',
      }}
    >
      {/* Universal Top Navigation Header */}
      <header
        style={{
          height: '52px',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 20px',
          background: 'rgba(9, 13, 22, 0.98)',
          zIndex: 100,
        }}
      >
        {/* Left: Brand / Title */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              width: '24px',
              height: '24px',
              borderRadius: '6px',
              background: 'linear-gradient(135deg, var(--accent-cyan), var(--accent-indigo))',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Sparkles size={14} color="#07090e" />
          </div>
          <span style={{ fontSize: '14px', fontWeight: 700, letterSpacing: '0.5px' }}>MARK</span>
          <span
            style={{
              fontSize: '11px',
              padding: '2px 8px',
              borderRadius: '9999px',
              background: 'rgba(6, 182, 212, 0.12)',
              color: 'var(--accent-cyan)',
              fontWeight: 600,
            }}
          >
            LOCAL-FIRST AGENT
          </span>
        </div>

        {/* Center: Dual Mode Switcher */}
        <div
          className="glass-panel-subtle"
          style={{
            display: 'flex',
            alignItems: 'center',
            padding: '3px',
            borderRadius: '9999px',
            gap: '4px',
          }}
        >
          <button
            onClick={() => setViewMode('voice')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 14px',
              borderRadius: '9999px',
              fontSize: '12px',
              fontWeight: 600,
              color: viewMode === 'voice' ? '#07090e' : 'var(--text-secondary)',
              background: viewMode === 'voice' ? 'var(--accent-cyan)' : 'transparent',
              boxShadow: viewMode === 'voice' ? 'var(--shadow-glow-cyan)' : 'none',
              transition: 'var(--transition-fast)',
            }}
          >
            <Mic size={14} />
            <span>Live Voice Mode</span>
          </button>

          <button
            onClick={() => setViewMode('canvas')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 14px',
              borderRadius: '9999px',
              fontSize: '12px',
              fontWeight: 600,
              color: viewMode === 'canvas' ? '#07090e' : 'var(--text-secondary)',
              background: viewMode === 'canvas' ? 'var(--accent-cyan)' : 'transparent',
              boxShadow: viewMode === 'canvas' ? 'var(--shadow-glow-cyan)' : 'none',
              transition: 'var(--transition-fast)',
            }}
          >
            <LayoutDashboard size={14} />
            <span>Detailed Canvas</span>
          </button>
        </div>

        {/* Right: Test Mode Toggle & Runtime Status */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {/* Top Right "Test" Toggle Button */}
          <button
            onClick={() => setIsTestMode(!isTestMode)}
            title="Toggle full telemetry and debug execution logs"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '5px 12px',
              borderRadius: '9999px',
              fontSize: '11px',
              fontWeight: 600,
              background: isTestMode ? 'rgba(245, 158, 11, 0.18)' : 'rgba(255, 255, 255, 0.04)',
              color: isTestMode ? 'var(--accent-amber)' : 'var(--text-secondary)',
              border: isTestMode ? '1px solid var(--accent-amber)' : '1px solid var(--border-subtle)',
              cursor: 'pointer',
              boxShadow: isTestMode ? '0 0 12px rgba(245, 158, 11, 0.35)' : 'none',
              transition: 'var(--transition-fast)',
              userSelect: 'none',
            }}
          >
            <FlaskConical size={13} />
            <span>Test</span>
            <div
              style={{
                width: '7px',
                height: '7px',
                borderRadius: '50%',
                backgroundColor: isTestMode ? 'var(--accent-amber)' : 'var(--text-dim)',
                boxShadow: isTestMode ? '0 0 6px var(--accent-amber)' : 'none',
              }}
            />
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '11px', color: 'var(--text-dim)' }}>
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: 'var(--accent-emerald)' }} />
            <span>Spring Boot (8081)</span>
          </div>
        </div>
      </header>

      {/* Main View Area */}
      <main style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>
        {viewMode === 'voice' ? (
          <LiveVoiceHud />
        ) : (
          <div style={{ flex: 1, display: 'flex', height: '100%', width: '100%', overflow: 'hidden' }}>
            <TaskSidebar />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
              <LiveAgentCanvas />
              <GoalPromptBar />
            </div>
            <EnvironmentInspector />
          </div>
        )}
      </main>

      {/* Global Human-In-The-Loop Approval Modal */}
      <HumanInTheLoopModal />

      {/* Test Mode Unfiltered Runtime Telemetry Drawer */}
      <TestLogsDrawer />
    </div>
  );
};
