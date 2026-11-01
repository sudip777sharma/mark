package dev.mark.agent.service;

import dev.mark.agent.model.AgentWorldStateDeltaModel;
import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.llm.dto.LlmMessageDTO;
import dev.mark.agent.config.AgentPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.registry.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service component responsible for synthesizing prompts and managing context history windowing
 * for the MARK (Multi-agent / Autonomous Reasoning Kernel) framework.
 *
 * <h3>1. What this class does</h3>
 * The {@code AgentPromptBuilderService} acts as the primary prompt engineering and context maintenance
 * engine for autonomous agents. It creates structured system prompts for both the planning phase
 * (decomposing high-level goals into step-by-step plans) and the execution phase (constructing real-time,
 * state-aware system instructions). In addition, it provides memory pruning capability to dynamically
 * compact message histories according to configured character and message count boundaries.
 *
 * <h3>2. Why it is useful</h3>
 * Autonomous LLM agents require precise prompt formatting, strict operational guardrails, explicit tool
 * definitions, and real-time environmental context to function reliably without hallucinating tools or
 * exceeding LLM context window limits. This service centralizes prompt synthesis and context window optimization,
 * preventing duplicate prompt logic across execution loops while protecting the application from LLM context
 * overflow errors during long-running automations.
 *
 * <h3>3. How it fits into the application flow</h3>
 * Within the execution loop of the MARK agent application:
 * <ul>
 *   <li><b>Planning Stage:</b> When an agent receives a goal, the core orchestrator calls
 *       {@link #systemPromptForPlanning()} to instruct the LLM on breaking down the user goal into actionable steps.</li>
 *   <li><b>Execution Stage:</b> Before every execution iteration, the orchestrator invokes
 *       {@link #systemPromptForExecution(List, AgentWorldStateModel, String)} to inject current environment state,
 *       state deltas, available tools from {@link ToolRegistry}, desktop/browser automation rules, and the generated plan.</li>
 *   <li><b>Context Pruning:</b> Prior to sending conversation history to the LLM client service, the orchestrator passes
 *       the history and {@link AgentPropertiesConfig} into {@link #compactHistory(List, AgentPropertiesConfig)} to trim excess
 *       historical turns while retaining critical context and remaining within memory limits.</li>
 * </ul>
 *
 * <h3>4. Methods and their usefulness</h3>
 * <ul>
 *   <li>{@link #AgentPromptBuilderService(ToolRegistry)}: Constructor for dependency injection. Useful for obtaining access
 *       to the dynamic tool ecosystem managed by {@link ToolRegistry}.</li>
 *   <li>{@link #systemPromptForPlanning()}: Generates the system prompt for goal decomposition. Useful for ensuring the LLM
 *       acts as a strict planning component that outputs clear, sequential steps bounded by tool capabilities.</li>
 *   <li>{@link #systemPromptForExecution(List, AgentWorldStateModel, String)}: Builds the primary execution prompt. Useful for
 *       providing the LLM with situational awareness (active window, active application, recent changes), tool metadata,
 *       desktop/browser interaction rules (e.g., UI inspection requirements, delays, verification), and plan state.</li>
 *   <li>{@link #compactHistory(List, AgentPropertiesConfig)}: Truncates historical conversation turns. Useful for managing LLM
 *       token limits by enforcing maximum message count limits and aggregate character length ceilings without breaking turn flow.</li>
 *   <li>{@link #messageLength(LlmMessageDTO)}: Private helper method. Useful for measuring the byte/character footprint of individual
 *       messages, accounting for text content, tool execution observations, and serialized tool call arguments.</li>
 * </ul>
 *
 * <h3>5. Variables and their usefulness</h3>
 * <ul>
 *   <li>{@link #toolRegistry}: Holds a reference to the injected {@link ToolRegistry}. Useful because it allows the execution prompt builder
 *       to dynamically inspect all available executable tools at runtime, extracting their names and descriptions so the LLM is always aware
 *       of current tool capabilities.</li>
 * </ul>
 *
 * <h3>6. Logic of methods and variables</h3>
 * <ul>
 *   <li><b>toolRegistry Field Logic:</b> Initialized via Spring dependency injection. Immutable field reference ensuring thread-safe access to
 *       tool definitions across prompt construction calls.</li>
 *   <li><b>systemPromptForPlanning Logic:</b> Returns a pre-defined directive string instructing the LLM to function as a master planner
 *       and produce concise, actionable steps based solely on available tools.</li>
 *   <li><b>systemPromptForExecution Logic:</b>
 *       <ol>
 *         <li>Appends recent state changes (deltas) and current environment details (active application and window title from {@code AgentWorldStateModel}).</li>
 *         <li>Iterates over all tools retrieved from {@code toolRegistry.availableTools()}, formatting each tool's name and description into a list.</li>
 *         <li>Appends strict operational rules covering tool execution, UI element inspection via {@code inspect_ui}, fallback handling via {@code screenshot},
 *             mandatory post-action verifications for browser automation, delay requirements for slow UI updates, and window focus target enforcement.</li>
 *         <li>If an execution plan list is provided and non-empty, appends a numbered step-by-step display of the execution plan.</li>
 *         <li>Returns the assembled string representation.</li>
 *       </ol>
 *   </li>
 *   <li><b>compactHistory Logic:</b>
 *       <ol>
 *         <li>Returns immediately if the full history list is empty.</li>
 *         <li>Copies full history into a mutable working list.</li>
 *         <li>If {@code maxHistoryLength} configuration is defined and smaller than list size, crops older messages from the top, retaining the most recent sublist.</li>
 *         <li>If {@code maxHistoryChars} configuration is defined, continuously evaluates total character sum via {@link #messageLength(LlmMessageDTO)}.
 *             Iteratively removes the oldest message (using {@code removeFirst()}) until the character sum drops below {@code maxHistoryChars} or until
 *             only a safety floor of 4 messages remains.</li>
 *         <li>Returns the compacted message list.</li>
 *       </ol>
 *   </li>
 *   <li><b>messageLength Logic:</b> Evaluates a {@link LlmMessageDTO} by summing:
 *       <ol>
 *         <li>The character length of main message content (if non-null).</li>
 *         <li>The character length of tool execution observations (if present).</li>
 *         <li>The character length of stringified argument objects for each tool call contained in the message.</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <h3>7. Collective logic and integration into MARK agent ecosystem</h3>
package dev.mark.agent.service;

import dev.mark.agent.model.AgentWorldStateDeltaModel;
import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.llm.dto.LlmMessageDTO;
import dev.mark.agent.config.AgentPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.registry.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service component responsible for synthesizing prompts and managing context history windowing
 * for the MARK (Multi-agent / Autonomous Reasoning Kernel) framework.
 *
 * <h3>1. What this class does</h3>
 * The {@code AgentPromptBuilderService} acts as the primary prompt engineering and context maintenance
 * engine for autonomous agents. It creates structured system prompts for both the planning phase
 * (decomposing high-level goals into step-by-step plans) and the execution phase (constructing real-time,
 * state-aware system instructions). In addition, it provides memory pruning capability to dynamically
 * compact message histories according to configured character and message count boundaries.
 *
 * <h3>2. Why it is useful</h3>
 * Autonomous LLM agents require precise prompt formatting, strict operational guardrails, explicit tool
 * definitions, and real-time environmental context to function reliably without hallucinating tools or
 * exceeding LLM context window limits. This service centralizes prompt synthesis and context window optimization,
 * preventing duplicate prompt logic across execution loops while protecting the application from LLM context
 * overflow errors during long-running automations.
 *
 * <h3>3. How it fits into the application flow</h3>
 * Within the execution loop of the MARK agent application:
 * <ul>
 *   <li><b>Planning Stage:</b> When an agent receives a goal, the core orchestrator calls
 *       {@link #systemPromptForPlanning()} to instruct the LLM on breaking down the user goal into actionable steps.</li>
 *   <li><b>Execution Stage:</b> Before every execution iteration, the orchestrator invokes
 *       {@link #systemPromptForExecution(List, AgentWorldStateModel, String)} to inject current environment state,
 *       state deltas, available tools from {@link ToolRegistry}, desktop/browser automation rules, and the generated plan.</li>
 *   <li><b>Context Pruning:</b> Prior to sending conversation history to the LLM client service, the orchestrator passes
 *       the history and {@link AgentPropertiesConfig} into {@link #compactHistory(List, AgentPropertiesConfig)} to trim excess
 *       historical turns while retaining critical context and remaining within memory limits.</li>
 * </ul>
 *
 * <h3>4. Methods and their usefulness</h3>
 * <ul>
 *   <li>{@link #AgentPromptBuilderService(ToolRegistry, dev.mark.memory.service.MemoryService)}: Constructor for dependency injection. Useful for obtaining access
 *       to the dynamic tool ecosystem managed by {@link ToolRegistry} and the memory service.</li>
 *   <li>{@link #systemPromptForPlanning()}: Generates the system prompt for goal decomposition. Useful for ensuring the LLM
 *       acts as a strict planning component that outputs clear, sequential steps bounded by tool capabilities.</li>
 *   <li>{@link #systemPromptForExecution(List, AgentWorldStateModel, String)}: Builds the primary execution prompt. Useful for
 *       providing the LLM with situational awareness (active window, active application, recent changes), tool metadata,
 *       desktop/browser interaction rules (e.g., UI inspection requirements, delays, verification), and plan state.</li>
 *   <li>{@link #compactHistory(List, AgentPropertiesConfig)}: Truncates historical conversation turns. Useful for managing LLM
 *       token limits by enforcing maximum message count limits and aggregate character length ceilings without breaking turn flow.</li>
 *   <li>{@link #messageLength(LlmMessageDTO)}: Private helper method. Useful for measuring the byte/character footprint of individual
 *       messages, accounting for text content, tool execution observations, and serialized tool call arguments.</li>
 * </ul>
 *
 * <h3>5. Variables and their usefulness</h3>
 * <ul>
 *   <li>{@link #toolRegistry}: Holds a reference to the injected {@link ToolRegistry}. Useful because it allows the execution prompt builder
 *       to dynamically inspect all available executable tools at runtime, extracting their names and descriptions so the LLM is always aware
 *       of current tool capabilities.</li>
 *   <li>{@link #memoryService}: Holds a reference to the injected {@link dev.mark.memory.service.MemoryService}. Useful for retrieving long-term
 *       memories to inject into the execution prompt context.</li>
 * </ul>
 *
 * <h3>6. Logic of methods and variables</h3>
 * <ul>
 *   <li><b>toolRegistry and memoryService Field Logic:</b> Initialized via Spring dependency injection. Immutable field references ensuring thread-safe access to
 *       tool definitions and long-term memory across prompt construction calls.</li>
 *   <li><b>systemPromptForPlanning Logic:</b> Returns a pre-defined directive string instructing the LLM to function as a master planner
 *       and produce concise, actionable steps based solely on available tools.</li>
 *   <li><b>systemPromptForExecution Logic:</b>
 *       <ol>
 *         <li>Retrieves long-term memories and appends them if present.</li>
 *         <li>Appends recent state changes (deltas) and current environment details (active application and window title from {@code AgentWorldStateModel}).</li>
 *         <li>Iterates over all tools retrieved from {@code toolRegistry.availableTools()}, formatting each tool's name and description into a list.</li>
 *         <li>Appends strict operational rules covering tool execution, UI element inspection via {@code inspect_ui}, fallback handling via {@code screenshot},
 *             mandatory post-action verifications for browser automation, delay requirements for slow UI updates, and window focus target enforcement.</li>
 *         <li>If an execution plan list is provided and non-empty, appends a numbered step-by-step display of the execution plan.</li>
 *         <li>Returns the assembled string representation.</li>
 *       </ol>
 *   </li>
 *   <li><b>compactHistory Logic:</b>
 *       <ol>
 *         <li>Returns immediately if the full history list is empty.</li>
 *         <li>Copies full history into a mutable working list.</li>
 *         <li>If {@code maxHistoryLength} configuration is defined and smaller than list size, crops older messages from the top, retaining the most recent sublist.</li>
 *         <li>If {@code maxHistoryChars} configuration is defined, continuously evaluates total character sum via {@link #messageLength(LlmMessageDTO)}.
 *             Iteratively removes the oldest message (using {@code removeFirst()}) until the character sum drops below {@code maxHistoryChars} or until
 *             only a safety floor of 4 messages remains.</li>
 *         <li>Returns the compacted message list.</li>
 *       </ol>
 *   </li>
 *   <li><b>messageLength Logic:</b> Evaluates a {@link LlmMessageDTO} by summing:
 *       <ol>
 *         <li>The character length of main message content (if non-null).</li>
 *         <li>The character length of tool execution observations (if present).</li>
 *         <li>The character length of stringified argument objects for each tool call contained in the message.</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <h3>7. Collective logic and integration into MARK agent ecosystem</h3>
 * Collectively, {@code AgentPromptBuilderService} acts as the context generator and memory regulator for the MARK framework.
 * It bridges domain state components (World State, Tool Registries, System Configs) and LLM interaction interfaces.
 * By combining runtime telemetry, dynamic tool definitions, behavioral guardrails, and automated history compaction into unified outputs,
 * it enables the MARK agent core to execute multi-turn desktop and browser automations safely, deterministically, and within context limits.
 */

@Service
public class AgentPromptBuilderService {
    private final dev.mark.preference.UserPreferenceService userPreferenceService;
    private final ToolRegistry toolRegistry;
    private final dev.mark.memory.service.MemoryService memoryService;

    public AgentPromptBuilderService(dev.mark.preference.UserPreferenceService userPreferenceService, ToolRegistry toolRegistry, dev.mark.memory.service.MemoryService memoryService) {
        this.userPreferenceService = userPreferenceService;
        this.toolRegistry = toolRegistry;
        this.memoryService = memoryService;
    }

        public String systemPromptForPlanning() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an elite, universally adaptable autonomous agent planner.\n\n");
        sb.append("AVAILABLE TOOLS:\n");
        sb.append("You MUST plan steps that map directly to the capabilities of these tools:\n");
        for (Tool tool : toolRegistry.availableTools()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        sb.append("\nPHASE 1: DOMAIN CATEGORIZATION & TOOL SELECTION\n");
        sb.append("- PREFER HIGH-LEVEL SPECIALIZED TOOLS: Whenever a specialized domain tool exists, ALWAYS plan around that tool rather than generic GUI/browser clicks.\n");
        sb.append("  * For WhatsApp tasks: Use 'whatsapp_web_automation' actions ('open_session', 'read_chat', 'verify_message', 'send_message', 'list_unread_chats', 'post_status', 'get_my_status', 'list_unseen_statuses', 'view_contact_status', 'start_audio_call', 'start_video_call') instead of manual browser clicks.\n");
        sb.append("  * For Screen & Audio Recording: Use 'screen_recorder' actions ('start_recording', 'stop_recording', 'status', 'open_folder') to capture desktop video and microphone audio. Always call 'stop_recording' before opening, dispatching, or verifying the recorded file. If the user asks to see or open where the file was saved, set 'open_on_complete: true' or call action 'open_folder'.\n");
        sb.append("  * For Compound Workflows (e.g. Record screen while performing actions, then show file and post status):\n");
        sb.append("    Plan in deterministic sequential order:\n");
        sb.append("    1. Start screen recording via 'screen_recorder' ('action': 'start_recording', 'duration_seconds': <secs>, 'include_audio': true)\n");
        sb.append("    2. Perform domain task (e.g., 'whatsapp_web_automation' post photo status updates)\n");
        sb.append("    3. Stop screen recording via 'screen_recorder' ('action': 'stop_recording')\n");
        sb.append("    4. Open the recorded video location in File Explorer via 'screen_recorder' ('action': 'open_folder')\n");
        sb.append("    5. Post the recorded video to WhatsApp Web via 'whatsapp_web_automation' ('action': 'post_status', 'file_path': '<recorded_mp4>', 'message': '<caption>')\n");
        sb.append("  * For Code/File tasks: Prefer 'execute_command' and CLI tools over manual GUI clicks.\n");
        sb.append("  * For Opening Windows Applications: To launch apps (such as Notepad, Calculator, Paint), plan to use 'execute_command' with command 'notepad', 'calc', 'mspaint', or 'start notepad'. Never use 'desktop_automation' to launch an app.\n");
        sb.append("  * For Finding / Counting Files: To count or list files and images in a folder, plan to use 'list_directory' (or 'search_file_content'). Never use 'desktop_automation' to browse directories.\n");
        sb.append("  * For User Clarification & Guidance: If the goal is ambiguous or required paths/files are missing, use 'ask_user' to pause and ask the user.\n");
        sb.append("  * For generic GUI tasks: If no high-level tool exists, plan to use 'inspect_ui' and 'desktop_automation'.\n\n");
        sb.append("PHASE 2: DYNAMIC STRATEGY\n");
        sb.append("- Keep the plan minimal, direct, and concise (usually 1-3 direct steps when using specialized tools).\n");
        sb.append("- Do not break high-level tool operations into manual micro-steps like 'click search bar', 'type text', 'press enter'.\n\n");
        sb.append("PHASE 3: ACTIONABLE PLAN\n");
        sb.append("First, explain your decomposition and tool selection reasoning inside <thinking>...</thinking> tags.\n");
        sb.append("Then output the final plan as a JSON object with a single key 'steps' containing an array of concise steps. Keep steps concise.");
        return sb.toString();
    }

            public String systemPromptForExecution(List<String> plan, AgentWorldStateModel worldState, String stateDelta) {
        return systemPromptForExecution(plan, worldState, stateDelta, 1);
    }

    public String systemPromptForExecution(List<String> plan, AgentWorldStateModel worldState, String stateDelta, int currentStepNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a super-intelligent execution agent. Execute the plan with extreme adaptability and dynamic problem-solving.\n\n");
        sb.append("UNIVERSAL EXECUTION GUIDELINES:\n");
        sb.append("- CATEGORIZE & FILTER: Mentally categorize the current step and only use tools relevant to that domain.\n");
        sb.append("- VERIFY & ADAPT: Do not blindly execute actions. Verify the environment state before acting. If an action fails, deduce the root cause and adapt.\n\n");
        sb.append("DESKTOP UI AUTOMATION (CRITICAL RULES):\n");
        sb.append("- DISCOVERY: NEVER guess coordinates or window titles. Use 'inspect_ui' ('list_windows', 'inspect_active', or 'inspect_window') to read the UI tree.\n");
        sb.append("- DYNAMIC MODALS: If you open a dialog (like 'Save As'), the OS spawns it as a completely new top-level window. If you can't find an element, use 'inspect_active' to see what is currently blocking the screen.\n");
        sb.append("- PRECISION INTERACTION: To interact with a UI element, read its BoundingRectangle [x, y, width, height], calculate its exact center point (x + width/2, y + height/2), and use 'desktop_automation' (move, click, type) with those exact integer coordinates.\n");
        sb.append("- TEXT ENTRY: Prefer using the 'type' or 'press' actions in 'desktop_automation' after clicking to focus an input field. Use 'press' for keyboard shortcuts.\n\n");
        
        java.util.Map<String, String> memories = memoryService.retrieveAll();
        if (!memories.isEmpty()) {
            sb.append("Long-term Memory:\n");
            for (java.util.Map.Entry<String, String> entry : memories.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Current Environment:\n");
        sb.append("Recent Environment Changes:\n");
        sb.append(stateDelta);
        sb.append("\n\n");
        sb.append("- Active application: ")
                .append(worldState.activeApplication())
                .append("\n");
        sb.append("- Active window: ")
                .append(worldState.activeWindow())
                .append("\n\n");

        sb.append("You are the reasoning component of MARK, an autonomous agent. ");
        sb.append("Select the BEST tool for the task. Available tools:\n");
        for (Tool tool : toolRegistry.availableTools()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        sb.append("\nRules:\n");
        sb.append("- Use ONLY the tools listed above. Never invent tools.\n");
        sb.append("- THOUGHT PROCESS: Before outputting any tool call, you MUST output a brief <thinking> block explaining your reasoning. For example:\n<thinking>I need to click the search bar, so I will use desktop_automation...</thinking>\nThen invoke the tool.\n");
        sb.append("- Match the task to the most specific tool. For file operations use read_file/write_file/list_directory.\n");
        sb.append("- **BATCHING**: Only batch sequential UI/keyboard actions (e.g., focus -> delay -> click -> delay -> type). Never output multiple file or execution operations in a single batch.\n");
        sb.append("- When the task is complete, return a concise final response WITHOUT a tool call.\n");
        sb.append("- Do not claim success before tool results provide evidence.\n");
        sb.append("- HUMAN CLARIFICATION: If you need clarification from the user or cannot locate a required file or directory, NEVER return raw text asking questions as completion. You MUST call the 'ask_user' tool with your question. Calling 'ask_user' pauses execution and opens an interactive prompt for the user.\n");
        sb.append("- If you perform a 'desktop_automation' action that opens an application or triggers a slow UI update, you MUST use the 'delay' action to wait before taking the next step.\n");
        sb.append("- WINDOWS FILE/FOLDER LAUNCH: When asked to open a folder or show a file in File Explorer, use 'screen_recorder' action 'open_folder' or 'explorer.exe /select,\"<file_path>\"'. NEVER use 'start \"<path>\"' because cmd.exe treats the first quoted string as a window title and will not open the file or folder.\n");
        sb.append("- SCREEN RECORDING COMPLETION: Always call 'stop_recording' on 'screen_recorder' before opening, moving, or using the recorded video file so that FFmpeg finishes writing and muxing.\n");
        sb.append("- WHATSAPP STATUS POSTING: When posting photo or video status, ALWAYS pass 'file_path' with the exact local path to the image/video file or image directory. For text status, pass 'message'.\n");
        sb.append("- If inspect_ui fails or returns insufficient data, use 'screenshot' as a fallback to capture what is on screen.\n");
        sb.append("- OPENING WINDOWS APPS: To open desktop applications (e.g. Notepad, Calculator), call 'execute_command' with command 'notepad' or 'calc', or launch via Run dialog. Never use 'desktop_automation' to start an unlaunched application.\n");
        sb.append("- COUNTING / LISTING FILES: To count or list files and images in a folder, call 'list_directory' with the directory path. Never use 'desktop_automation' to inspect directories.\n");

        if (plan != null && !plan.isEmpty()) {
            sb.append("\nYour Execution Plan:\n");
            for (int i = 0; i < plan.size(); i++) {
                int pNum = i + 1;
                if (pNum == currentStepNumber) {
                    sb.append("-> Step ").append(pNum).append(": ").append(plan.get(i)).append(" [ACTIVE STEP]\n");
                } else if (pNum < currentStepNumber) {
                    sb.append("   Step ").append(pNum).append(": ").append(plan.get(i)).append(" [COMPLETED]\n");
                } else {
                    sb.append("   Step ").append(pNum).append(": ").append(plan.get(i)).append("\n");
                }
            }
            if (currentStepNumber <= plan.size()) {
                sb.append("\nCURRENT ACTIVE STEP: ").append(plan.get(currentStepNumber - 1)).append("\n");
                sb.append("CRITICAL: Execute ONLY the tool needed for this active step. Do NOT skip ahead to other steps or repeat failed calls.\n");
            }
        }

        return sb.toString();
    }

    public List<LlmMessageDTO> compactHistory(List<LlmMessageDTO> fullHistory, AgentPropertiesConfig properties) {
        if (fullHistory.isEmpty()) return fullHistory;

        List<LlmMessageDTO> compacted = new ArrayList<>(fullHistory);
        if (compacted.size() > userPreferenceService.getInt("agent.maxHistoryLength", properties.maxHistoryLength())) {
            compacted = new ArrayList<>(compacted.subList(compacted.size() - userPreferenceService.getInt("agent.maxHistoryLength", properties.maxHistoryLength()), compacted.size()));
        }

        if (true) {
            while (compacted.size() > 4) {
                int totalChars = compacted.stream().mapToInt(this::messageLength).sum();
                if (totalChars <= userPreferenceService.getInt("agent.maxHistoryChars", properties.maxHistoryChars())) {
                    break;
                }
                compacted.removeFirst();
            }
        }
        return compacted;
    }

    private int messageLength(LlmMessageDTO msg) {
        int len = 0;
        if (msg.content() != null) len += msg.content().length();
        if (msg.toolObservation() != null && msg.toolObservation().observation() != null) {
            len += msg.toolObservation().observation().length();
        }
        for (dev.mark.llm.dto.LlmToolCallDTO tc : msg.toolCalls()) {
            if (tc.arguments() != null) {
                len += tc.arguments().toString().length();
            }
        }
        return len;
    }
}


