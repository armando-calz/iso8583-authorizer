-- Issuer data (ADR 0003). PANs are never stored in clear: cards are keyed by a
-- keyed hash (HMAC-SHA256) of the PAN, plus the BIN and last 4 digits for display.

CREATE TABLE credit_accounts (
    id                 UUID    PRIMARY KEY,
    currency           CHAR(3) NOT NULL CHECK (currency ~ '^[0-9]{3}$'),   -- ISO 4217 numeric
    credit_limit_minor BIGINT  NOT NULL CHECK (credit_limit_minor >= 0),
    -- Second line of defense: an approval can never take available credit below zero
    available_minor    BIGINT  NOT NULL CHECK (available_minor >= 0)
);

CREATE TABLE cards (
    pan_token  CHAR(64) PRIMARY KEY,                                        -- HMAC-SHA256(PAN), hex
    bin        CHAR(6)  NOT NULL,
    last4      CHAR(4)  NOT NULL,
    expiry     CHAR(4)  NOT NULL CHECK (expiry ~ '^[0-9]{2}(0[1-9]|1[0-2])$'),  -- YYMM
    status     TEXT     NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'LOST', 'STOLEN')),
    account_id UUID     NOT NULL REFERENCES credit_accounts (id)
);

CREATE INDEX cards_account_id_idx ON cards (account_id);

-- Every authorization request that could be parsed, approved or declined, for audit.
CREATE TABLE authorizations (
    id                     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pan_token              CHAR(64)    NOT NULL,
    account_id             UUID        REFERENCES credit_accounts (id),       -- null if the card is unknown
    stan                   CHAR(6)     NOT NULL,
    rrn                    CHAR(12)    NOT NULL,
    terminal_id            CHAR(8)     NOT NULL,
    transmission_date_time CHAR(10)    NOT NULL,
    amount_minor           BIGINT      NOT NULL CHECK (amount_minor >= 0),
    currency               CHAR(3)     NOT NULL,
    response_code          CHAR(2)     NOT NULL,
    auth_code              CHAR(6),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((response_code = '00') = (auth_code IS NOT NULL))                  -- approvals, and only approvals, carry a code
);
