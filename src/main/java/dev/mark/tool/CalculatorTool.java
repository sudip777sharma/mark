package dev.mark.tool;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic calculator tool.
 *
 * <p>Evaluates a mathematical expression string containing {@code +}, {@code -},
 * {@code *}, {@code /}, parentheses, unary minus, and decimal numbers.
 *
 * <p>Uses a hand-written recursive-descent parser so that only arithmetic is
 * ever evaluated — no {@code ScriptEngine} or {@code eval} that could execute
 * arbitrary code from untrusted LLM input.
 *
 * <p>Grammar (informal):
 * <pre>
 *   expression = term (('+' | '-') term)*
 *   term       = factor (('*' | '/') factor)*
 *   factor     = '-' factor | '(' expression ')' | number
 *   number     = digit+ ('.' digit+)?
 * </pre>
 */
@Component
public class CalculatorTool implements Tool {

    @Override
    public String name() { return "calculate"; }

    @Override
    public String description() {
        return "Evaluate a mathematical expression. Supports +, -, *, /, parentheses, and decimal numbers.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "Mathematical expression to evaluate, e.g. (2+3)*4")),
                "required", List.of("expression"));
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Object raw = request.arguments().get("expression");
        if (!(raw instanceof String expression) || expression.isBlank()) {
            return new ToolResult(false, "calculate requires a non-blank string expression argument", Map.of());
        }
        try {
            double result = evaluate(expression);
            String formatted = format(result);
            return ToolResult.success("Result: " + formatted);
        } catch (ArithmeticException e) {
            return new ToolResult(false, "Arithmetic error: " + e.getMessage(), Map.of());
        } catch (IllegalArgumentException e) {
            return new ToolResult(false, "Invalid expression: " + e.getMessage(), Map.of());
        }
    }

    // ---- recursive-descent parser ----

    static double evaluate(String expression) {
        Parser parser = new Parser(expression);
        double result = parser.parseExpression();
        if (parser.hasMore()) {
            throw new IllegalArgumentException("Unexpected character at position " + parser.pos + ": '" + parser.peek() + "'");
        }
        return result;
    }

    private static String format(double value) {
        if (value == (long) value && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /**
     * Minimal recursive-descent parser.  Holds the input string and a current
     * position; each {@code parse*} method advances the position as it consumes
     * tokens.
     */
    private static final class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input.replaceAll("\\s+", "");
            if (this.input.isEmpty()) {
                throw new IllegalArgumentException("Empty expression");
            }
        }

        // expression = term (('+' | '-') term)*
        double parseExpression() {
            double result = parseTerm();
            while (hasMore() && (peek() == '+' || peek() == '-')) {
                char op = advance();
                double right = parseTerm();
                result = op == '+' ? result + right : result - right;
            }
            return result;
        }

        // term = factor (('*' | '/') factor)*
        private double parseTerm() {
            double result = parseFactor();
            while (hasMore() && (peek() == '*' || peek() == '/')) {
                char op = advance();
                double right = parseFactor();
                if (op == '/') {
                    if (right == 0) throw new ArithmeticException("Division by zero");
                    result /= right;
                } else {
                    result *= right;
                }
            }
            return result;
        }

        // factor = '-' factor | '(' expression ')' | number
        private double parseFactor() {
            if (!hasMore()) throw new IllegalArgumentException("Unexpected end of expression");
            if (peek() == '-') {
                advance();
                return -parseFactor();
            }
            if (peek() == '(') {
                advance(); // consume '('
                double result = parseExpression();
                if (!hasMore() || peek() != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                advance(); // consume ')'
                return result;
            }
            return parseNumber();
        }

        private double parseNumber() {
            int start = pos;
            while (hasMore() && (Character.isDigit(peek()) || peek() == '.')) {
                advance();
            }
            if (pos == start) {
                throw new IllegalArgumentException("Expected a number at position " + pos + " but found: '" + (hasMore() ? peek() : "end") + "'");
            }
            String token = input.substring(start, pos);
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: '" + token + "'");
            }
        }

        boolean hasMore() { return pos < input.length(); }
        private char peek() { return input.charAt(pos); }
        private char advance() { return input.charAt(pos++); }
    }
}
