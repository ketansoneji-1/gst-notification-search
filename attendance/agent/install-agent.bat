@echo off
rem Office Attendance - install the PC agent for the CURRENT Windows user.
rem Run this while logged in as the staff member (no administrator rights needed):
rem     install-agent.bat http://192.168.1.10:8080 AGENTKEY
rem The exact command, with your server address and key, is shown on the admin Settings page.

if "%~2"=="" (
  echo Usage: install-agent.bat SERVER_ADDRESS AGENT_KEY
  echo Example: install-agent.bat http://192.168.1.10:8080 0123456789abcdef01234567
  pause
  exit /b 1
)

set "DEST=%LOCALAPPDATA%\OfficeAttendance"
set "VBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\OfficeAttendance.vbs"

if not exist "%DEST%" mkdir "%DEST%"
copy /y "%~dp0agent.ps1" "%DEST%\agent.ps1" >nul || (echo Could not copy agent.ps1 & pause & exit /b 1)
> "%DEST%\config.json" echo {"server":"%~1","key":"%~2"}

rem Startup entry: runs the agent hidden at every login of this user.
> "%VBS%" echo CreateObject("WScript.Shell").Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""%DEST%\agent.ps1""", 0, False

rem Start it now as well.
wscript.exe "%VBS%"

echo.
echo Agent installed for Windows user: %USERNAME%  on computer: %COMPUTERNAME%
echo Enter "%USERNAME%" as the Windows user name for this staff member on the admin Staff page.
echo The attendance page should open in the browser within a few seconds.
pause
