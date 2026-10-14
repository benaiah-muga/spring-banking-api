package com.banking.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(initializers = FlywayBaselineIT.ExistingSchemaInitializer.class)
class FlywayBaselineIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "bG9jYWwtdGVzdC1vbmx5LXByb2R1Y3Rpb24ta2V5LWRvLW5vdC1yZXVzZS0wMQ==");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }

    @Test
    void existingSchemaIsBaselinedAndDataIsKeptWhileTheSafetyMigrationRuns() {
        Integer users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer lockStripes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_locks", Integer.class);
        String currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class);

        assertThat(users).isEqualTo(1);
        assertThat(lockStripes).isEqualTo(64);
        assertThat(currentVersion).isEqualTo("2");
    }

    static class ExistingSchemaInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target("1")
                    .load()
                    .migrate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users (full_name, email, password, role, created_at, updated_at) " +
                        "VALUES ('Existing Demo User', 'existing@example.test', 'not-a-real-password', 'CUSTOMER', " +
                        "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
                statement.executeUpdate("DROP TABLE flyway_schema_history");
            } catch (Exception exception) {
                throw new IllegalStateException("Could not prepare an existing database schema", exception);
            }
        }
    }
}
