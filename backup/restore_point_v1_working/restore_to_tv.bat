@echo off
setlocal
echo ========================================================
echo   UpgradeMyTV - Emergency Restore Point V1.0 (Working)
echo ========================================================
echo.
set /p TV_IP="Enter your TV IP address (e.g. 192.168.0.7): "
if "%TV_IP%"=="" (
    echo [ERROR] No TV IP entered. Aborting.
    pause
    exit /b 1
)

echo.
echo [*] Connecting to TV at %TV_IP%:5555...
adb connect %TV_IP%:5555
echo [*] Installing verified working TVPowerNew_v1_working.apk...
adb -s %TV_IP%:5555 install -r TVPowerNew_v1_working.apk
echo [*] Launching TV Power Hub...
adb -s %TV_IP%:5555 shell "am start -n com.gathin.tvpowerhub/.MainActivity"
echo [*] Disconnecting ADB to free USB ports...
adb disconnect
echo.
echo [SUCCESS] Restored to verified V1 working state!
echo Open http://%TV_IP%:8088 on your phone.
pause
