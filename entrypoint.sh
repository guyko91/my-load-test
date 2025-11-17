#!/bin/sh

# OpenTelemetry Agent Configuration
export OTEL_RESOURCE_ATTRIBUTES="service.name=load-test,service.namespace=test,service.version=1.0.0,env=dev"
export OTEL_EXPORTER_OTLP_HEADERS="api-key=key,other-config-value=value"
export OTEL_EXPORTER_OTLP_PROTOCOL="grpc"
export OTEL_TRACES_EXPORTER="otlp"
export OTEL_METRICS_EXPORTER="otlp"
export OTEL_LOGS_EXPORTER="otlp"
export OTEL_EXPORTER_OTLP_ENDPOINT="http://10.101.91.145:4317"
export OTEL_JAVAAGENT_DEBUG="true"
export OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS="io.opentelemetry.contrib.aws.resource.Ec2ResourceProvider"
export ADOT_AGENT="/app/aws-opentelemetry-agent.jar"

echo "=================================================="
echo "Starting Spring Boot application with ADOT Agent"
echo "OTEL Endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT}"
echo "=================================================="

# Using exec to make the Java process the main process (PID 1)
exec java \
-javaagent:"$ADOT_AGENT" \
-jar /app/app.jar
