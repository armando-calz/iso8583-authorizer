# ADR 0006 — EMV cryptogram verification (ARQC) and issuer authentication (ARPC)

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

A chip card proves it is genuine, and that it approved this exact transaction, by computing an **Authorization Request Cryptogram (ARQC)**: a MAC under a key only the card and its issuer can derive, over the transaction data. The terminal forwards it in field 55. The issuer must:

1. verify the ARQC; a failure means a counterfeit card or altered data;
2. make sure the ISO 8583 message asks for what the card actually signed;
3. reject a cryptogram it has already seen (replay);
4. answer with an **Authorization Response Cryptogram (ARPC)**, so the card can tell the response came from its issuer.

Schemes define several variants (cryptogram versions). This host implements one consistent set taken from the public **EMV 4.x Book 2**, using test keys.

## Decision

### Keys

| Key | Derivation (EMV Book 2, Annex A1) |
|---|---|
| Issuer master key for AC (IMK-AC) | Configuration (test key). In production it lives in an HSM. |
| ICC master key | **Option A**: Y = rightmost 16 digits of PAN ‖ PAN sequence number; key = 3DES(IMK)[Y] ‖ 3DES(IMK)[Y ⊕ FF…FF], odd parity |
| Session key | **Common Session Key**: R = ATC ‖ 00…00; key = 3DES(MK)[R with byte 3 = F0] ‖ 3DES(MK)[R with byte 3 = 0F] |

### ARQC

ISO/IEC 9797-1 **MAC Algorithm 3** (DES, padding method 2) under the session key, over Book 2's recommended minimum data set, in order: amount authorised (9F02), amount other (9F03), terminal country code (9F1A), TVR (95), transaction currency (5F2A), transaction date (9A), transaction type (9C), unpredictable number (9F37), AIP (82), ATC (9F36). The host compares in constant time (`MessageDigest.isEqual`).

### Rules (between "card exists" and "card active", ADR 0003)

For chip transactions (POS entry mode `05x` or `07x`, or field 55 present), all answered `05` if they fail:

1. The ARQC is valid.
2. **Field 4 and field 49 equal the amount (9F02) and currency (5F2A) the card signed.**
3. The ATC is above the last one seen for the card (atomic `UPDATE … WHERE last_atc < :atc`).

Chip entry without field 55, or malformed field 55, is a format error (`30`).

### ARPC

**Method 1**: ARPC = 3DES(session key)[ARQC ⊕ (ARC ‖ 00…00)], where the ARC is the ISO 8583 response code in ASCII. It is returned as tag 91 (ARPC ‖ ARC) in field 55 of the `0110`, for approvals and declines alike, whenever the ARQC was valid. A retransmission gets the same ARPC.

## Rule 2 was found by a test

The first version verified the ARQC and nothing else. A test that sent a genuine cryptogram for **25.00** with field 4 changed to **900.00** was **approved for 900.00**. The cryptogram signs the amount inside field 55, not field 4, so a compromised terminal or acquirer could charge any amount with a legitimate cryptogram. Binding fields 4 and 49 to tags 9F02 and 5F2A closed it, and `declinesTheAmountTheCardDidNotSign` / `declinesACurrencyTheCardDidNotSign` keep it closed.

## How correctness is checked without published EMV vectors

- **DES**: the classic known answer (key `0123456789ABCDEF`, plaintext "Now is t" → `3FA40E8A984D4815`).
- **MAC Algorithm 3**: compared, for several lengths, with an independent computation built from JCE DES-CBC and JCE triple DES.
- **ARPC Method 1**: recomputed from its definition in the test.
- **Binding**: altering any one of the ten signed elements, the PAN, the PAN sequence number or the issuer key makes verification fail.
- **End to end**: a card simulator computes ARQCs like a chip, and the host verifies them over TCP.

## Alternatives considered

| Option | Why not |
|---|---|
| A scheme-specific cryptogram version | Their exact definitions are in scheme documentation, not the public EMV books. This project stays with public material (README, "Scope and sources"). |
| ARPC Method 2 (CSU + proprietary data) | Method 1 is the simpler, widely used option and enough to show issuer authentication. |
| No ATC tracking | A captured cryptogram could be replayed with new ISO 8583 identifiers (new STAN/RRN), and duplicate detection would not catch it. |

## Consequences

- ✅ Counterfeit cards, altered data, mismatched amounts and replayed cryptograms are declined.
- ✅ Cards can authenticate the issuer's response.
- ⚠️ The IMK is a configuration value. Production key handling (HSM, key blocks, rotation) is out of scope; it is the subject of standards such as TR-31.
- ⚠️ Strictly increasing ATCs reject legitimately out-of-order transactions (e.g. offline ones uploaded late). Real issuers allow a window.
- ⚠️ Only the recommended minimum data set is supported. A card with a different CDOL1 would need its own input list.
