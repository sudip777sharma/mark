package dev.mark.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.llm.dto.LlmRequestDTO;
import dev.mark.llm.dto.LlmResponseDTO;
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
        routerService = new LlmRouterService(geminiProvider, llmSettingsService, restClientBuilder, objectMapper, org.mockito.Mockito.mock(dev.mark.task.repository.LlmInteractionLogRepository.class));
    }

    @Test
    void testRoutesToGeminiWhenConfigIsGemini() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(1L, "sys", "user", List.of(), List.of(), null);
        LlmProviderConfig config = new LlmProviderConfig("gemini-test", "gemini", "gemini-1.5-pro", "url", true, List.of());
        config.setId(1L);

        when(llmSettingsService.getConfig(1L)).thenReturn(Optional.of(config));
        
        // Act
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
        lenient().when(uriSpec.contentType(any())).thenReturn(mock(RestClient.RequestBodySpec.class));

        // Act
        try {
            routerService.complete(request);
        } catch (Exception e) {
            // Expected since network call is mocked out incompletely
        }

        // Assert
        verify(geminiProvider, never()).complete(request);
        verify(llmSettingsService, times(1)).getNextApiKey(2L);
    }

    @Test
    void testCascadesToFallbackProviderWhenPrimaryProviderFailsWith429() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(1L, "sys", "user", List.of(), List.of(), null);

        LlmProviderConfig primaryConfig = new LlmProviderConfig("primary-cloud", "groq", "llama-3", "https://api.groq.com", false, List.of());
        primaryConfig.setId(1L);

        LlmProviderConfig fallbackConfig = new LlmProviderConfig("gemini-fallback", "gemini", "gemini-1.5-flash", "url", false, List.of());
        fallbackConfig.setId(2L);

        when(llmSettingsService.getConfig(1L)).thenReturn(Optional.of(primaryConfig));
        when(llmSettingsService.getAvailableFallbackConfigs(1L)).thenReturn(List.of(fallbackConfig));
        when(llmSettingsService.getApiKeysCount(1L)).thenReturn(1);
        when(llmSettingsService.getNextApiKey(1L)).thenReturn("key-1");

        RestClient mockRestClient = mock(RestClient.class);
        lenient().when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.build()).thenReturn(mockRestClient);
        when(mockRestClient.post()).thenThrow(new dev.mark.llm.exception.LlmProviderException("HTTP 429 Rate limit / daily quota exceeded"));

        LlmResponseDTO fallbackResponse = new LlmResponseDTO("Fallback success", "gemini-fallback", false, List.of());
        when(geminiProvider.complete(request)).thenReturn(fallbackResponse);

        // Act
        LlmResponseDTO result = routerService.complete(request);

        // Assert
        assertNotNull(result);
        assertEquals("Fallback success", result.content());
        verify(geminiProvider, times(1)).complete(request);
    }
}
