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
  const setVoiceState = useAgentStore((s) => s.setVoiceState);
  const setAgentSpeechText = useAgentStore((s) => s.setAgentSpeechText);
  const setWaitingUserInput = useAgentStore((s) => s.setWaitingUserInput);
  const addRawLog = useAgentStore((s) => s.addRawLog);

  const seenStepsCountRef = useRef<number>(0);
  const lastTaskIdRef = useRef<string | null>(null);

  useEffect(() => {
    const taskId = activeTask?.taskId || activeTask?.id;
    if (!taskId) return;

    if (lastTaskIdRef.current !== taskId) {
      lastTaskIdRef.current = taskId;
      seenStepsCountRef.current = activeTask?.steps?.length || 0;
      addRawLog({
        level: 'INFO',
        message: `Task activated: ${taskId} (${activeTask.goal})`,
        payload: { status: activeTask.status, goal: activeTask.goal },
      });
    }

    const isRunning = activeTask.status === 'PLANNING' || activeTask.status === 'EXECUTING';
    if (!isRunning) {
      setIsStreaming(false);
      return;
    }

    setIsStreaming(true);

    const interval = setInterval(async () => {
      try {
        const freshTask = await taskApi.getTask(taskId);
        if (!freshTask) return;

        addRawLog({
          level: 'DEBUG',
          message: `Polled task status: ${freshTask.status} | Steps: ${freshTask.steps?.length || 0}`,
        });

        // Refresh sidebar task list
        taskApi.listTasks().then((list) => {
          if (list.length > 0) setTasks(list);
        });

        // Update plan roadmap if generated
        if (freshTask.plan && freshTask.plan.length > 0) {
          setActivePlan(freshTask.plan);
        }

        // Process any newly added steps
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

            // Parse tool arguments into rawPayload for the ToolCallCard to display
            let parsedArgs: Record<string, unknown> = {};
            if (step.toolArguments) {
              try {
                parsedArgs = JSON.parse(step.toolArguments);
              } catch (_e) {
                // If it's not JSON, wrap it as a raw string
                parsedArgs = { raw: step.toolArguments };
              }
            }

            addStreamLog({
              id: `${taskId}-step-${step.stepNumber}-${Date.now()}`,
              timestamp: new Date().toLocaleTimeString(),
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

        // Handle completion / failure
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

          // Speak final answer aloud
          speechService.speak(
            answer,
            () => setVoiceState('speaking'),
            () => setVoiceState('idle')
          );

          clearInterval(interval);
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
          clearInterval(interval);
        } else {
          setActiveTask(freshTask);
        }
      } catch (err: any) {
        addRawLog({
          level: 'WARN',
          message: `Network polling error: ${err?.message || err}`,
        });
      }
    }, 1200);

    return () => clearInterval(interval);
  }, [activeTask?.taskId, activeTask?.id, activeTask?.status]);
}
