# Financial Calculations

All formulas operate on BOOKED, non-reversed normalized data unless
explicitly stated. Totals are computed **per ISO 4217 currency** and
returned as arrays. There is no single cross-currency total in v1.

## Account types in aggregates

| Type | Shown on Accounts | Liquid cash | Net worth |
|------|-------------------|-------------|-----------|
| CURRENT | yes | yes | yes |
| SAVINGS | yes | yes | yes |
| CARD | yes | no | no |
| BROKERAGE | via investments | no (cash is investment cash) | via investment value |
| OTHER | yes | no | no |

`includedInLiquidCash` / `includedInNetWorth` on the Account DTO MUST
match this table. Owner cannot override the table in v1.

## Liquid cash

For each currency, sum the selected balance of every **active**
`CURRENT` and `SAVINGS` account in that currency.

Selected balance per account: prefer provider `available`/`interimAvailable`
when present and mapped as reliable; otherwise booked/`closingBooked`.
The chosen `balance_type` is returned as `usedForLiquidCash` on the
Balance DTO used for the aggregate.

Do not include CARD, BROKERAGE, OTHER, inactive accounts, or pending
authorizations.

## Investment value

Trading 212 provider-reported portfolio/position value plus brokerage
cash when the provider summary semantics do not already include it.
Adapter must document whether account summary total includes cash to
avoid double counting. Grouped by currency.

## Net worth

Per currency:

`liquid_cash[C] + investment_value[C]`

V1 has no manual assets/liabilities UI. CARD balances are excluded.
Do not add currencies together.

## Monthly income

Per currency: sum of BOOKED transactions with economic type `INCOME`
whose reporting timestamp falls in the reporting month. Excludes
internal transfers, refunds, investment withdrawals and reversals.

## Monthly expenses

Per currency: absolute sum of BOOKED transactions with economic type
`EXPENSE` or consumption `FEE`. Excludes internal transfers, investment
funding, refunds and reversals.

## Amount invested

Per currency: `INVESTMENT_FUNDING - INVESTMENT_WITHDRAWAL` for the
period. Do not use market buys/sells.

## Savings amount

Per currency: `monthly_income - monthly_expenses`.

## Savings rate

Per currency only. If income > 0: `(income - expenses) / income * 100`.
If income <= 0: `savingsRate = null` and `savingsRateReason =
NO_POSITIVE_INCOME`. There is no global savings rate until FX exists.

## Refunds

Refunds reduce the category expense in the period in which the refund is
BOOKED. Historical "original purchase month" restatement is out of scope
v1.

## Dividends

Default economic type `INCOME` with investment-income category, but not
bank salary income.

## Reporting timestamp

Bank transactions use booked timestamp/date when available; date-only
values are interpreted in owner timezone. Reporting month is
`[start-of-month, start-of-next-month)` in `app_user.reporting_timezone`
converted to UTC.

## Rounding

Persist provider precision. Aggregate with full decimal precision; round
only at presentation according to currency minor units.
