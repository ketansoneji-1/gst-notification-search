# Office Attendance - session agent (Windows PowerShell 5.1+, nothing to install).
# Runs hidden inside each staff member's Windows session - on an RDP server this means
# inside every Remote Desktop session - started by the Startup entry install-agent.bat creates.
#
# What it does while the session is actually being used (keyboard / mouse activity):
#   - Attendance : signals the attendance server, at most once every 5 minutes.
#                  First signal of the day = arrival, last signal = leaving.
#                  The server stamps the time with ITS OWN clock.
#   - Work log   : notes which program and window title is in front (sampled every 10 seconds)
#                  and sends the totals with each signal. The server works out client and
#                  type of work from the window title.
#   - Opens the attendance page once a day so the staff member can declare the time.
# It does NOT record keystrokes, screen images, passwords or file contents.

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
using System.Text;
using System.Diagnostics;
using System.Runtime.InteropServices;
public static class OfficeAttendanceNative {
    [StructLayout(LayoutKind.Sequential)]
    struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
    [DllImport("user32.dll")]
    static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);
    [DllImport("user32.dll")]
    static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
    [DllImport("user32.dll")]
    static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);

    public static uint IdleSeconds() {
        LASTINPUTINFO l = new LASTINPUTINFO();
        l.cbSize = (uint)Marshal.SizeOf(l);
        if (!GetLastInputInfo(ref l)) return 0;
        return ((uint)Environment.TickCount - l.dwTime) / 1000;
    }
    // Returns { program name, window title } of the window in front, or null.
    public static string[] Foreground() {
        IntPtr h = GetForegroundWindow();
        if (h == IntPtr.Zero) return null;
        StringBuilder sb = new StringBuilder(512);
        GetWindowText(h, sb, sb.Capacity);
        uint pid;
        GetWindowThreadProcessId(h, out pid);
        string app = "";
        try { app = Process.GetProcessById((int)pid).ProcessName; } catch { }
        return new string[] { app, sb.ToString() };
    }
}
"@

$tick        = 10     # seconds between samples
$beatEvery   = 300    # seconds between signals while the session is in use
$idleLimit   = 120    # no input for this long = not in use
$lastSent    = [datetime]::MinValue
$pageOpenDay = ''
$first       = $true
$usage       = @{}    # "program|title" -> seconds, since the last successful signal

function Send-Beat {
    try {
        $samples = @()
        foreach ($k in $usage.Keys) {
            $parts = $k.Split([char]1, 2)
            $samples += @{ app = $parts[0]; title = $parts[1]; seconds = $usage[$k] }
        }
        $body = @{ key = $cfg.key; user = $env:USERNAME; host = $env:COMPUTERNAME; samples = @($samples) } |
            ConvertTo-Json -Depth 4 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        Invoke-RestMethod -Uri "$server/api/agent" -Method Post -ContentType 'application/json; charset=utf-8' `
            -Body $bytes -TimeoutSec 10 | Out-Null
        return $true
    } catch { return $false }
}

while ($true) {
    $now = Get-Date
    $inUse = ([OfficeAttendanceNative]::IdleSeconds() -lt $idleLimit)

    if ($inUse) {
        $fg = [OfficeAttendanceNative]::Foreground()
        if ($fg -and ($fg[0] -or $fg[1])) {
            $title = $fg[1]; if ($title.Length -gt 200) { $title = $title.Substring(0, 200) }
            $key = $fg[0] + [char]1 + $title
            if ($usage.ContainsKey($key)) { $usage[$key] += $tick }
            elseif ($usage.Count -lt 400) { $usage[$key] = $tick }
        }
    }

    $due = (($now - $lastSent).TotalSeconds -ge $beatEvery) -or ($lastSent.Date -ne $now.Date)
    if (($inUse -or $first) -and $due) {
        if (Send-Beat) {
            $lastSent = $now
            $first = $false
            $usage = @{}
            $today = $now.ToString('yyyy-MM-dd')
            if ($pageOpenDay -ne $today) {
                $pageOpenDay = $today
                Start-Process "$server/?u=$([uri]::EscapeDataString($env:USERNAME))"
            }
        }
    }
    Start-Sleep -Seconds $tick
}
