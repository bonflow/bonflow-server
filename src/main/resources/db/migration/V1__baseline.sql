-- BonFlow baseline schema (see docs/architecture.md, section 3).
-- The Spring Modulith event publication table is created by Modulith itself.

-- ---------------------------------------------------------------- master data
CREATE TABLE area (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE dining_table (
    id      BIGSERIAL PRIMARY KEY,
    area_id BIGINT       NOT NULL REFERENCES area (id),
    label   VARCHAR(50)  NOT NULL UNIQUE,
    active  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE printer (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL UNIQUE,
    host           VARCHAR(255) NOT NULL,
    port           INTEGER      NOT NULL DEFAULT 9100,
    protocol       VARCHAR(20)  NOT NULL DEFAULT 'ESC_POS',
    paper_width_mm INTEGER      NOT NULL DEFAULT 80,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE station (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    has_display BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE station_printer (
    station_id BIGINT NOT NULL REFERENCES station (id) ON DELETE CASCADE,
    printer_id BIGINT NOT NULL REFERENCES printer (id),
    PRIMARY KEY (station_id, printer_id)
);

CREATE TABLE article_category (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    default_station_id BIGINT REFERENCES station (id),
    parent_id          BIGINT REFERENCES article_category (id)
);

CREATE TABLE article (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200)   NOT NULL,
    short_name          VARCHAR(50),
    category_id         BIGINT         NOT NULL REFERENCES article_category (id),
    price               NUMERIC(10, 2) NOT NULL DEFAULT 0, -- informational, payment is external
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    station_override_id BIGINT REFERENCES station (id)
);

CREATE TABLE modifier_group (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    multi_select BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE modifier (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT       NOT NULL REFERENCES modifier_group (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE article_modifier_group (
    article_id BIGINT NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    group_id   BIGINT NOT NULL REFERENCES modifier_group (id),
    PRIMARY KEY (article_id, group_id)
);

-- ---------------------------------------------------------------------- users
CREATE TABLE app_user (
    id           BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    login        VARCHAR(100) NOT NULL UNIQUE,
    pin_hash     VARCHAR(255) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    role         VARCHAR(20)  NOT NULL DEFAULT 'WAITER' CHECK (role IN ('WAITER'))
);

CREATE TABLE admin_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER'))
);

-- ------------------------------------------------------------------- ordering
-- "order" is a reserved word, hence customer_order.
-- The primary key is the client-generated UUID: a retried POST /orders cannot duplicate.
CREATE TABLE customer_order (
    id              UUID PRIMARY KEY,
    dining_table_id BIGINT      NOT NULL REFERENCES dining_table (id),
    waiter_id       BIGINT      NOT NULL REFERENCES app_user (id),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'COMPLETED', 'CANCELLED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at    TIMESTAMPTZ,
    note            TEXT,
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_customer_order_table_status ON customer_order (dining_table_id, status);

CREATE TABLE order_item (
    id            BIGSERIAL PRIMARY KEY,
    order_id      UUID        NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    article_id    BIGINT      NOT NULL REFERENCES article (id),
    quantity      INTEGER     NOT NULL CHECK (quantity > 0),
    note          TEXT,
    course        INTEGER,
    submission_no INTEGER     NOT NULL DEFAULT 1, -- follow-up orders are new submissions
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED'))
);
CREATE INDEX idx_order_item_order ON order_item (order_id);

CREATE TABLE order_item_modifier (
    order_item_id BIGINT NOT NULL REFERENCES order_item (id) ON DELETE CASCADE,
    modifier_id   BIGINT NOT NULL REFERENCES modifier (id),
    PRIMARY KEY (order_item_id, modifier_id)
);

CREATE TABLE station_ticket (
    id            BIGSERIAL PRIMARY KEY,
    order_id      UUID        NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    station_id    BIGINT      NOT NULL REFERENCES station (id),
    submission_no INTEGER     NOT NULL DEFAULT 1,
    kind          VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (kind IN ('NEW', 'ADDITION', 'STORNO')),
    status        VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    printed_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_station_ticket_station_status ON station_ticket (station_id, status);

CREATE TABLE station_ticket_item (
    ticket_id     BIGINT NOT NULL REFERENCES station_ticket (id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES order_item (id),
    PRIMARY KEY (ticket_id, order_item_id)
);

-- ------------------------------------------------------------------- printing
CREATE TABLE print_job (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT      NOT NULL REFERENCES station_ticket (id) ON DELETE CASCADE,
    printer_id      BIGINT      NOT NULL REFERENCES printer (id),
    payload         BYTEA       NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    last_error      TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_print_job_due ON print_job (status, next_attempt_at);
