# Phase 1 --- Enable Banking Proof

## Goal

Securely retrieve and persist one real owned bank account twice without
duplicates.

## Tasks

1.  Enable Banking configuration: app ID/key path/base URL/callback URL.
2.  JWT signer with private RSA key loaded from secret file.
3.  `GET /aspsps?country=PT` adapter + cached short-lived discovery.
4.  Worthly `GET /connections/banks` returns supported discovered
    choices relevant to v1.
5.  Start authorization endpoint persists hashed state with 10m TTL.
6.  Callback validates state and exchanges code via `POST /sessions`.
7.  Persist connection and accounts; reconcile `identification_hash`.
8.  Fetch balances.
9.  Fetch transactions with continuation until exhausted.
10. Persist source + normalized records transactionally.
11. Run same sync again; assert zero duplicates.
12. Simulate 429, expired/revoked session and callback replay.
13. Validate one restricted-production owned Santander or Revolut
    connection.
14. Sanitize fixtures before committing.

## Exit

-   One real account imported.
-   Second sync duplicates = 0.
-   State replay rejected.
-   Account remains same local ID after test reauthorization when
    identification_hash matches.
-   429 schedules retry without tight loop.
-   Provider secret/private key absent from logs/API/browser/mobile.
