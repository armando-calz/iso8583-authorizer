# ADR 0004 — Duplicate transmissions answered with the original decision

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

A terminal that gets no answer within its timeout sends the authorization again, either as the same `0100` or explicitly flagged as a repeat (`0101`). The first copy may well have been approved and the response lost on the way back. If the host evaluates the copy as a new purchase, the cardholder's available credit is held twice for one purchase.

The host has to:

1. recognize a copy of a transaction it has already decided;
2. answer it with the **same** decision and authorization code, holding nothing more;
3. tell a genuine retransmission from a different transaction that reuses the same identifiers by mistake;
4. do all of this even when copies arrive **at the same time** (a terminal that retries quickly while the first request is still being processed).

## Decision

### Transaction key: terminal id + RRN + STAN

Fields 41, 37 and 11 identify a transaction from the acquirer's side. **Field 7 (transmission date and time) is deliberately not part of the key**: it records when each copy was sent, so a retransmission may legitimately carry a new value. A unique index on `(terminal_id, rrn, stan)` in the authorization log enforces one decision per transaction.

### Same key, same data: replay. Same key, different data: 94

Before evaluating the rules, the host looks the key up in the log:

| Found? | Same card, processing code, amount and currency? | Answer |
|---|---|---|
| No | — | Evaluate the rules (ADR 0003) and record the decision |
| Yes | Yes | The **original** decision and authorization code; nothing is held or recorded again |
| Yes | No | `94` duplicate transmission |

Declines are replayed too: a copy of a request declined with `51` is answered `51` again, without re-evaluating. A retransmission is the same request, not a new attempt.

`0101` is routed to the same handler and answered with `0110`.

### Simultaneous copies: let the unique index decide

Two copies can both miss the lookup, because neither has committed yet. Both then evaluate the rules and lock the account (copies of one transaction share an account, so they serialize there). Both try to record. The second `INSERT` hits the unique index and its whole transaction rolls back, **including its hold on the available credit**. The service catches the `DuplicateKeyException` and runs once more. This time the lookup finds the committed original, and the copy gets the replayed answer.

`AuthorizationIT` sends 20 identical requests at once: all get `00` with the same authorization code, the credit is held once, and one row is recorded. With the retry removed, some copies were answered `96` (system malfunction) instead (verified).

## Alternatives considered

| Option | Why not |
|---|---|
| Include field 7 in the key | Genuine retransmissions with a refreshed timestamp would be treated as new purchases. |
| Treat every copy as new and rely on the terminal | Terminals retry by design; the issuer is the only party that can make retries safe. |
| Advisory lock on the key before deciding | Also correct, but adds a lock outside the data. The unique index is already the source of truth, and the retry path is rare. |
| Re-evaluate declined copies | Could approve a copy of a declined request after a payment frees credit, so the terminal would see two different answers for one transaction. |

## Consequences

- ✅ Retries never double-hold credit, including concurrent ones.
- ✅ Terminals always get one consistent answer per transaction.
- ⚠️ A `94` is not recorded, since its key belongs to another transaction. It is still logged by the server.
- ⚠️ Keys are kept forever. Real hosts only check duplicates within a window (e.g. the business day), which would also bound the index size.
