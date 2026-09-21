# GOD PHASE 01 — Specification Consolidation / Lock

Status: **COMPLETE — SPEC ONLY**

## Goal

実装を触る前に、Piri GOD の挙動仕様を一つに統合し、曖昧な箇所を明示的に止める。

Canonical document:
- `../docs/GOD_MASTER_SPEC.md`
- `../docs/GOD_ROLE_CONTRACT_V1.md`
- `../docs/GOD_STATE_MACHINE_SPEC_V1.md`
- `../docs/GOD_SOURCE_REGISTRY.md`

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


## 2026-09-21 consolidation result

Completed in spec/research only:
- source hierarchy and registry
- source-backed role/payout/representative-form contract
- explicit 3-medal vs 15-medal lower-yellow distinction
- Piri-specific shared visible-row stop-control policy
- Piri-specific input-order safety policy
- state-machine semantic contract
- correction of SP and Z-ZONE rules from Piri-specific to reference-backed where sources exist

Final gate result:
- user review/acceptance completed across the Phase 01 decisions
- mutually-exclusive whole-game role draw locked
- premium slip exception locked
- precursor/latent entitlement arbitration locked
- canonical contradictions cleaned
- final audit recorded in `../docs/GOD_PHASE_01_FINAL_AUDIT.md`

No production gameplay code was changed in this Phase 01 consolidation.
