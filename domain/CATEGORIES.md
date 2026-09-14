# Category Taxonomy

Stable system category codes:

-   `income.salary`
-   `income.other`
-   `income.investment.dividend`
-   `income.investment.interest`
-   `expense.housing`
-   `expense.groceries`
-   `expense.restaurants`
-   `expense.transport`
-   `expense.fuel`
-   `expense.health`
-   `expense.shopping`
-   `expense.entertainment`
-   `expense.subscriptions`
-   `expense.travel`
-   `expense.education`
-   `expense.insurance`
-   `expense.taxes`
-   `expense.fees`
-   `expense.other`
-   `transfer.internal`
-   `transfer.investment_funding`
-   `transfer.investment_withdrawal`
-   `uncategorized`

System codes are immutable; labels may be localized. User categories
receive UUIDs and may have a system/custom parent.

## Rule fields and operators

Fields: `MERCHANT`, `DESCRIPTION`, `ACCOUNT_ID`, `DIRECTION`.

Operators: `EQUALS`, `CONTAINS`, `STARTS_WITH`.

No regular expressions in v1 (ReDoS / ambiguous matching). Matching is
case-insensitive after Unicode NFKC normalization and trimmed
whitespace. `DIRECTION` only allows `EQUALS` against `CREDIT` or
`DEBIT`. Optional `amountMin`/`amountMax` apply to the transaction
absolute amount in its original currency.

## Precedence

1. Manual transaction override.
2. Enabled user rules by numeric `priority` ascending (lower runs
   first). An exact-merchant `EQUALS` rule is just a rule; it has no
   extra implicit rank beyond priority.
3. System heuristic.
4. `uncategorized`. Uncategorized **DEBIT** defaults to economic type
   `EXPENSE`; uncategorized **CREDIT** defaults to `INCOME`. That uses
   the provider credit/debit indicator, not the amount sign.

Rules never overwrite a manual choice. Re-import/sync re-applies 2–4
only when `categorization_source` is not `MANUAL`.

## System heuristic (v1)

A **static fixture map** shipped in the repository
(`worthly-api` resources): normalized merchant/counterparty string ->
system category code.

Algorithm: 1. Normalize merchant then description the same way as rules.
2. `EQUALS` against map keys. 3. If no hit, `CONTAINS` against map keys
longer than 4 characters, first match in fixture order. 4. Else
uncategorized.

No machine learning, no third-party merchant API, no LLM in v1. The
fixture is reviewed in PRs like any other code. Heuristic assignments
use `categorization_source = HEURISTIC` and may be overwritten by later
user rules on next non-manual pass.
