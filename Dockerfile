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

# Copy and set up the entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 28080

ENTRYPOINT ["/app/entrypoint.sh"]
