#!/usr/bin/env bash
set -euo pipefail
# Logical backup of the Worthly PostgreSQL volume. Encrypt and store off-host.
# Usage: ./backup.sh [output-dir]

OUT="${1:-./backups}"
mkdir -p "$OUT"
FILE="$OUT/worthly-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
docker compose -f "$(dirname "$0")/../compose.yaml" exec -T postgres \
  pg_dump -U worthly worthly | gzip > "$FILE"
echo "Wrote $FILE"
