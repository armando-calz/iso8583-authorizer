# ADR 0001 — ISO 8583:1987, ASCII encoding, length-prefixed TCP framing

- **Status:** Accepted
- **Date:** 2026-10-14

## Context

ISO 8583 is a family, not a single wire format. Every network and processor publishes its own specification on top of it: which version (1987, 1993, 2003), how numbers are encoded (ASCII, BCD, EBCDIC), how bitmaps are represented (binary or hex), which fields are used and with what meaning, and how messages are framed on TCP. A host has to pick one concrete dialect.

## Decision

### Version and field subset

**ISO 8583:1987**, the most widely deployed version, with only the data elements an issuer authorization host needs:

| # | Name | Format |
|---|---|---|
| 2 | Primary account number (PAN) | n..19, LLVAR |
| 3 | Processing code | n6 |
| 4 | Amount, transaction (minor units) | n12 |
| 7 | Transmission date and time (MMDDhhmmss) | n10 |
| 11 | System trace audit number (STAN) | n6 |
| 12 | Local transaction time (hhmmss) | n6 |
| 13 | Local transaction date (MMDD) | n4 |
| 14 | Expiration date (YYMM) | n4 |
| 18 | Merchant category code | n4 |
| 22 | POS entry mode | n3 |
| 25 | POS condition code | n2 |
| 32 | Acquiring institution id | n..11, LLVAR |
| 35 | Track 2 equivalent data | z..37, LLVAR |
| 37 | Retrieval reference number | an12 |
| 38 | Authorization id response | an6 |
| 39 | Response code | an2 |
| 41 | Card acceptor terminal id | ans8 |
| 42 | Card acceptor id code | ans15 |
| 43 | Card acceptor name / location | ans40 |
| 49 | Currency code, transaction (ISO 4217 numeric) | n3 |
| 55 | ICC (EMV) data, TLV | b..255, LLLVAR |
| 70 | Network management information code | n3 |
| 90 | Original data elements | n42 |

Field 1 is the secondary bitmap, present when any field above 64 is.

### Encoding: ASCII

- MTI: 4 ASCII digits (e.g. `0100`).
- Bitmaps: 16 hexadecimal ASCII characters each.
- `n`, `an`, `ans`, `z` fields: ASCII text. LLVAR/LLLVAR lengths are 2/3 ASCII digits.
- `b` (binary) fields: hex-encoded ASCII, two characters per byte; the length prefix counts bytes.

### Framing

Each message on the TCP stream is preceded by a **2-byte, big-endian, unsigned length** of the message that follows.

### Strictness

The codec rejects instead of guessing: values must match the field's character set and exact length (fixed fields) or maximum length (variable fields). Padding is the caller's job, through explicit helpers. Unknown fields, truncated messages and trailing bytes are errors.

## Alternatives considered

| Option | Why not (for now) |
|---|---|
| BCD / binary encoding | More compact, and common in real networks, but much harder to read in logs and test vectors. The codec keeps encoding concerns in one place, so a BCD variant can be added later. |
| ISO 8583:1993 / 2003 | Fewer deployments; 1987 is what most people mean by "ISO 8583". |
| Use jPOS | jPOS is the mature, production-grade choice in Java. Here, writing the codec is part of the point, and the scope is small enough to test exhaustively. |
| Implicit padding in the codec | Convenient, but it hides bugs: a 5-digit amount silently padded can turn into a real, wrong amount. |

## Consequences

- ✅ Messages are human-readable in logs and tests (apart from the length prefix).
- ✅ A small, explicit field table that is easy to audit against the standard.
- ⚠️ Not interoperable with any specific network out of the box; a real integration means adding that network's dialect.
- ⚠️ ASCII roughly doubles the size of binary fields such as EMV data. That is irrelevant at simulator volumes.
