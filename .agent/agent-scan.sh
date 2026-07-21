#!/bin/bash
echo "[AI-Agent-Runner] Running RefactorScanner (Static Code Rule Inspection)..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
"$SCRIPT_DIR/agent-test.sh" "-Dtest=cn.net.pap.example.devtools.RefactorScanner" "$@"
