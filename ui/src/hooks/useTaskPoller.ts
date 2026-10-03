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
      lastStatusRef.current = activeTask?.status || null;
      lastActionRef.current = activeTask?.currentAction || null;
      seenStepsCountRef.current = activeTask?.steps?.length || 0;
      addRawLog({
        level: 'INFO',
        message: `Task activated: ${taskId} (${activeTask.goal})`,
        payload: { status: activeTask.status, goal: activeTask.goal },
      });
    }

    const isRunning = activeTask.status === 'CREATED' || 
                      activeTask.status === 'PLANNING' || 
                      activeTask.status === 'EXECUTING' || 
                      activeTask.status === 'QUOTA_EXHAUSTED' || 
                      activeTask.status === 'COOLDOWN';
    if (!isRunning) {
      setIsStreaming(false);
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
            if (freshTask.status === 'PLANNING') {
              clearLlmThoughtStream();
            }
            lastStatusRef.current = freshTask.status;
            taskApi.listTasks().then((list) => {
              if (list.length > 0) setTasks(list);
            });
        }
        
        if (lastActionRef.current !== freshTask.currentAction) {
            if (freshTask.currentAction === 'Thinking (Waiting for LLM)...') {
                clearLlmThoughtStream();
            }
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

            if (step.toolName && step.toolName.toLowerCase().includes('askuser')) {
              setWaitingUserInput({
                taskId,
                prompt: step.description || 'MARK requires your clarification to proceed.',
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

            addStreamLog({
              id: `${taskId}-step-${step.stepNumber}-${Date.now()}`,
              timestamp: stepTime,
              type: step.toolName ? 'tool:result' : 'agent:thinking',
              title: step.toolName ? `Tool: ${step.toolName}` : `Step ${step.stepNumber}`,
              detail: step.outcome || step.description,
              toolName: step.toolName,
              rawPayload: parsedArgs,
              successful:
                !step.outcome?.toLowerCase().includes('fail') &&
                !step.outcome?.toLowerCase().includes('error'),
            });
          }
          seenStepsCountRef.current = currentSteps.length;
        }

        if (freshTask.status === 'COMPLETED') {
          setActiveTask(freshTask);
          setIsStreaming(false);
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

