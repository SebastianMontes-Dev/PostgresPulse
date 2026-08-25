# ============================================================
# PostgresPulse - Genera el token JWT que usa Prometheus para raspar
# /actuator/prometheus (docs/DEPLOYMENT.md #5.6).
#
# /actuator/** exige rol ADMIN (RBAC desde v1.4.0): usa las mismas
# credenciales de administrador que scripts/demo.ps1, o las que pases via
# -Usuario/-Contrasena. Vuelve a correr este script para refrescar el
# token si expira (PULSE_JWT_EXPIRACION_MINUTOS, 480min por defecto) --
# equivalente manual al "cron externo" que docs/DEPLOYMENT.md #5.4
# describe para credentials_file.
#
# Uso:
#   .\scripts\generar-token-monitoreo.ps1
#   .\scripts\generar-token-monitoreo.ps1 -BaseUrl "http://localhost:8080" -Usuario admin -Contrasena admin
# ============================================================

param(
    [string]$BaseUrl = $(if ($env:PULSE_DEMO_URL) { $env:PULSE_DEMO_URL } else { "http://localhost:8080" }),
    [string]$Usuario = $(if ($env:PULSE_ADMIN_USER) { $env:PULSE_ADMIN_USER } else { "admin" }),
    [string]$Contrasena = $(if ($env:PULSE_ADMIN_PASSWORD) { $env:PULSE_ADMIN_PASSWORD } else { "admin" })
)

$ErrorActionPreference = "Stop"

$loginBody = @{ usuario = $Usuario; contrasena = $Contrasena } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$destino = Join-Path $scriptDir "..\monitoring\prometheus_token.txt"
[System.IO.File]::WriteAllText($destino, $login.token)

Write-Host "Token de monitoreo escrito en: $destino"
Write-Host "Reinicia el contenedor prometheus (o recarga su config) para que tome el token nuevo:"
Write-Host "  docker compose --profile monitoring restart prometheus"
