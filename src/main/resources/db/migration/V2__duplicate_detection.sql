-- Duplicate transmission detection (ADR 0004). A transaction is identified by the terminal,
-- the retrieval reference number and the STAN. Field 7 (transmission date/time) is left out on
-- purpose: a retransmission may carry a new one.
ALTER TABLE authorizations ADD COLUMN processing_code CHAR(6);
UPDATE authorizations SET processing_code = '000000' WHERE processing_code IS NULL;  -- earlier rows were purchases
ALTER TABLE authorizations ALTER COLUMN processing_code SET NOT NULL;

CREATE UNIQUE INDEX authorizations_transaction_key ON authorizations (terminal_id, rrn, stan);
