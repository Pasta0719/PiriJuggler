# NEXT PHASE 03 — Economy / Probability / Long-run Simulation

Status: **IN_PROGRESS — WHOLE-MACHINE SIMULATOR IMPLEMENTED; RTP FITTING PENDING**

After the core game is behaviorally correct:
- implement/tune locked probabilities and configurable values
- run long-run simulation
- verify payout/return/entry rates or other locked economy targets
- publish assumptions and error/confidence
- keep cosmetic presentation from changing authoritative economy

Do not invent target values that were not locked in NEXT Phase 01.

## Phase 03 implementation progress

- Added a dedicated whole-machine `JugglerGodSimulator` instead of reusing the ordinary JUGGLER simulator.
- Simulation includes ordinary role payouts, replay-free normal games, bonus entry/bonus-game bets, BIG/REG gross payout, independent 1/8192 GOD, GOD 15-medal role payout, five guaranteed GOD BIGs, setting-dependent continuation BIGs, hidden heaven targets, and normal/heaven transition probabilities.
- Added `/piri godsim <setting> <games>` to run the successor economy simulation without mutating live machine state.
- Added deterministic/invariant coverage for the successor simulator.
- Current configured heaven rates remain 0 until measured whole-machine results are used for tuning.
