#!/bin/bash
echo "[AI-Agent-Runner] Running Checkstyle & Javadoc Inspection..."

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if command -v mvn &> /dev/null; then
    echo "[AI-Agent-Runner] Found system 'mvn', executing..."
    mvn checkstyle:check "-Dmaven.gitcommitid.skip=true" "-Dfile.encoding=UTF-8" "$@" "-Dmaven-checkstyle-plugin.skip=false"
else
    echo "[AI-Agent-Runner] System 'mvn' not found. Falling back to project wrapper 'mvnw'..."
    "$SCRIPT_DIR/../mvnw" checkstyle:check "-Dmaven.gitcommitid.skip=true" "-Dfile.encoding=UTF-8" "$@" "-Dmaven-checkstyle-plugin.skip=false"
fi
