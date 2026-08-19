@echo off
echo ============================================
echo   CouponSnatcher - one-click APK build (Windows)
echo   Auto-downloads JDK / Android SDK / Gradle
echo   Make sure this PC has internet access (5-10 min)
echo ============================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_apk.ps1"
echo.
pause
