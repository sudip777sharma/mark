package dev.mark.api.interceptor;

import dev.mark.llm.service.LlmSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SetupInterceptor implements HandlerInterceptor {

    private final LlmSettingsService llmSettingsService;

    public SetupInterceptor(LlmSettingsService llmSettingsService) {
        this.llmSettingsService = llmSettingsService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        // Let static resources and API calls (other than task creation maybe) through, but redirect web UI pages.
        if (uri.startsWith("/css") || uri.startsWith("/js") || uri.startsWith("/img") || uri.startsWith("/webjars")) {
            return true;
        }

        if (uri.startsWith("/settings") || uri.startsWith("/api/config/llm")) {
            return true;
        }

        if (!llmSettingsService.hasDefaultConfig()) {
            if (uri.startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "LLM Configuration is missing. Please configure it in settings.");
                return false;
            } else {
                response.sendRedirect("/settings");
                return false;
            }
        }

        return true;
    }
}
