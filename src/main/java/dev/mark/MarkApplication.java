package dev.mark;

import dev.mark.agent.AgentProperties;
import dev.mark.browser.BrowserProperties;
import dev.mark.llm.GeminiLlmProperties;
import dev.mark.tool.FileSystemProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
@EnableConfigurationProperties({
    AgentProperties.class,
    BrowserProperties.class,
    FileSystemProperties.class,
    GeminiLlmProperties.class
})
public class MarkApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(MarkApplication.class).headless(false).run(args);
    }
}
