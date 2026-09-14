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
 * These require a working Docker daemon and must not run inside a Docker image
 * build (Docker-in-Docker). Exclude with {@code -Pdocker-build} /
 * {@code -Dsurefire.excludedGroups=integration}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Tag("integration")
public @interface IntegrationTest {
}
