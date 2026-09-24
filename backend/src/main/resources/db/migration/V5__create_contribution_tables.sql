CREATE TABLE contribution_definition (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    amount NUMERIC(14, 0),
    amount_mode VARCHAR(20) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    mandatory BOOLEAN NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    fund_destination VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE contribution_participant (
    id UUID PRIMARY KEY,
    contribution_definition_id UUID NOT NULL REFERENCES contribution_definition (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_contribution_participant UNIQUE (contribution_definition_id, member_id)
);

CREATE TABLE contribution_beneficiary (
    id UUID PRIMARY KEY,
    contribution_definition_id UUID NOT NULL REFERENCES contribution_definition (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_contribution_beneficiary UNIQUE (contribution_definition_id, member_id)
);

CREATE TABLE contribution_period (
    id UUID PRIMARY KEY,
    contribution_definition_id UUID NOT NULL REFERENCES contribution_definition (id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_contribution_period UNIQUE (contribution_definition_id, session_id)
);
