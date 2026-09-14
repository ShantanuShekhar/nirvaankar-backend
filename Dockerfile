# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Stage 1 — Build JAR using Maven
# -----------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom and download dependencies (cache layer)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests

# Copy source code
COPY src ./src

# Build jar
RUN mvn -B -q -DskipTests package \
    && cp "$(ls target/*.jar | grep -v '\.original$' | head -n 1)" /app/app.jar


# -----------------------------------------------------------------------------
# Stage 2 — Run with lightweight JRE
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre AS runtime

ENV LANG=C.UTF-8 \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/app.jar ./nirvaankar.jar

# Expose port
EXPOSE 8080

# Run app
ENTRYPOINT ["java", "-jar", "nirvaankar.jar"]