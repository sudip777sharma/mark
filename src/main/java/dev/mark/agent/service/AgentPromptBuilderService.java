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
 * Collectively, {@code AgentPromptBuilderService} acts as the context generator and memory regulator for the MARK framework.
 * It bridges domain state components (World State, Tool Registries, System Configs) and LLM interaction interfaces.
 * By combining runtime telemetry, dynamic tool definitions, behavioral guardrails, and automated history compaction into unified outputs,
 * it enables the MARK agent core to execute multi-turn desktop and browser automations safely, deterministically, and within context limits.
 */

@Service
public class AgentPromptBuilderService {
    private final ToolRegistry toolRegistry;

    public AgentPromptBuilderService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public String systemPromptForPlanning() {
        return "You are a master planner for an autonomous agent. Break down the user's goal into a sequential, logical list of steps using ONLY the available tools. Keep steps concise and actionable.";
    }

    public String systemPromptForExecution(List<String> plan, AgentWorldStateModel worldState, String stateDelta) {
        StringBuilder sb = new StringBuilder();
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
        sb.append("- Match the task to the most specific tool. For file operations use read_file/write_file/list_directory, for math use calculate, for echoing use echo.\n");
        sb.append("- Return exactly one tool call when work remains.\n");
        sb.append("- When the task is complete, return a concise final response WITHOUT a tool call.\n");
        sb.append("- Do not claim success before tool results provide evidence.\n");
        sb.append("- If you perform a desktop_automation action that opens an application or triggers a slow UI update, you MUST use the 'delay' action to wait before taking the next step.\n");
        sb.append("- If you perform a browser action (navigate, click, type), you MUST verify the result by using browser_read_page or browser_extract before returning COMPLETED.\n");
        sb.append("- DESKTOP UI WORKFLOW: Before interacting with a native desktop application, ALWAYS use 'inspect_ui' first to discover UI elements and their bounding rectangles. Use these coordinates for desktop_automation actions.\n");
        sb.append("- When using desktop_automation for a specific application, ALWAYS set the 'target_window' parameter to the application's title substring. This ensures focus is verified and restored before each action.\n");
        sb.append("- If inspect_ui fails or returns insufficient data for a UI element, use 'screenshot' as a fallback to capture what is on screen.\n");

        if (plan != null && !plan.isEmpty()) {
            sb.append("\nYour Execution Plan:\n");
            for (int i = 0; i < plan.size(); i++) {
                sb.append((i + 1)).append(". ").append(plan.get(i)).append("\n");
            }
            sb.append("\nFollow this plan closely, step by step.");
        }

        return sb.toString();
    }

    public List<LlmMessageDTO> compactHistory(List<LlmMessageDTO> fullHistory, AgentPropertiesConfig properties) {
        if (fullHistory.isEmpty()) return fullHistory;

        List<LlmMessageDTO> compacted = new ArrayList<>(fullHistory);
        if (properties.maxHistoryLength() != null && compacted.size() > properties.maxHistoryLength()) {
            compacted = new ArrayList<>(compacted.subList(compacted.size() - properties.maxHistoryLength(), compacted.size()));
        }

        if (properties.maxHistoryChars() != null) {
            while (compacted.size() > 4) {
                int totalChars = compacted.stream().mapToInt(this::messageLength).sum();
                if (totalChars <= properties.maxHistoryChars()) {
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
