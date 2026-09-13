# Decision Register

Locked for v4.1:

- single owner
- local Spring OAuth/OIDC authority
- Web BFF cookie session; API Bearer-only
- Mobile Authorization Code + PKCE
- no public registration
- owner bootstrap from server secret
- Europe/Lisbon reporting timezone default; EUR reporting currency default
- PATCH /me updates timezone/currency after IANA/ISO validation
- no implicit FX; aggregates grouped by currency
- read-only providers
- Enable Banking ASPSP dynamic discovery
- Trading 212 configuration-backed unique connection per owner
- transfer thresholds 90 auto / 70 suggestion
- raw provider payload encrypted, default 30-day retention
- access 10m / refresh idle 14d / refresh absolute 30d / web idle 30m / web absolute 12h
- connection statuses include CONFIGURATION_REQUIRED
- account types CURRENT/SAVINGS/CARD/BROKERAGE/OTHER with fixed aggregate inclusion
- scheduler tick 15m; ACTIVE sync if last success older than 3h; stale 24h
- disconnect retains history; purge requires confirm=true
- categorization operators EQUALS/CONTAINS/STARTS_WITH only; heuristic is a static fixture map
- UI copy English; number/date format pt-PT
- Android API 26+ / iOS 16+
- production host baseline 2 vCPU / 4 GiB RAM / 40 GiB disk
- OAuth redirect URIs allowlisted (ADR-002)
- OpenAPI 4.1.0 is the HTTP contract
