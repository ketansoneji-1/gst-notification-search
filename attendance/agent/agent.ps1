# Office Attendance - PC agent (Windows PowerShell 5.1+, no installation needed).
# Started hidden at every Windows login by the Startup entry that install-agent.bat creates.
#
# What it does:
#   - Tells the attendance server whenever this PC is actually being used
#     (keyboard / mouse activity), at most once every 5 minutes.
#     The server stamps the time with ITS OWN clock, so changing this PC's clock has no effect.
#   - First signal of the day  = arrival.   Last signal of the day = leaving.
#   - Opens the attendance page once a day so the staff member can declare the time.
# It does NOT record keystrokes, screens, websites or files.

$ErrorActionPreference = 'SilentlyContinue'
$cfg = Get-Content (Join-Path $PSScriptRoot 'config.json') -Raw | ConvertFrom-Json
if (-not $cfg.server) { exit 1 }
$server = $cfg.server.TrimEnd('/')

# Only one copy per Windows session
$created = $false
$mutex = New-Object System.Threading.Mutex($true, 'Local\OfficeAttendanceAgent', [ref]$created)
if (-not $created) { exit 0 }

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class OfficeAttendanceIdle {
    [StructLayout(LayoutKind.Sequential)]
    struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
    [DllImport("user32.dll")]
    static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);
    public static uint Seconds() {
        LASTINPUTINFO l = new LASTINPUTINFO();
        l.cbSize = (uint)Marshal.SizeOf(l);
        if (!GetLastInputInfo(ref l)) return 0;
        return ((uint)Environment.TickCount - l.dwTime) / 1000;
    }
}
"@

function Send-Beat([string]$kind) {
    try {
        $body = @{ key = $cfg.key; user = $env:USERNAME; host = $env:COMPUTERNAME; kind = $kind } | ConvertTo-Json
        Invoke-RestMethod -Uri "$server/api/agent" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 10 | Out-Null
        return $true
    } catch { return $false }
}

$beatEvery   = 300    # seconds between signals while the PC is in use
$idleLimit   = 120    # no input for this long = not in use
$lastSent    = [datetime]::MinValue
$pageOpenDay = ''
$kind        = 'login'

while ($true) {
    $now = Get-Date
    $inUse = ([OfficeAttendanceIdle]::Seconds() -lt $idleLimit) -or ($kind -eq 'login')
    $due = (($now - $lastSent).TotalSeconds -ge $beatEvery) -or ($lastSent.Date -ne $now.Date)
    if ($inUse -and $due) {
        if (Send-Beat $kind) {
            $lastSent = $now
            $kind = 'active'
            $today = $now.ToString('yyyy-MM-dd')
            if ($pageOpenDay -ne $today) {
                $pageOpenDay = $today
                Start-Process "$server/?u=$([uri]::EscapeDataString($env:USERNAME))"
            }
        }
    }
    Start-Sleep -Seconds 30
}
