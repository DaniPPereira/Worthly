# ADR-001 --- Modular Monolith

**Decision:** one Spring Boot deployable with domain modules and
hexagonal boundaries.

**Why:** single-owner workload, transactional consistency, simpler
security/operations. Microservices add failure modes without current
scaling need.

**Consequences:** module boundaries are enforced in code/tests; no
cross-module repository access; future extraction remains possible.
