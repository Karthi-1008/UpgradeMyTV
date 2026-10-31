@echo off
setlocal enabledelayedexpansion
title TV Screen Recorder (30 FPS) - UpgradeMyTV

echo ================================================================
echo       UpgradeMyTV - Ultra-Smooth TV Screen Recorder (30 FPS)
echo ================================================================
echo.
set /p TV_IP="Enter TV IP address [Press Enter for 192.168.0.7]: "
if "%TV_IP%"=="" set TV_IP=192.168.0.7

echo.
echo Select Recording Quality:
echo   [1] 1080p Full HD (1920x1080, 8 Mbps) - Best Quality
echo   [2] 720p HD       (1280x720,  5 Mbps) - Best for GitHub / Fast Upload
set /p QCHOICE="Choose quality [1 or 2, default 1]: "
if "%QCHOICE%"=="2" (
    set REC_SIZE=1280x720
    set REC_BITRATE=5000000
    set QLABEL=720p HD (1280x720 @ 5 Mbps)
) else (
    set REC_SIZE=1920x1080
    set REC_BITRATE=8000000
    set QLABEL=1080p Full HD (1920x1080 @ 8 Mbps)
)

echo.
echo [*] Connecting to TV at %TV_IP%:5555...
adb connect %TV_IP%:5555
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to connect to TV at %TV_IP%. Ensure TV is on and on the same Wi-Fi.
    pause
    exit /b 1
)

:: Create local output directory
if not exist "recordings" mkdir "recordings"

:: Clean any previous temp recording on TV
adb -s %TV_IP%:5555 shell "rm -f /sdcard/tv_rec_temp.mp4 > /dev/null 2>&1"

:: Generate timestamp for filename
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set TIMESTAMP=!datetime:~0,4!-!datetime:~4,2!-!datetime:~6,2!_!datetime:~8,2!-!datetime:~10,2!-!datetime:~12,2!
set OUTPUT_FILE=recordings\tv_rec_%TIMESTAMP%.mp4

echo.
echo [*] Initializing hardware H.264 screen encoder (%QLABEL%)...
:: Start screenrecord in background on the TV
adb -s %TV_IP%:5555 shell "nohup screenrecord --size %REC_SIZE% --bit-rate %REC_BITRATE% --time-limit 180 /sdcard/tv_rec_temp.mp4 > /dev/null 2>&1 &"

echo.
echo ================================================================
echo   [REC] RECORDING LIVE TV DISPLAY AT 30 FPS...
echo ================================================================
echo   Target: %TV_IP% (%QLABEL%)
echo   Output: %OUTPUT_FILE%
echo.
echo   >>> PRESS [ENTER] WHEN YOU ARE READY TO STOP RECORDING <<<
echo ================================================================
echo.
pause > nul

echo.
echo [*] Stopping recording and finalizing MP4 container headers...
adb -s %TV_IP%:5555 shell "pkill -2 screenrecord"

:: Wait 2 seconds for TV encoder to flush the MP4 atom
timeout /t 2 /nobreak > nul

echo [*] Transferring video to PC...
adb -s %TV_IP%:5555 pull /sdcard/tv_rec_temp.mp4 "%OUTPUT_FILE%"

:: Cleanup on TV
adb -s %TV_IP%:5555 shell "rm -f /sdcard/tv_rec_temp.mp4 > /dev/null 2>&1"

:: Disconnect ADB so TV pen drive / ports remain 100% free
adb disconnect

if exist "%OUTPUT_FILE%" (
    echo.
    echo ================================================================
    echo   [SUCCESS] VIDEO SAVED SUCCESSFULLY!
    echo ================================================================
    echo   File location: %OUTPUT_FILE%
    echo   You can now attach this file directly to GitHub or view it!
    echo ================================================================
    echo.
    :: Open File Explorer and select the recorded video
    explorer.exe /select,"%CD%\%OUTPUT_FILE%"
) else (
    echo.
    echo [ERROR] Video file could not be pulled. Please check TV connection.
)

pause
