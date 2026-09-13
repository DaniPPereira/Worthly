# Worthly v4 --- Implementation-Ready Specification

**Status:** Canonical engineering specification\
**Product:** Worthly --- self-hosted personal finance platform\
**Clients:** Worthly Web + Worthly Mobile\
**Version:** 4.1

The Markdown/YAML/SQL files in this repository are canonical. The
consolidated DOCX is generated documentation and MUST NOT override them.

## Locked technology decisions

-   Java 21 LTS + Spring Boot 3.x
-   Spring Security + Spring Authorization Server
-   PostgreSQL + Flyway
-   Next.js + React + TypeScript for Web
-   Flutter + Dart for Android/iOS
-   OpenAPI 3.1 contract
-   Modular monolith / Hexagonal Architecture
-   Docker Compose deployment
-   Enable Banking AIS for Santander Portugal + Revolut
-   Trading 212 Public API, read-only
-   Single owner in v1
-   Europe/Lisbon as the owner's reporting timezone by default;
    configurable
-   EUR as reporting currency by default; configurable
-   Provider credentials only in backend
-   No payment initiation or trading in v1

## Authentication decision

Worthly owns one local identity and exposes standards-based OAuth/OIDC
endpoints.

-   **Web:** server-side/BFF session using Secure + HttpOnly + SameSite
    cookie. Browser never stores OAuth tokens.
-   **Mobile:** OAuth 2.1 Authorization Code + PKCE public client.
    Short-lived access token; rotating refresh token in OS secure
    storage.
-   **API:** OAuth2 Resource Server; Bearer access token only. The BFF
    `web_session` cookie is not an API credential.
-   **Owner:** bootstrapped once from server-side secret files. Public
    registration does not exist.

See `adr/ADR-002-authentication.md` and
`adr/ADR-003-single-owner-bootstrap.md`.

## Implementation order

Phase 0 is infrastructure + identity + contract + schema only.\
Phase 1 validates Enable Banking with one owned account.\
Web dashboard and Mobile come only after the banking/domain foundation
is proven.

## Canonical documents

-   `01_PRODUCT_REQUIREMENTS.md`
-   `02_ARCHITECTURE.md`
-   `03_SECURITY.md`
-   `04_INTEGRATIONS.md`
-   `05_ENGINEERING_STANDARDS.md`
-   `06_TESTING_AND_QUALITY.md`
-   `07_DEVOPS_AND_OPERATIONS.md`
-   `08_ROADMAP.md`
-   `10_MOBILE_APP.md`
-   `adr/*`
-   `api/openapi.yaml` (v4.1.0)
-   `implementation/V4_CONSISTENCY_CONTRACT.md`
-   `domain/*`
-   `integrations/*`
-   `database/SCHEMA.md`
-   `implementation/*`


## v4 consistency fixes

V4.1 closes remaining contract gaps before implementation:

1. aggregate analytics/investment DTOs are multi-currency safe;
2. Trading 212 has a deterministic configuration-backed connection
   lifecycle;
3. Web BFF -> API authentication is Bearer-only at the API boundary;
4. owner reporting preferences are persisted and patchable;
5. transaction filters match FR-033 and OpenAPI;
6. wealth summary, logout, sync-runs, transfer-match list, rule
   patch/delete, purge and CSV columns exist on the contract;
7. account types, scheduler, heuristic, disconnect/purge and Web IA
   are written down.
