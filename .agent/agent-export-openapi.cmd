@echo off
chcp 65001 > nul
echo [AI-Agent-Runner] Exporting OpenAPI Catalog JSON Snapshot...
call "%~dp0\agent-test.cmd" "-Dtest=cn.net.pap.example.proguard.diagnostics.ApiRouterCatalogExporterTest" %*
