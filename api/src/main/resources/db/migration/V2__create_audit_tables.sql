CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_type VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor_id BIGINT REFERENCES admin_users(id),
    actor_name VARCHAR(100),
    actor_role VARCHAR(20),
    session_id UUID,
    interaction_id UUID,
    request_id UUID,
    source VARCHAR(20) NOT NULL CHECK (source IN ('CLIENT', 'SECURITY', 'HTTP', 'BUSINESS', 'SYSTEM')),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    http_method VARCHAR(10),
    request_path VARCHAR(500),
    status_code INTEGER,
    duration_ms BIGINT,
    result VARCHAR(20) NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED', 'ATTEMPTED')),
    ip_address INET,
    user_agent TEXT,
    error_code VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE audit_log_changes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_log_id UUID NOT NULL REFERENCES audit_logs(id),
    field_name VARCHAR(100) NOT NULL,
    before_value JSONB,
    after_value JSONB
);

CREATE INDEX idx_audit_time ON audit_logs(occurred_at DESC, id DESC);
CREATE INDEX idx_audit_actor_time ON audit_logs(actor_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_type_time ON audit_logs(event_type, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_interaction ON audit_logs(interaction_id);
CREATE INDEX idx_audit_request ON audit_logs(request_id);
CREATE INDEX idx_audit_changes ON audit_log_changes(audit_log_id);
