#!/usr/bin/env bash
# ============================================================
# PostgresPulse - Demostracion de remediacion (docs/SPECS.md #17,
# criterios "recomendaciones SQL mejoran la puntuacion" y
# "historial de tendencia muestra reparacion").
#
# Equivalente en bash/psql de scripts/remediar-demo.ps1. Aplica
# EXACTAMENTE las recomendaciones que el motor de analisis genera para los
# defectos sembrados en scripts/db-init/sample_data.sql, contra
# target-demo -- nunca a traves de PostgresPulse (que es solo-lectura por
# diseno; ver RegistroConexionesServicioSoloLecturaTest), sino por
# "docker exec ... psql" directo al contenedor de la BD objetivo.
#
# Requiere los 3 servicios de docker-compose.yml arriba, la fuente
# "Ventas Demo" ya sembrada (PULSE_DEMO_SEED=true) y `jq` instalado.
#
# Uso:
#   ./scripts/remediar-demo.sh
#   PULSE_DEMO_URL=http://localhost:8080 PULSE_ADMIN_USER=admin PULSE_ADMIN_PASSWORD=admin ./scripts/remediar-demo.sh
# ============================================================
set -euo pipefail

BASE_URL="${PULSE_DEMO_URL:-http://localhost:8080}"
USUARIO="${PULSE_ADMIN_USER:-admin}"
CONTRASENA="${PULSE_ADMIN_PASSWORD:-admin}"
NOMBRE_FUENTE="${PULSE_DEMO_NOMBRE:-Ventas Demo}"
CONTENEDOR_OBJETIVO="${PULSE_DEMO_CONTENEDOR:-pulse-target-demo}"
TOKEN="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usuario\":\"${USUARIO}\",\"contrasena\":\"${CONTRASENA}\"}" | jq -r .token)"
AUTH="Authorization: Bearer ${TOKEN}"

paso() { echo; echo "[$1] $2"; }

ejecutar_sql() {
  docker exec "$CONTENEDOR_OBJETIVO" psql -U demo -d ventas_db -v ON_ERROR_STOP=1 -c "$1" >/dev/null
}

paso 1 "Localizando la fuente '$NOMBRE_FUENTE' en $BASE_URL ..."
fuentes_json="$(curl -sf -H "$AUTH" "$BASE_URL/api/v1/fuentes")"
fuente_id="$(echo "$fuentes_json" | jq -r --arg nombre "$NOMBRE_FUENTE" '.[] | select(.nombre == $nombre) | .id' | head -n1)"
if [ -z "$fuente_id" ] || [ "$fuente_id" = "null" ]; then
  echo "No se encontro la fuente '$NOMBRE_FUENTE'. ¿Esta PULSE_DEMO_SEED=true y target-demo saludable?" >&2
  exit 1
fi
echo "  Fuente encontrada: id=$fuente_id"

paso 2 "Analisis base (antes de remediar) ..."
analisis1_json="$(curl -sf -H "$AUTH" -X POST "$BASE_URL/api/v1/fuentes/$fuente_id/analizar")"
puntaje1="$(echo "$analisis1_json" | jq -r '.puntajeSalud')"
estado1="$(echo "$analisis1_json" | jq -r '.estado')"
echo "  Puntaje base: $puntaje1 ($estado1)"

paso 3 "Aplicando las recomendaciones SQL sobre target-demo (contenedor '$CONTENEDOR_OBJETIVO') ..."
ejecutar_sql "CREATE INDEX IF NOT EXISTS idx_ventas_cliente_id ON ventas (cliente_id);"
ejecutar_sql "CREATE INDEX IF NOT EXISTS idx_productos_categoria_id ON productos (categoria_id);"
ejecutar_sql "CREATE INDEX IF NOT EXISTS idx_detalle_ventas_producto_id ON detalle_ventas (producto_id);"
ejecutar_sql "ALTER TABLE detalle_ventas ADD COLUMN IF NOT EXISTS id BIGSERIAL PRIMARY KEY;"
ejecutar_sql "DROP INDEX IF EXISTS idx_clientes_email_dup;"
ejecutar_sql "ALTER TABLE eventos_auditoria SET (autovacuum_enabled = true);"
echo "  6 sentencias aplicadas."

paso 4 "VACUUM FULL sobre eventos_auditoria (limpia tuplas muertas Y reduce el hinchamiento) ..."
ejecutar_sql "VACUUM FULL ANALYZE eventos_auditoria;"

paso 5 "Reiniciando contadores acumulados y regenerando trafico real ..."
# pg_stat_user_tables acumula seq_scan/idx_scan desde siempre: sin resetear,
# SEQ_SCAN seguiria viendo el ratio historico y el puntaje casi no mejoraria.
ejecutar_sql "SELECT pg_stat_reset();"
ejecutar_sql "
DO \$\$
DECLARE i INT;
BEGIN
    FOR i IN 1..40 LOOP
        PERFORM count(*) FROM ventas WHERE cliente_id = 1 + floor(random() * 15000)::int;
        PERFORM count(*) FROM detalle_ventas WHERE producto_id = 1 + floor(random() * 1500)::int;
        PERFORM count(*) FROM productos WHERE categoria_id = 1 + floor(random() * 10)::int;
        PERFORM count(*) FROM clientes WHERE id = 1 + floor(random() * 15000)::int;
    END LOOP;
END \$\$;
"
ejecutar_sql "ANALYZE;"

paso 6 "Re-analizando (despues de remediar) ..."
analisis2_json="$(curl -sf -H "$AUTH" -X POST "$BASE_URL/api/v1/fuentes/$fuente_id/analizar")"
puntaje2="$(echo "$analisis2_json" | jq -r '.puntajeSalud')"
estado2="$(echo "$analisis2_json" | jq -r '.estado')"
echo "  Puntaje despues: $puntaje2 ($estado2)"

paso 7 "Resultado"
echo
echo "  Puntaje base:      $puntaje1 ($estado1)"
echo "  Puntaje despues:   $puntaje2 ($estado2)"
echo "  Tendencia (2 puntos, la reparacion es visible en el panel/historial):"
echo "    $BASE_URL/fuentes/$fuente_id/historial"

if awk -v a="$puntaje2" -v b="$puntaje1" 'BEGIN { exit !(a <= b) }'; then
  echo
  echo "El puntaje NO mejoro. Revisar el orden de los pasos (pg_stat_reset / trafico) antes de considerar esto una demostracion valida." >&2
  exit 1
fi

echo
echo "Remediacion demostrada con exito: el puntaje mejoro aplicando las recomendaciones del motor."
