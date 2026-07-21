@echo off
chcp 65001 > nul
echo [AI-Agent-Runner] Starting Maven Test with Agent Profile...

where mvn >nul 2>nul
if %errorlevel% equ 0 (
    echo [AI-Agent-Runner] Found system 'mvn', executing...
    mvn -Pagent clean %*
) else (
    echo [AI-Agent-Runner] System 'mvn' not found. Falling back to project wrapper 'mvnw.cmd'...
    "%~dp0\..\mvnw.cmd" -Pagent clean %*
)
