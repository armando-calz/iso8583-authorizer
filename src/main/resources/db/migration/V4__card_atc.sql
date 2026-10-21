-- Highest application transaction counter (ATC) seen with a valid cryptogram (ADR 0006).
-- A cryptogram with an ATC not above it is a replay and is declined.
ALTER TABLE cards ADD COLUMN last_atc INTEGER CHECK (last_atc BETWEEN 0 AND 65535);
