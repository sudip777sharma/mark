import React, { useEffect } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { taskApi } from '../../api/taskClient';
import { Plus, CheckCircle, Clock, AlertCircle, Sparkles, Terminal } from 'lucide-react';
import { TaskResponse } from '../../types/agent';

export const TaskSidebar: React.FC = () => {
  const tasks = useAgentStore((s) => s.tasks);
  const setTasks = useAgentStore((s) => s.setTasks);
  const activeTaskId = useAgentStore((s) => s.activeTaskId);
  const setActiveTask = useAgentStore((s) => s.setActiveTask);
  const clearStreamLogs = useAgentStore((s) => s.clearStreamLogs);
  const setStreamLogs = useAgentStore((s) => s.setStreamLogs);
  const setActivePlan = useAgentStore((s) => s.setActivePlan);
  const setViewMode = useAgentStore((s) => s.setViewMode);

  useEffect(() => {
    taskApi.listTasks().then((loaded) => {
      if (loaded.length > 0) {
        setTasks(loaded);
        if (!activeTaskId) {
          setActiveTask(loaded[0]);
        }
      }
    });
  }, []);

  const handleNewTask = () => {
    setActiveTask(null);
    clearStreamLogs();
    setActivePlan([]);
  };

  const handleSelectTask = async (task: TaskResponse) => {
    setActiveTask(task);
    
    // Reconstruct stream logs from historical task steps
    if (task.steps && task.steps.length > 0) {
      const logs = [];
      task.steps.forEach((step, idx) => {
        const stepNum = step.number ?? step.stepNumber ?? (idx + 1);
        const timeStr = `Step ${stepNum}`;
        
        // 1. Agent Reasoning (if present)
        if (step.description && step.description.trim() !== '') {
          logs.push({
            id: `hist-think-${stepNum}`,
            timestamp: timeStr,
            type: 'agent:thinking',
            title: 'Agent Reasoning',
            detail: step.description
          });
        }
        
        // 2. Tool Execution
        if (step.toolName && step.toolName !== 'None') {
          const isSuccess = step.outcome ? !step.outcome.startsWith('Failed') : true;
          let parsedArgs = {};
          if (step.toolArguments) {
            try {
              parsedArgs = JSON.parse(step.toolArguments);
            } catch (e) {}
          }

          logs.push({
            id: `hist-tool-${stepNum}`,
            timestamp: timeStr,
            type: 'tool:result',
            title: `Tool: ${step.toolName}`,
            toolName: step.toolName,
            detail: step.outcome,
            successful: isSuccess,
            rawPayload: parsedArgs 
          });
        }
      });
      
      // If task is completed, add a completion log
      if (task.status === 'COMPLETED') {
        logs.push({
          id: `hist-comp-${task.taskId}`,
          timestamp: 'Final',
          type: 'task:completed',
          title: 'Mission Accomplished & Verified',
          detail: task.finalAnswer || 'Task completed successfully.'
        });
      }
      
      // @ts-ignore - TS might complain about exact type match depending on imports
      setStreamLogs(logs);
    } else {
      clearStreamLogs();
    }
    
    setActivePlan(task.plan || []);
    setViewMode('canvas');
  };

  return (
    <div
      style={{
        width: '280px',
        height: '100%',
        borderRight: '1px solid var(--border-subtle)',
        background: 'rgba(9, 13, 22, 0.95)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Brand Header */}
      <div
        style={{
          padding: '20px',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              background: 'linear-gradient(135deg, var(--accent-cyan), var(--accent-indigo))',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: 'var(--shadow-glow-cyan)',
            }}
          >
            <Sparkles size={18} color="#07090e" />
          </div>
          <div>
            <h1 style={{ fontSize: '15px', fontWeight: 700, letterSpacing: '0.5px' }}>MARK</h1>
            <span style={{ fontSize: '11px', color: 'var(--accent-cyan)', fontWeight: 500 }}>
              Autonomous Agent
            </span>
          </div>
        </div>

        <button
          onClick={handleNewTask}
          title="New Task Goal"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '28px',
            height: '28px',
            borderRadius: '6px',
            background: 'rgba(255, 255, 255, 0.05)',
            border: '1px solid var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        >
          <Plus size={16} />
        </button>
      </div>

      {/* Task List */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '12px 10px' }}>
        <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)', padding: '6px 10px', letterSpacing: '0.5px' }}>
          TASK SESSIONS
        </div>

        {tasks.length === 0 ? (
          <div style={{ padding: '24px 12px', textAlign: 'center', color: 'var(--text-dim)', fontSize: '12px' }}>
            No previous tasks recorded
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {tasks.map((task, idx) => {
              const taskId = task.taskId || task.id || `task-${idx}`;
              const isSelected = taskId === activeTaskId;
              return (
                <div
                  key={taskId}
                  onClick={() => handleSelectTask(task)}
                  style={{
                    padding: '10px 12px',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    background: isSelected ? 'rgba(6, 182, 212, 0.12)' : 'transparent',
                    border: isSelected ? '1px solid var(--border-focus)' : '1px solid transparent',
                    transition: 'var(--transition-fast)',
                  }}
                >
                  <div
                    style={{
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                      color: isSelected ? 'var(--text-primary)' : 'var(--text-secondary)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      marginBottom: '4px',
                    }}
                  >
                    {task.goal}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '11px', color: 'var(--text-dim)' }}>
                    <span>{task.steps ? `${task.steps.length} steps` : '0 steps'}</span>
                    <span
                      style={{
                        color:
                          task.status === 'COMPLETED'
                            ? 'var(--accent-emerald)'
                            : task.status === 'FAILED'
                            ? 'var(--accent-rose)'
                            : 'var(--accent-cyan)',
                      }}
                    >
                      {task.status}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Footer System Pill */}
      <div
        style={{
          padding: '12px 16px',
          borderTop: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '11px',
          color: 'var(--text-muted)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <div style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: 'var(--accent-emerald)' }} />
          <span>Backend Live (8081)</span>
        </div>
        <span>v0.2</span>
      </div>
    </div>
  );
};
