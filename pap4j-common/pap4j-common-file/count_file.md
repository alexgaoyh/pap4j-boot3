## 文件夹-文件数量 统计脚本 (count_files.bat)
```shell
@echo off
REM ============================================================================
REM 文件名称: count_files.bat
REM 功能描述: 递归统计指定文件夹及其所有子文件夹中的文件数量
REM            显示每个目录的文件数量，并汇总文件夹总数和文件总数
REM 使用语法: count_files.bat [文件夹路径]
REM            如果不提供参数，会提示输入文件夹路径
REM ============================================================================

chcp 65001 >nul
setlocal enabledelayedexpansion

echo.
echo === Directory File Counter ===
echo.

REM 检查是否提供了文件夹路径
if "%~1"=="" (
    set /p "root_folder=Enter root folder path: "
) else (
    set "root_folder=%~1"
)

set "root_folder=%root_folder:"=%"

REM 检查文件夹是否存在
if not exist "%root_folder%" (
    echo Error: Folder does not exist - "%root_folder%"
    pause
    exit /b 1
)

echo Scanning: %root_folder%
echo Please wait...
echo.

set total_files=0
set folder_count=0

REM 先统计根目录的文件
set root_count=0
for /f "delims=" %%f in ('dir /a-d /b "%root_folder%" 2^>nul') do (
    set /a root_count+=1
    set /a total_files+=1
)

if !root_count! gtr 0 (
    echo [ROOT] %root_folder% : !root_count! files
) else (
    echo [ROOT] %root_folder% : 0 files
)
set /a folder_count+=1

echo ----------------------------------------

REM 统计所有子目录的文件
for /f "delims=" %%d in ('dir /ad /b /s "%root_folder%" 2^>nul') do (
    set count=0
    for /f "delims=" %%f in ('dir /a-d /b "%%d" 2^>nul') do (
        set /a count+=1
        set /a total_files+=1
    )
    echo %%d : !count! files
    set /a folder_count+=1
)

echo ========================================
echo SUMMARY:
echo Total Folders: !folder_count!
echo Total Files: !total_files!
echo ========================================

pause
```