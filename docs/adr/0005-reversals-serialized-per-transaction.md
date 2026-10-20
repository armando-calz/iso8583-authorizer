# ADR 0005 — Reversals, serialized per transaction

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

A terminal that gets no answer to an authorization may send a **reversal** (`0400`) instead of retrying: *"if you approved that purchase, undo it"*. The reversal points to the original through the terminal id (field 41), the RRN (field 37) and, inside field 90 (original data elements), the original MTI and STAN.

The host has to release held credit **exactly once**, in every ordering the network can produce:

- the reversal may be sent several times (`0400` again, or `0401`);
- the original may have been declined, so there is nothing to release;
- the original may arrive **after** its reversal (it was delayed, or the terminal gave up early). It must not then hold credit that nobody will ever release;
- the original and its reversal may be processed **at the same time**.

## Decision

### Outcomes

| Situation | Answer | Effect |
|---|---|---|
| Original approved, not yet reversed | `00` | Hold released, authorization marked reversed |
| Original declined | `00` | Nothing to release |
| Reversal already received (repeat, `0401`) | `00` | Nothing released again |
| Original not received yet | `00` | Reversal recorded; the original, when it arrives, is declined with `05` and holds nothing |
| Card or amount differ from the original | `12` / `13` | Nothing released or recorded; a correct reversal can still follow |
| Field 90 missing or malformed | `30` | — |
| Field 90 refers to something other than a `0100` | `12` | — |

Answering `00` to a reversal of an unknown original is deliberate. It tells the acquirer the reversal is accepted, so it stops retrying, and the recorded reversal guarantees the late original will not hold credit.

### One lock per transaction

Both paths, authorizing a `0100` and reversing it, start by taking a **PostgreSQL transaction-scoped advisory lock** on a hash of the transaction key (terminal, RRN, STAN). Only then do they look at the authorization log, the reversal log and the credit account. The lock is released on commit or rollback.

Without it, the race in the last row of the table above is real. The reversal looks for the original, which is not committed yet, and records itself as "original not found". The original looks for a reversal, which is not committed yet either, and approves and holds. `AuthorizationIT` fires 40 purchase/reversal pairs simultaneously. With the lock disabled, **7 to 8 holds of 100.00 were left stuck in each of three runs**. With it, every hold is released, and every purchase is either approved and then reversed, or declined with `05`.

**This revisits ADR 0004**, which rejected an advisory lock for duplicate detection because a unique index sufficed there. Reversals involve two tables (authorizations and reversals). The question "has the other side happened yet?" spans both, and no single unique index can answer it. The `DuplicateKeyException` retry of ADR 0004 stays as a second line of defense.

**Lock order is always key, then account**, for both authorizations and reversals, so they cannot deadlock.

### Data

- `authorizations.reversed_at` marks a released hold; the update only applies once (`WHERE reversed_at IS NULL`).
- `reversals` records the first reversal per original key (`UNIQUE (terminal_id, rrn, original_stan)`), with whether the original was found.

## Alternatives considered

| Option | Why not |
|---|---|
| Answer `25` (unable to locate original) and record nothing | The acquirer would keep retrying, and a late original would hold credit forever. |
| Lock the authorization row | There is no row to lock when the reversal comes first. |
| `SERIALIZABLE` isolation | Would detect the conflict, but as serialization failures every caller must retry, on a path that is already a retry. |

## Consequences

- ✅ Credit is released exactly once in every ordering, verified under concurrency.
- ✅ Late originals cannot leak holds.
- ⚠️ Partial reversals (field 95, replacement amounts) are not supported; a different amount is rejected with `13`.
- ⚠️ Advisory locks are invisible in the schema, so the reason for taking them is documented here and next to the code.
