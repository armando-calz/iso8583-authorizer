-- Reversals (ADR 0005). A reversal identifies the original authorization by terminal, RRN and
-- the original STAN (carried in field 90). It is recorded even if the original has not arrived
-- yet, so a late original is declined instead of holding credit nobody will release.
ALTER TABLE authorizations ADD COLUMN reversed_at TIMESTAMPTZ;

CREATE TABLE reversals (
    id                     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    terminal_id            CHAR(8)     NOT NULL,
    rrn                    CHAR(12)    NOT NULL,
    original_stan          CHAR(6)     NOT NULL,
    stan                   CHAR(6)     NOT NULL,
    transmission_date_time CHAR(10)    NOT NULL,
    pan_token              CHAR(64)    NOT NULL,
    amount_minor           BIGINT      NOT NULL CHECK (amount_minor >= 0),
    original_found         BOOLEAN     NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (terminal_id, rrn, original_stan)
);
