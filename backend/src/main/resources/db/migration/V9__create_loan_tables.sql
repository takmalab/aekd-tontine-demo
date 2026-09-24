CREATE TABLE loan_policy (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    min_amount NUMERIC(14, 0),
    max_amount NUMERIC(14, 0),
    max_duration_months INT,
    interest_rate NUMERIC(6, 2),
    min_savings_required NUMERIC(14, 0),
    min_seniority_months INT,
    max_active_loans INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE loan (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    loan_policy_id UUID NOT NULL REFERENCES loan_policy (id),
    requested_amount NUMERIC(14, 0) NOT NULL,
    approved_amount NUMERIC(14, 0),
    requested_duration_months INT NOT NULL,
    approved_duration_months INT,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    decision_date TIMESTAMPTZ,
    decision_by UUID REFERENCES users (id),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE loan_rule_evaluation (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loan (id) ON DELETE CASCADE,
    rule_name VARCHAR(50) NOT NULL,
    respected BOOLEAN NOT NULL,
    message TEXT NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE loan_repayment (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loan (id) ON DELETE CASCADE,
    amount NUMERIC(14, 0) NOT NULL,
    payment_date DATE NOT NULL,
    operator VARCHAR(20) NOT NULL,
    reference VARCHAR(150) NOT NULL,
    recorded_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);
