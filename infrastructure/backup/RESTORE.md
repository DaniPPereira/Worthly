# PostgreSQL backup and restore

Daily encrypted logical backups (`pg_dump`) are the Phase 0 baseline.
Keep the encryption key off the database host. Default retention: 7 daily
+ 4 weekly copies.

## Backup

From `infrastructure/`:

```bash
./backup/backup.sh ./backups
```

Encrypt the resulting `.sql.gz` with a key that is not stored on the
same disk as PostgreSQL.

## Restore

1. Stop `api` and `web`.
2. Recreate an empty database volume or drop/create `worthly`.
3. Decrypt the backup locally.
4. Restore:

```bash
gunzip -c worthly-YYYYMMDD.sql.gz | docker compose exec -T postgres \
  psql -U worthly -d worthly
```

5. Start `api` and confirm `/actuator/health/readiness` is UP.
6. Sign in as the owner and confirm `/api/v1/me`.

A restore drill should be run after the first production backup and at
least quarterly afterwards.
