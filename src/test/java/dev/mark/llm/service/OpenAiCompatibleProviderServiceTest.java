package dev.mark.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.llm.dto.LlmRequestDTO;
import dev.mark.llm.exception.LlmProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleProviderServiceTest {

    @Mock
    private LlmSettingsService llmSettingsService;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private OpenAiCompatibleProviderService service;

    private final Long testConfigId = 100L;
    private final String testApiKey = "sk-test-key";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Mock RestClient builder chain
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        // Mock restClient execution chain
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        
        service = new OpenAiCompatibleProviderService(
                testConfigId, "Groq", "https://api.groq.com/openai/v1/chat/completions",
                testApiKey, "llama-4-scout", restClientBuilder, objectMapper, llmSettingsService);
    }

    @Test
    void testHandleRateLimit_DeactivatesKeyOnFreeTierLimit() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(testConfigId, "sys", "user", List.of(), List.of(), null);
        
        RestClientResponseException exception = mock(RestClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS); // 429
        when(exception.getResponseBodyAsString()).thenReturn("{\"error\": {\"message\": \"You have reached your free tier limit per day.\"}}");
        
        when(requestBodySpec.retrieve()).thenThrow(exception);

        // Act & Assert
        assertThrows(LlmProviderException.class, () -> service.complete(request));

        // Verify that the key was marked as inactive
        verify(llmSettingsService, times(1)).markKeyAsInactive(testConfigId, testApiKey);
    }
    
    @Test
    void testHandleRateLimit_DeactivatesKeyOnInsufficientQuota_402() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(testConfigId, "sys", "user", List.of(), List.of(), null);
        
        RestClientResponseException exception = mock(RestClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(HttpStatus.PAYMENT_REQUIRED); // 402
        when(exception.getResponseBodyAsString()).thenReturn("{\"error\": {\"message\": \"insufficient_quota\"}}");
        
        when(requestBodySpec.retrieve()).thenThrow(exception);

        // Act & Assert
        assertThrows(LlmProviderException.class, () -> service.complete(request));

        // Verify that the key was marked as inactive
        verify(llmSettingsService, times(1)).markKeyAsInactive(testConfigId, testApiKey);
    }

    @Test
    void testHandleRateLimit_DoesNotDeactivateKeyOnTemporaryRateLimit() {
        // Arrange
        LlmRequestDTO request = new LlmRequestDTO(testConfigId, "sys", "user", List.of(), List.of(), null);
        
        RestClientResponseException exception = mock(RestClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS); // 429
        when(exception.getResponseBodyAsString()).thenReturn("{\"error\": {\"message\": \"Rate limit exceeded. Please try again in 20s.\"}}"); // Doesn't contain permanent quota keywords
        
        when(requestBodySpec.retrieve()).thenThrow(exception);

        // Act & Assert
        assertThrows(LlmProviderException.class, () -> service.complete(request));

        // Verify that the key was NOT marked as inactive
        verify(llmSettingsService, never()).markKeyAsInactive(anyLong(), anyString());
    }
}
