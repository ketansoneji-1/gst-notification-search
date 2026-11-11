@echo off
rem Office Attendance - ONE-CLICK install of the session agent for ALL users of this PC.
rem Use this on the RDP server where the attendance server (start-server.bat) also runs.
rem Just double-click it. It asks for administrator permission, reads the agent key from
rem the attendance database, and installs the agent for every Windows user.
rem If the server uses a port other than 8080:  install-all-users.bat 9000

net session >nul 2>&1
if errorlevel 1 goto elevate

cd /d "%~dp0"
set "PORT=8080"
if not "%~1"=="" set "PORT=%~1"
set "KEY="
for /f "usebackq delims=" %%K in (`python "%~dp0..\server.py" --print-agent-key`) do set "KEY=%%K"
if "%KEY%"=="" goto nokey

call "%~dp0install-agent.bat" http://127.0.0.1:%PORT% %KEY% all
exit /b

:elevate
echo Asking for administrator permission...
powershell.exe -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '%~1' -Verb RunAs"
exit /b

:nokey
echo Could not read the agent key. Check that Python is installed and that this "agent" folder
echo is still inside the attendance folder next to server.py.
pause
exit /b 1
