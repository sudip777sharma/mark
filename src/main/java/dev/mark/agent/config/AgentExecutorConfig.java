package dev.mark.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Provides dedicated threading resources for Mark's long-running agent tasks.
 * Separates agent execution from the web (Tomcat) and common ForkJoinPool threads,
 * preventing JVM-wide starvation when orchestrator tasks sleep (e.g., during 429 rate limits).
 */
@Configuration
public class AgentExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutorConfig.class);

    @Bean(name = "agentTaskExecutor")
    public Executor agentTaskExecutor() {
        log.info("Initializing dedicated AgentTaskExecutor with thread prefix 'agent-worker-'");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("agent-worker-");
        executor.initialize();
        return executor;
    }
}
