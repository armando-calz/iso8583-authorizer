# iso8583-authorizer

[![CI](https://github.com/armando-calz/iso8583-authorizer/actions/workflows/ci.yml/badge.svg)](https://github.com/armando-calz/iso8583-authorizer/actions/workflows/ci.yml)

A card **authorization host** that speaks ISO 8583: it receives authorization requests from terminals over TCP, applies issuer rules (card status, expiry, credit limit, duplicates, EMV cryptograms) and answers with the standard response codes, in a few milliseconds and without losing a cent under retries and reversals.

> **Status:** 🚧 in development. See the roadmap below.

## Why

Every card payment ends in a message like this one: a terminal asks the issuer *"can this card spend 250.00 MXN here?"* and has a few seconds to get an answer. The difficult parts are rarely the happy path:

- the same request **retransmitted** after a timeout must not be authorized twice;
- a **reversal** for a request whose answer was lost must restore the available credit exactly once;
- chip cards prove they are genuine with an **EMV cryptogram** (ARQC) that the issuer has to verify, and the issuer answers with its own (ARPC);
- all of it over a binary-framed TCP protocol with fixed and variable-length fields, defined by a **bitmap**.

This project implements those mechanics end to end, from bytes on the socket to the authorization decision.

## How it fits together

```mermaid
flowchart LR
    T["Terminal simulator<br/>(CLI)"] -- "TCP, 2-byte length prefix<br/>ISO 8583 messages" --> S
    subgraph Host["Authorization host"]
        S[Connection handler<br/>virtual threads] --> C[ISO 8583 codec]
        C --> R{MTI router}
        R -- "0100" --> A[Authorization engine]
        R -- "0400" --> V[Reversal handler]
        R -- "0800" --> N[Network management]
        A --> E[EMV ARQC / ARPC]
        A --> D[(Cards, accounts,<br/>authorizations)]
        V --> D
    end
```

| Message | Meaning |
|---|---|
| `0100` / `0110` | Authorization request / response |
| `0400` / `0410` | Reversal advice / response |
| `0800` / `0810` | Network management (echo test) |

## Roadmap

- [x] ISO 8583 codec: MTI, primary and secondary bitmaps, fixed and LLVAR/LLLVAR fields
- [x] TCP server with length-prefixed framing on virtual threads
- [x] Authorization rules: card status, expiry, Luhn check, available credit, standard response codes
- [x] Duplicate transmission detection with idempotent responses (`0100` copies and `0101` repeats)
- [x] Reversals (`0400`/`0401`) that release held credit exactly once, in any order
- [x] Network management (`0800` sign-on, sign-off, echo)
- [x] EMV: ARQC verification, amount/currency binding, ATC replay protection and ARPC generation (test keys)
- [ ] Terminal simulator CLI
- [ ] Docker Compose setup

Design decisions are recorded as ADRs in [`docs/adr`](docs/adr).

## Authorization rules

Applied in order; the first rule that fails decides the response code (field 39).

| Rule | Declined with |
|---|---|
| Processing code is a purchase (`00xxxx`) | `12` invalid transaction |
| PAN passes the Luhn check and belongs to a known card | `14` invalid card number |
| Chip only: valid ARQC, fields 4/49 match the signed amount/currency, ATC not replayed | `05` do not honor |
| Card is active (not blocked, lost or stolen) | `62` restricted card |
| Card is not expired | `54` expired card |
| Expiry date sent matches the card's | `05` do not honor |
| Amount is positive | `13` invalid amount |
| Currency matches the credit account's | `57` transaction not permitted |
| Available credit covers the amount | `51` insufficient funds |

An approval (`00`) returns a 6-digit authorization code (field 38) and holds the amount against the available credit, under a row lock on the account ([ADR 0003](docs/adr/0003-issuer-data-in-postgresql-with-tokenized-pans.md)). Card numbers are stored only as HMAC tokens plus BIN and last 4 digits.

**Retransmissions** (same terminal, RRN and STAN) get the original answer and authorization code, with nothing held again, even when copies arrive simultaneously. The same key with different card, amount or currency is declined with `94` ([ADR 0004](docs/adr/0004-duplicate-transmissions-answered-with-the-original-decision.md)).

## EMV (chip) transactions

For chip transactions the host verifies the **ARQC** in field 55 (EMV Book 2: Option A card key, Common Session Key, ISO/IEC 9797-1 MAC Algorithm 3). It checks that the amount and currency in fields 4 and 49 are the ones the card signed, and rejects replayed transaction counters (ATC). It answers with an **ARPC** (Method 1, tag 91), so the card can authenticate the issuer. A test found the host approving 900.00 with a genuine cryptogram for 25.00 before the amount binding existed ([ADR 0006](docs/adr/0006-emv-cryptogram-verification.md)).

## Reversals

A `0400` undoes an authorization the terminal is unsure about, matched through terminal, RRN and the original STAN in field 90. Held credit is released **exactly once**, whether the reversal is repeated, the original was declined, or the reversal arrives **before** the original (which is then declined with `05` and holds nothing). Authorizations and reversals of the same transaction take the same lock first, so a purchase racing its own reversal never leaves credit held ([ADR 0005](docs/adr/0005-reversals-serialized-per-transaction.md)).

## Scope and sources

Built only from **public** material: the ISO 8583:1987 data element definitions as widely documented, and the EMV specifications published by EMVCo. It does not use or reproduce any network's or processor's proprietary message specification. All keys are test keys and all card numbers are test numbers. It is a learning and portfolio project, not a certified payment system.

## License

[MIT](LICENSE)
