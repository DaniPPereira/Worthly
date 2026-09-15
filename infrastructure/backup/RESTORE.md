# PostgreSQL backup and restore

Daily encrypted logical backups (`pg_dump` + gzip + OpenSSL AES-256-CBC)
are mandatory in production. Keep `WORTHLY_BACKUP_KEY_FILE` off the
database host. Default retention: 7 daily + 4 weekly copies.

## Backup

From `infrastructure/`, with a passphrase file that is not stored next to
PostgreSQL:

```bash
export WORTHLY_BACKUP_KEY_FILE=/path/to/backup-passphrase.txt
export WORTHLY_COMPOSE_FILE=./compose.prod.yaml   # optional; default is compose.yaml
./backup/backup.sh ./backups
```

The script writes `worthly-YYYYMMDDTHHMMSSZ.sql.gz.enc`.

Local development only:

```bash
WORTHLY_BACKUP_ALLOW_PLAIN=1 ./backup/backup.sh ./backups
```

## Restore

1. Stop `api` and `web`.
2. Recreate an empty database volume or drop/create `worthly`.
3. Decrypt locally, then restore:

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -pass file:$WORTHLY_BACKUP_KEY_FILE \
  -in worthly-YYYYMMDDTHHMMSSZ.sql.gz.enc \
| gunzip | docker compose exec -T postgres psql -U worthly -d worthly
```

Unencrypted local dumps:

```bash
gunzip -c worthly-YYYYMMDD.sql.gz | docker compose exec -T postgres \
  psql -U worthly -d worthly
```

4. Start `api` and confirm `/actuator/health/readiness` is UP.
5. Sign in as the owner and confirm `/api/v1/me`.

A restore drill should be run after the first production backup and at
least quarterly afterwards.
