#!/usr/bin/env bash
# ============================================================
# PostgresPulse - Respaldo y restauracion de pulse-db
# (docs/DEPLOYMENT.md #5.3: hasta ahora solo habia un pg_dump manual de
# una linea, sin contraparte de restauracion).
#
# pulse-db es la BD PROPIA de la aplicacion (fuentes registradas +
# historial de analisis) -- nunca la BD objetivo que se analiza.
#
# Uso:
#   ./scripts/respaldar.sh backup
#   ./scripts/respaldar.sh restaurar backups/pulse_db_2026-08-18_120000.sql
# ============================================================
set -euo pipefail

ACCION="${1:-}"
CONTENEDOR="${PULSE_DB_CONTENEDOR:-pulse-db}"
USUARIO="${PULSE_DB_USER:-pulse}"
BASE_DE_DATOS="${PULSE_DB_NAME:-pulse_db}"
DIR_BACKUPS="${PULSE_BACKUPS_DIR:-backups}"

case "$ACCION" in
  backup)
    mkdir -p "$DIR_BACKUPS"
    destino="$DIR_BACKUPS/pulse_db_$(date +%F_%H%M%S).sql"
    echo "Respaldando $BASE_DE_DATOS desde el contenedor '$CONTENEDOR' ..."
    docker exec "$CONTENEDOR" pg_dump -U "$USUARIO" -d "$BASE_DE_DATOS" > "$destino"
    echo "Respaldo guardado en: $destino ($(wc -c < "$destino") bytes)"
    ;;
  restaurar)
    archivo="${2:-}"
    if [ -z "$archivo" ]; then
      echo "Uso: $0 restaurar <ruta-al-backup.sql>" >&2
      exit 1
    fi
    if [ ! -f "$archivo" ]; then
      echo "No existe el archivo: $archivo" >&2
      exit 1
    fi
    echo "ADVERTENCIA: esto reemplaza el contenido actual de '$BASE_DE_DATOS' en el contenedor '$CONTENEDOR'."
    echo "Detén la app (servicio 'app' de docker-compose.yml) antes de continuar para evitar"
    echo "escrituras concurrentes durante la restauracion."
    read -r -p "Escribe 'restaurar' para confirmar: " confirmacion
    if [ "$confirmacion" != "restaurar" ]; then
      echo "Cancelado."
      exit 1
    fi
    echo "Restaurando '$archivo' sobre '$BASE_DE_DATOS' ..."
    docker exec -i "$CONTENEDOR" psql -U "$USUARIO" -d "$BASE_DE_DATOS" -v ON_ERROR_STOP=1 < "$archivo"
    echo "Restauracion completada."
    ;;
  *)
    echo "Uso: $0 {backup|restaurar <archivo>}" >&2
    exit 1
    ;;
esac
