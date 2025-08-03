@echo off
:: 基于ffmpeg工具，将webm文件转为gif

setlocal enabledelayedexpansion

set maxHeight=480

:: 初始化变量
set inputFile=
set outputFile=

:: 解析参数
:parse_args
if "%~1"=="" goto done
if "%~1"=="-i" (
    set inputFile=%~2
    shift
) else if "%~1"=="-o" (
    set outputFile=%~2
    shift
)
shift
goto parse_args

:done

:: 检查参数
if "%inputFile%"=="" (
    echo [ERROR] Missing input file. Use -i input_file.webm
    exit /b 1
)

if "%outputFile%"=="" (
    echo [ERROR] Missing output file. Use -o output_file.gif
    exit /b 1
)

:: 检查 FFmpeg 是否安装
where ffmpeg >nul 2>nul
if errorlevel 1 (
    echo [ERROR] FFmpeg not found. Please ensure it's in your PATH.
    exit /b 1
)

:: 开始转换
echo Converting "%inputFile%" to "%outputFile%"...
ffmpeg -y -i "%inputFile%" -vf "fps=15,scale='if(gt(ih,%maxHeight%),trunc(iw*%maxHeight%/ih),iw)':'if(gt(ih,%maxHeight%),%maxHeight%,ih)':flags=lanczos" -loop 0 "%outputFile%"

if errorlevel 1 (
    echo [ERROR] Conversion failed.
    exit /b 1
) else (
    echo [SUCCESS] GIF created: %outputFile%
)

exit /b 0
