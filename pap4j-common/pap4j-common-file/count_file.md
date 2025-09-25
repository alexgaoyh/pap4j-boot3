## 文件夹-文件数量 统计脚本 (count_files.bat)
```shell
@echo off
REM ============================================================================
REM 文件名称: count_files.bat
REM 功能描述: 递归统计指定文件夹及其所有子文件夹中的文件数量
REM            显示每个目录的文件数量，并汇总文件夹总数和文件总数
REM 使用语法: count_files.bat [文件夹路径] [/f | -f]
REM            如果不提供参数，会提示输入文件夹路径
REM            /f 或 -f 参数：只显示包含文件的文件夹
REM ============================================================================

chcp 65001 >nul
setlocal enabledelayedexpansion

echo.
echo === Directory File Counter ===
echo.

REM 初始化参数
set "root_folder="
set "filter_empty=0"

REM 解析参数
:parse_args
if "%~1"=="" goto end_parse
if /i "%~1"=="/f" set "filter_empty=1" & shift & goto parse_args
if /i "%~1"=="-f" set "filter_empty=1" & shift & goto parse_args
if not defined root_folder set "root_folder=%~1"
shift
goto parse_args

:end_parse

REM 如果没有提供文件夹路径，提示输入
if not defined root_folder (
    set /p "root_folder=Enter root folder path: "
)

set "root_folder=%root_folder:"=%"

REM 检查文件夹是否存在
if not exist "%root_folder%" (
    echo Error: Folder does not exist - "%root_folder%"
    pause
    exit /b 1
)

echo Scanning: %root_folder%
if !filter_empty! equ 1 (
    echo Mode: Display only folders with files
) else (
    echo Mode: Display all folders
)
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

REM 显示根目录（根据filter_empty设置）
if !filter_empty! equ 0 (
    echo [ROOT] %root_folder% : !root_count! files
) else if !root_count! gtr 0 (
    echo [ROOT] %root_folder% : !root_count! files
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
    
    REM 根据filter_empty设置决定是否显示
    if !filter_empty! equ 0 (
        echo %%d : !count! files
    ) else if !count! gtr 0 (
        echo %%d : !count! files
    )
    set /a folder_count+=1
)

echo ========================================
echo SUMMARY:
echo Total Folders: !folder_count!
echo Total Files: !total_files!
if !filter_empty! equ 1 (
    echo Note: Only folders with files are displayed
)
echo ========================================

pause
```

