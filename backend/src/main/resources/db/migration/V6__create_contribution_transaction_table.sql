CREATE TABLE contribution_transaction (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    contribution_period_id UUID NOT NULL REFERENCES contribution_period (id) ON DELETE CASCADE,
    amount NUMERIC(14, 0) NOT NULL,
    operator VARCHAR(20) NOT NULL,
    transaction_reference VARCHAR(150) NOT NULL,
    payment_date DATE NOT NULL,
    observation TEXT,
    status VARCHAR(20) NOT NULL,
    validated_by UUID REFERENCES users (id),
    validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
