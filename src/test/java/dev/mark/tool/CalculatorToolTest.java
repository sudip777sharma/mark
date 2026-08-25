package dev.mark.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalculatorToolTest {
    private final CalculatorTool calculator = new CalculatorTool();

    private ToolResult eval(String expression) {
        return calculator.execute(new ToolRequest(UUID.randomUUID(), "calculate", Map.of("expression", expression)));
    }

    // --- happy-path tests ---

    @Test void simpleAddition() {
        ToolResult result = eval("2+3");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("5"));
    }

    @Test void operatorPrecedence() {
        ToolResult result = eval("2+3*4");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("14"));
    }

    @Test void parenthesesOverridePrecedence() {
        ToolResult result = eval("(2+3)*4");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("20"));
    }

    @Test void decimalDivision() {
        ToolResult result = eval("10/4");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("2.5"));
    }

    @Test void unaryNegative() {
        ToolResult result = eval("-5+3");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("-2"));
    }

    @Test void nestedParentheses() {
        ToolResult result = eval("((1+2)*(3+4))");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("21"));
    }

    @Test void wholeNumberFormattedWithoutDecimal() {
        ToolResult result = eval("3+7");
        assertTrue(result.successful());
        assertEquals("Result: 10", result.observation());
    }

    // --- error-path tests ---

    @Test void divisionByZeroReturnsFailed() {
        ToolResult result = eval("1/0");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("Division by zero"));
    }

    @Test void malformedExpressionReturnsFailed() {
        ToolResult result = eval("2++3");
        assertFalse(result.successful());
    }

    @Test void emptyExpressionReturnsFailed() {
        ToolResult result = eval("");
        assertFalse(result.successful());
    }

    @Test void missingArgumentReturnsFailed() {
        ToolResult result = calculator.execute(new ToolRequest(UUID.randomUUID(), "calculate", Map.of()));
        assertFalse(result.successful());
    }

    @Test void nonStringArgumentReturnsFailed() {
        ToolResult result = calculator.execute(new ToolRequest(UUID.randomUUID(), "calculate", Map.of("expression", 42)));
        assertFalse(result.successful());
    }
}
