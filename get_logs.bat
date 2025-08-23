@echo off
echo Pulling MeetingMate debug logs...
echo.

REM Create logs directory if it doesn't exist
if not exist "logs" mkdir logs

REM Pull the entire analytics directory
adb pull /data/data/ai.intelliswarm.meetingmate/files/analytics/ logs/ 2>nul

if %errorlevel% equ 0 (
    echo SUCCESS: Logs pulled to 'logs' directory
    echo.
    echo Available log files:
    dir logs /b
    echo.
    echo You can now open these files with any text editor
    echo Most recent log is usually app_debug.log
) else (
    echo ERROR: Could not pull logs. Make sure:
    echo 1. Device/emulator is connected
    echo 2. App has been run at least once
    echo 3. USB debugging is enabled
    echo.
    echo Try: adb devices
    adb devices
)

echo.
echo Press any key to exit...
pause >nul