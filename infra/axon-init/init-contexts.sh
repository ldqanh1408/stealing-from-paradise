#!/bin/bash
# This script initializes Axon Server contexts after startup

# Wait for Axon Server to be ready
sleep 10

# Create default context via HTTP API if it doesn't exist
echo "Creating default context in Axon Server..."

# Try to create the default context
curl -X POST http://localhost:8024/v1/contexts -H "Content-Type: application/json" -d '{
  "name": "default",
  "replicationGroup": "default"
}' 2>/dev/null || echo "Context creation request sent"

echo "Axon Server initialization complete"

