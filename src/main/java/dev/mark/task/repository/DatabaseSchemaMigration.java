package dev.mark.task.repository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures existing persistent H2 database tables have expanded column types for LLM outputs.
 */
@Component
public class DatabaseSchemaMigration {
    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        try {
            jdbcTemplate.execute("ALTER TABLE IF EXISTS task_steps ALTER COLUMN description TEXT");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS task_steps ALTER COLUMN outcome TEXT");
            jdbcTemplate.execute("ALTER TABLE IF EXISTS task_steps ALTER COLUMN tool_name TEXT");
            // Bypass H2 strict enum validation which breaks when new enums are added
            jdbcTemplate.execute("ALTER TABLE IF EXISTS tasks ALTER COLUMN status VARCHAR(255)");
            log.info("Database schema verified: task_steps columns upgraded to TEXT.");
        } catch (Exception e) {
            log.warn("Notice during schema migration: {}", e.getMessage());
        }
    }
}
