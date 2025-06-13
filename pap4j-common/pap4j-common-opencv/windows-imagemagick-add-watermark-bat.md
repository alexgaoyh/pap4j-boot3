```shell
@echo off
setlocal enabledelayedexpansion

:: 使用说明
if "%~1"=="/?" (
    echo Using: add_watermark.bat [inputPath] [ouputPath] [waterMarkPath]
    echo Example:
    echo     add_watermark.bat "input.jpg" "output.jpg" "watermark.png"
    exit /b 0
)

:: 设置默认参数
set "input=%~1"
set "output=%~2"
set "watermark=%~3"

if "%input%"=="" set "input=input.jpg"
if "%output%"=="" set "output=output.jpg"
if "%watermark%"=="" set "watermark=watermark.png"

:: 检查原图是否存在
if not exist "%input%" (
    >&2 echo fail：no input
    exit /b 1
)

:: 检查水印是否存在
if not exist "%watermark%" (
    >&2 echo fail：no watermark
    exit /b 2
)

:: 获取原图宽度并计算水印宽度为其五分之一
for /f %%I in ('magick identify -format %%w "%input%"') do (
    set /a wm_width=%%I / 5
)

:: 添加水印
magick "%input%" ( "%watermark%" -resize !wm_width!x ) -gravity southeast -composite "%output%"
if errorlevel 1 (
    >&2 echo fail：ImageMagick fail
    exit /b 3
)

:: 确认输出文件是否生成
if exist "%output%" (
    echo success
    exit /b 0
) else (
    >&2 echo fail：no output
    exit /b 4
)

endlocal

```