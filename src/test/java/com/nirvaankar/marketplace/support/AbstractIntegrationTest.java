package com.nirvaankar.marketplace.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Real MySQL, not H2. H2 would silently accept things this schema depends on
 * and then behave differently: STORED generated columns, FOR UPDATE SKIP
 * LOCKED, JSON functions, case-insensitive collation and RANGE partitioning
 * all differ. A green test on H2 would tell us nothing.
 * <p>
 * The container is static, so one MySQL is shared by the whole suite instead
 * of being started per class.
 */
@SpringBootTest
@ActiveProfiles("test")
@IntegrationTest
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("nirvaankar")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+00:00")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                + "&rewriteBatchedStatements=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
