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
#
# Problems are written to  %LOCALAPPDATA%\OfficeAttendance\agent.log  (run check-agent.bat to see them).

$ErrorActionPreference = 'Stop'

$logDir = Join-Path $env:LOCALAPPDATA 'OfficeAttendance'
$logFile = Join-Path $logDir 'agent.log'
function Write-Log([string]$msg) {
    try {
        if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
        if ((Test-Path $logFile) -and ((Get-Item $logFile).Length -gt 200KB)) { Remove-Item $logFile -Force }
        Add-Content -Path $logFile -Value ("{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $msg) -Encoding UTF8
    } catch { }
}

try {
    $cfg = Get-Content (Join-Path $PSScriptRoot 'config.json') -Raw | ConvertFrom-Json
    $server = ([string]$cfg.server).TrimEnd('/')
    if (-not $server) { throw 'server address missing in config.json' }
} catch {
    Write-Log "STOPPED: cannot read config.json - $($_.Exception.Message)"
    exit 1
}

# Only one copy per Windows session
$created = $false
try { $mutex = New-Object System.Threading.Mutex($true, 'Local\OfficeAttendanceAgent', [ref]$created) } catch { $created = $true }
if (-not $created) { exit 0 }

Write-Log "Started. user=$env:USERNAME computer=$env:COMPUTERNAME session=$env:SESSIONNAME server=$server PS=$($PSVersionTable.PSVersion) mode=$($ExecutionContext.SessionState.LanguageMode)"

# Idle time and foreground window. If this cannot be loaded (e.g. PowerShell is restricted by
# policy) the agent still records attendance, but not idle time or window titles.
$native = $true
try {
    Add-Type -TypeDefinition @'
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

    public static int IdleSeconds() {
        LASTINPUTINFO l = new LASTINPUTINFO();
        l.cbSize = (uint)Marshal.SizeOf(l);
        if (!GetLastInputInfo(ref l)) return 0;
        return (int)(unchecked((uint)Environment.TickCount - l.dwTime) / 1000);
    }
    public static int ForegroundPid() {
        try { uint pid; GetWindowThreadProcessId(GetForegroundWindow(), out pid); return (int)pid; } catch { return 0; }
    }
    public static string ForegroundApp() {
        try {
            uint pid;
            GetWindowThreadProcessId(GetForegroundWindow(), out pid);
            return Process.GetProcessById((int)pid).ProcessName;
        } catch { return ""; }
    }
    public static string ForegroundTitle() {
        try {
            StringBuilder sb = new StringBuilder(512);
            GetWindowText(GetForegroundWindow(), sb, sb.Capacity);
            return sb.ToString();
        } catch { return ""; }
    }
}
'@
} catch {
    $native = $false
    Write-Log "WARNING: activity detection unavailable, attendance only - $($_.Exception.Message)"
}

$tick        = 10     # seconds between samples
$beatEvery   = 300    # seconds between signals while the session is in use
$idleLimit   = 120    # no input for this long = not in use
$lastSent    = [datetime]::MinValue
$pageOpenDay = ''
$first       = $true
$sentOk      = $false
$lastError   = ''
$usage       = @{}    # "program<TAB>title" -> seconds, since the last successful signal
$paths       = @{}    # "program<TAB>title" -> file / folder the program was opened with
$argCache    = @{}    # process id -> what it was opened with
$browsers    = @('chrome', 'msedge', 'firefox', 'brave', 'opera', 'iexplore')

# The file or folder a program was started with, e.g. EXCEL.EXE "E:\Clients\Shree Traders\GSTR1.xlsx"
# gives  E:\Clients\Shree Traders\GSTR1.xlsx . The folder name often identifies the client even
# when the window title does not. Browsers are skipped (their command lines are only switches).
function Get-OpenedWith([int]$procId, [string]$app) {
    if ($procId -le 0 -or ($browsers -contains $app.ToLower())) { return '' }
    if ($argCache.ContainsKey($procId)) { return $argCache[$procId] }
    $r = ''
    try {
        $cl = [string](Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -Property CommandLine).CommandLine
        $r = ($cl -replace '^\s*("[^"]*"|\S+)\s*', '' -replace '[\t\r\n]', ' ').Trim().Trim('"')
        if ($r.Length -gt 300) { $r = $r.Substring(0, 300) }
    } catch { }
    if ($argCache.Count -gt 300) { $argCache.Clear() }
    $argCache[$procId] = $r
    return $r
}

