-- Phase 3: system taxonomy, rules, transfer matches. Source: domain/CATEGORIES.md, database/SCHEMA.md

INSERT INTO category (id, user_id, code, parent_id, label, system, active, created_at, updated_at) VALUES
('a1000000-0000-4000-8000-000000000001', NULL, 'income.salary', NULL, 'Salary', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000002', NULL, 'income.other', NULL, 'Other income', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000003', NULL, 'income.investment.dividend', NULL, 'Dividends', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000004', NULL, 'income.investment.interest', NULL, 'Interest', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000010', NULL, 'expense.housing', NULL, 'Housing', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000011', NULL, 'expense.groceries', NULL, 'Groceries', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000012', NULL, 'expense.restaurants', NULL, 'Restaurants', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000013', NULL, 'expense.transport', NULL, 'Transport', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000014', NULL, 'expense.fuel', NULL, 'Fuel', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000015', NULL, 'expense.health', NULL, 'Health', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000016', NULL, 'expense.shopping', NULL, 'Shopping', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000017', NULL, 'expense.entertainment', NULL, 'Entertainment', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000018', NULL, 'expense.subscriptions', NULL, 'Subscriptions', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000019', NULL, 'expense.travel', NULL, 'Travel', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000020', NULL, 'expense.education', NULL, 'Education', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000021', NULL, 'expense.insurance', NULL, 'Insurance', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000022', NULL, 'expense.taxes', NULL, 'Taxes', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000023', NULL, 'expense.fees', NULL, 'Fees', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000024', NULL, 'expense.other', NULL, 'Other expense', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000030', NULL, 'transfer.internal', NULL, 'Internal transfer', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000031', NULL, 'transfer.investment_funding', NULL, 'Investment funding', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000032', NULL, 'transfer.investment_withdrawal', NULL, 'Investment withdrawal', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00'),
('a1000000-0000-4000-8000-000000000040', NULL, 'uncategorized', NULL, 'Uncategorized', TRUE, TRUE, TIMESTAMPTZ '2026-01-01+00', TIMESTAMPTZ '2026-01-01+00');

CREATE TABLE categorization_rule (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    priority int NOT NULL,
    field text NOT NULL,
    operator text NOT NULL,
    match_value text NOT NULL,
    amount_min numeric(19, 4) NULL,
    amount_max numeric(19, 4) NULL,
    target_category_id uuid NOT NULL REFERENCES category (id),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_categorization_rule_field CHECK (field IN ('MERCHANT', 'DESCRIPTION', 'ACCOUNT_ID', 'DIRECTION')),
    CONSTRAINT chk_categorization_rule_operator CHECK (operator IN ('EQUALS', 'CONTAINS', 'STARTS_WITH'))
);

CREATE INDEX idx_categorization_rule_user_priority
    ON categorization_rule (user_id, priority);

CREATE TABLE transfer_match (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id),
    left_transaction_id uuid NOT NULL REFERENCES transaction (id),
    right_transaction_id uuid NOT NULL REFERENCES transaction (id),
    confidence int NOT NULL,
    method text NOT NULL,
    status text NOT NULL,
    pair_fingerprint text NOT NULL,
    rejected_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (left_transaction_id, right_transaction_id),
    CONSTRAINT chk_transfer_match_method CHECK (method IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_transfer_match_status CHECK (status IN ('LINKED', 'SUGGESTED', 'REJECTED')),
    CONSTRAINT chk_transfer_match_confidence CHECK (confidence BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX uq_transfer_match_active_pair
    ON transfer_match (pair_fingerprint)
    WHERE status IN ('LINKED', 'SUGGESTED');
