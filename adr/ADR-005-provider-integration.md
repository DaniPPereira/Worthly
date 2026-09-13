# ADR-005 --- Financial Providers

Santander Portugal and Revolut use Enable Banking AIS. ASPSPs are
dynamically discovered; Worthly does not hardcode nonexistent/static
IDs. Reauthorization reconciles accounts using `identification_hash`
when available.

Trading 212 uses official Public API with read-only key and optional IP
restriction. Banking and brokerage adapters remain separate.

No scraping. No bank passwords. No payment initiation. No order
placement.
