@echo off
rem Office Attendance - install the session agent.
rem
rem RDP server (everyone works in Remote Desktop sessions on this PC):
rem   Right-click Command Prompt, "Run as administrator", then ONCE on the server:
rem       install-agent.bat http://localhost:8080 AGENTKEY all
rem   The agent then starts in every user's session at sign-in.
rem
rem Single PC, current Windows user only (no administrator rights needed):
rem       install-agent.bat http://192.168.1.10:8080 AGENTKEY
rem
rem The exact command, with your server address and key, is shown on the admin Settings page.

if "%~2"=="" (
  echo Usage: install-agent.bat SERVER_ADDRESS AGENT_KEY [all]
  echo Example: install-agent.bat http://localhost:8080 0123456789abcdef01234567 all
  pause
  exit /b 1
)

if /i "%~3"=="all" (
  set "DEST=%ProgramData%\OfficeAttendance"
  set "VBS=%ProgramData%\Microsoft\Windows\Start Menu\Programs\StartUp\OfficeAttendance.vbs"
) else (
  set "DEST=%LOCALAPPDATA%\OfficeAttendance"
  set "VBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\OfficeAttendance.vbs"
)

if not exist "%DEST%" mkdir "%DEST%"
copy /y "%~dp0agent.ps1" "%DEST%\agent.ps1" >nul || (echo Could not copy agent.ps1 - for "all", run as administrator. & pause & exit /b 1)
> "%DEST%\config.json" echo {"server":"%~1","key":"%~2"}

rem Startup entry: runs the agent hidden at every sign-in.
> "%VBS%" echo CreateObject("WScript.Shell").Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""%DEST%\agent.ps1""", 0, False
if errorlevel 1 (echo Could not create the Startup entry - for "all", run as administrator. & pause & exit /b 1)

rem Start it now for the current session as well.
wscript.exe "%VBS%"

echo.
if /i "%~3"=="all" (
  echo Agent installed for ALL users of %COMPUTERNAME%. It starts in each session at the next sign-in.
  echo Users who are signed in now must sign out and sign in again once.
) else (
  echo Agent installed for Windows user: %USERNAME%  on computer: %COMPUTERNAME%
)
echo On the admin Staff page, link each Windows user name to the staff member.
pause
