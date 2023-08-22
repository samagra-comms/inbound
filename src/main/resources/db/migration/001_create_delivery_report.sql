CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE table IF NOT EXISTS delivery_report
(
    id           uuid DEFAULT uuid_generate_v4(),
    external_id   VARCHAR(100),
    user_id       VARCHAR(50),
    bot_id        VARCHAR(100) NOT NULL,
    bot_name      VARCHAR(100),
    fcm_token     VARCHAR(200),
    message_state VARCHAR(10),
    cass_id       VARCHAR(100),
    created_on    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_bot_id ON delivery_report (bot_id);
