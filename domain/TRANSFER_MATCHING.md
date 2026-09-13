# Internal Transfer Matching

## Candidate rule

Two BOOKED transactions on owner-controlled accounts may be candidates
when: - opposite directions; - same currency; - absolute amounts equal
within currency precision; - timestamps/dates within 3 calendar days; -
neither already manually linked.

## Confidence

Start at 0: - exact amount/currency: +60 - same-day: +25; 1 day: +20;
2-3 days: +10 - counterparty/reference contains owned account/provider
hint: +20 - one side is known Trading 212 funding/withdrawal: +20 -
merchant strongly indicates ordinary purchase/cash withdrawal: -50

> =90: auto-link, reversible.\
> 70-89: suggest for confirmation.\
> \<70: do not suggest.

A manual reject suppresses the same pair fingerprint. Manual links
override algorithm.

## Economic classification

Bank-to-bank owned transfer -\> `INTERNAL_TRANSFER`.\
Bank-to-Trading212 deposit -\> bank side `INVESTMENT_FUNDING`; brokerage
side linked investment deposit.\
Trading212 withdrawal -\> `INVESTMENT_WITHDRAWAL`.

Matching is recalculated after new imports but never overrides manual
decisions.
