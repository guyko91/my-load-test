#!/bin/sh
set -e

# --- Build Project ---
echo "=================================================="
echo "Building the project..."
echo "=================================================="
./gradlew build

# --- Configuration ---
JAR_PATH=$(find build/libs/ -name "load-test-toy-*.jar" | head -n 1)
AGENT_PATH="aws-opentelemetry-agent.jar"
JAVA_OPTS="-Xmx1g"

# --- Pre-flight Checks ---
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ Application JAR not found at '$JAR_PATH'"
    echo "Build failed or JAR name changed. Exiting."
    exit 1
fi

if [ ! -f "$AGENT_PATH" ]; then
    echo "❌ OpenTelemetry Agent not found at '$AGENT_PATH'"
    echo "Please ensure 'aws-opentelemetry-agent.jar' is in the project root."
    exit 1
fi

# --- OpenTelemetry Agent Configuration ---
echo "=================================================="
echo "Setting OpenTelemetry Environment Variables"
echo "=================================================="

export OTEL_RESOURCE_ATTRIBUTES="service.name=load-test,service.namespace=test,service.version=1.0.0,env=local"
export OTEL_EXPORTER_OTLP_HEADERS="api-key=key,other-config-value=value"
export OTEL_EXPORTER_OTLP_PROTOCOL="grpc"
export OTEL_TRACES_EXPORTER="otlp"
export OTEL_METRICS_EXPORTER="otlp"
export OTEL_LOGS_EXPORTER="otlp"
# IMPORTANT: Change this endpoint if your collector is not running on this address
export OTEL_EXPORTER_OTLP_ENDPOINT="http://10.101.91.145:4317"
export OTEL_JAVAAGENT_DEBUG="true"
export OTEL_JAVA_DISABLED_RESOURCE_PROVIDERS="io.opentelemetry.contrib.aws.resource.Ec2ResourceProvider"

echo "OTEL Endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT}"
echo "JAR File:      ${JAR_PATH}"
echo "Java Options:  ${JAVA_OPTS}"
echo "=================================================="
echo "🚀 Starting Spring Boot application..."

# --- Execution ---
java $JAVA_OPTS -javaagent:"$AGENT_PATH" -jar "$JAR_PATH"
