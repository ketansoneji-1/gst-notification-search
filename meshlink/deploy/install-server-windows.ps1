# MeshLink server + WireGuard hub for Windows Server / Windows 10-11 (on-premises).
# Run in an elevated PowerShell from the meshlink\deploy folder:
#   .\install-server-windows.ps1 [-UPnP] [-AllowRdpFromMesh] [-WgPort 51820] [-ApiPort 8443]
#
# Fully offline install: put these in meshlink\deploy\offline\ beforehand and nothing is downloaded:
#   wireguard-amd64-*.msi, python-3.*-amd64.exe, and a wheels\ folder created on any PC with
#   `pip download -r ..\requirements.txt -d wheels --platform win_amd64 --python-version 3.12 --only-binary=:all:`
param(
    [switch] $UPnP,             # let the server open the router ports itself (UPnP)
    [switch] $AllowRdpFromMesh, # allow Remote Desktop into this server from MeshLink devices
    [int] $WgPort = 51820,
    [int] $ApiPort = 8443
)
$ErrorActionPreference = 'Stop'
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this script as Administrator.' }

$src     = Split-Path $PSScriptRoot -Parent
$offline = Join-Path $PSScriptRoot 'offline'
$appDir  = "$env:ProgramFiles\MeshLink"
$dataDir = "$env:ProgramData\MeshLink\server"

# ---- 1. WireGuard ---------------------------------------------------------------------------
if (-not (Test-Path "$env:ProgramFiles\WireGuard\wireguard.exe")) {
    $msi = Get-ChildItem $offline -Filter 'wireguard-*.msi' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($msi) { Start-Process msiexec.exe -ArgumentList "/i `"$($msi.FullName)`" /qn DO_NOT_LAUNCH=1" -Wait }
    else { winget install --id WireGuard.WireGuard -e --accept-source-agreements --accept-package-agreements }
}

# ---- 2. Python (machine-wide, so the SYSTEM account can run it) ------------------------------
$python = "$env:ProgramFiles\Python312\python.exe"
if (-not (Test-Path $python)) {
    $exe = Get-ChildItem $offline -Filter 'python-3*-amd64.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($exe) { Start-Process $exe.FullName -ArgumentList '/quiet InstallAllUsers=1 PrependPath=1 Include_test=0' -Wait }
    else { winget install --id Python.Python.3.12 -e --scope machine --accept-source-agreements --accept-package-agreements }
    $python = (Get-ChildItem "$env:ProgramFiles\Python3*\python.exe" | Select-Object -Last 1).FullName
}

# ---- 3. Application files + virtual environment ----------------------------------------------
New-Item -ItemType Directory -Force -Path $appDir, $dataDir | Out-Null
Copy-Item "$src\server" $appDir -Recurse -Force
Copy-Item "$src\requirements.txt", "$src\agent\meshlink.py" $appDir -Force
& $python -m venv "$appDir\.venv"
$pip = "$appDir\.venv\Scripts\pip.exe"
if (Test-Path "$offline\wheels") { & $pip install --no-index --find-links "$offline\wheels" -r "$appDir\requirements.txt" }
else { & $pip install -r "$appDir\requirements.txt" }
if ($LASTEXITCODE -ne 0) { throw 'Installing Python packages failed.' }

# ---- 4. Settings (admin token is generated once and kept) ------------------------------------
$envFile = "$dataDir\server.env"
if (-not (Test-Path $envFile)) {
    $bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $token = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''
    @(
        "MESHLINK_ADMIN_TOKEN=$token",
        'MESHLINK_PUBLIC_ENDPOINT=auto',
        "MESHLINK_WG_PORT=$WgPort",
        "MESHLINK_API_PORT=$ApiPort",
        'MESHLINK_CIDR=100.96.0.0/16',
        'MESHLINK_DOMAIN=mesh',
        'MESHLINK_HUB_APPLY=1',
        "MESHLINK_UPNP_MAP=$([int][bool]$UPnP)"
    ) | Set-Content -Path $envFile -Encoding ASCII
}
# Only SYSTEM and Administrators may read the data folder (database holds the hub private key).
icacls "$env:ProgramData\MeshLink" /inheritance:r /grant:r 'SYSTEM:(OI)(CI)F' 'Administrators:(OI)(CI)F' | Out-Null

# ---- 5. Routing + firewall --------------------------------------------------------------------
Set-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters' -Name IPEnableRouter -Value 1
Remove-NetFirewallRule -DisplayName 'MeshLink WireGuard', 'MeshLink API', 'MeshLink RDP to server' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'MeshLink WireGuard' -Direction Inbound -Protocol UDP -LocalPort $WgPort -Action Allow | Out-Null
New-NetFirewallRule -DisplayName 'MeshLink API' -Direction Inbound -Protocol TCP -LocalPort $ApiPort -Action Allow | Out-Null
if ($AllowRdpFromMesh) {
    Set-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server' -Name fDenyTSConnections -Value 0
    Set-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp' -Name UserAuthentication -Value 1
    New-NetFirewallRule -DisplayName 'MeshLink RDP to server' -Direction Inbound -Protocol TCP -LocalPort 3389 `
        -RemoteAddress 100.96.0.0/16 -Action Allow | Out-Null
}

# ---- 6. TLS certificate + background service ---------------------------------------------------
$env:MESHLINK_DATA_DIR = $dataDir
Push-Location $appDir
$fingerprint = & "$appDir\.venv\Scripts\python.exe" -c "from server.tls import ensure_cert; print(ensure_cert(r'$dataDir\tls')[2])"
Pop-Location

$action   = New-ScheduledTaskAction -Execute "$appDir\.venv\Scripts\python.exe" -Argument '-m server.run' -WorkingDirectory $appDir
$trigger  = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit 0 -AllowStartIfOnBatteries
Register-ScheduledTask -TaskName 'MeshLink Server' -Action $action -Trigger $trigger -Settings $settings `
    -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Stop-ScheduledTask -TaskName 'MeshLink Server' -ErrorAction SilentlyContinue
Start-ScheduledTask -TaskName 'MeshLink Server'

$adminToken = (Select-String -Path $envFile -Pattern '^MESHLINK_ADMIN_TOKEN=(.*)$').Matches[0].Groups[1].Value
Write-Host ''
Write-Host 'MeshLink server installed and started.' -ForegroundColor Green
Write-Host "  Dashboard        : https://localhost:$ApiPort/  (accept the self-signed certificate warning)"
Write-Host "  Admin token      : $adminToken"
Write-Host "  Cert fingerprint : $fingerprint"
Write-Host ''
Write-Host 'Router: forward these ports to this PC''s LAN IP (or re-run with -UPnP if the router supports it):'
Write-Host "  UDP $WgPort  and  TCP $ApiPort"
Write-Host 'Give this PC a fixed LAN IP (DHCP reservation on the router).'
