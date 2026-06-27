#!/bin/bash
set -euo pipefail

DATA=/data

# ── 1. Validate required env vars ─────────────────────────────────────────────
if [ -z "${KAFKA_BOOTSTRAP_SERVERS:-}" ]; then
    echo "[publisher] ERROR: KAFKA_BOOTSTRAP_SERVERS is required but not set." >&2
    exit 1
fi

ARTIFACT_PATH="${ARTIFACT_PATH:-$DATA/shards_csv}"

if [ ! -d "$ARTIFACT_PATH" ]; then
    echo "[publisher] ERROR: Shards directory not found at ARTIFACT_PATH=$ARTIFACT_PATH" >&2
    echo "[publisher] Seed the Railway Volume with shards_csv before starting." >&2
    exit 1
fi

CSV_COUNT=$(find "$ARTIFACT_PATH" -name "*.csv" | wc -l)
echo "[publisher] Found $CSV_COUNT CSV shard files at $ARTIFACT_PATH"

# ── 2. security_mapping.json: prefer volume path, fall back to baked-in copy ──
MAPPING_FILE="${MAPPING_FILE:-$DATA/security_mapping.json}"
if [ ! -f "$MAPPING_FILE" ]; then
    cp /app/security_mapping.json "$MAPPING_FILE"
    echo "[publisher] Copied security_mapping.json from image to $MAPPING_FILE"
fi

mkdir -p "$DATA/metrics_output"

# ── 3. Generate config/application.yaml from env vars ─────────────────────────
mkdir -p /app/config

CONFIG="replay:
  horizon_start: \"${REPLAY_HORIZON_START:-2025-04-02T10:00:00Z}\"
  artifact_path: \"$ARTIFACT_PATH\"
  scale_policy: \"${REPLAY_SCALE_POLICY:-session_rebase}\""

# Only emit single_session_date when explicitly set (blank → horizon mode).
if [ -n "${REPLAY_SINGLE_SESSION_DATE:-}" ]; then
    CONFIG="$CONFIG
  single_session_date: \"${REPLAY_SINGLE_SESSION_DATE}\""
fi

CONFIG="$CONFIG
kafka:
  bootstrap_servers: \"${KAFKA_BOOTSTRAP_SERVERS}\"
  topic_prefix: \"${KAFKA_TOPIC_PREFIX:-stock-ticks}\"
  linger_ms: ${KAFKA_LINGER_MS:-5}
  batch_size: ${KAFKA_BATCH_SIZE:-16384}
  acks: \"${KAFKA_ACKS:-1}\"
  max_in_flight: ${KAFKA_MAX_IN_FLIGHT:-100}
  partitions: ${KAFKA_PARTITIONS:-32}
scheduler:
  thread_pool_size: ${SCHEDULER_THREAD_POOL_SIZE:-8}
  dispatch_interval_ms: ${SCHEDULER_DISPATCH_INTERVAL_MS:-10}
  lag_threshold_warn_ms: ${SCHEDULER_LAG_THRESHOLD_WARN_MS:-1000}
  lag_threshold_fail_ms: ${SCHEDULER_LAG_THRESHOLD_FAIL_MS:-4000}
symbols:
  mapping_file: \"$MAPPING_FILE\"
metrics:
  output_dir: \"$DATA/metrics_output\""

printf '%s\n' "$CONFIG" > /app/config/application.yaml
echo "[publisher] Config written to /app/config/application.yaml:"
cat /app/config/application.yaml

# ── 4. Launch ──────────────────────────────────────────────────────────────────
echo "[publisher] Starting EGX Publisher Service..."
exec java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -jar /app/publisher-service.jar \
    /app/config/application.yaml
