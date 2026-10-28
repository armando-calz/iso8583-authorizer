# Changelog

## [1.0.0] - 2026-10-27

First release: a card authorization host speaking ISO 8583 over TCP, built from public standards.

### Protocol
- ISO 8583:1987 codec in ASCII: MTI, primary and secondary bitmaps, fixed, LLVAR and LLLVAR fields, strict validation ([ADR 0001](docs/adr/0001-iso8583-1987-ascii-with-length-prefixed-framing.md)).
- TCP server with 2-byte length framing, one virtual thread per connection; a malformed message is answered `30` without dropping the link ([ADR 0002](docs/adr/0002-one-virtual-thread-per-connection.md)).
- Messages: `0100`/`0101` → `0110`, `0400`/`0401` → `0410`, `0800` → `0810`.

### Issuer decisions
- Authorization rules with standard response codes (`00`, `05`, `12`, `13`, `14`, `30`, `51`, `54`, `57`, `62`, `94`).
- Credit held under a row lock on the account; PANs stored only as HMAC tokens ([ADR 0003](docs/adr/0003-issuer-data-in-postgresql-with-tokenized-pans.md)).
- Duplicate transmissions answered with the original decision, including simultaneous copies ([ADR 0004](docs/adr/0004-duplicate-transmissions-answered-with-the-original-decision.md)).
- Reversals that release credit exactly once, in any order, serialized per transaction ([ADR 0005](docs/adr/0005-reversals-serialized-per-transaction.md)).
- EMV: ARQC verification, binding of fields 4/49 to the signed amount and currency, ATC replay protection, ARPC Method 1 ([ADR 0006](docs/adr/0006-emv-cryptogram-verification.md)).

### Tooling and operations
- Terminal simulator CLI with a self-checking demo scenario.
- Docker Compose stack (PostgreSQL, host, terminal); CI runs the demo against the containers on every push.
- Micrometer metrics per MTI and response code, and health, over Actuator.

[1.0.0]: https://github.com/armando-calz/iso8583-authorizer/releases/tag/v1.0.0
