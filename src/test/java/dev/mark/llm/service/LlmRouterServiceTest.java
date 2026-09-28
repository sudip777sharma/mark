package dev.mark.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.llm.dto.LlmRequestDTO;
import dev.mark.llm.entity.LlmProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmRouterServiceTest {

    @Mock
    private GeminiLlmProviderService geminiProvider;

    @Mock
    private LlmSettingsService llmSettingsService;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private ObjectMapper objectMapper;

    private LlmRouterService routerService;

    @BeforeEach
    void setUp() {
        routerService = new LlmRouterService(geminiProvider, llmSettingsService, restClientBuilder, objectMapper);
    }

    @Test
    void testRoutesToGeminiWhenConfigIsGemini() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(1L, "sys", "user", List.of(), List.of(), null);
        LlmProviderConfig config = new LlmProviderConfig("gemini-test", "gemini", "gemini-1.5-pro", "url", true, List.of());
        config.setId(1L);

        when(llmSettingsService.getConfig(1L)).thenReturn(Optional.of(config));
        
        // Act
        // Because geminiProvider is a mock, calling complete on it returns null by default.
        // We just want to verify geminiProvider was called.
        routerService.complete(request);

        // Assert
        verify(geminiProvider, times(1)).complete(request);
    }

    @Test
    void testRoutesToOpenAiCompatibleProviderWhenConfigIsGroq() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(2L, "sys", "user", List.of(), List.of(), null);
        LlmProviderConfig config = new LlmProviderConfig("groq-test", "groq", "llama-4-scout", "https://api.groq.com", false, List.of());
        config.setId(2L);

        when(llmSettingsService.getConfig(2L)).thenReturn(Optional.of(config));
        when(llmSettingsService.getNextApiKey(2L)).thenReturn("gsk-test-key");
        
        lenient().when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        
        RestClient mockRestClient = mock(RestClient.class);
        lenient().when(restClientBuilder.build()).thenReturn(mockRestClient);
        
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        lenient().when(mockRestClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.contentType(any())).thenReturn(mock(RestClient.RequestBodySpec.class)); // Just for setup, it will throw NPE if deeper methods are called in complete(), but we expect the LlmProviderException on error.

        // Act
        // The OpenAiCompatibleProviderService will attempt to make a request and likely fail because of partial mocks.
        // But we can assert that it did NOT call gemini, and it fetched the next api key from settings.
        try {
            routerService.complete(request);
        } catch (Exception e) {
            // Expected since network call is mocked out incompletely
        }

        // Assert
        verify(geminiProvider, never()).complete(request);
        verify(llmSettingsService, times(1)).getNextApiKey(2L);
    }
}
