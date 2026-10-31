@echo off
setlocal enabledelayedexpansion
title TV Screen Recorder (30 FPS) - UpgradeMyTV

:: Auto-detect ADB
set ADB_CMD=adb
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set ADB_CMD="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    )
)

echo ================================================================
echo       UpgradeMyTV - Ultra-Smooth TV Screen Recorder (30 FPS)
echo ================================================================
echo.
set TV_IP=192.168.0.7
set /p USER_IP="Enter TV IP address [Press Enter for 192.168.0.7]: "
if not "!USER_IP!"=="" set TV_IP=!USER_IP!
set TV_IP=!TV_IP: =!

echo.
echo Select Recording Quality:
echo   [1] 1080p Full HD - 1920x1080, 8 Mbps (Best Quality)
echo   [2] 720p HD       - 1280x720,  5 Mbps (Best for GitHub / Fast Upload)
set QCHOICE=1
set /p USER_Q="Choose quality [1 or 2, default 1]: "
if not "!USER_Q!"=="" set QCHOICE=!USER_Q!
set QCHOICE=!QCHOICE: =!

if "!QCHOICE!"=="2" (
    set REC_SIZE=1280x720
    set REC_BITRATE=5000000
    set QLABEL=720p HD - 1280x720 at 5 Mbps
) else (
    set REC_SIZE=1920x1080
    set REC_BITRATE=8000000
    set QLABEL=1080p Full HD - 1920x1080 at 8 Mbps
)

echo.
echo [*] Connecting to TV at !TV_IP!:5555...
!ADB_CMD! connect !TV_IP!:5555
ping -n 2 127.0.0.1 > nul

:: Create local output directory
if not exist "recordings" mkdir "recordings"

:: Clean any previous temp recording on TV
!ADB_CMD! -s !TV_IP!:5555 shell "rm -f /sdcard/tv_rec_temp.mp4 > /dev/null 2>&1"

:: Reliable timestamp via powershell
for /f %%a in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH-mm-ss"') do set TIMESTAMP=%%a
if "!TIMESTAMP!"=="" set TIMESTAMP=!RANDOM!
set OUTPUT_FILE=recordings\tv_rec_!TIMESTAMP!.mp4

echo.
echo [*] Initializing hardware H.264 screen encoder (!QLABEL!)...
:: Start screenrecord in background on the TV
!ADB_CMD! -s !TV_IP!:5555 shell "nohup screenrecord --size !REC_SIZE! --bit-rate !REC_BITRATE! --time-limit 180 /sdcard/tv_rec_temp.mp4 > /dev/null 2>&1 &"

echo.
echo ================================================================
echo   [REC] RECORDING LIVE TV DISPLAY AT 30 FPS...
echo ================================================================
echo   Target:  !TV_IP!
echo   Quality: !QLABEL!
echo   Output:  !OUTPUT_FILE!
echo.
echo   *** PRESS [ENTER] WHEN YOU ARE READY TO STOP RECORDING ***
echo ================================================================
echo.
pause > nul

echo.
echo [*] Stopping recording and finalizing MP4 container headers...
!ADB_CMD! -s !TV_IP!:5555 shell "pkill -2 screenrecord"

:: Wait 2 seconds for TV encoder to flush the MP4 atom
ping -n 3 127.0.0.1 > nul

echo [*] Transferring video to PC...
!ADB_CMD! -s !TV_IP!:5555 pull /sdcard/tv_rec_temp.mp4 "!OUTPUT_FILE!"

:: Cleanup on TV
!ADB_CMD! -s !TV_IP!:5555 shell "rm -f /sdcard/tv_rec_temp.mp4 > /dev/null 2>&1"

:: Disconnect ADB so TV pen drive / ports remain 100% free
!ADB_CMD! disconnect

if exist "!OUTPUT_FILE!" (
    echo.
    echo ================================================================
    echo   [SUCCESS] VIDEO SAVED SUCCESSFULLY!
    echo ================================================================
    echo   File location: !OUTPUT_FILE!
    echo   You can now attach this file directly to GitHub or view it!
    echo ================================================================
    echo.
    explorer.exe /select,"%CD%\!OUTPUT_FILE!"
) else (
    echo.
    echo [ERROR] Video file could not be pulled. Please check TV connection.
)

pause
