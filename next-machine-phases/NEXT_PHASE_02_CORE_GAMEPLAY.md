# NEXT PHASE 02 — Core Gameplay / Reel / State Implementation

Status: **IN_PROGRESS**

Implement only the locked gameplay contract from NEXT Phase 01.

Scope is determined by the successor master specification and may include:
- role/result draw
- reel/stop behavior
- player input
- state transitions
- payout/replay
- server-authoritative state
- persistence/recovery primitives
- Fabric presentation contract

Do not tune long-run economy or build final presentation here unless Phase 01 explicitly requires it for correctness.

Exit requires unit/integration tests, build PASS, and real Paper+Fabric runtime evidence for the implemented core loop.


## Asset isolation requirement

JUGGLER_GOD must use its own logical asset namespace even where the initial appearance is identical to JUGGLER.

Implementation rule:
- client code requests dedicated JUGGLER_GOD resource IDs first;
- if a dedicated resource is absent, it falls back to the existing JUGGLER resource;
- later resource-pack or repository assets placed at the dedicated path replace only JUGGLER_GOD;
- do not duplicate gameplay constants or reel symbol semantics merely for presentation isolation.

This applies to reel symbols, Piri Chance lamp imagery, UI imagery, and independently replaceable sound aliases used by the successor machine.
