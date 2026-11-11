# Office Attendance - diagnostic. Run check-agent.bat while signed in AS THE STAFF MEMBER
# (inside their Remote Desktop session). It tests every step the agent depends on and prints
# OK / FAIL for each, so the failing step is obvious. It changes nothing except sending one
# test signal (which marks this user as active now).

$ErrorActionPreference = 'Stop'
function Step($name, [scriptblock]$code) {
    try { $r = & $code; Write-Host ("[ OK ] {0}: {1}" -f $name, $r) -ForegroundColor Green; return $true }
    catch { Write-Host ("[FAIL] {0}: {1}" -f $name, $_.Exception.Message) -ForegroundColor Red; return $false }
}

Write-Host "Office Attendance - agent check" -ForegroundColor Cyan
Write-Host ("User: {0}   Computer: {1}   Session: {2}" -f $env:USERNAME, $env:COMPUTERNAME, $env:SESSIONNAME)
Write-Host ("PowerShell: {0}   Language mode: {1}   Execution policy: {2}" -f $PSVersionTable.PSVersion, $ExecutionContext.SessionState.LanguageMode, (Get-ExecutionPolicy))
Write-Host ""

$allDir  = Join-Path $env:ProgramData 'OfficeAttendance'
$userDir = Join-Path $env:LOCALAPPDATA 'OfficeAttendance'
$allVbs  = Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\StartUp\OfficeAttendance.vbs'
$userVbs = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Startup\OfficeAttendance.vbs'
$script:dir = $null; $script:cfg = $null; $script:server = $null

$ok = Step '1. Agent files installed' {
    foreach ($d in @($allDir, $userDir)) { if (Test-Path (Join-Path $d 'agent.ps1')) { $script:dir = $d } }
    if (-not $script:dir) { throw "agent.ps1 not found in $allDir or $userDir. Run install-agent.bat first (as administrator, with 'all' at the end, on an RDP server)." }
    $script:dir
}
if ($ok) { $ok = Step '2. config.json readable' {
    $script:cfg = Get-Content (Join-Path $script:dir 'config.json') -Raw | ConvertFrom-Json
    $script:server = ([string]$script:cfg.server).TrimEnd('/')
    if (-not $script:server -or -not $script:cfg.key) { throw 'server or key missing' }
    "server = $($script:server)"
} }
[void](Step '3. Startup entry present' {
    $found = @($allVbs, $userVbs) | Where-Object { Test-Path $_ }
    if (-not $found) { throw 'OfficeAttendance.vbs is not in any Startup folder, so the agent will not start at sign-in. Run install-agent.bat again.' }
    $found -join ' ; '
})
[void](Step '3b. Reconnect / unlock task (all-users install)' {
    & schtasks.exe /Query /TN 'OfficeAttendance' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'scheduled task OfficeAttendance not found - run install-all-users.bat again (needed so the agent starts when a user reconnects to an open RDP session).' }
    'registered'
})
[void](Step '4. Agent running in this session' {
    $m = $null
    if ([System.Threading.Mutex]::TryOpenExisting('Local\OfficeAttendanceAgent', [ref]$m)) { $m.Dispose(); 'yes' }
    else { throw 'NOT running in this session. Lock and unlock the session (Win+L), or sign out and sign in again; if it still does not start, hidden PowerShell is probably being blocked by antivirus or group policy (see README, Troubleshooting).' }
})
if ($ok) {
    $ok = Step '5. Server reachable' {
        $r = Invoke-RestMethod -Uri "$($script:server)/api/staff-list" -TimeoutSec 15
        "yes, firm = $($r.firm), staff on list = $(@($r.staff).Count)"
    }
    if (-not $ok) { Write-Host "       Check that start-server.bat is running, and that the address above is right. On the server itself use http://127.0.0.1:8080" -ForegroundColor Yellow }
}
if ($ok) { [void](Step '6. Test signal accepted' {
    $payload = @{ key = [string]$script:cfg.key; user = $env:USERNAME; host = $env:COMPUTERNAME; samples = @(@{ app = 'check-agent'; title = 'Agent check'; seconds = 10 }) }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $payload -Depth 4 -Compress))
    $r = Invoke-RestMethod -Uri "$($script:server)/api/agent" -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 15
    "server time $($r.server_time). '$($env:USERNAME.ToLower())' should now appear on the admin Staff page / Settings > Agent status."
}) }
[void](Step '7. Activity detection (idle time, window title)' {
    Add-Type -TypeDefinition @'
using System; using System.Text; using System.Runtime.InteropServices;
public static class OfficeAttendanceCheck {
    [StructLayout(LayoutKind.Sequential)] struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
    [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
    public static int Idle() { LASTINPUTINFO l = new LASTINPUTINFO(); l.cbSize = (uint)Marshal.SizeOf(l); GetLastInputInfo(ref l); return (int)(unchecked((uint)Environment.TickCount - l.dwTime) / 1000); }
    public static string Title() { StringBuilder sb = new StringBuilder(512); GetWindowText(GetForegroundWindow(), sb, sb.Capacity); return sb.ToString(); }
}
'@
    "idle $([OfficeAttendanceCheck]::Idle()) s, window in front = '$([OfficeAttendanceCheck]::Title())'"
})


# What the programs expose - this decides how well the client can be identified automatically.
Write-Host ""
Write-Host "Open Tally with a company selected, and Excel with a client file, BEFORE running this check:" -ForegroundColor Cyan
[void](Step '8. Tally window title' {
    $t = @(Get-Process | Where-Object { $_.ProcessName -like 'tally*' -and $_.SessionId -eq (Get-Process -Id $PID).SessionId } | ForEach-Object { "[" + $_.ProcessName + "] '" + $_.MainWindowTitle + "'" })
    if (-not $t) { throw 'Tally is not running in this session.' }
    $t -join '  ;  '
})
[void](Step '9. Excel file path' {
    $x = [System.Runtime.InteropServices.Marshal]::GetActiveObject('Excel.Application')
    $w = $x.ActiveWorkbook
    if (-not $w) { throw 'Excel is open but no workbook is open.' }
    "'" + $w.FullName + "'"
})

Write-Host ""
$log = Join-Path $userDir 'agent.log'
if (Test-Path $log) { Write-Host "Last lines of $log :" -ForegroundColor Cyan; Get-Content $log -Tail 15 }
else { Write-Host "No agent.log for this user yet - the agent has never started in this user's session." -ForegroundColor Yellow }
Write-Host ""
