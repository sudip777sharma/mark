package dev.mark.tool.impl.browser;

import dev.mark.tool.util.BrowserSession;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BrowserInspectTool implements Tool {

    private final BrowserSession browserSession;
    private final ObjectMapper objectMapper;

    public BrowserInspectTool(BrowserSession browserSession, ObjectMapper objectMapper) {
        this.browserSession = browserSession;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "browser_inspect";
    }

    @Override
    public String description() {
        return "Inspects the current web page and returns a list of interactive elements (buttons, links, inputs) that are ACTUALLY visible and not blocked by overlays/modals. Use this when your clicks fail to find the correct elements or when you need spatial awareness.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of()
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResultDTO(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
            
            String js = """
                () => {
                  function isVisibleAndNotBlocked(el) {
                    const rect = el.getBoundingClientRect();
                    if(rect.width === 0 || rect.height === 0) return false;
                    const style = window.getComputedStyle(el);
                    if(style.visibility === 'hidden' || style.display === 'none' || style.opacity === '0') return false;
                    
                    const x = rect.left + rect.width / 2;
                    const y = rect.top + rect.height / 2;
                    const topEl = document.elementFromPoint(x, y);
                    if(!topEl) return false;
                    return el.contains(topEl) || topEl.contains(el);
                  }
                  
                  const selectors = 'a, button, input, select, textarea, [role="button"], [role="link"], [role="menuitem"], [role="tab"], [role="checkbox"], [role="radio"]';
                  return Array.from(document.querySelectorAll(selectors))
                    .filter(isVisibleAndNotBlocked)
                    .map(el => {
                      let text = el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || '';
                      text = text.trim().substring(0, 50); // limit length
                      
                      let selector = el.tagName.toLowerCase();
                      if (el.id) selector += '#' + el.id;
                      if (el.className && typeof el.className === 'string') {
                         selector += '.' + el.className.trim().replace(/\\s+/g, '.');
                      }
                      
                      return {
                        tag: el.tagName.toLowerCase(),
                        text: text,
                        type: el.getAttribute('type') || undefined,
                        placeholder: el.getAttribute('placeholder') || undefined,
                        suggestedSelector: selector
                      };
                    });
                }
            """;
            
            Object result = page.evaluate(js);
            String jsonSnapshot = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            
            return new ToolResultDTO(true, "Interactable Elements Currently Visible:\n" + jsonSnapshot, Map.of());
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to inspect page: " + e.getMessage(), Map.of());
        }
    }
}
