#!/usr/bin/env bash
# ============================================================
# PostgresPulse - Demostracion E2E (docs/SPECS.md #14, fila
# "Demostracion E2E": registrar -> analizar -> ver panel -> exportar).
#
# Equivalente en cURL/bash de scripts/demo.ps1, para CI/Linux/macOS.
# Requiere los 3 servicios de docker-compose.yml arriba, la fuente
# "Ventas Demo" ya sembrada (PULSE_DEMO_SEED=true) y `jq` instalado.
#
# Uso:
#   ./scripts/demo.sh
#   PULSE_DEMO_URL=http://localhost:8080 PULSE_ADMIN_USER=admin PULSE_ADMIN_PASSWORD=admin ./scripts/demo.sh
# ============================================================
set -euo pipefail

BASE_URL="${PULSE_DEMO_URL:-http://localhost:8080}"
USUARIO="${PULSE_ADMIN_USER:-admin}"
CONTRASENA="${PULSE_ADMIN_PASSWORD:-admin}"
NOMBRE_FUENTE="${PULSE_DEMO_NOMBRE:-Ventas Demo}"
AUTH="${USUARIO}:${CONTRASENA}"

paso() { echo; echo "[$1] $2"; }

paso 1 "Listando fuentes registradas en $BASE_URL ..."
fuentes_json="$(curl -sf -u "$AUTH" "$BASE_URL/api/v1/fuentes")"
fuente_id="$(echo "$fuentes_json" | jq -r --arg nombre "$NOMBRE_FUENTE" '.[] | select(.nombre == $nombre) | .id' | head -n1)"
if [ -z "$fuente_id" ] || [ "$fuente_id" = "null" ]; then
  echo "No se encontro la fuente '$NOMBRE_FUENTE'. ¿Esta PULSE_DEMO_SEED=true y target-demo saludable?" >&2
  exit 1
fi
echo "  Fuente encontrada: id=$fuente_id"

paso 2 "Probando conexion a la fuente ..."
curl -sf -u "$AUTH" -X POST "$BASE_URL/api/v1/fuentes/$fuente_id/probar" | jq -c .

paso 3 "Ejecutando analisis (8 chequeos) ..."
analisis_json="$(curl -sf -u "$AUTH" -X POST "$BASE_URL/api/v1/fuentes/$fuente_id/analizar")"
analisis_id="$(echo "$analisis_json" | jq -r '.id')"
echo "  Analisis #$analisis_id: $(echo "$analisis_json" | jq -c '{puntajeSalud, estado, duracionMs}')"

paso 4 "Consultando salud y tendencia 7d ..."
salud_json="$(curl -sf -u "$AUTH" "$BASE_URL/api/v1/fuentes/$fuente_id/salud")"
echo "  $(echo "$salud_json" | jq -c '{puntajeActual, estadoActual, puntosTendencia: (.tendencia7d | length)}')"

paso 5 "Exportando reporte HTML del analisis #$analisis_id ..."
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
destino="$script_dir/../target/demo-reporte-$analisis_id.html"
mkdir -p "$(dirname "$destino")"
curl -sf -u "$AUTH" "$BASE_URL/api/v1/analisis/$analisis_id/exportar?formato=html" -o "$destino"
echo "  Reporte guardado en: $destino"

echo
echo "Demostracion completa. Panel: $BASE_URL/fuentes/$fuente_id"
