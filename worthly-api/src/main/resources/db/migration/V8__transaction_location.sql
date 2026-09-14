-- Store provider place details on normalized transactions.
-- Uncategorized spending was OTHER, so expense dashboards skipped it.

ALTER TABLE transaction ADD COLUMN location text NULL;

UPDATE transaction
SET economic_type = CASE WHEN direction = 'CREDIT' THEN 'INCOME' ELSE 'EXPENSE' END
WHERE economic_type = 'OTHER'
  AND categorization_source = 'UNCATEGORIZED';
