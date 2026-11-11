# HYPER_DOG Phase 01 — baseline verification record

**Verification date:** 2026-11-07 (JST)
**Result:** PASS — numerical source and design contract are ready for the next implementation phase.
**Scope:** specification/numerical baseline validation only. No Paper/Fabric implementation or Minecraft runtime acceptance is claimed.

## Evidence

- `reference/pirijuggler_new_machine_calibrated_v5.json`: SHA256 `af1ae6f88e6593116692f987534c406673fbbfa73cb25f52615a7f1674c20ab3` (MATCH).
- `reference/piri_simulate_full_v1.py`: SHA256 `f8ad162cb844bd31e37058732f960ea9db88c0e7e66290acc061e61f253d0664` (MATCH).
- Source-independent local audit: **86/86 PASS, 0 FAIL**. Re-run structural verification via `python3 original-at-phases/reference/verify_phase01.py` in this branch.
- Independent v5 holdout simulation **previously reported**, not re-executed in this audit: target 97.0 / 99.3 / 102.5 / 104.8 / 107.4 / 116.0%; previously observed 96.97 / 99.17 / 102.55 / 104.79 / 107.29 / 115.78% based on 432 million paid games. See `reference/piri_simulation_report_v5.md`. This is not a real-game payout measurement.

## Invariants verified

1. 3BET and 0BET special stage, nine internal roles and per-mode payout matrix.
2. GRAPE NORMAL 3 versus AT 30, cherry 1, rare 5, BAR 15.
3. Six normal modes and six setting multipliers; source role probability + GRAPE + MISS = 1 in all three paid modes.
4. BAR 1/65536; upper-MISS rewrite 1/32 and conditional destination sum 100%.
5. Normal mode transition distributions, miss drop destinations, AT exit mode distributions sum 100%.
6. Lower initial 1G +150, upper initial 5G +500, rare addon distributions and +2000 restrictions.
7. Five-game CZ cumulative 50%, 32-paid-game comeback cumulative 20%.
8. Original AT game changes are isolated under `original-at-phases/`; protected existing machine source/configuration not altered by this Phase.

## Design lock

- New machine type `HYPER_DOG`. All implementation choices in `SPEC_LOCK.md`, `IMPLEMENTATION_DECISIONS_V1.md`, `PRESENTATION_RULES_V1.json`, `REEL_DESIGN_V1.md`.
- Existing machine families remain unchanged; `CHANGE_SAFETY_POLICY.md` takes precedence.
- User creates seven RGBA symbol PNGs; development is responsible for reel strips, stopping rules and integration.
- Paper controls draw, outcomes and persistent state; Fabric renders and emits inputs.

## Phase gate

**Phase 01: COMPLETE** (documentation and baseline audit only). Phase 02 code and real runtime acceptance are separately required. A runtime acceptance failure must never be counted as PASS, and a local Java test cannot replace Minecraft runtime evidence.
