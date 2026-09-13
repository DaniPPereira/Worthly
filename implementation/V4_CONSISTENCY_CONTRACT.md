# Contract authority

There is **no open disagreement** between prose and OpenAPI as of
specification 4.1.0.

Split of authority (so a future edit cannot fork the design):

| Subject | Canonical file |
|---------|----------------|
| HTTP paths, DTOs, status codes, query params | `api/openapi.yaml` 4.1.0 |
| Formulas, account-type inclusion, matching | `domain/*` |
| Tables, unique indexes, disconnect/purge SQL rules | `database/SCHEMA.md` |
| Auth token placement | `adr/ADR-002-authentication.md` |
| Product intent (MUST/SHOULD) | `01_PRODUCT_REQUIREMENTS.md` |

If a later PR changes one column of this table, it MUST change the
others in the same PR. That is an editing rule, not an unresolved
design issue.

## Locked alignments (already applied)

1. Aggregates: `totalsByCurrency[]` on `/analytics/summary`,
   `/analytics/monthly`, `/investments/summary`.
2. Trading 212: configuration-backed unique connection; missing secrets
   = `CONFIGURATION_REQUIRED`; 401/403 = `ERROR`.
3. Web: encrypted BFF cookie holds tokens; API is Bearer-only;
   `POST /me/logout` plus cookie clear.
4. Owner prefs: `app_user` columns + `PATCH /me`.
5. Transaction list and CSV share the same filters; CSV columns are on
   the export operation.
6. CURRENT/SAVINGS in liquid cash and net worth; CARD/OTHER excluded;
   BROKERAGE via investments.
7. Disconnect keeps history; purge requires `confirm=true`.
