package dev.mark.agent.model.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionParserTest {

    @Test
    void testPolymorphicDeserialization() throws JsonProcessingException {
        String json = """
        [
            { "type": "click", "targetId": "ui-1", "button": "left" },
            { "type": "type", "targetId": "ui-2", "text": "hello" },
            { "type": "verify", "targetId": "ui-3", "expectedState": "visible", "timeoutMs": 5000 },
            { "type": "wait", "durationMs": 1000 }
        ]
        """;

        ObjectMapper mapper = new ObjectMapper();
        List<Action> actions = mapper.readValue(json, new TypeReference<List<Action>>() {});

        assertEquals(4, actions.size());
        
        assertInstanceOf(ClickAction.class, actions.get(0));
        assertEquals("ui-1", ((ClickAction) actions.get(0)).targetId());
        
        assertInstanceOf(TypeAction.class, actions.get(1));
        assertEquals("hello", ((TypeAction) actions.get(1)).text());
        
        assertInstanceOf(VerifyAction.class, actions.get(2));
        assertEquals("visible", ((VerifyAction) actions.get(2)).expectedState());
        
        assertInstanceOf(WaitAction.class, actions.get(3));
        assertEquals(1000, ((WaitAction) actions.get(3)).durationMs());
    }
}
