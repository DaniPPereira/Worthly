# ADR-005 --- Financial Providers

Santander Portugal and Revolut use Enable Banking AIS. ASPSPs are
dynamically discovered; Worthly does not hardcode nonexistent/static
IDs. Reauthorization reconciles accounts using `identification_hash`
when available.

Trade Republic and Revolut also appear as AIS banks. That connection is
cash and card payments only. Holdings are a separate brokerage product
and need an official brokerage adapter. Until one exists, the catalog
lists those investments as not connectable.

Trading 212 uses official Public API with read-only key and optional IP
restriction. Banking and brokerage adapters remain separate. Sync is
routed by `ConnectionSyncAdapter`; UI uses `kind` and `holdingsIncluded`,
not the provider string.

No scraping. No bank passwords. No payment initiation. No order
placement.
