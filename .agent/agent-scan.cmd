@echo off
chcp 65001 > nul
echo [AI-Agent-Runner] Running RefactorScanner (Static Code Rule Inspection)...
call "%~dp0\agent-test.cmd" "-Dtest=cn.net.pap.example.devtools.RefactorScanner" %*
