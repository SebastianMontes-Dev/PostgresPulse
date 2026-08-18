# ============================================================
# PostgresPulse - Respaldo y restauracion de pulse-db
# (docs/DEPLOYMENT.md #5.3: hasta ahora solo habia un pg_dump manual de
# una linea, sin contraparte de restauracion).
#
# pulse-db es la BD PROPIA de la aplicacion (fuentes registradas +
# historial de analisis) -- nunca la BD objetivo que se analiza.
#
# Uso:
#   .\scripts\respaldar.ps1 -Accion Backup
#   .\scripts\respaldar.ps1 -Accion Restaurar -Archivo backups\pulse_db_2026-08-18.sql
# ============================================================

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Backup", "Restaurar")]
    [string]$Accion,

    [string]$Contenedor = "pulse-db",
    [string]$Usuario = "pulse",
    [string]$BaseDeDatos = "pulse_db",
    [string]$DirectorioBackups = "backups",
    [string]$Archivo
)

$ErrorActionPreference = "Stop"

if ($Accion -eq "Backup") {
    New-Item -ItemType Directory -Force -Path $DirectorioBackups | Out-Null
    $destino = Join-Path $DirectorioBackups "pulse_db_$(Get-Date -Format 'yyyy-MM-dd_HHmmss').sql"

    Write-Host "Respaldando $BaseDeDatos desde el contenedor '$Contenedor' ..." -ForegroundColor Cyan
    docker exec $Contenedor pg_dump -U $Usuario -d $BaseDeDatos | Out-File -FilePath $destino -Encoding utf8
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump fallo (codigo $LASTEXITCODE)"
    }

    $tamano = (Get-Item $destino).Length
    Write-Host "Respaldo guardado en: $destino ($tamano bytes)" -ForegroundColor Green
    exit 0
}

# Restaurar
if (-not $Archivo) {
    throw "Falta -Archivo <ruta-al-backup.sql>"
}
if (-not (Test-Path $Archivo)) {
    throw "No existe el archivo: $Archivo"
}

Write-Host "ADVERTENCIA: esto reemplaza el contenido actual de '$BaseDeDatos' en el contenedor '$Contenedor'." -ForegroundColor Yellow
Write-Host "Detén la app (servicio 'app' de docker-compose.yml) antes de continuar para evitar" -ForegroundColor Yellow
Write-Host "escrituras concurrentes durante la restauracion." -ForegroundColor Yellow
$confirmacion = Read-Host "Escribe 'restaurar' para confirmar"
if ($confirmacion -ne "restaurar") {
    Write-Host "Cancelado."
    exit 1
}

Write-Host "Restaurando '$Archivo' sobre '$BaseDeDatos' ..." -ForegroundColor Cyan
Get-Content $Archivo -Raw | docker exec -i $Contenedor psql -U $Usuario -d $BaseDeDatos -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
    throw "La restauracion fallo (codigo $LASTEXITCODE)"
}

Write-Host "Restauracion completada." -ForegroundColor Green
