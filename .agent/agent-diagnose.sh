#!/bin/bash
echo "[AI-Agent-Runner] Extracting Test Failures & Diagnostics Stack Traces..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
"$SCRIPT_DIR/agent-test.sh" "-Dtest=cn.net.pap.example.devtools.DiagnosticsExtractor" "$@"
