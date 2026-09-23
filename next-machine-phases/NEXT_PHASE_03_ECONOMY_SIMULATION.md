# NEXT PHASE 03 — Economy / Probability / Long-run Simulation

Status: **IN_PROGRESS — WHOLE-MACHINE SIMULATOR IMPLEMENTED; CANDIDATE TUNING CONFIGURED; LONG-RUN ACCEPTANCE PENDING**

After the core game is behaviorally correct:
- implement/tune locked probabilities and configurable values
- run long-run simulation
- verify payout/return/entry rates or other locked economy targets
- publish assumptions and error/confidence
- keep cosmetic presentation from changing authoritative economy

Do not invent target values that were not locked in NEXT Phase 01.

## Phase 03 implementation progress

- Added a dedicated whole-machine `JugglerGodSimulator` instead of reusing the ordinary JUGGLER simulator.
- Simulation includes ordinary role payouts, replay-free normal games, bonus entry/bonus-game bets, BIG/REG gross payout, independent 1/8192 GOD, GOD 15-medal role payout, five guaranteed GOD BIGs, setting-dependent continuation BIGs, hidden heaven targets, normal/heaven transition probabilities, visible bonus-in-bonus stock, and GOD-in-GOD +7 BIG stock.
- Added `/piri godsim <setting> <games>` to run the successor economy simulation without mutating live machine state.
- Added deterministic/invariant coverage for the successor simulator.
- Production and simulator both consume the same explicit JUGGLER_GOD tuning values; ordinary JUGGLER probability rows remain unchanged.

## Current candidate tuning

The current authoritative configuration contains the following Phase 03 candidates:

- normal BIG -> heaven: `125000 ppm` (12.5%)
- normal REG -> heaven: `62500 ppm` (6.25%)
- heaven -> heaven: `500000 ppm` (50%)
- setting 1 bonus/small-role scale: `804200 ppm`
- setting 2 bonus/small-role scale: `801100 ppm`
- setting 3 bonus/small-role scale: `809800 ppm`
- setting 4 bonus/small-role scale: `814400 ppm`
- setting 5 bonus/small-role scale: `824600 ppm`
- setting 6 bonus/small-role scale: `810900 ppm`

These are tuning candidates, not a declaration that the locked RTP targets are already accepted. Final Phase 03 completion still requires sufficiently long whole-machine runs for settings 1..6 against the locked targets `97.5 / 99.0 / 101.5 / 105.0 / 109.5 / 115.0%`, with the observed result and sampling error recorded.

## Phase 03 acceptance gate

Phase 03 may be marked COMPLETE only after all of the following are true:

1. settings 1..6 are simulated with the production tuning values;
2. observed whole-machine RTP is compared with the locked target for every setting;
3. run length/seed (or equivalent reproducibility information) and error/confidence are recorded;
4. GOD remains exactly 1/8192, the five guaranteed BIGs and setting-dependent continuation structure remain intact, and GOD-in-GOD remains +7 BIG stock;
5. ordinary JUGGLER probabilities/assets are not changed to make JUGGLER_GOD fit;
6. JUGGLER_GOD presentation remains independently replaceable through its dedicated asset namespace.

Until that evidence exists, NEXT Phase 04 remains blocked by Phase 03.
