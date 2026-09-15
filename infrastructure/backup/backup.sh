#!/usr/bin/env bash
set -euo pipefail
# Logical backup of the Worthly PostgreSQL volume.
# Production: set WORTHLY_BACKUP_KEY_FILE to a passphrase file (not on the DB disk).
# Local plaintext dumps: WORTHLY_BACKUP_ALLOW_PLAIN=1 ./backup.sh
# Usage: ./backup.sh [output-dir]

OUT="${1:-./backups}"
mkdir -p "$OUT"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
COMPOSE="${WORTHLY_COMPOSE_FILE:-$(dirname "$0")/../compose.yaml}"

dump() {
  docker compose -f "$COMPOSE" exec -T postgres pg_dump -U worthly worthly | gzip
}

KEY_FILE="${WORTHLY_BACKUP_KEY_FILE:-}"
if [ -n "$KEY_FILE" ]; then
  if [ ! -f "$KEY_FILE" ]; then
    echo "WORTHLY_BACKUP_KEY_FILE does not exist: $KEY_FILE" >&2
    exit 1
  fi
  FILE="$OUT/worthly-$STAMP.sql.gz.enc"
  dump | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "file:$KEY_FILE" -out "$FILE"
  echo "Wrote encrypted $FILE"
  exit 0
fi

if [ "${WORTHLY_BACKUP_ALLOW_PLAIN:-}" != "1" ]; then
  echo "Refusing an unencrypted dump. Set WORTHLY_BACKUP_KEY_FILE (production) or WORTHLY_BACKUP_ALLOW_PLAIN=1 (local)." >&2
  exit 1
fi

FILE="$OUT/worthly-$STAMP.sql.gz"
dump > "$FILE"
echo "Wrote unencrypted $FILE"
