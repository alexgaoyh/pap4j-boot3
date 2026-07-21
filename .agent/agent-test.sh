#!/bin/bash
echo "[AI-Agent-Runner] Starting Maven Test with Agent Profile..."

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if command -v mvn &> /dev/null; then
    echo "[AI-Agent-Runner] Found system 'mvn', executing..."
    mvn -Pagent clean "$@"
else
    echo "[AI-Agent-Runner] System 'mvn' not found. Falling back to project wrapper 'mvnw'..."
    "$SCRIPT_DIR/../mvnw" -Pagent clean "$@"
fi
