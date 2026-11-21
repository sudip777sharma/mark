package dev.mark.tool.contract;

import dev.mark.tool.core.Tool;

/**
 * Interface for tools that explicitly define a formal ToolContract.
 */
public interface ContractAwareTool extends Tool {
    ToolContract contract();
}
