#!/bin/bash
set -e

# Start the Kafka Streams candle processor in the background.
# It has no HTTP server and runs independently of Spring Boot.
java ${JAVA_OPTS} -jar /app/candle-processor.jar &
CANDLE_PID=$!

echo "Candle processor started (PID $CANDLE_PID)"

cleanup() {
    echo "Shutting down candle processor (PID $CANDLE_PID)..."
    kill "$CANDLE_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Start the Spring Boot dashboard in the foreground.
# The container lives as long as this process does.
exec java ${JAVA_OPTS} \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /app/dashboard.jar
