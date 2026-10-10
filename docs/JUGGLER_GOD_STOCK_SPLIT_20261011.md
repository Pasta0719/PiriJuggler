# JUGGLER GOD and EXTREME GOD: stock/normal odds split (2026-10-11)

## Design contract

- BIG/REG acquisition **inside a running BIG/REG** uses per-setting `bonus_stock_scale_ppm` and is **independent of the normal-mode `bonus_scale_ppm`**.
- Default stock scales are restored to the historical pre-HIGH/ULTRA stock values, without changing GOD probability, GOD-in-GOD, guaranteed BIGs, continuation, payout size, heaven probabilities, or small-role draws.
- `bonus_scale_ppm` is a **normal-mode bonus-family EV budget**. Its reduction is applied **only to standalone BIG/REG** by computing a smaller direct scale while retaining the pre-split reference scale for CHERRY_BIG/REG and PIERO_BIG/REG. This leaves the normal-mode combined base odds nearly unchanged versus the calibrated budget while preserving overlapping-role probabilities.
- HIGH/ULTRA are calculated from the same adjusted normal base, but constrained by **minimum strength**:
  - HIGH: at least 38% underlying base-bonus probability across an uninterrupted 20-lever window (formerly 40%)
  - ULTRA: at least 68% underlying base-bonus probability across an uninterrupted 15-lever window (formerly 70%)
- When normal base is not reduced, the established HIGH 40% and ULTRA 70% targets remain the reference.
- These are independent within-window *base-draw probabilities*, not absolute guarantees that a player will see a bonus: GOD preemption, earlier bonus, and role-triggered state transitions can interrupt the full window. Role-based bonus triggers also add outcomes.
- The small-role triggered HIGH/ULTRA promotion table, 2-streak precursor odds, and BIG/REG family mix remain unchanged.
- The same split is applied in `/piri sim`, and persists as stable tuning across restarts via `config.yml`. Previously customized normal probabilities are preserved by the startup migration.

## New tuning table

These are **provisional payout-neutral calibration settings** based on an independent simulation of the source-level mechanics. This is not a full Paper-server execution; verify all 12 settings in game before declaring exact RTP.

| Setting | GOD normal scale | GOD stock scale | EXTREME normal scale | EXTREME stock scale |
|---|---:|---:|---:|---:|
| 1 | 270000 | 743613 | 198000 | 564190 |
| 2 | 267000 | 734884 | 192500 | 554200 |
| 3 | 270500 | 734653 | 194000 | 558800 |
| 4 | 270500 | 739194 | 197000 | 562600 |
| 5 | 268000 | 741839 | 199500 | 571106 |
| 6 | 255000 | 696323 | 187000 | 537600 |

Target RTP remains 97.5%, 99.0%, 101.5%, 105.0%, 109.5%, 115.0% (settings 1–6).

## Implementation

- `JugglerGodGameEngine.drawBonusOverlay`: only the stock scale is used for overlay BIG/REG draws, including during GOD-chain bonuses.
- `JugglerGodOdds.normalStandaloneScale`: transfers the budget reduction into standalone BIG/REG only; cherry/piero overlap bonus chances remain at their reference rates.
- `JugglerGodOdds.hotScale`: computes HIGH/ULTRA per-lever bonus scale from the normal-family budget ratio and clamps its full-window base expectation to at least 38%/68%.
- `JugglerGodMachineDataSimulator`: shares the exact scaling helper and the separate stock scale.
- `JugglerGodStockOddsMigration`: migrates untouched previous rows; custom normal odds are never overwritten and operator-specified stock odds are preserved.
- `ConfigValidation`: accepts and validates the optional stock field in existing and new configs.
- Tests cover every profile/setting pair and the legacy migration.

## Verification limits

Previous PR text describing fixed 40/70 or exactly 65% context-led hits reflects a different configuration. Under this rebalance, the 65/35 breakdown needs to be remeasured before being represented as verified. Do not mistake the /piri sim approximation for live Paper engine acceptance.
