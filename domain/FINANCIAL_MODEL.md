# Financial Model

## Transaction classification dimensions

A transaction has independent concepts:

- direction: CREDIT / DEBIT
- lifecycle: PENDING / BOOKED / REVERSED / UNKNOWN
- economic type: INCOME / EXPENSE / INTERNAL_TRANSFER /
  INVESTMENT_FUNDING / INVESTMENT_WITHDRAWAL / REFUND / FEE / OTHER
- category: hierarchical user/system category

Do not infer economic type solely from sign.

## Account types

Normalized `financial_account.type`:

- `CURRENT` — everyday payment account
- `SAVINGS` — deposit/savings
- `CARD` — card-only account when the provider exposes one separately
- `BROKERAGE` — brokerage wrapper; cash/positions live in investment tables
- `OTHER` — unmapped provider type

Mapping from Enable Banking account type/cash account type happens in
the adapter. Unknown provider types become `OTHER`, never crash sync.

Aggregate inclusion is defined in `domain/CALCULATIONS.md`.

## Balance

Store all provider balance types. `liquid_cash` uses the selection rule
in `domain/CALCULATIONS.md`. The exact chosen balance type is returned
in API metadata (`usedForLiquidCash`).

## Account identity

Local `financial_account.id` is stable. Enable Banking session/account
IDs are external aliases. `identification_hash` is the primary
reauthorization reconciliation key; masked/raw identifiers are fallback
only under controlled logic.

## Provider source preservation

Normalized records link to a minimal source record with provider
ID/hash/status and optionally encrypted raw payload for bounded
retention (default 30 days). Normalized data persists until purge.

## Data lifecycle

See `database/SCHEMA.md` disconnect vs purge. Disconnect keeps history.
Purge requires `confirm=true` and is audited. Audit rows are
append-only.
