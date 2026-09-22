# NEXT PHASE 02 — Core Gameplay / Reel / State Implementation

Status: **IN_PROGRESS — CORE IMPLEMENTATION COMPLETE; REAL PAPER+FABRIC ACCEPTANCE PENDING**

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

## Implemented core

- Added independent machine type `JUGGLER_GOD`; ordinary `JUGGLER` remains on its existing engine.
- Added authoritative GOD role with 15-medal payout and center-line BAR-BAR-BAR result.
- Removed legacy premium-BAR dependency from JUGGLER_GOD while preserving ordinary JUGGLER behavior.
- Added GOD lever freeze contract, blackout, per-reel middle-BAR reveal, silent Piri Chance on final BAR confirmation, and dedicated `piri:god_freeze` audio slot.
- Added persistent successor runtime state for NORMAL / HEAVEN / GOD_CHAIN.
- GOD initializes five BIGs total: first BIG plus four remaining guaranteed stocks.
- Guaranteed GOD-stock follow-up BIG draws do not advance normal-game count.
- Post-guarantee continuation BIG draws advance exactly one game.
- Added setting-dependent continuation rates: 25/30/35/45/55/70%.
- GOD-chain termination enters guaranteed <=32G heaven.
- Heaven stores a server-side target game 1..32 and forces a setting-weighted BIG/REG family at that target.
- Added dedicated GOD parent-history storage/display without conflating it with ordinary BIG/REG history.
- Added dedicated `textures/juggler_god/...` presentation namespace with ordinary JUGGLER fallback for currently reused assets.
- The same isolated asset fallback is used in the slot screen and world cabinet renderer.

## Verification

- Existing production build/test suite returned PASS after successor integration and regression fixes.
- Added `JugglerGodCoreTest` for guaranteed-stock zero-G accounting, post-guarantee 1G accounting, heaven target forcing, GOD BAR contract, and runtime-state persistence.
- Added runtime-test helper support for `/piritest force god` on JUGGLER_GOD only.
- Added `runtime-test-support/run_next_phase02.py` and `run-next-phase02-runtime.bat`.

Phase 02 must remain IN_PROGRESS until the dedicated NEXT Phase 02 real Paper+Fabric acceptance result is PASS.

## Phase 02 hardening notes

- The persisted heaven target game is exclusive: pre-target heaven spins are conditionally drawn from non-bonus roles only, so a normal BIG/REG cannot pre-empt the selected 1..32 target.
- GOD remains an independent 1/8192 eligible normal-lever event and may supersede a heaven target window; after the GOD chain ends, heaven is entered again with a fresh target.
- GOD-chain BIG start accounting is shared for direct-entry and bonus-entry paths so `godBigCount` cannot diverge by reel outcome shape.
- Dedicated NEXT Phase 02 runtime client configuration is validated in CI (`next02-main`, `NEXT_PHASE_02`, port 25597, Phase02Probe enabled).
- Runtime runner third-stop waits observe terminal state instead of transient `stopped_mask=7`, because production clears spin state in the same third-stop commit.
