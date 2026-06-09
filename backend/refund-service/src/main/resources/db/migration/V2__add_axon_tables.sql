-- 1. Bảng token_entry: Lưu vết tiến độ của Processor
CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INT NOT NULL,
    owner VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

CREATE INDEX idx_token_owner ON token_entry (owner);

-- 2. Bảng saga_entry: Lưu trạng thái của Saga
CREATE TABLE saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    saga_type VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

-- 3. Bảng association_value_entry: Lưu ánh xạ giữa Saga và các định danh
CREATE TABLE association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255)
);

CREATE INDEX idx_saga_association ON association_value_entry (association_key, association_value);
CREATE INDEX idx_saga_id_type ON association_value_entry (saga_id, saga_type);
