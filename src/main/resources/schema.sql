CREATE SCHEMA IF NOT EXISTS legacy;
CREATE SCHEMA IF NOT EXISTS target;

CREATE TABLE IF NOT EXISTS legacy.customers (
    id BIGSERIAL PRIMARY KEY,
    document_number VARCHAR(30),
    full_name VARCHAR(200),
    email VARCHAR(255),
    phone VARCHAR(30),
    status VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS target.customers (
    id BIGSERIAL PRIMARY KEY,
    document_number VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    migrated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_target_customers_document UNIQUE (document_number),
    CONSTRAINT ck_target_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE IF NOT EXISTS target.migration_errors (
    id BIGSERIAL PRIMARY KEY,
    job_execution_id BIGINT,
    step_execution_id BIGINT,
    legacy_customer_id BIGINT,
    document_number VARCHAR(30),
    stage VARCHAR(20) NOT NULL,
    error_type VARCHAR(255) NOT NULL,
    error_message TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_legacy_customers_document ON legacy.customers(document_number);
CREATE INDEX IF NOT EXISTS idx_target_customers_status ON target.customers(status);
CREATE INDEX IF NOT EXISTS idx_migration_errors_job_execution ON target.migration_errors(job_execution_id);
CREATE INDEX IF NOT EXISTS idx_migration_errors_occurred_at ON target.migration_errors(occurred_at);
