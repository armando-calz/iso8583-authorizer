# ADR 0003 — Issuer data in PostgreSQL, with tokenized PANs

- **Status:** Accepted
- **Date:** 2026-10-16

## Context

To answer a `0100`, the host needs cards (status, expiry, owning account), credit accounts (limit, available credit) and a record of every decision. That state:

- must survive restarts: an issuer cannot forget amounts it has already authorized;
- is updated concurrently: several terminals can authorize against the same credit line at once;
- includes card numbers, the most sensitive data in payments (PCI DSS).

## Decision

### PostgreSQL, accessed with plain SQL

Tables `credit_accounts`, `cards` and `authorizations`, managed by Flyway and accessed with `JdbcClient`.

### Available credit is updated under a row lock

An approval runs in one transaction that locks the credit account (`SELECT … FOR UPDATE`), checks that the available credit covers the amount, decrements it and records the decision. Concurrent authorizations on the same account are serialized; authorizations on different accounts are not.

A `CHECK (available_minor >= 0)` constraint is a second line of defense, but **it does not replace the lock**. With the lock removed, `AuthorizationIT` fired 50 concurrent 100.00 authorizations at a 1,000.00 credit line, and **18 and 21 were approved** in two runs instead of 10. Every transaction read the same stale balance and wrote back a non-negative result (a lost update), so the constraint never fired.

### PANs are never stored in clear

A card is keyed by a **PAN token: HMAC-SHA256(PAN) under a secret key**. Only the BIN (first 6 digits) and the last 4 digits are kept, for display and routing. The `authorizations` table stores the token, never the PAN. A test dumps both tables and asserts the PAN does not appear anywhere.

A plain SHA-256 would not be enough: there are few enough valid PANs for a given BIN that an attacker with the table could hash them all. The HMAC key makes that impossible without the key, which a real issuer keeps in an HSM or secrets manager. Here it is a configuration value with a development default.

### Every parsed request is recorded

Approvals and declines alike go to `authorizations`, with the response code. A `CHECK` guarantees that approvals, and only approvals, carry an authorization code. This log is what duplicate detection and reversals will build on.

## Alternatives considered

| Option | Why not |
|---|---|
| In-memory maps | Simpler, but approvals vanish on restart and nothing would survive a crash between approving and answering. |
| Storing the PAN encrypted (AES) | Needed when the issuer must recover the PAN (e.g. to send it to the network); this host only needs to *recognize* cards, so a one-way keyed token is the smaller attack surface. |
| Optimistic locking with retries | Retries on a hot credit line under a terminal's response timeout make latency unpredictable. |

## Consequences

- ✅ No overspending under concurrency, verified by a test that fails without the lock.
- ✅ A database dump does not expose card numbers.
- ⚠️ Rotating the token key means re-tokenizing every card (which requires the PANs from the card management system).
- ⚠️ Authorizations on the same account are serialized. That is fine for consumer cards; a corporate card shared by many terminals would need a different hold strategy.
