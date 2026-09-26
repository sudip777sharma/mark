package dev.mark.llm.service;

import dev.mark.llm.entity.LlmProviderConfig;
import dev.mark.llm.repository.LlmProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LlmSettingsServiceTest {

    @Mock
    private LlmProviderConfigRepository repository;

    @InjectMocks
    private LlmSettingsService service;

    private LlmProviderConfig config;

    @BeforeEach
    void setUp() {
        config = new LlmProviderConfig();
        config.setConfigName("groq");
        config.setActiveModel("test-model");
        config.setApiKeys(List.of(new dev.mark.llm.entity.ApiKeyEntry("","key1"), new dev.mark.llm.entity.ApiKeyEntry("","key2"), new dev.mark.llm.entity.ApiKeyEntry("","key3")));
    }

    @Test
    void testGetNextApiKey_RoundRobin() {
        when(repository.findById("groq")).thenReturn(Optional.of(config));

        assertEquals("key1", service.getNextApiKey("groq"));
        assertEquals("key2", service.getNextApiKey("groq"));
        assertEquals("key3", service.getNextApiKey("groq"));
        assertEquals("key1", service.getNextApiKey("groq"));
        assertEquals("key2", service.getNextApiKey("groq"));
    }

    @Test
    void testGetNextApiKey_EmptyKeys() {
        config.setApiKeys(List.of());
        when(repository.findById("groq")).thenReturn(Optional.of(config));

        assertNull(service.getNextApiKey("groq"));
    }

    @Test
    void testGetNextApiKey_SingleKey() {
        config.setApiKeys(List.of("only-key"));
        when(repository.findById("groq")).thenReturn(Optional.of(config));

        assertEquals("only-key", service.getNextApiKey("groq"));
        assertEquals("only-key", service.getNextApiKey("groq"));
    }
}
