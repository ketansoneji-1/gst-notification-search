# Office Attendance - registers a scheduled task (all-users install only) that starts the agent
# in a user's session when the user signs in, RECONNECTS to a disconnected Remote Desktop
# session, or unlocks it. The Startup-folder entry alone only runs at a fresh sign-in, and
# RDP users usually reconnect to a session that was left open.
# The agent allows only one copy per session, so repeated starts are harmless.
param([Parameter(Mandatory = $true)][string]$Launcher)

$ErrorActionPreference = 'Stop'
$esc = [System.Security.SecurityElement]::Escape('"' + $Launcher + '"')
$xml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo><Description>Starts the Office Attendance agent in each user session.</Description></RegistrationInfo>
  <Triggers>
    <LogonTrigger><Enabled>true</Enabled></LogonTrigger>
    <SessionStateChangeTrigger><Enabled>true</Enabled><StateChange>RemoteConnect</StateChange></SessionStateChangeTrigger>
    <SessionStateChangeTrigger><Enabled>true</Enabled><StateChange>ConsoleConnect</StateChange></SessionStateChangeTrigger>
    <SessionStateChangeTrigger><Enabled>true</Enabled><StateChange>SessionUnlock</StateChange></SessionStateChangeTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author"><GroupId>S-1-5-32-545</GroupId><RunLevel>LeastPrivilege</RunLevel></Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>Parallel</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Enabled>true</Enabled>
  </Settings>
  <Actions Context="Author">
    <Exec><Command>wscript.exe</Command><Arguments>$esc</Arguments></Exec>
  </Actions>
</Task>
"@
$file = Join-Path $env:TEMP 'OfficeAttendanceTask.xml'
Set-Content -Path $file -Value $xml -Encoding Unicode
& schtasks.exe /Create /TN 'OfficeAttendance' /XML $file /F
$code = $LASTEXITCODE
Remove-Item $file -Force -ErrorAction SilentlyContinue
if ($code -ne 0) { Write-Host 'Could not register the scheduled task (the Startup entry is still in place).' -ForegroundColor Yellow; exit 1 }
# Start it now in the sessions that are already signed in, so nobody has to sign out.
& schtasks.exe /Run /TN 'OfficeAttendance' | Out-Null
Write-Host 'Scheduled task registered: the agent now also starts when a user reconnects or unlocks.'
