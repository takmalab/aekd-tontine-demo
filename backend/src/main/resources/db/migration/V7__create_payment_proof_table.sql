CREATE TABLE payment_proof (
    id UUID PRIMARY KEY,
    contribution_transaction_id UUID NOT NULL UNIQUE REFERENCES contribution_transaction (id) ON DELETE CASCADE,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users (id),
    uploaded_at TIMESTAMPTZ NOT NULL
);
