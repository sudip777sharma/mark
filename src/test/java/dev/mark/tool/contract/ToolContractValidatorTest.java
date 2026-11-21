package dev.mark.tool.contract;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolContractValidatorTest {

    @Test
    void rejectsMissingRequiredParameter() {
        ToolContract contract = ToolContract.builder("test_tool")
                .parameter(ToolParameterSchema.requiredString("target_file", "The file to process"))
                .build();

        var result = ToolContractValidator.validate(contract, Map.of());
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required parameter 'target_file'")));
    }

    @Test
    void rejectsUnexpectedParametersWhenStrict() {
        ToolContract contract = ToolContract.builder("strict_tool")
                .parameter(ToolParameterSchema.requiredString("name", "Name"))
                .allowUnknownParameters(false)
                .build();

        var result = ToolContractValidator.validate(contract, Map.of("name", "valid", "extra_param", 123));
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unexpected argument 'extra_param'")));
    }

    @Test
    void permitsBenignLlmMetaParameters() {
        ToolContract contract = ToolContract.builder("strict_tool")
                .parameter(ToolParameterSchema.requiredString("name", "Name"))
                .allowUnknownParameters(false)
                .build();

        var result = ToolContractValidator.validate(contract, Map.of("name", "valid", "thought", "processing"));
        assertTrue(result.valid());
        assertEquals("valid", result.normalizedArguments().get("name"));
    }

    @Test
    void enforcesNumericBoundaries() {
        ToolContract contract = ToolContract.builder("boundary_tool")
                .parameter(ToolParameterSchema.requiredInt("count", "Count", 1, 10))
                .build();

        var resultLow = ToolContractValidator.validate(contract, Map.of("count", 0));
        assertFalse(resultLow.valid());
        assertTrue(resultLow.errors().stream().anyMatch(e -> e.contains("less than minimum allowed")));

        var resultHigh = ToolContractValidator.validate(contract, Map.of("count", 15));
        assertFalse(resultHigh.valid());
        assertTrue(resultHigh.errors().stream().anyMatch(e -> e.contains("exceeds maximum allowed")));

        var resultValid = ToolContractValidator.validate(contract, Map.of("count", 5));
        assertTrue(resultValid.valid());
    }

    @Test
    void enforcesAllowedEnumValues() {
        ToolContract contract = ToolContract.builder("enum_tool")
                .parameter(ToolParameterSchema.enumString("action", "Action", true, Set.of("create", "delete")))
                .build();

        var resultInvalid = ToolContractValidator.validate(contract, Map.of("action", "unknown"));
        assertFalse(resultInvalid.valid());
        assertTrue(resultInvalid.errors().stream().anyMatch(e -> e.contains("not in allowed values")));

        var resultValid = ToolContractValidator.validate(contract, Map.of("action", "create"));
        assertTrue(resultValid.valid());
    }
}
