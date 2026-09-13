# ADR-004 --- Money, Reporting Time and Currency

-   Money = decimal amount + ISO 4217 currency.
-   Java `BigDecimal`; PostgreSQL `NUMERIC(19,4)`.
-   No `double`/`float`.
-   Instants stored UTC.
-   Provider booking/value date retained separately.
-   Owner reporting timezone default `Europe/Lisbon`.
-   Reporting currency default `EUR`.
-   A reporting month is `[start-of-month, start-of-next-month)` in
    owner timezone converted to UTC boundaries.
-   Until an FX subsystem exists, totals across different currencies are
    returned grouped by currency; no implicit conversion.


## Aggregate API contract

Any aggregate that may contain more than one currency MUST return a
collection grouped by currency. `WealthSummary`, `MonthlyAnalytics` and `InvestmentSummary` therefore
MUST NOT expose one cross-currency `Money`.

Canonical shape:

```text
CurrencyAmount {
  currency
  amount
}

MonthlyCurrencyAnalytics {
  currency
  income
  expenses
  invested
  savings
  savingsRate
}

MonthlyAnalytics {
  month
  timezone
  totalsByCurrency[]
}

InvestmentCurrencySummary {
  currency
  cash
  portfolioValue
}

InvestmentSummary {
  totalsByCurrency[]
  observedAt
}

WealthCurrencySummary {
  currency
  liquidCash
  investmentValue
  netWorth
}

WealthSummary {
  asOf
  timezone
  totalsByCurrency[]
}
```

Each monetary amount inside a currency group is a decimal amount in that
group's currency. Savings rate is calculated per currency. A future
reporting-currency view requires a dedicated FX ADR defining source,
rate timestamp, historical-rate policy, rounding and auditability.
`reporting_currency` is a user preference today; it does not authorize
silent FX conversion.
