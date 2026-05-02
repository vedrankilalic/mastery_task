CREATE DATABASE IF NOT EXISTS masterydatabase
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE masterydatabase;

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_path VARCHAR(500) NULL,
    file_type ENUM('PDF', 'IMAGE', 'CSV', 'TXT') NOT NULL,
    document_type ENUM('INVOICE', 'PURCHASE_ORDER') NULL,
    supplier_name VARCHAR(255) NULL,
    document_number VARCHAR(100) NULL,
    issue_date DATE NULL,
    due_date DATE NULL,
    currency_code CHAR(3) NULL,
    subtotal DECIMAL(18,2) NULL,
    tax_amount DECIMAL(18,2) NULL,
    total_amount DECIMAL(18,2) NULL,
    status ENUM('UPLOADED', 'NEEDS_REVIEW', 'VALIDATED', 'REJECTED') NOT NULL DEFAULT 'UPLOADED',
    source_payload JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS line_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    line_no INT NULL,
    description VARCHAR(500) NULL,
    quantity DECIMAL(18,4) NULL,
    unit_price DECIMAL(18,4) NULL,
    line_tax_amount DECIMAL(18,2) NULL,
    line_total DECIMAL(18,2) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_line_items_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS validation_issues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    issue_type ENUM(
        'MISSING_FIELD',
        'TOTAL_MISMATCH',
        'INVALID_DATE',
        'LINE_CALC_ERROR',
        'DUPLICATE_DOC_NUMBER',
        'OTHER'
    ) NOT NULL,
    field_name VARCHAR(100) NULL,
    message VARCHAR(1000) NOT NULL,
    severity ENUM('INFO', 'WARNING', 'ERROR') NOT NULL DEFAULT 'ERROR',
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_validation_issues_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS document_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    changed_by VARCHAR(100) NULL,
    change_reason VARCHAR(255) NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_document_revisions_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_created_at ON documents (created_at);
CREATE INDEX idx_documents_doc_number ON documents (document_number);
CREATE INDEX idx_documents_supplier_doc_num ON documents (supplier_name, document_number);
CREATE INDEX idx_line_items_document_id ON line_items (document_id);
CREATE INDEX idx_validation_issues_document_id ON validation_issues (document_id);
CREATE INDEX idx_validation_issues_resolved ON validation_issues (is_resolved);
CREATE INDEX idx_document_revisions_document_id ON document_revisions (document_id);
