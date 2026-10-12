# MeshLink agent installer for Windows PCs/laptops. Run in an elevated PowerShell.
# Copy the exact command shown on the admin dashboard after creating an auth key, e.g.:
#   .\install-agent-windows.ps1 -Server "https://192.168.1.10:8443,https://203.0.113.7:8443" `
#       -Fingerprint 05E6B8... -AuthKey mlk-... [-EnableRdp]
#
# Offline install: place wireguard-amd64-*.msi and python-3.*-amd64.exe in deploy\offline\ first.
param(
    [Parameter(Mandatory)] [string] $Server,       # office LAN URL first, then public URL
    [Parameter(Mandatory)] [string] $Fingerprint,  # server certificate fingerprint (dashboard)
    [Parameter(Mandatory)] [string] $AuthKey,
    [switch] $EnableRdp                            # allow RDP into this PC from permitted devices
)
$ErrorActionPreference = 'Stop'
$offline = Join-Path $PSScriptRoot 'offline'

if (-not (Test-Path "$env:ProgramFiles\WireGuard\wireguard.exe")) {
    Write-Host 'Installing WireGuard...'
    $msi = Get-ChildItem $offline -Filter 'wireguard-*.msi' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($msi) { Start-Process msiexec.exe -ArgumentList "/i `"$($msi.FullName)`" /qn DO_NOT_LAUNCH=1" -Wait }
    else { winget install --id WireGuard.WireGuard -e --accept-source-agreements --accept-package-agreements }
}

# Machine-wide Python so the SYSTEM account (which runs the agent) can use it.
$python = (Get-ChildItem "$env:ProgramFiles\Python3*\python.exe" -ErrorAction SilentlyContinue | Select-Object -Last 1).FullName
if (-not $python) {
    Write-Host 'Installing Python...'
    $exe = Get-ChildItem $offline -Filter 'python-3*-amd64.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($exe) { Start-Process $exe.FullName -ArgumentList '/quiet InstallAllUsers=1 PrependPath=1 Include_test=0' -Wait }
    else { winget install --id Python.Python.3.12 -e --scope machine --accept-source-agreements --accept-package-agreements }
    $python = (Get-ChildItem "$env:ProgramFiles\Python3*\python.exe" | Select-Object -Last 1).FullName
}

$dir = "$env:ProgramFiles\MeshLink"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Copy-Item "$PSScriptRoot\..\agent\meshlink.py" $dir -Force
# Convenience wrapper so `meshlink status` / `meshlink rdp office-pc` work from any prompt.
"@echo off`r`n`"$python`" `"$dir\meshlink.py`" %*" | Set-Content "$dir\meshlink.cmd" -Encoding ASCII
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
if ($machinePath -notlike "*$dir*") { [Environment]::SetEnvironmentVariable('Path', "$machinePath;$dir", 'Machine') }

$upArgs = @("$dir\meshlink.py", 'up', '--server', $Server, '--fingerprint', $Fingerprint, '--authkey', $AuthKey)
if ($EnableRdp) { $upArgs += '--rdp' }
& $python @upArgs
if ($LASTEXITCODE -ne 0) { throw 'meshlink up failed' }

# Keep peers, names and RDP firewall rules in sync, starting at boot.
$action  = New-ScheduledTaskAction -Execute $python -Argument "`"$dir\meshlink.py`" daemon"
$trigger = New-ScheduledTaskTrigger -AtStartup
$set     = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit 0 -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName 'MeshLink Agent' -Action $action -Trigger $trigger -Settings $set -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Start-ScheduledTask -TaskName 'MeshLink Agent'
Write-Host 'MeshLink installed. Open a new prompt and run:  meshlink status'
