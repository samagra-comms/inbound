CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE table IF NOT EXISTS delivery_report
(
    id           uuid DEFAULT uuid_generate_v4(),
    external_id   VARCHAR(100),
    user_id       VARCHAR(10),
    bot_id        VARCHAR(100) NOT NULL,
    bot_name      VARCHAR(100),
    fcm_token     VARCHAR(200),
    message_state VARCHAR(10),
    created_on    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (created_on, id)
) PARTITION BY RANGE (created_on);

CREATE INDEX IF NOT EXISTS idx_bot_id ON delivery_report (bot_id);

CREATE TABLE delivery_report_2023 PARTITION OF delivery_report FOR VALUES FROM ('2023-08-01') TO ('2024-07-31');