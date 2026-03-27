#!/bin/bash
# Clean up Kafka cluster ID mismatch issues on startup

KAFKA_DATA_DIR="/var/lib/kafka/data"

# If this is a fresh start or the meta.properties file is corrupted, remove it
# Kafka will regenerate it with the current cluster ID from Zookeeper
if [ -f "$KAFKA_DATA_DIR/meta.properties" ]; then
    echo "Found existing meta.properties, removing for clean startup..."
    rm -f "$KAFKA_DATA_DIR/meta.properties"
fi

# Remove any log directories to ensure clean state
if [ -d "$KAFKA_DATA_DIR/log-0" ]; then
    echo "Removing existing log directories for clean startup..."
    rm -rf "$KAFKA_DATA_DIR/log-0"
fi

echo "Starting Kafka with clean state..."
exec /etc/confluent/docker/run

