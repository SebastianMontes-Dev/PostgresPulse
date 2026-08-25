#!/usr/bin/env bash
# ============================================================
# PostgresPulse - Genera el token JWT que usa Prometheus para raspar
# /actuator/prometheus (docs/DEPLOYMENT.md #5.6).
#
# /actuator/** exige estar autenticado: usa las mismas credenciales que
# scripts/demo.sh, o las que pases via PULSE_ADMIN_USER/PULSE_ADMIN_PASSWORD.
# Vuelve a correr este script para
# refrescar el token si expira (PULSE_JWT_EXPIRACION_MINUTOS, 480min por
# defecto) -- equivalente manual al "cron externo" que docs/DEPLOYMENT.md
# #5.4 describe para credentials_file. Requiere `jq` instalado.
#
# Uso:
#   ./scripts/generar-token-monitoreo.sh
#   PULSE_DEMO_URL=http://localhost:8080 PULSE_ADMIN_USER=admin PULSE_ADMIN_PASSWORD=admin ./scripts/generar-token-monitoreo.sh
# ============================================================
set -euo pipefail

BASE_URL="${PULSE_DEMO_URL:-http://localhost:8080}"
USUARIO="${PULSE_ADMIN_USER:-admin}"
CONTRASENA="${PULSE_ADMIN_PASSWORD:-admin}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
destino="$script_dir/../monitoring/prometheus_token.txt"

TOKEN="$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usuario\":\"${USUARIO}\",\"contrasena\":\"${CONTRASENA}\"}" | jq -r .token)"

printf '%s' "$TOKEN" > "$destino"

echo "Token de monitoreo escrito en: $destino"
echo "Reinicia el contenedor prometheus (o recarga su config) para que tome el token nuevo:"
echo "  docker compose --profile monitoring restart prometheus"
