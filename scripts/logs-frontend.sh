#!/bin/bash

# Tail frontend logs
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${SCRIPT_DIR}/logs/frontend.log"

if [ ! -f "$LOG_FILE" ]; then
    echo "Frontend log file not found: $LOG_FILE"
    echo "Make sure the frontend is running (./scripts/start.sh)"
    exit 1
fi

echo "Tailing frontend logs (Ctrl+C to stop)..."
echo "Log file: $LOG_FILE"
echo "=========================================="
tail -f "$LOG_FILE"
