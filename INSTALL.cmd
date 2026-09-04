@echo off
setlocal
chcp 65001 >nul
echo LUMI to GPT portable installer
echo.
echo [1] GPT Add-on only
echo [2] GPT Add-on + local GPT-SoVITS TTS
echo.
choice /C 12 /N /M "Select 1 or 2: "
if errorlevel 2 (
  set "INSTALL_MODE=WithTts"
) else (
  set "INSTALL_MODE=AddonOnly"
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -InstallMode "%INSTALL_MODE%"
if errorlevel 1 (
  echo.
  echo Installation failed. See the message above.
  pause
  exit /b 1
)
echo.
pause
