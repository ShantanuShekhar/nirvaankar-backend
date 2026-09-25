package com.nirvaankar.marketplace.support;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks Testcontainers-backed Spring Boot tests.
 * <p>
 * These require a working Docker daemon. They are excluded from default {@code mvn test}
 * (see {@code surefire.excludedGroups=integration} in the root POM). Run with
 * {@code mvn test -Pintegration} when Docker is available.
 * <p>
 * Docker image builds use {@code -Pdocker-build} (same exclusion, no Docker-in-Docker).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Tag("integration")
public @interface IntegrationTest {
}
