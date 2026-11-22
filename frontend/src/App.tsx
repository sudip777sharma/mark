import { useMemo, useState, type FormEvent } from "react";

type TaskStatus = "Completed" | "Executing" | "Failed" | "Queued";

type Task = {
  id: string;
  title: string;
  status: TaskStatus;
  updated: string;
};

type Activity = {
  id: string;
  time: string;
  message: string;
  kind: "success" | "info" | "warning" | "error";
  details?: string;
};

const initialTasks: Task[] = [
  { id: "task-1", title: "Research Spring Boot updates", status: "Completed", updated: "Today" },
  { id: "task-2", title: "Open Notepad", status: "Failed", updated: "Today" },
  { id: "task-3", title: "Organize downloads", status: "Queued", updated: "Yesterday" },
];

const initialActivity: Activity[] = [
  { id: "a1", time: "10:21:02", message: "Task created.", kind: "info" },
  { id: "a2", time: "10:21:04", message: "Connecting to the selected Windows device…", kind: "info" },
  { id: "a3", time: "10:21:05", message: "Device is not paired yet. This is preview activity, not a live run.", kind: "warning" },
];

function StatusDot({ status }: { status: TaskStatus }) {
  const color =
    status === "Completed" ? "bg-emerald-500" :
    status === "Executing" ? "bg-blue-500" :
    status === "Failed" ? "bg-red-500" : "bg-zinc-400";

  return <span className={`inline-block h-2 w-2 shrink-0 rounded-full ${color}`} />;
}

