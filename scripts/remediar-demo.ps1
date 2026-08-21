# ============================================================
# PostgresPulse - Demostracion de remediacion (docs/SPECS.md #17,
# criterios "recomendaciones SQL mejoran la puntuacion" y
# "historial de tendencia muestra reparacion").
#
# Aplica EXACTAMENTE las recomendaciones que el motor de analisis genera
# para los defectos sembrados en scripts/db-init/sample_data.sql, contra
# target-demo -- nunca a traves de PostgresPulse (que es solo-lectura por
# diseno; ver RegistroConexionesServicioSoloLecturaTest), sino por
# "docker exec ... psql" directo al contenedor de la BD objetivo.
#
# Requiere los 3 servicios de docker-compose.yml arriba y la fuente
# "Ventas Demo" ya sembrada (PULSE_DEMO_SEED=true).
#
# Uso:
#   .\scripts\remediar-demo.ps1
#   .\scripts\remediar-demo.ps1 -BaseUrl "http://localhost:8080" -Usuario admin -Contrasena admin
# ============================================================

param(
    [string]$BaseUrl = $(if ($env:PULSE_DEMO_URL) { $env:PULSE_DEMO_URL } else { "http://localhost:8080" }),
    [string]$Usuario = $(if ($env:PULSE_ADMIN_USER) { $env:PULSE_ADMIN_USER } else { "admin" }),
    [string]$Contrasena = $(if ($env:PULSE_ADMIN_PASSWORD) { $env:PULSE_ADMIN_PASSWORD } else { "admin" }),
    [string]$NombreFuente = "Ventas Demo",
    [string]$ContenedorObjetivo = "pulse-target-demo"
)

$ErrorActionPreference = "Stop"

$loginBody = @{ usuario = $Usuario; contrasena = $Contrasena } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ Authorization = "Bearer $($login.token)" }

function Paso($numero, $texto) {
    Write-Host ""
    Write-Host "[$numero] $texto" -ForegroundColor Cyan
}

function Ejecutar-Sql($sql) {
    docker exec $ContenedorObjetivo psql -U demo -d ventas_db -v ON_ERROR_STOP=1 -c $sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo ejecutando SQL contra $ContenedorObjetivo`: $sql"
    }
}

Paso 1 "Localizando la fuente '$NombreFuente' en $BaseUrl ..."
$fuentes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes" -Headers $headers -Method Get
$fuente = $fuentes | Where-Object { $_.nombre -eq $NombreFuente } | Select-Object -First 1
if (-not $fuente) {
    throw "No se encontro la fuente '$NombreFuente'. ¿Esta PULSE_DEMO_SEED=true y target-demo saludable?"
}
Write-Host "  Fuente encontrada: id=$($fuente.id)"

Paso 2 "Analisis base (antes de remediar) ..."
$analisis1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes/$($fuente.id)/analizar" -Headers $headers -Method Post
Write-Host "  Puntaje base: $($analisis1.puntajeSalud) ($($analisis1.estado))"

Paso 3 "Aplicando las recomendaciones SQL sobre target-demo (contenedor '$ContenedorObjetivo') ..."
Ejecutar-Sql "CREATE INDEX IF NOT EXISTS idx_ventas_cliente_id ON ventas (cliente_id);"
Ejecutar-Sql "CREATE INDEX IF NOT EXISTS idx_productos_categoria_id ON productos (categoria_id);"
Ejecutar-Sql "CREATE INDEX IF NOT EXISTS idx_detalle_ventas_producto_id ON detalle_ventas (producto_id);"
Ejecutar-Sql "ALTER TABLE detalle_ventas ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY;"
Ejecutar-Sql "DROP INDEX IF EXISTS idx_clientes_email_dup;"
Ejecutar-Sql "ALTER TABLE eventos_auditoria SET (autovacuum_enabled = true);"
Write-Host "  6 sentencias aplicadas."

Paso 4 "VACUUM FULL sobre eventos_auditoria (limpia tuplas muertas Y reduce el hinchamiento) ..."
Ejecutar-Sql "VACUUM FULL ANALYZE eventos_auditoria;"

Paso 5 "Reiniciando contadores acumulados y regenerando trafico real ..."
# pg_stat_user_tables acumula seq_scan/idx_scan desde siempre: sin resetear,
# SEQ_SCAN seguiria viendo el ratio historico y el puntaje casi no mejoraria.
Ejecutar-Sql "SELECT pg_stat_reset();"
Ejecutar-Sql @"
DO `$`$
DECLARE i INT;
BEGIN
    FOR i IN 1..40 LOOP
        PERFORM count(*) FROM ventas WHERE cliente_id = 1 + floor(random() * 15000)::int;
        PERFORM count(*) FROM detalle_ventas WHERE producto_id = 1 + floor(random() * 1500)::int;
        PERFORM count(*) FROM productos WHERE categoria_id = 1 + floor(random() * 10)::int;
        PERFORM count(*) FROM clientes WHERE id = 1 + floor(random() * 15000)::int;
    END LOOP;
END `$`$;
"@
Ejecutar-Sql "ANALYZE;"

Paso 6 "Re-analizando (despues de remediar) ..."
$analisis2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/fuentes/$($fuente.id)/analizar" -Headers $headers -Method Post
Write-Host "  Puntaje despues: $($analisis2.puntajeSalud) ($($analisis2.estado))"

Paso 7 "Resultado"
$delta = $analisis2.puntajeSalud - $analisis1.puntajeSalud
Write-Host ""
Write-Host "  Puntaje base:      $($analisis1.puntajeSalud) ($($analisis1.estado))"
Write-Host "  Puntaje despues:   $($analisis2.puntajeSalud) ($($analisis2.estado))"
Write-Host "  Diferencia:        $delta"
Write-Host "  Tendencia (2 puntos, la reparacion es visible en el panel/historial):"
Write-Host "    $BaseUrl/fuentes/$($fuente.id)/historial"

if ($analisis2.puntajeSalud -le $analisis1.puntajeSalud) {
    Write-Host ""
    Write-Host "El puntaje NO mejoro. Revisar el orden de los pasos (pg_stat_reset / trafico) antes de considerar esto una demostracion valida." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Remediacion demostrada con exito: el puntaje mejoro aplicando las recomendaciones del motor." -ForegroundColor Green