# Full path of the document in front in Excel / Word, asked from the program itself. Needed when
# staff start Excel first and then open the file from inside it (nothing on the command line).
# Returns '' when the program is busy (e.g. a cell is being edited) or not reachable.
function Get-OfficeDocPath([string]$app) {
    $progId = @{ excel = 'Excel.Application'; winword = 'Word.Application' }[$app.ToLower()]
    if (-not $progId) { return '' }
    $o = $null; $d = $null; $r = ''
    try {
        $o = [System.Runtime.InteropServices.Marshal]::GetActiveObject($progId)
        if ($progId -like 'Excel*') { $d = $o.ActiveWorkbook } else { $d = $o.ActiveDocument }
        if ($d) { $r = [string]$d.FullName }
    } catch { }
    foreach ($c in @($d, $o)) { if ($c) { try { [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($c) } catch { } } }
    if ($r.Length -gt 300) { $r = $r.Substring(0, 300) }
    return $r
}

function Send-Beat {
    $samples = New-Object System.Collections.ArrayList
    foreach ($k in @($usage.Keys)) {
        $i = $k.IndexOf("`t")
        [void]$samples.Add(@{ app = $k.Substring(0, $i); title = $k.Substring($i + 1); seconds = [int]$usage[$k]; path = [string]$paths[$k] })
    }
    $payload = @{ key = [string]$cfg.key; user = $env:USERNAME; host = $env:COMPUTERNAME; samples = $samples.ToArray() }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $payload -Depth 4 -Compress))
    Invoke-RestMethod -Uri "$server/api/agent" -Method Post -ContentType 'application/json; charset=utf-8' `
        -Body $bytes -TimeoutSec 15 | Out-Null
}

while ($true) {
    try {
        $now = Get-Date
        $inUse = $true
        if ($native) {
            $inUse = ([OfficeAttendanceNative]::IdleSeconds() -lt $idleLimit)
            if ($inUse) {
                $app = [OfficeAttendanceNative]::ForegroundApp()
                $title = [OfficeAttendanceNative]::ForegroundTitle() -replace "[`t`r`n]", ' '
                if ($title.Length -gt 200) { $title = $title.Substring(0, 200) }
                if ($app -or $title) {
                    $key = "$app`t$title"
                    if ($usage.ContainsKey($key)) { $usage[$key] = $usage[$key] + $tick }
                    elseif ($usage.Count -lt 400) {
                        $usage[$key] = $tick
                        # Keep the path only when this window really shows that file (one program
                        # can have several documents open; only the first is on its command line).
                        $p = Get-OfficeDocPath $app
                        if (-not $p) { $p = Get-OpenedWith ([OfficeAttendanceNative]::ForegroundPid()) $app }
                        if ($p) {
                            $leaf = ''
                            try { $leaf = [System.IO.Path]::GetFileNameWithoutExtension($p) } catch { }
                            if (-not $leaf -or $title.IndexOf($leaf, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) { $p = '' }
                        }
                        $paths[$key] = $p
                    }
                }
            }
        }

        $due = (($now - $lastSent).TotalSeconds -ge $beatEvery) -or ($lastSent.Date -ne $now.Date)
        if (($inUse -or $first) -and $due) {
            try {
                Send-Beat
                $lastSent = $now
                $first = $false
                $usage = @{}
                $paths = @{}
                if (-not $sentOk) { $sentOk = $true; Write-Log 'First signal accepted by the server.' }
                $lastError = ''
                $today = $now.ToString('yyyy-MM-dd')
                if ($pageOpenDay -ne $today) {
                    $pageOpenDay = $today
                    try { Start-Process "$server/?u=$([uri]::EscapeDataString($env:USERNAME))" } catch { Write-Log "Could not open the attendance page - $($_.Exception.Message)" }
                }
            } catch {
                $sentOk = $false
                $m = $_.Exception.Message
                if ($m -ne $lastError) { $lastError = $m; Write-Log "SIGNAL FAILED ($server/api/agent): $m" }
            }
        }
    } catch {
        $m = $_.Exception.Message
        if ($m -ne $lastError) { $lastError = $m; Write-Log "ERROR: $m" }
    }
    Start-Sleep -Seconds $tick
}
