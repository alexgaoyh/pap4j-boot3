@echo off
chcp 65001 > nul
echo "[AI-Agent-Runner] Running Checkstyle & Javadoc Inspection..."

where mvn >nul 2>nul
if %errorlevel% equ 0 (
    echo [AI-Agent-Runner] Found system 'mvn', executing...
    mvn checkstyle:check "-Dmaven.gitcommitid.skip=true" "-Dfile.encoding=UTF-8" %* "-Dmaven-checkstyle-plugin.skip=false"
) else (
    echo [AI-Agent-Runner] System 'mvn' not found. Falling back to project wrapper 'mvnw.cmd'...
    "%~dp0\..\mvnw.cmd" checkstyle:check "-Dmaven.gitcommitid.skip=true" "-Dfile.encoding=UTF-8" %* "-Dmaven-checkstyle-plugin.skip=false"
)
