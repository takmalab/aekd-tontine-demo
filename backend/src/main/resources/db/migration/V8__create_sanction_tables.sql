CREATE TABLE sanction_rule (
    id UUID PRIMARY KEY,
    contribution_definition_id UUID NOT NULL REFERENCES contribution_definition (id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    late_days_threshold INT NOT NULL,
    monetary_amount NUMERIC(14, 0),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE applied_sanction (
    id UUID PRIMARY KEY,
    sanction_rule_id UUID NOT NULL REFERENCES sanction_rule (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    contribution_period_id UUID NOT NULL REFERENCES contribution_period (id) ON DELETE CASCADE,
    amount NUMERIC(14, 0),
    description TEXT,
    status VARCHAR(20) NOT NULL,
    applied_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
