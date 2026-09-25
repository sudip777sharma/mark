package dev.mark;

import dev.mark.agent.config.AgentPropertiesConfig;
import dev.mark.tool.config.BrowserPropertiesConfig;
import dev.mark.tool.config.FileSystemPropertiesConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * - Acts as the primary entry point and main bootstrap class for the Spring Boot application.
 * - Centralizes configuration binding by enabling external properties for agents, browsers, file systems, and LLMs for modular setup.
 * - Sits at the root of the application architecture, initializing the Spring container and launching execution.
 * - Contains the main method, which uses SpringApplicationBuilder to configure and run the application context in non-headless mode.
 * - Integrates application initialization into the standard Spring Boot lifecycle, triggering the startup of all dependent modules.
 */

/**
 * - Acts as the primary entry point and main bootstrap class for the Spring Boot application.
 * - Centralizes configuration binding by enabling external properties for agents, browsers, file systems, and LLMs for modular setup.
 * - Sits at the root of the application architecture, initializing the Spring container and launching execution.
 * - Contains the main method, which uses SpringApplicationBuilder to configure and run the application context in non-headless mode.
 * - Integrates application initialization into the standard Spring Boot lifecycle, triggering the startup of all dependent modules.
 */

/**
 * - Acts as the primary entry point and main bootstrap class for the Spring Boot application.
 * - Centralizes configuration binding by enabling external properties for agents, browsers, file systems, and LLMs, making setup modular and maintainable.
 * - Sits at the absolute root of the application architecture, initializing the Spring container and launching execution.
 * - Contains a main method that uses SpringApplicationBuilder to configure and run the application context in non-headless mode.
 * - Integrates application initialization into the standard Spring Boot lifecycle, triggering the startup of all dependent modules and services.
 */

/**
 * - Acts as the primary entry point for the Spring Boot application, bootstrapping and launching the system.
 * - Centralizes configuration binding by enabling external properties for agents, browsers, file systems, and LLMs.
 * - Sits at the absolute root of the application flow, initializing the Spring container and launching execution.
 * - Relies on the main method to configure the application context in non-headless mode using SpringApplicationBuilder.
 * - Integrates application initialization into standard Spring Boot lifecycle logic to start all dependent modules.
 */

@SpringBootApplication
@EnableConfigurationProperties({
    AgentPropertiesConfig.class,
    BrowserPropertiesConfig.class,
    FileSystemPropertiesConfig.class
})
public class MarkApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(MarkApplication.class).headless(false).run(args);
    }
}
