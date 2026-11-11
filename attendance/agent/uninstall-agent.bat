@echo off
rem Office Attendance - remove the PC agent for the current Windows user.
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\OfficeAttendance.vbs" 2>nul
rmdir /s /q "%LOCALAPPDATA%\OfficeAttendance" 2>nul
echo Agent removed. It stops completely at the next sign-out or restart.
pause
