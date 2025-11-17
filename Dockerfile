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

# Create a non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 28080

ENTRYPOINT ["java", "-jar", "app.jar"]
