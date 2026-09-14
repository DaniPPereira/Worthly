# DevOps and Operations

## Environments

`local`, `ci`, `production`. CI never touches real accounts. Enable
Banking sandbox/mock and Trading 212 demo for development where
applicable.

## Runtime

Docker Compose: - worthly-api - worthly-web - postgres - optional
metrics stack

Mobile is built separately and distributed as signed Android/iOS
artifacts.

Production host baseline for a registered household and 100k local
transactions: **2 vCPU, 4 GiB RAM, 40 GiB disk** (database volume
included). Smaller hosts are acceptable for local development.

## Secrets

Mounted secret files or protected CI secrets. `.env.example` contains
names/placeholders only.

## CI

secret scan -\> backend build/lint -\> frontend typecheck/lint -\>
Flutter analyze/test -\> Testcontainers -\> OpenAPI validation -\> build
images -\> dependency/SAST/image scan.

## Backups

Daily encrypted PostgreSQL logical backup; 7 daily + 4 weekly default.
Store separately from primary disk. Quarterly restore drill after
initial monthly validation. Encryption key separate from backup.

## Observability

Metrics: sync duration/result, imported counts, provider latency/status,
rate-limit events, last successful sync age, auth failures, DB pool,
disk/backup health.

Liveness is process-only. Readiness requires DB. Provider outage does
not mark app dead.

## Deployment

Immutable image SHA tags. Manual production deployment initially. Backup
before migrations. Expand/migrate/contract for destructive changes.

## Push exception

FCM/APNs is permitted despite self-hosting goal because native OS push
requires platform transport. It carries only notification
metadata/opaque IDs.

## Runbooks

Provider reauth, T212 key rotation, Enable Banking key rotation, OAuth
signing-key rotation, owner password recovery, DB restore, rollback,
disk full, provider outage and suspected compromise.
