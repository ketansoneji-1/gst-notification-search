@echo off
rem Office Attendance - remove the session agent.
rem Run as administrator to remove an "all users" installation.
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\OfficeAttendance.vbs" 2>nul
rmdir /s /q "%LOCALAPPDATA%\OfficeAttendance" 2>nul
del "%ProgramData%\Microsoft\Windows\Start Menu\Programs\StartUp\OfficeAttendance.vbs" 2>nul
schtasks.exe /Delete /TN OfficeAttendance /F >nul 2>&1
rmdir /s /q "%ProgramData%\OfficeAttendance" 2>nul
echo Agent removed. It stops completely in each session at the next sign-out or restart.
pause
