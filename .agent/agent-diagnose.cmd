@echo off
chcp 65001 > nul
echo "[AI-Agent-Runner] Extracting Test Failures & Diagnostics Stack Traces..."
call "%~dp0\agent-test.cmd" "-Dtest=cn.net.pap.example.devtools.DiagnosticsExtractor" %*
