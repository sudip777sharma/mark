import React, { useEffect, useRef } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { VoiceModeState } from '../../types/agent';

interface AudioOrbProps {
  size?: number;
  state?: VoiceModeState;
  interactive?: boolean;
  onClick?: () => void;
}

export const AudioOrbVisualizer: React.FC<AudioOrbProps> = ({
  size = 280,
  state,
  interactive = true,
  onClick,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const globalVoiceState = useAgentStore((s) => s.voiceState);
  const setAudioLevel = useAgentStore((s) => s.setAudioLevel);
  const activeState = state || globalVoiceState;

  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  // Initialize Web Audio API when in listening mode
  useEffect(() => {
    let isMounted = true;

    async function initAudio() {
      if (activeState === 'listening') {
        try {
          if (!streamRef.current) {
            streamRef.current = await navigator.mediaDevices.getUserMedia({ audio: true });
          }
          if (!audioContextRef.current) {
            audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
            analyserRef.current = audioContextRef.current.createAnalyser();
            analyserRef.current.fftSize = 64;
            const source = audioContextRef.current.createMediaStreamSource(streamRef.current);
            source.connect(analyserRef.current);
          }
        } catch (err) {
          console.warn('Microphone access not granted or unavailable:', err);
        }
      } else {
        if (streamRef.current) {
          streamRef.current.getTracks().forEach((track) => track.stop());
          streamRef.current = null;
        }
        if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
          audioContextRef.current.close();
          audioContextRef.current = null;
          analyserRef.current = null;
        }
      }
    }

    initAudio();

    return () => {
      isMounted = false;
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
      if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
        audioContextRef.current.close();
      }
    };
  }, [activeState]);

  // Canvas render loop
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let angle = 0;
    const dataArray = new Uint8Array(32);

    const render = () => {
      ctx.clearRect(0, 0, size, size);
      const centerX = size / 2;
      const centerY = size / 2;
      const baseRadius = size * 0.28;

      let currentVolume = 0;

      // Extract real audio frequency data if listening
      if (activeState === 'listening' && analyserRef.current) {
        analyserRef.current.getByteFrequencyData(dataArray);
        const sum = dataArray.reduce((acc, val) => acc + val, 0);
        currentVolume = sum / (dataArray.length * 255); // 0.0 to 1.0
        setAudioLevel(currentVolume);
      } else if (activeState === 'speaking') {
        // Synthetic speaking wave
        currentVolume = 0.25 + Math.sin(angle * 4) * 0.18 + Math.cos(angle * 7) * 0.12;
      } else if (activeState === 'thinking') {
        currentVolume = 0.15 + Math.sin(angle * 3) * 0.08;
      } else {
        // Idle gentle breathing
        currentVolume = 0.05 + Math.sin(angle) * 0.03;
      }

      angle += 0.04;

      // Dynamic color themes based on state
      let colorPrimary = 'rgba(6, 182, 212, 0.9)'; // Cyan
      let colorSecondary = 'rgba(99, 102, 241, 0.7)'; // Indigo
      let colorCore = 'rgba(14, 165, 233, 0.85)';

      if (activeState === 'thinking') {
        colorPrimary = 'rgba(168, 85, 247, 0.9)'; // Purple/Violet
        colorSecondary = 'rgba(59, 130, 246, 0.8)';
      } else if (activeState === 'speaking') {
        colorPrimary = 'rgba(16, 185, 129, 0.95)'; // Emerald
        colorSecondary = 'rgba(6, 182, 212, 0.8)';
      } else if (activeState === 'listening') {
        colorPrimary = 'rgba(6, 182, 212, 1)';
        colorSecondary = 'rgba(244, 63, 94, 0.7)'; // Active Rose-Cyan
      }

      // Outer radial glow
      const glowGradient = ctx.createRadialGradient(
        centerX,
        centerY,
        baseRadius * 0.4,
        centerX,
        centerY,
        baseRadius * (1.8 + currentVolume * 0.8)
      );
      glowGradient.addColorStop(0, colorCore);
      glowGradient.addColorStop(0.5, colorSecondary);
      glowGradient.addColorStop(1, 'rgba(0, 0, 0, 0)');

      ctx.fillStyle = glowGradient;
      ctx.beginPath();
      ctx.arc(centerX, centerY, baseRadius * (1.8 + currentVolume * 0.8), 0, Math.PI * 2);
      ctx.fill();

      // Reactive waveform spikes
      const spikes = 36;
      ctx.beginPath();
      for (let i = 0; i <= spikes; i++) {
        const theta = (i / spikes) * Math.PI * 2;
        const waveOffset =
          Math.sin(theta * 6 + angle * 2) * 8 +
          Math.cos(theta * 4 - angle) * 6;
        const r = baseRadius * (1 + currentVolume * 0.7) + waveOffset;
        const x = centerX + Math.cos(theta) * r;
        const y = centerY + Math.sin(theta) * r;

        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
      ctx.closePath();
      ctx.fillStyle = colorPrimary;
      ctx.globalAlpha = 0.85;
      ctx.fill();
      ctx.globalAlpha = 1.0;

      // Inner liquid core
      const coreGradient = ctx.createRadialGradient(
        centerX - baseRadius * 0.2,
        centerY - baseRadius * 0.2,
        0,
        centerX,
        centerY,
        baseRadius * 0.8
      );
      coreGradient.addColorStop(0, '#ffffff');
      coreGradient.addColorStop(0.3, colorPrimary);
      coreGradient.addColorStop(1, colorSecondary);

      ctx.beginPath();
      ctx.arc(centerX, centerY, baseRadius * (0.7 + currentVolume * 0.3), 0, Math.PI * 2);
      ctx.fillStyle = coreGradient;
      ctx.fill();

      // Rotating orbital ring in thinking/speaking mode
      if (activeState === 'thinking' || activeState === 'speaking') {
        ctx.save();
        ctx.translate(centerX, centerY);
        ctx.rotate(angle * 1.5);
        ctx.beginPath();
        ctx.ellipse(0, 0, baseRadius * 1.35, baseRadius * 0.55, Math.PI / 4, 0, Math.PI * 2);
        ctx.strokeStyle = colorPrimary;
        ctx.lineWidth = 2.5;
        ctx.shadowColor = colorPrimary;
        ctx.shadowBlur = 10;
        ctx.stroke();
        ctx.restore();
      }

      animationFrameRef.current = requestAnimationFrame(render);
    };

    render();

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [size, activeState]);

  return (
    <div
      onClick={interactive ? onClick : undefined}
      style={{
        width: size,
        height: size,
        position: 'relative',
        cursor: interactive ? 'pointer' : 'default',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
      title={interactive ? `Click to toggle voice state (Current: ${activeState})` : undefined}
    >
      <canvas
        ref={canvasRef}
        width={size}
        height={size}
        style={{
          width: size,
          height: size,
          display: 'block',
          userSelect: 'none',
        }}
      />
    </div>
  );
};
