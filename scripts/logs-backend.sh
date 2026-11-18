#!/bin/bash

# Tail backend logs
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${SCRIPT_DIR}/logs/backend.log"

if [ ! -f "$LOG_FILE" ]; then
    echo "Backend log file not found: $LOG_FILE"
    echo "Make sure the backend is running (./scripts/start.sh)"
    exit 1
fi

echo "Tailing backend logs (Ctrl+C to stop)..."
echo "Log file: $LOG_FILE"
echo "=========================================="
tail -f "$LOG_FILE"
