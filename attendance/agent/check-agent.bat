@echo off
rem Office Attendance - diagnostic. Double-click while signed in as the staff member
rem (inside their Remote Desktop session). Shows OK / FAIL for every step the agent needs.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-agent.ps1"
echo.
pause
