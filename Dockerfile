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

# Install gosu for user switching
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends gosu; \
    rm -rf /var/lib/apt/lists/*; \
    gosu nobody true

# Create appuser (use existing group if GID 1000 exists)
RUN (groupadd -r -g 1000 appuser 2>/dev/null || groupadd -r appuser) && \
    (useradd -r -u 1000 -g appuser appuser 2>/dev/null || useradd -r -g appuser appuser) && \
    groupadd -r docker && \
    usermod -aG docker appuser && \
    chown -R appuser:appuser /app

# Copy and set up the entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Don't set USER here - entrypoint will handle user switching after group setup

EXPOSE 28080

ENTRYPOINT ["/app/entrypoint.sh"]
