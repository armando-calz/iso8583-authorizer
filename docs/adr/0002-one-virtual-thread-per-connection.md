# ADR 0002 — One virtual thread per connection

- **Status:** Accepted
- **Date:** 2026-10-15

## Context

Terminals and acquirers keep long-lived TCP connections to the authorization host and send messages over them: authorizations, reversals, and periodic `0800` echo tests to check that the link is alive. The host must:

- serve many connections at once, most of them idle most of the time;
- never let one slow or broken connection affect the others;
- turn a malformed message into a response the sender understands, without dropping the link.

Classic options are a thread per connection (simple but expensive with platform threads) or an event loop (Netty / NIO: scalable but callback-based and harder to read and debug).

## Decision

**Blocking I/O, one Java 21 virtual thread per connection.** Each connection runs a plain loop (read frame → decode → handle → encode → write). Blocking reads park the virtual thread, not an OS thread, so thousands of mostly idle connections cost little memory. The code reads top to bottom like the protocol does.

**Messages on one connection are processed in order.** A response is written before the next request on that connection is read. That matches how terminals use a connection, and it keeps request/response matching trivial. Different connections are fully concurrent.

**Failures stay local to the message:**

| Situation | Response | Connection |
|---|---|---|
| Message cannot be decoded, MTI readable and a request/advice | Response MTI with code `30` (format error) | Kept open |
| Message cannot be decoded, MTI unreadable, or it is a response | None (dropped, logged) | Kept open |
| Handler throws | Response MTI with code `96` (system malfunction) | Kept open |
| Request with an MTI the host does not support | Response MTI with code `12` (invalid transaction) | Kept open |
| Peer closes mid-frame | None | Closed |

**Logs never contain full card data:** `IsoMessage.toString()` masks the PAN (first 6 / last 4) and redacts track 2 and EMV data.

## Alternatives considered

| Option | Why not |
|---|---|
| Netty / NIO event loop | The production standard for very high throughput, but callback-based code is harder to read and test, and virtual threads give the same scalability for this workload. |
| Platform thread per connection | Correct but heavy: about 1 MB of stack per idle connection. |
| Processing several requests of one connection concurrently (multiplexing) | Needs matching responses by STAN/RRN and ordering rules. Some acquirer links do work this way, and it can be added later without changing the codec. |

## Consequences

- ✅ Simple, linear code, with a test of 500 concurrent connections × 10 messages each.
- ✅ A bad message never disconnects a terminal.
- ⚠️ A slow handler (e.g. a slow database) delays later messages on the same connection. Other connections are unaffected.
- ⚠️ Virtual threads pin to their carrier inside `synchronized` blocks on older JDKs. Handlers should prefer `java.util.concurrent` locks.
