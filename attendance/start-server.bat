@echo off
rem Office Attendance - start the server on the office "server" PC.
rem Requires Python 3.9 or later (python.org, tick "Add Python to PATH" during setup).
cd /d "%~dp0"
python server.py --port 8080
pause
