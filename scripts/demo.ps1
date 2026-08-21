# ============================================================
# PostgresPulse - Demostracion E2E (docs/SPECS.md #14, fila
# "Demostracion E2E": registrar -> analizar -> ver panel -> exportar).
#
# Requiere los 3 servicios de docker-compose.yml arriba y la fuente
# "Ventas Demo" ya sembrada automaticamente (PULSE_DEMO_SEED=true).
# No registra nada: solo recorre y verifica el flujo real via API REST.
#
# Uso:
#   .\scripts\demo.ps1
#   .\scripts\demo.ps1 -BaseUrl "http://localhost:8080" -Usuario admin -Contrasena admin
# ============================================================

param(
    [string]$BaseUrl = $(if ($env:PULSE_DEMO_URL) { $env:PULSE_DEMO_URL } else { "http://localhost:8080" }),
    [string]$Usuario = $(if ($env:PULSE_ADMIN_USER) { $env:PULSE_ADMIN_USER } else { "admin" }),
    [string]$Contrasena = $(if ($env:PULSE_ADMIN_PASSWORD) { $env:PULSE_ADMIN_PASSWORD } else { "admin" }),
    [string]$NombreFuente = "Ventas Demo"
)

$ErrorActionPreference = "Stop"

$loginBody = @{ usuario = $Usuario; contrasena = $Contrasena } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ Authorization = "Bearer $($login.token)" }

function Paso($numero, $texto) {
    Write-Host ""
    Write-Host "[$numero] $texto" -ForegroundColor Cyan
}

Paso 1 "Listando fuentes registradas en $BaseUrl ..."
$fuentes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes" -Headers $headers -Method Get
$fuente = $fuentes | Where-Object { $_.nombre -eq $NombreFuente } | Select-Object -First 1
if (-not $fuente) {
    throw "No se encontro la fuente '$NombreFuente'. ¿Esta PULSE_DEMO_SEED=true y target-demo saludable?"
}
Write-Host "  Fuente encontrada: id=$($fuente.id) estado=$($fuente.estado)"

Paso 2 "Probando conexion a la fuente ..."
$prueba = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes/$($fuente.id)/probar" -Headers $headers -Method Post
Write-Host "  Resultado: $($prueba | ConvertTo-Json -Compress)"

Paso 3 "Ejecutando analisis (8 chequeos) ..."
$analisis = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes/$($fuente.id)/analizar" -Headers $headers -Method Post
Write-Host "  Analisis #$($analisis.id): puntaje=$($analisis.puntajeSalud) estado=$($analisis.estado) duracion=$($analisis.duracionMs)ms"

Paso 4 "Consultando salud y tendencia 7d ..."
$salud = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes/$($fuente.id)/salud" -Headers $headers -Method Get
Write-Host "  Puntaje actual: $($salud.puntajeActual) ($($salud.estadoActual)) - puntos de tendencia: $($salud.tendencia7d.Count)"

Paso 5 "Exportando reporte HTML del analisis #$($analisis.id) ..."
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$destino = Join-Path $scriptDir "..\target\demo-reporte-$($analisis.id).html"
New-Item -ItemType Directory -Force -Path (Split-Path $destino) | Out-Null
Invoke-WebRequest -Uri "$BaseUrl/api/v1/analisis/$($analisis.id)/exportar?formato=html" -Headers $headers -OutFile $destino
Write-Host "  Reporte guardado en: $destino"

Write-Host ""
Write-Host "Demostracion completa. Panel: $BaseUrl/fuentes/$($fuente.id)" -ForegroundColor Green
