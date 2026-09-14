# Multi-stage build for nirvaankar-marketplace-backend.
# Unit + ArchUnit tests run during the image build.
# Testcontainers integration tests are excluded (-Pdocker-build) because they
# require a Docker daemon and must not nest Docker-in-Docker here.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -Pdocker-build clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /workspace/target/marketplace-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
