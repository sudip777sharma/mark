import { useEffect, useRef } from 'react';
import { useAgentStore } from '../store/agentStore';
import { taskApi } from '../api/taskClient';
import { AgentStep } from '../types/agent';
import { speechService } from '../utils/speech';

export function useTaskPoller() {
  const activeTask = useAgentStore((s) => s.activeTask);
  const setActiveTask = useAgentStore((s) => s.setActiveTask);
  const setTasks = useAgentStore((s) => s.setTasks);
  const addStreamLog = useAgentStore((s) => s.addStreamLog);
  const setActivePlan = useAgentStore((s) => s.setActivePlan);
  const setIsStreaming = useAgentStore((s) => s.setIsStreaming);
  const appendLlmThoughtChunk = useAgentStore((s) => s.appendLlmThoughtChunk);
  const clearLlmThoughtStream = useAgentStore((s) => s.clearLlmThoughtStream);
  const setLlmThoughtStream = useAgentStore((s) => s.setLlmThoughtStream);
  const setVoiceState = useAgentStore((s) => s.setVoiceState);
  const setAgentSpeechText = useAgentStore((s) => s.setAgentSpeechText);
  const setWaitingUserInput = useAgentStore((s) => s.setWaitingUserInput);
  const addRawLog = useAgentStore((s) => s.addRawLog);

  const seenStepsCountRef = useRef<number>(0);
  const lastTaskIdRef = useRef<string | null>(null);
  const lastStatusRef = useRef<string | null>(null);
  const lastActionRef = useRef<string | null>(null);

  useEffect(() => {
    const taskId = activeTask?.taskId || activeTask?.id;
    if (!taskId) return;

    if (lastTaskIdRef.current !== taskId) {
      lastTaskIdRef.current = taskId;
      clearLlmThoughtStream();
      lastStatusRef.current = activeTask?.status || null;
      lastActionRef.current = activeTask?.currentAction || null;
      seenStepsCountRef.current = activeTask?.steps?.length || 0;
      addRawLog({
        level: 'INFO',
        message: `Task activated: ${taskId} (${activeTask.goal})`,
        payload: { status: activeTask.status, goal: activeTask.goal },
      });
    }

    const loadPersistedThoughts = (tId: string) => {
      taskApi.getLlmLogs(tId).then((logs) => {
        if (logs && logs.length > 0) {
          const formatted = logs
            .map((l) => {
              const header = l.phase ? `=== [${l.phase}] ===` : '=== [LLM Interaction] ===';
              const thought = l.thinking && l.thinking.trim() ? l.thinking.trim() : null;
              const resp = l.response && l.response.trim() ? l.response.trim() : null;
              if (thought && resp && thought !== resp) {
                return `${header}\nThought:\n${thought}\n\nResponse:\n${resp}`;
              }
              return `${header}\n${thought || resp || '(No content)'}`;
            })
            .join('\n\n---\n\n');
          if (formatted.trim()) {
            setLlmThoughtStream(formatted);
          }
        }
      }).catch((e) => console.warn('Failed to load LLM logs:', e));
    };

    if (activeTask.status === 'NEEDS_INPUT') {
      setIsStreaming(false);
      loadPersistedThoughts(taskId);
      setWaitingUserInput({
        taskId,
        prompt: activeTask.currentAction || 'MARK requires your guidance to proceed.',
      });
    }

    const isRunning = activeTask.status === 'CREATED' || 
                      activeTask.status === 'PLANNING' || 
                      activeTask.status === 'EXECUTING' || 
                      activeTask.status === 'QUOTA_EXHAUSTED' || 
                      activeTask.status === 'COOLDOWN' ||
                      activeTask.status === 'NEEDS_INPUT';
    if (!isRunning) {
      setIsStreaming(false);
      loadPersistedThoughts(taskId);
      return;
    }

    setIsStreaming(true);

    const eventSource = new EventSource('/api/tasks/stream');

    eventSource.addEventListener('thought_chunk', (event) => {
      if (event.data) {
        appendLlmThoughtChunk(event.data);
      }
    });

    eventSource.addEventListener('task_update', (event) => {
      try {
        const freshTask = JSON.parse(event.data);
        
        // We broadcast all updates, but only process if it's our active task
        if (freshTask.id !== taskId && freshTask.taskId !== taskId) {
           return;
        }

        addRawLog({
          level: 'DEBUG',
          message: `SSE update received: ${freshTask.status} | Steps: ${freshTask.steps?.length || 0}`,
        });

        // Only fetch the full list if the status transitions (e.g. CREATED -> EXECUTING or EXECUTING -> COMPLETED)
        if (lastStatusRef.current !== freshTask.status) {
            lastStatusRef.current = freshTask.status;
            taskApi.listTasks().then((list) => {
              if (list.length > 0) setTasks(list);
            });
        }
        
        if (lastActionRef.current !== freshTask.currentAction) {
            lastActionRef.current = freshTask.currentAction;
        }

        if (freshTask.plan && freshTask.plan.length > 0) {
          setActivePlan(freshTask.plan);
        }

        const currentSteps = freshTask.steps || [];
        if (currentSteps.length > seenStepsCountRef.current) {
          for (let i = seenStepsCountRef.current; i < currentSteps.length; i++) {
            const step: AgentStep = currentSteps[i];

            addRawLog({
              level: step.outcome?.toLowerCase().includes('fail') ? 'WARN' : 'INFO',
              message: `Step ${step.stepNumber}: ${step.toolName || 'Reasoning'} - ${step.outcome || step.description}`,
              payload: step,
            });

            const toolLower = (step.toolName || '').toLowerCase().replace(/_/g, '');
            if (toolLower.includes('askuser')) {
              setWaitingUserInput({
                taskId,
                prompt: step.description || freshTask.currentAction || 'MARK requires your guidance to proceed.',
              });
              speechService.speak('MARK requires your guidance to proceed.');
            }

            let parsedArgs: Record<string, unknown> = {};
            if (step.toolArguments) {
              try {
                parsedArgs = JSON.parse(step.toolArguments);
              } catch (_e) {
                parsedArgs = { raw: step.toolArguments };
              }
            }

            let stepTime = new Date().toLocaleTimeString();
            if (step.timestamp) {
              try {
                const d = new Date(step.timestamp);
                if (!isNaN(d.getTime())) {
                  stepTime = d.toLocaleTimeString();
                }
              } catch(e) {}
            }

            if (step.description) {
              addStreamLog({
                id: `${taskId}-step-${step.stepNumber}-${Date.now()}-thought`,
                timestamp: stepTime,
                type: 'agent:thinking',
                title: `LLM Reasoning (Step ${step.stepNumber})`,
                detail: step.description,
                toolName: step.toolName,
                rawPayload: parsedArgs,
                successful: true,
              });
            }

            if (step.outcome || step.toolName) {
              addStreamLog({
                id: `${taskId}-step-${step.stepNumber}-${Date.now()}-outcome`,
                timestamp: stepTime,
                type: 'tool:result',
                title: step.toolName ? `Tool: ${step.toolName}` : `Step ${step.stepNumber}`,
                detail: step.outcome || '',
                toolName: step.toolName,
                rawPayload: parsedArgs,
                successful: !step.outcome?.toLowerCase().includes('fail') && !step.outcome?.toLowerCase().includes('error'),
              });
            }
          }
          seenStepsCountRef.current = currentSteps.length;
        }

        if (freshTask.status === 'NEEDS_INPUT') {
          setActiveTask(freshTask);
          setIsStreaming(false);
          loadPersistedThoughts(taskId);
          setWaitingUserInput({
            taskId,
            prompt: freshTask.currentAction || 'MARK requires your guidance to proceed.',
          });
          speechService.speak('MARK requires your guidance to proceed.');
        } else if (freshTask.status === 'EXECUTING') {
          setActiveTask(freshTask);
          setWaitingUserInput(null);
        } else if (freshTask.status === 'COMPLETED') {
          setActiveTask(freshTask);
          setIsStreaming(false);
          loadPersistedThoughts(taskId);
          setVoiceState('speaking');
          const answer = freshTask.finalAnswer || 'Task successfully verified and completed.';
          setAgentSpeechText(answer);

          addRawLog({
            level: 'INFO',
            message: `Task ${taskId} COMPLETED: ${answer}`,
            payload: freshTask,
          });

          addStreamLog({
            id: `${taskId}-completed`,
            timestamp: new Date().toLocaleTimeString(),
            type: 'task:completed',
            title: 'Mission Accomplished & Verified',
            detail: answer,
          });

          speechService.speak(
            answer,
            () => setVoiceState('speaking'),
            () => setVoiceState('idle')
          );

          eventSource.close();
        } else if (freshTask.status === 'FAILED') {
          setActiveTask(freshTask);
          setIsStreaming(false);
          loadPersistedThoughts(taskId);
          setVoiceState('idle');
          const failureDetail = freshTask.finalAnswer || 'Task execution failed.';
          setAgentSpeechText(failureDetail);

          addRawLog({
            level: 'ERROR',
            message: `Task ${taskId} FAILED: ${failureDetail}`,
            payload: freshTask,
          });

          addStreamLog({
            id: `${taskId}-failed`,
            timestamp: new Date().toLocaleTimeString(),
            type: 'task:failed',
            title: 'Task Execution Failed',
            detail: failureDetail,
          });

          speechService.speak('Task execution encountered an error.');
          eventSource.close();
        } else {
          setActiveTask(freshTask);
        }
      } catch (err: any) {
        addRawLog({
          level: 'WARN',
          message: `SSE parsing error: ${err?.message || err}`,
        });
      }
    });

    eventSource.onerror = () => {
      addRawLog({
        level: 'WARN',
        message: 'SSE connection error, will attempt to reconnect.',
      });
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [activeTask?.taskId, activeTask?.id, activeTask?.status]);
}

