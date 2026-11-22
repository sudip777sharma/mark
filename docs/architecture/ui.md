# MARK UI

## Layout

A minimal three-column desktop layout:

1. Left: recent tasks and New Task.
2. Center: task title, current status, one chronological activity stream, and the prompt composer fixed at the bottom.
3. Right: collapsible Context panel.

## Main activity stream

Use plain, concise status text such as:

- Connecting to the selected device…
- Planning the next action…
- Opening Chrome…
- Search completed.
- Waiting for your approval…
- Could not find the requested control.
- Task completed.

Do not create separate screens or tabs for planning, thoughts, execution traces, and tool calls. Do not expose private chain-of-thought. Show only user-facing progress, actions, outcomes, and useful explanations.

Long outputs and tool details can use an inline disclosure such as "Show details".

## Prompt composer

Keep one compact composer with:
- Goal input
- Provider/model selector
- Execution device selector
- Assisted/autonomous mode selector
- Submit/Stop action

The selected provider/model and device are visible without opening Settings.

## Context panel

Keep this panel narrow and collapsible. Show only details relevant to the selected task:
- Paired device and connection status
- Execution mode and approval policy
- Current or recent artifacts
- Optional task metadata

It must not duplicate the activity feed or become a second dashboard.
