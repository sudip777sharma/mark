import React, { useState, useRef } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { AudioOrbVisualizer } from './AudioOrbVisualizer';
import { Mic, MicOff, Square, LayoutDashboard, Sparkles, MessageSquare } from 'lucide-react';
import { taskApi } from '../../api/taskClient';
import { speechService } from '../../utils/speech';

export const LiveVoiceHud: React.FC = () => {
  const voiceState = useAgentStore((s) => s.voiceState);
  const setVoiceState = useAgentStore((s) => s.setVoiceState);
  const setViewMode = useAgentStore((s) => s.setViewMode);
  const voiceTranscript = useAgentStore((s) => s.voiceTranscript);
  const setVoiceTranscript = useAgentStore((s) => s.setVoiceTranscript);
  const agentSpeechText = useAgentStore((s) => s.agentSpeechText);
  const setAgentSpeechText = useAgentStore((s) => s.setAgentSpeechText);
  const activeTask = useAgentStore((s) => s.activeTask);
  const setActiveTask = useAgentStore((s) => s.setActiveTask);
  const addStreamLog = useAgentStore((s) => s.addStreamLog);
  const addRawLog = useAgentStore((s) => s.addRawLog);

  const [inputGoal, setInputGoal] = useState('');
  const recognizerRef = useRef<any>(null);

  const toggleVoiceInteraction = () => {
    if (voiceState === 'idle') {
      speechService.stopSpeaking();
      setVoiceState('listening');
      setVoiceTranscript('Listening to your microphone...');

      const recognizer = speechService.createRecognizer(
        (transcript, isFinal) => {
          setVoiceTranscript(transcript);
          if (isFinal && transcript.trim()) {
            handleQuickPrompt(transcript.trim());
          }
        },
        () => {
          if (useAgentStore.getState().voiceState === 'listening') {
            setVoiceState('idle');
          }
        },
        (err) => {
          console.warn('Speech recognition notice:', err);
          if (useAgentStore.getState().voiceState === 'listening') {
            setVoiceState('idle');
          }
        }
      );

      if (recognizer) {
        recognizerRef.current = recognizer;
        try {
          recognizer.start();
          addRawLog({ level: 'INFO', message: 'Microphone recognition activated.' });
        } catch (e) {
          console.warn('Recognition start exception:', e);
        }
      } else {
        setVoiceTranscript('Microphone speech recognition is not supported in this browser. You can click quick prompts or type below.');
      }
    } else if (voiceState === 'listening') {
      if (recognizerRef.current) {
        try { recognizerRef.current.stop(); } catch {}
      }
      setVoiceState('idle');
    } else if (voiceState === 'speaking') {
      speechService.stopSpeaking();
      setVoiceState('idle');
    } else {
      setVoiceState('idle');
    }
  };

  const handleQuickPrompt = async (text: string) => {
    if (recognizerRef.current) {
      try { recognizerRef.current.stop(); } catch {}
    }

    setVoiceTranscript(`"${text}"`);
    setVoiceState('thinking');
    setAgentSpeechText(`Processing...`);

    addRawLog({ level: 'INFO', message: `Input submitted via Voice HUD: ${text}` });

    try {
      const interaction = await taskApi.interact(text);
      addRawLog({ level: 'INFO', message: `Intent routed: ${interaction.intent} (isTask=${interaction.isTask})` });

      if (interaction.isTask && interaction.taskId) {
        setVoiceState('speaking');
        const announcement = interaction.reply || `Task identified. Launching autonomous execution.`;
        setAgentSpeechText(announcement);
        speechService.speak(announcement);

        const task = await taskApi.getTask(interaction.taskId);
        if (task) setActiveTask(task);

        addStreamLog({
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          type: 'task:created',
          title: 'Autonomous Task Dispatched',
          detail: text,
        });
      } else {
        // Conversational greeting or factual knowledge answer
        setVoiceState('speaking');
        setAgentSpeechText(interaction.reply);
        speechService.speak(interaction.reply);

        addStreamLog({
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          type: 'agent:thinking',
          title: interaction.intent === 'CHAT' ? 'Conversational Reply' : 'Direct Answer',
          detail: interaction.reply,
        });
      }
    } catch (e: any) {
      console.error(e);
      setVoiceState('speaking');
      setAgentSpeechText(`Encountered error connecting to MARK.`);
      speechService.speak('Encountered an error connecting to MARK.');
    }
  };

  const stateLabels: Record<string, { label: string; color: string; bg: string }> = {
    idle: { label: 'STANDBY', color: 'var(--text-secondary)', bg: 'rgba(255, 255, 255, 0.05)' },
    listening: { label: 'LISTENING', color: 'var(--accent-cyan)', bg: 'rgba(6, 182, 212, 0.15)' },
    thinking: { label: 'REASONING & PLANNING', color: 'var(--accent-violet)', bg: 'rgba(139, 92, 246, 0.15)' },
    speaking: { label: 'SPEAKING', color: 'var(--accent-emerald)', bg: 'rgba(16, 185, 129, 0.15)' },
  };

  const currentBadge = stateLabels[voiceState] || stateLabels.idle;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100%',
        width: '100%',
        padding: '32px',
        position: 'relative',
        background: 'radial-gradient(circle at 50% 45%, rgba(14, 28, 54, 0.7) 0%, var(--bg-app) 70%)',
      }}
    >
      {/* Top Status Pill */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '6px 16px',
          borderRadius: '9999px',
          background: currentBadge.bg,
          border: `1px solid ${currentBadge.color}`,
          color: currentBadge.color,
          fontSize: '11px',
          fontWeight: 600,
          letterSpacing: '1px',
          marginBottom: '28px',
          transition: 'var(--transition-normal)',
        }}
      >
        <div
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: currentBadge.color,
            boxShadow: `0 0 10px ${currentBadge.color}`,
            animation: voiceState !== 'idle' ? 'pulseGlow 1.5s infinite' : 'none',
          }}
        />
        {currentBadge.label}
      </div>

      {/* Central Living Orb */}
      <div style={{ marginBottom: '24px' }}>
        <AudioOrbVisualizer size={320} onClick={toggleVoiceInteraction} />
      </div>

      {/* Voice Subtitle / Agent Speech Feedback */}
      <div
        style={{
          maxWidth: '680px',
          width: '100%',
          textAlign: 'center',
          minHeight: '72px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: '32px',
        }}
      >
        {voiceTranscript && (
          <p
            style={{
              fontSize: '18px',
              fontWeight: 500,
              color: 'var(--text-primary)',
              lineHeight: 1.5,
              marginBottom: '6px',
            }}
          >
            {voiceTranscript}
          </p>
        )}
        {agentSpeechText && (
          <p
            style={{
              fontSize: '15px',
              fontFamily: 'var(--font-mono)',
              color: 'var(--accent-cyan)',
              opacity: 0.9,
            }}
          >
            {agentSpeechText}
          </p>
        )}
        {!voiceTranscript && !agentSpeechText && (
          <p style={{ color: 'var(--text-muted)', fontSize: '14px' }}>
            Click the living orb or tap the microphone to begin talking with MARK
          </p>
        )}
      </div>

      {/* Primary Voice Controls */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '16px',
          marginBottom: '36px',
        }}
      >
        <button
          onClick={toggleVoiceInteraction}
          className="glass-panel"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '12px 24px',
            borderRadius: '9999px',
            background: voiceState === 'listening' ? 'var(--accent-rose)' : 'var(--accent-cyan)',
            color: '#07090e',
            fontWeight: 600,
            fontSize: '14px',
            transition: 'var(--transition-fast)',
            boxShadow: 'var(--shadow-glow-cyan)',
          }}
        >
          {voiceState === 'listening' ? <MicOff size={18} /> : <Mic size={18} />}
          {voiceState === 'listening' ? 'Stop Listening' : 'Talk with MARK'}
        </button>

        {activeTask && (
          <button
            onClick={() => setVoiceState('idle')}
            className="glass-panel"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '12px 20px',
              borderRadius: '9999px',
              color: 'var(--accent-amber)',
              fontSize: '13px',
              fontWeight: 500,
            }}
          >
            <Square size={16} /> Interrupt Action
          </button>
        )}

        <button
          onClick={() => setViewMode('canvas')}
          className="glass-panel"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '12px 20px',
            borderRadius: '9999px',
            color: 'var(--text-secondary)',
            fontSize: '13px',
            fontWeight: 500,
          }}
        >
          <LayoutDashboard size={16} /> Switch to Canvas
        </button>
      </div>

      {/* Quick Prompt Chips */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '8px',
          justifyContent: 'center',
          maxWidth: '720px',
        }}
      >
        <button
          onClick={() => handleQuickPrompt('Calculate (15 * 8) + 42')}
          className="glass-panel-subtle"
          style={{
            padding: '8px 14px',
            fontSize: '12px',
            color: 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <Sparkles size={13} color="var(--accent-cyan)" /> "Calculate (15 * 8) + 42"
        </button>
        <button
          onClick={() => handleQuickPrompt('Inspect the active desktop window')}
          className="glass-panel-subtle"
          style={{
            padding: '8px 14px',
            fontSize: '12px',
            color: 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <Sparkles size={13} color="var(--accent-cyan)" /> "Inspect active window"
        </button>
        <button
          onClick={() => handleQuickPrompt('Check browser status & read page content')}
          className="glass-panel-subtle"
          style={{
            padding: '8px 14px',
            fontSize: '12px',
            color: 'var(--text-secondary)',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <Sparkles size={13} color="var(--accent-cyan)" /> "Read browser page"
        </button>
      </div>
    </div>
  );
};
