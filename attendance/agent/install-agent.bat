@echo off
rem Office Attendance - install the session agent.
rem
rem RDP server (everyone works in Remote Desktop sessions on this PC):
rem   Right-click Command Prompt, "Run as administrator", then ONCE on the server:
rem       install-agent.bat http://127.0.0.1:8080 AGENTKEY all
rem   The agent then starts in every user's session at sign-in.
rem
rem Single PC, current Windows user only (no administrator rights needed):
rem       install-agent.bat http://192.168.1.10:8080 AGENTKEY
rem
rem The exact command, with your server address and key, is shown on the admin Settings page.

if "%~2"=="" (
  echo Usage: install-agent.bat SERVER_ADDRESS AGENT_KEY [all]
  echo Example: install-agent.bat http://127.0.0.1:8080 0123456789abcdef01234567 all
  pause
  exit /b 1
)

if /i "%~3"=="all" (
  net session >nul 2>&1 || (echo For "all" this must be run from a Command Prompt opened with "Run as administrator". & pause & exit /b 1)
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
copy /y "%~dp0check-agent.ps1" "%DEST%\" >nul
copy /y "%~dp0check-agent.bat" "%DEST%\" >nul
> "%DEST%\config.json" echo {"server":"%~1","key":"%~2"}

rem Startup entry: runs the agent hidden at every sign-in.
> "%VBS%" echo CreateObject("WScript.Shell").Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""%DEST%\agent.ps1""", 0, False
if errorlevel 1 (echo Could not create the Startup entry - for "all", run as administrator. & pause & exit /b 1)

rem Start it now for the current session as well.
wscript.exe "%VBS%"

rem All-users install: also start the agent whenever a user reconnects to an RDP session or unlocks it.
if /i "%~3"=="all" copy /y "%VBS%" "%DEST%\run-agent.vbs" >nul
if /i "%~3"=="all" powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0register-task.ps1" "%DEST%\run-agent.vbs"

echo.
if /i "%~3"=="all" (
  echo Agent installed for ALL users of %COMPUTERNAME%.
  echo It starts in each session at sign-in, and when a user reconnects to or unlocks a session.
  echo Users who are connected now: disconnect and reconnect once, or lock and unlock.
) else (
  echo Agent installed for Windows user: %USERNAME%  on computer: %COMPUTERNAME%
)
echo On the admin Staff page, link each Windows user name to the staff member.
echo To verify in any session, run:  "%DEST%\check-agent.bat"
pause
