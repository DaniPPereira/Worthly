# ADR-003 --- Single Owner Bootstrap and Recovery

**Status:** Superseded by ADR-007

v1 originally had no `/register` and exactly one bootstrapped owner.
That isolation model is replaced by public registration and per-user
tenancy. Optional bootstrap secrets may still create the first user on
an empty database; they no longer refuse additional users.

Recovery CLI and login lockout described here remain in force, applied
per user.