```shell
#!/bin/bash
# ============================================================================
# 文件名称: count_files.sh
# 功能描述: Linux 递归统计指定文件夹及其所有子文件夹中的文件数量
#            显示每个目录的文件数量，并汇总文件夹总数和文件总数
#            可以使用 > 来重定向输出到指定文件 base count_files.sh /mnt/bj -f > mnt_bj_count_files.out
# 使用语法: count_files.sh [文件夹路径] [-f | --filter-empty]
#            如果不提供参数，会提示输入文件夹路径
#            -f 或 --filter-empty 参数：只显示包含文件的文件夹
# ============================================================================

echo ""
echo "=== Directory File Counter ==="
echo ""

# 初始化参数
root_folder=""
filter_empty=0

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--filter-empty)
            filter_empty=1
            shift
            ;;
        *)
            if [[ -z "$root_folder" ]]; then
                root_folder="$1"
            else
                echo "Warning: Unknown parameter '$1'"
            fi
            shift
            ;;
    esac
done

# 如果没有提供文件夹路径，提示输入
if [[ -z "$root_folder" ]]; then
    read -p "Enter root folder path: " root_folder
fi

# 移除可能的引号
root_folder="${root_folder//\"/}"

# 检查文件夹是否存在
if [[ ! -d "$root_folder" ]]; then
    echo "Error: Folder does not exist - '$root_folder'"
    exit 1
fi

echo "Scanning: $root_folder"
if [[ $filter_empty -eq 1 ]]; then
    echo "Mode: Display only folders with files"
else
    echo "Mode: Display all folders"
fi
echo "Please wait..."
echo ""

total_files=0
folder_count=0

# 统计根目录的文件
root_count=0
if [[ -d "$root_folder" ]]; then
    # 使用 find 命令统计文件，排除目录本身
    while IFS= read -r -d '' file; do
        ((root_count++))
        ((total_files++))
    done < <(find "$root_folder" -maxdepth 1 -type f -print0 2>/dev/null)
fi

# 显示根目录（根据filter_empty设置）
if [[ $filter_empty -eq 0 ]] || [[ $root_count -gt 0 ]]; then
    echo "[ROOT] $root_folder : $root_count files"
fi
((folder_count++))

echo "----------------------------------------"

# 统计所有子目录的文件
while IFS= read -r -d '' dir; do
    count=0
    # 统计当前目录中的文件数量
    while IFS= read -r -d '' file; do
        ((count++))
        ((total_files++))
    done < <(find "$dir" -maxdepth 1 -type f -print0 2>/dev/null)
    
    # 根据filter_empty设置决定是否显示
    if [[ $filter_empty -eq 0 ]] || [[ $count -gt 0 ]]; then
        echo "$dir : $count files"
    fi
    ((folder_count++))
done < <(find "$root_folder" -type d -print0 2>/dev/null | tail -z -n +2) # 跳过根目录

echo "========================================"
echo "SUMMARY:"
echo "Total Folders: $folder_count"
echo "Total Files: $total_files"
if [[ $filter_empty -eq 1 ]]; then
    echo "Note: Only folders with files are displayed"
fi
echo "========================================"

read -p "Press Enter to continue..."
```

## 此脚本递归扫描指定文件夹，生成保持完整层级关系的目录树结构，并输出为 JSON 文件。
```shell
# 使用方式： .\Export-DirectoryTree.ps1 -Path "D:\\knowledge\\OUTPUT" -OutputFile "test_tree.json"
param(
    [string]$Path = ".",
    [string]$OutputFile = "directory_tree.json",
    [int]$MaxDepth = 10
)

# 定义函数
function Get-DirectoryTree {
    param(
        [string]$RootPath,
        [int]$MaxDepth = 10,
        [int]$CurrentDepth = 0
    )
    
    $directory = Get-Item $RootPath
    $result = @{
        Name = $directory.Name
        FullName = $directory.FullName
        Type = "Directory"
        Children = @()
    }
    
    if ($CurrentDepth -ge $MaxDepth) {
        $result.Note = "已达到最大深度 $MaxDepth"
        return $result
    }
    
    try {
        $children = Get-ChildItem -Path $RootPath -ErrorAction SilentlyContinue
        $subDirectories = $children | Where-Object { $_.PSIsContainer }
        $files = $children | Where-Object { -not $_.PSIsContainer }
        
        foreach ($dir in $subDirectories) {
            $childNode = Get-DirectoryTree -RootPath $dir.FullName -MaxDepth $MaxDepth -CurrentDepth ($CurrentDepth + 1)
            $result.Children += $childNode
        }
        
        foreach ($file in $files) {
            $fileNode = @{
                Name = $file.Name
                FullName = $file.FullName
                Type = "File"
                Extension = $file.Extension
                Size = $file.Length
            }
            $result.Children += $fileNode
        }
        
    } catch {
        $result.Error = $_.Exception.Message
    }
    
    return $result
}

# 主程序
Write-Host "正在扫描目录: $Path" -ForegroundColor Green
$tree = Get-DirectoryTree -RootPath $Path -MaxDepth $MaxDepth
$jsonOutput = $tree | ConvertTo-Json -Depth 20

# 保存到文件
$jsonOutput | Out-File $OutputFile -Encoding UTF8
Write-Host "目录树已导出到: $OutputFile" -ForegroundColor Green
Write-Host "扫描深度: $MaxDepth" -ForegroundColor Cyan
```

```shell
# 在 linux 环境下，可以使用 tree 命令，达到相同的效果，比如 tree -J 的使用，同样输出 json 格式.
```