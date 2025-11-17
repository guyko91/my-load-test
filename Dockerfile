# Stage 1: Get the Docker CLI binary
FROM docker:latest as docker-cli

# Build stage
FROM --platform=$BUILDPLATFORM gradle:8.5-jdk21 AS build
ARG TARGETPLATFORM
ARG BUILDPLATFORM
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
COPY aws-opentelemetry-agent.jar /app/aws-opentelemetry-agent.jar

# Copy the Docker CLI binary from the docker-cli stage
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

# Create a non-root user and add to docker group
USER root
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN usermod -aG docker appuser
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 28080

ENTRYPOINT ["java", "-javaagent:/app/aws-opentelemetry-agent.jar", "-jar", "app.jar"]
