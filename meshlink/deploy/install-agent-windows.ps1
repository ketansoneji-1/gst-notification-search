# MeshLink agent installer for Windows. Run in an elevated PowerShell:
#   .\install-agent-windows.ps1 -Server https://vpn.example.com -AuthKey mlk-... [-EnableRdp]
param(
    [Parameter(Mandatory)] [string] $Server,
    [Parameter(Mandatory)] [string] $AuthKey,
    [switch] $EnableRdp
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path "$env:ProgramFiles\WireGuard\wireguard.exe")) {
    Write-Host 'Installing WireGuard...'
    winget install --id WireGuard.WireGuard -e --accept-source-agreements --accept-package-agreements
}
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python) {
    Write-Host 'Installing Python...'
    winget install --id Python.Python.3.12 -e --scope machine --accept-source-agreements --accept-package-agreements
    $python = "$env:ProgramFiles\Python312\python.exe"
}

$dir = "$env:ProgramFiles\MeshLink"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Copy-Item "$PSScriptRoot\..\agent\meshlink.py" $dir -Force

$upArgs = @("$dir\meshlink.py", 'up', '--server', $Server, '--authkey', $AuthKey)
if ($EnableRdp) { $upArgs += '--rdp' }
& $python @upArgs
if ($LASTEXITCODE -ne 0) { throw 'meshlink up failed' }

# Keep peers in sync at boot via a SYSTEM scheduled task.
$action  = New-ScheduledTaskAction -Execute $python -Argument "`"$dir\meshlink.py`" daemon"
$trigger = New-ScheduledTaskTrigger -AtStartup
$set     = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit 0
Register-ScheduledTask -TaskName 'MeshLink Agent' -Action $action -Trigger $trigger -Settings $set -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Start-ScheduledTask -TaskName 'MeshLink Agent'
Write-Host "MeshLink installed. Check with:  python `"$dir\meshlink.py`" status"
