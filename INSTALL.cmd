@echo off
setlocal
chcp 65001 >nul
echo LUMI to GPT portable installer
echo.
echo [1] GPT Add-on only
echo [2] GPT Add-on + LUMI GPT-SoVITS TTS
echo [3] Add LUMI GPT-SoVITS TTS to an existing install
echo.
choice /C 123 /N /M "Select 1, 2 or 3: "
if errorlevel 3 (
  set "INSTALL_MODE=TtsOnly"
) else if errorlevel 2 (
  set "INSTALL_MODE=WithTts"
) else (
  set "INSTALL_MODE=AddonOnly"
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -InstallMode "%INSTALL_MODE%"
if errorlevel 1 (
  echo.
  echo Installation failed. See the detailed log path above.
  pause
  exit /b 1
)
echo.
pause
