# Engineering Standards

## Java/Spring

Java 21 LTS. Constructor injection. Records/immutable DTOs where
suitable. `BigDecimal` + `Currency` for money. `Instant` for instants,
`LocalDate` only for date semantics. No provider DTO in domain. No
business logic in controllers. Disable Open Session in View. Typed
`@ConfigurationProperties`. Flyway owns schema. Problem Details errors.

Use Spring Data JPA for ordinary aggregates and explicit
SQL/JdbcTemplate for analytics when clearer. External HTTP calls outside
long DB transactions.

## TypeScript/Next.js

Strict TypeScript, avoid `any`, feature-first structure, no duplicate
financial formulas. Use locale-aware currency/date formatting (`pt-PT`
default). UI copy is English in v1. No OAuth token in
`localStorage`/`sessionStorage`. Accessibility and explicit
loading/empty/error/stale states.

## Flutter/Dart

Sound null safety, feature-first structure, one state-management
approach (Riverpod recommended), centralized HTTP/auth client,
secure-storage wrapper, no provider endpoints, no financial business
logic duplicated from backend.

## SQL

snake_case, explicit FK/unique constraints, NUMERIC for money, indexes
based on query patterns. Applied migrations never edited.

## API

OpenAPI 3.1 is canonical. Opaque UUIDs. UTC timestamps. bounded
pagination. RFC 9457 Problem Details. Unknown provider enums map safely
to UNKNOWN while retaining safe raw value.

## Git

Protected `main`, short-lived branches, PR review, secret scanning.
Never commit `.env`, PEM/private keys, dumps or production logs.
Conventional commit style recommended.

## ADR rule

Any change to auth, monetary model, provider, database, deployment
boundary, reporting timezone/currency or write-capability requires an
ADR.
