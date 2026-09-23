# NEXT PHASE 03 — Economy / Probability / Long-run Simulation

Status: **COMPLETE — LONG-RUN RTP ACCEPTANCE RECORDED**

After the core game is behaviorally correct:
- implement/tune locked probabilities and configurable values
- run long-run simulation
- verify payout/return/entry rates or other locked economy targets
- publish assumptions and error/confidence
- keep cosmetic presentation from changing authoritative economy

Do not invent target values that were not locked in NEXT Phase 01.

## Phase 03 implementation

- Added a dedicated whole-machine `JugglerGodSimulator` instead of reusing the ordinary JUGGLER simulator.
- Simulation includes ordinary role payouts, replay-free normal games, bonus entry/bonus-game bets, BIG/REG gross payout, independent 1/8192 GOD, GOD 15-medal role payout, five guaranteed GOD BIGs, setting-dependent continuation BIGs, hidden heaven targets, normal/heaven transition probabilities, visible bonus-in-bonus stock, and GOD-in-GOD +7 BIG stock.
- Added `/piri godsim <setting> <games>` to run the successor economy simulation without mutating live machine state.
- Production and simulator consume the same explicit JUGGLER_GOD tuning values; ordinary JUGGLER probability rows remain unchanged.

## Accepted production tuning

- normal BIG -> heaven: `125000 ppm` (12.5%)
- normal REG -> heaven: `62500 ppm` (6.25%)
- heaven -> heaven: `500000 ppm` (50%)
- setting 1 bonus/small-role scale: `804200 ppm`
- setting 2 bonus/small-role scale: `801100 ppm`
- setting 3 bonus/small-role scale: `809800 ppm`
- setting 4 bonus/small-role scale: `814400 ppm`
- setting 5 bonus/small-role scale: `824600 ppm`
- setting 6 bonus/small-role scale: `810900 ppm`

Locked RTP targets are `97.5 / 99.0 / 101.5 / 105.0 / 109.5 / 115.0%` for settings 1..6.

## Long-run acceptance evidence

CI commit `9e13e2b2ce065b0c19d765a9acff5cd2f1142e1f` ran five deterministic replications of 3,000,000 lever games for every setting (15,000,000 games per setting; 90,000,000 total). Seeds are printed per replication by `JugglerGodRtpAcceptanceTest`, and the CI build completed successfully.

Observed means and 95% Student-t confidence intervals over the five replication RTPs:

| Setting | Target | Mean RTP | Delta | Sample SD | 95% CI | Half-width |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 97.500% | 97.925789% | +0.425789 pp | 0.723634 pp | 97.027278–98.824300% | 0.898511 pp |
| 2 | 99.000% | 98.897834% | -0.102166 pp | 0.346660 pp | 98.467399–99.328269% | 0.430435 pp |
| 3 | 101.500% | 101.874294% | +0.374294 pp | 0.216212 pp | 101.605831–102.142757% | 0.268463 pp |
| 4 | 105.000% | 104.784656% | -0.215344 pp | 0.312181 pp | 104.397033–105.172280% | 0.387624 pp |
| 5 | 109.500% | 108.936572% | -0.563428 pp | 0.398728 pp | 108.441486–109.431659% | 0.495086 pp |
| 6 | 115.000% | 115.168347% | +0.168347 pp | 0.869285 pp | 114.088988–116.247706% | 1.079359 pp |

All six deterministic acceptance assertions passed. The largest observed mean error was setting 5 at -0.563428 percentage points. The evidence records run length, deterministic seeds, sampling spread, and 95% confidence intervals; no ordinary JUGGLER probability or asset row was modified to obtain these results.

## Acceptance gate result

1. settings 1..6 were simulated with production tuning values — **PASS**;
2. observed whole-machine RTP was compared with every locked target — **PASS**;
3. run length, deterministic seeds, sample error and 95% confidence are recorded — **PASS**;
4. GOD remains exactly 1/8192, five guaranteed BIGs, setting-dependent continuation, GOD-in-GOD +7 BIG stock — **PASS**;
5. ordinary JUGGLER probabilities/assets were not changed to fit JUGGLER_GOD — **PASS**;
6. JUGGLER_GOD presentation remains independently replaceable through its dedicated asset namespace — **PASS**.

NEXT Phase 03 is complete. NEXT Phase 04 may proceed.
