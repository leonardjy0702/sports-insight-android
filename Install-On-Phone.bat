@echo off
title GridironIQ — 1-Click Phone Installer
color 0A
cls
echo ============================================================
echo         GridironIQ Sports AI — 1-Click Phone Installer
echo ============================================================
echo.

:: 1. Check ADB
where adb >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\platform-tools\adb.exe" (
        set "PATH=%PATH%;C:\platform-tools"
    ) else (
        echo [!] ADB not found in PATH.
        echo [*] Installing Android Platform-Tools via winget...
        winget install --id Google.PlatformTools -e --accept-source-agreements --accept-package-agreements
        set "PATH=%PATH%;C:\platform-tools;%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe"
    )
)

:: 2. Check Device
echo [*] Checking connected Android devices...
adb devices
echo.

:: 3. Download Latest APK
echo [*] Fetching latest compiled APK from GitHub...
curl -L -o "%TEMP%\GridironIQ.apk" https://github.com/leonardjy0702/sports-insight-android/raw/main/app-debug.apk
if %errorlevel% neq 0 (
    echo [ERROR] Failed to download APK. Check internet connection.
    pause
    exit /b 1
)

:: 4. Stream Install via ADB
echo [*] Installing GridironIQ onto connected phone...
adb install -r -d "%TEMP%\GridironIQ.apk"
if %errorlevel% neq 0 (
    echo.
    echo [!] Install failed. Please unlock your phone and tap 'Allow USB Debugging'.
    pause
    exit /b 1
)

:: 5. Launch App
echo.
echo [*] Launching GridironIQ on your phone screen...
adb shell am start -n com.gridironiq.sports/.MainActivity

echo.
echo ============================================================
echo  SUCCESS: GridironIQ is now running on your phone!
echo ============================================================
echo.
pause
