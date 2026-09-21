# GOD PHASE 01 — Specification Consolidation / Lock

Status: **IN_PROGRESS — SPEC ONLY**

## Goal

実装を触る前に、Piri GOD の挙動仕様を一つに統合し、曖昧な箇所を明示的に止める。

Canonical document:
- `../docs/GOD_MASTER_SPEC.md`

Research/reference notes:
- `../docs/GOD_PRODUCTION_SPEC_V1.md`
- `../docs/GOD_ROLE_MODE_BASELINE.md`
- `../docs/GOD_KISEKI_BASELINE.md`
- `../docs/GOD_PRODUCTION_TARGET.md`
- other `../docs/GOD_*.md`

## Hard rule for this phase

**Production gameplay/reel/client behavior codeを変更しない。**

Allowed:
- research
- source comparison
- specification documents
- tables
- explicit UNKNOWN markers
- acceptance criteria

Not allowed:
- changing `GodGameEngine`
- changing `GodStopControl`
- changing `GodReelStrip`
- changing `GodScreen` gameplay presentation behavior
- payout calibration changes
- "temporary" behavior fixes

## Work items

### A. Role contract
For every GodRole, lock:
- probability classification
- payout/replay
- visible 3-reel formation
- navigation/order
- stop-control constraints
- gameplay effect/transition
- evidence source

### B. Reel convention
Lock:
- 20-stop strip arrays
- published index 1..20 <-> internal 0..19 mapping
- physical direction
- client TOP/MIDDLE/BOTTOM index offsets
- slip direction and maximum
- representative form vs complete control-table distinction

### C. State machine
Lock:
- NORMAL / GG / G_ZONE / SGG / SGG_COMEBACK / Z_ZONE / Z_GAME
- entry/exit conditions
- stock handling
- loop handling
- premium precedence
- replay behavior

### D. Economy constraints
Lock immutable rules before fitting:
- target payout curve
- GOD 1/8192
- GG structure
- premium rewards
- rejected bell-chain V
- parameters that are allowed to move

## Known blocker discovered in runtime

Development build currently allows:
- a 3-medal yellow result
- a 15-medal yellow result
to appear visually indistinguishable.

That behavior is not accepted. It demonstrates why implementation must now wait for a locked role/reel specification.

## Exit gate

Do not mark COMPLETE until:
- `docs/GOD_MASTER_SPEC.md` contains no implementation-critical UNKNOWNs.
- user explicitly accepts the specification.
- a Phase 02 test matrix can be generated directly from the spec without guessing.

## Stop condition

When Phase 01 is complete, stop. Do not begin GOD Phase 02 automatically.