function App() {
  const [tasks, setTasks] = useState(initialTasks);
  const [selectedTaskId, setSelectedTaskId] = useState("task-1");
  const [activity] = useState(initialActivity);
  const [goal, setGoal] = useState("");
  const [provider, setProvider] = useState("Ollama");
  const [model, setModel] = useState("qwen3:4b");
  const [device, setDevice] = useState("My Windows PC");
  const [mode, setMode] = useState("Assisted");
  const [contextOpen, setContextOpen] = useState(true);
  const [notice, setNotice] = useState("Preview data — this UI is not connected to the backend yet.");

  const selectedTask = useMemo(
    () => tasks.find((task) => task.id === selectedTaskId) ?? tasks[0],
    [tasks, selectedTaskId],
  );

  function submitGoal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedGoal = goal.trim();
    if (!trimmedGoal) return;

    const task: Task = {
      id: crypto.randomUUID(),
      title: trimmedGoal,
      status: "Queued",
      updated: "Just now",
    };

    setTasks((current) => [task, ...current]);
    setSelectedTaskId(task.id);
    setGoal("");
    setNotice("Added to the local UI preview only. Backend task submission is the next milestone.");
  }

  return (
    <main className="flex h-screen min-h-[600px] flex-col overflow-hidden bg-[#111318] text-zinc-100">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-white/10 px-5">
        <div className="flex items-center gap-2.5">
          <div className="grid h-7 w-7 place-items-center rounded-lg bg-violet-500/15 text-sm font-semibold text-violet-300">M</div>
          <span className="text-sm font-semibold tracking-wide">MARK</span>
          <span className="ml-2 rounded-full border border-white/10 px-2 py-0.5 text-[11px] text-zinc-400">Preview</span>
        </div>
        <div className="flex items-center gap-2 text-xs text-zinc-400">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
          Device not paired
        </div>
      </header>

      <div className="grid min-h-0 flex-1 grid-cols-[240px_minmax(420px,1fr)_260px]">
        <aside className="flex min-h-0 flex-col border-r border-white/10 bg-[#15171d]">
          <div className="p-3">
            <button
              className="w-full rounded-lg border border-white/10 px-3 py-2 text-left text-sm text-zinc-200 transition hover:bg-white/5"
              onClick={() => {
                setSelectedTaskId("");
                setNotice("Enter a goal below to start a new task.");
              }}
            >
              <span className="mr-2 text-zinc-400">+</span> New task
            </button>
          </div>
          <div className="px-4 pb-2 pt-1 text-[11px] font-medium uppercase tracking-wider text-zinc-500">Recent tasks</div>
          <div className="min-h-0 flex-1 space-y-1 overflow-y-auto px-2 pb-3">
            {tasks.map((task) => (
              <button
                key={task.id}
                onClick={() => setSelectedTaskId(task.id)}
                className={`w-full rounded-lg px-3 py-2.5 text-left transition ${selectedTaskId === task.id ? "bg-white/[0.08]" : "hover:bg-white/[0.04]"}`}
              >
                <span className="block truncate text-[13px] text-zinc-200">{task.title}</span>
                <span className="mt-1.5 flex items-center gap-2 text-[11px] text-zinc-500">
                  <StatusDot status={task.status} /> {task.status} <span>·</span> {task.updated}
                </span>
              </button>
            ))}
          </div>
          <div className="border-t border-white/10 p-3 text-xs text-zinc-500">Settings</div>
        </aside>

        <section className="flex min-h-0 min-w-0 flex-col">
          <div className="flex h-[68px] shrink-0 items-center justify-between border-b border-white/10 px-6">
            <div className="min-w-0">
              <h1 className="truncate text-sm font-medium text-zinc-100">
                {selectedTask?.title ?? "New task"}
              </h1>
              <p className="mt-1 text-[11px] text-zinc-500">
                {selectedTask ? `Task ID: ${selectedTask.id}` : "Describe what you want MARK to do"}
              </p>
            </div>
            {selectedTask && (
              <span className="ml-3 flex shrink-0 items-center gap-2 rounded-full border border-white/10 px-2.5 py-1 text-[11px] text-zinc-300">
                <StatusDot status={selectedTask.status} /> {selectedTask.status}
              </span>
            )}
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
            <div className="mx-auto max-w-3xl">
              {selectedTask?.id === "task-1" ? (
                <div className="mb-5 max-w-2xl text-sm leading-6 text-zinc-300">
                  I’ll research the official documentation, summarize the relevant changes, and keep the execution steps visible here.
                </div>
              ) : null}

              <div className="space-y-4">
                {activity.map((item) => (
                  <div key={item.id} className="flex gap-3">
                    <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                      item.kind === "success" ? "bg-emerald-500" :
                      item.kind === "warning" ? "bg-amber-400" :
                      item.kind === "error" ? "bg-red-400" : "bg-zinc-500"
                    }`} />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                        <span className="text-sm text-zinc-200">{item.message}</span>
                        <time className="text-[10px] text-zinc-600">{item.time}</time>
                      </div>
                      {item.details && (
                        <details className="mt-1 text-xs text-zinc-500">
                          <summary className="cursor-pointer select-none hover:text-zinc-300">Show details</summary>
                          <pre className="mt-2 overflow-x-auto rounded-md bg-black/20 p-3">{item.details}</pre>
                        </details>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              <div className="mt-8 rounded-lg border border-dashed border-white/10 px-4 py-3 text-xs text-zinc-500">
                {notice}
              </div>
            </div>
          </div>

          <div className="shrink-0 border-t border-white/10 px-5 py-4">
            <form onSubmit={submitGoal} className="mx-auto max-w-4xl rounded-xl border border-white/10 bg-[#191c23] focus-within:border-violet-400/40">
              <textarea
                value={goal}
                onChange={(event) => setGoal(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    event.currentTarget.form?.requestSubmit();
                  }
                }}
                rows={2}
                placeholder="Give MARK a task…"
                className="max-h-32 min-h-14 w-full resize-y bg-transparent px-4 pt-3 text-sm text-zinc-100 outline-none placeholder:text-zinc-600"
              />
              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/[0.07] px-3 py-2">
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-1.5 text-[11px] text-zinc-400">
                    <span>Provider</span>
                    <select value={provider} onChange={(e) => setProvider(e.target.value)} className="select-dark">
                      <option>Ollama</option>
                      <option>Cloud</option>
                    </select>
                  </label>
                  <label className="flex items-center gap-1.5 text-[11px] text-zinc-400">
                    <span>Model</span>
                    <select value={model} onChange={(e) => setModel(e.target.value)} className="select-dark">
                      <option>qwen3:4b</option>
                      <option>local-general</option>
                    </select>
                  </label>
                  <label className="flex items-center gap-1.5 text-[11px] text-zinc-400">
                    <span>Device</span>
                    <select value={device} onChange={(e) => setDevice(e.target.value)} className="select-dark">
                      <option>My Windows PC</option>
                    </select>
                  </label>
                  <label className="flex items-center gap-1.5 text-[11px] text-zinc-400">
                    <span>Mode</span>
                    <select value={mode} onChange={(e) => setMode(e.target.value)} className="select-dark">
                      <option>Assisted</option>
                      <option>Autonomous</option>
                    </select>
                  </label>
                </div>
                <button
                  type="submit"
                  disabled={!goal.trim()}
                  className="rounded-lg bg-violet-500 px-3.5 py-1.5 text-xs font-medium text-white transition hover:bg-violet-400 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Submit
                </button>
              </div>
            </form>
          </div>
        </section>

        {contextOpen ? (
          <aside className="flex min-h-0 flex-col border-l border-white/10 bg-[#15171d]">
            <div className="flex h-[68px] shrink-0 items-center justify-between border-b border-white/10 px-4">
              <span className="text-xs font-medium text-zinc-300">Context</span>
              <button onClick={() => setContextOpen(false)} className="text-xs text-zinc-500 hover:text-zinc-200" aria-label="Close context panel">Hide</button>
            </div>
            <div className="space-y-5 overflow-y-auto p-4">
              <div>
                <div className="text-[11px] uppercase tracking-wider text-zinc-500">Device</div>
                <div className="mt-2 text-sm text-zinc-200">My Windows PC</div>
                <div className="mt-1 flex items-center gap-2 text-xs text-amber-400">
                  <span className="h-1.5 w-1.5 rounded-full bg-amber-400" /> Not paired
                </div>
                <p className="mt-2 text-xs leading-5 text-zinc-500">Pair a Windows runtime to enable local computer actions.</p>
              </div>
              <div className="border-t border-white/10 pt-4">
                <div className="text-[11px] uppercase tracking-wider text-zinc-500">Selected execution</div>
                <dl className="mt-2 space-y-2 text-xs">
                  <div className="flex justify-between gap-2"><dt className="text-zinc-500">Provider</dt><dd className="text-zinc-300">{provider}</dd></div>
                  <div className="flex justify-between gap-2"><dt className="text-zinc-500">Model</dt><dd className="text-zinc-300">{model}</dd></div>
                  <div className="flex justify-between gap-2"><dt className="text-zinc-500">Mode</dt><dd className="text-zinc-300">{mode}</dd></div>
                </dl>
              </div>
              <div className="border-t border-white/10 pt-4">
                <div className="text-[11px] uppercase tracking-wider text-zinc-500">Permissions</div>
                <p className="mt-2 text-xs leading-5 text-zinc-500">Device permissions and approval rules will appear here after pairing.</p>
              </div>
            </div>
          </aside>
        ) : (
          <aside className="flex items-start justify-center border-l border-white/10 bg-[#15171d] p-2">
            <button onClick={() => setContextOpen(true)} className="text-xs text-zinc-500 hover:text-zinc-200">Show context</button>
          </aside>
        )}
      </div>
    </main>
  );
}

export default App;
