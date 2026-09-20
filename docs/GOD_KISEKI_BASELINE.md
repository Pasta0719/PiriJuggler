# Piri GOD — Kamigami no Kiseki baseline

## Production direction

Piri GOD uses **Million God: Kamigami no Kiseki as the basic gameplay baseline**.

The intended rule is:

> Keep the Kiseki-style game structure and core behavior, but make the GOD flag an independent 1/8192 draw.

This replaces the previous idea of mixing in Gaisen's ordinary GG bell-streak V-stock. That mechanic remains rejected.

## Baseline mechanics to preserve

Reference values/mechanics from Kamigami no Kiseki:

- Main AT: GOD GAME (GG)
- GG: 1 set = 50 games
- GG pure increase: about +7.0 medals/game
- GG: set-stock management
- loop stock: up to 80%
- after GG: G-ZONE, up to 5 games
- RED 7: SUPER GOD GAME (SGG)
- SGG: 10–100 games per set, 75%+ continuation
- Z-ZONE: 5G+alpha challenge zone
- Z-GAME: high-frequency GG-stock acquisition route
- normal play still uses role/mode/history based GG draws
- setting-specific published AT initial-hit benchmark:
  - 1: 1/533
  - 2: 1/420
  - 3: 1/496
  - 4: 1/338
  - 5: 1/455
  - 6: 1/295
- published Kiseki GOD probability: 1/16384
- published Kiseki RED 7 probability: 1/6900

## Intentional Piri change

Piri GOD changes only the premium GOD trigger rule at the baseline-design level:

- GOD = **exact independent 1/8192**
- same probability at settings 1–6
- no mode dependence
- no hidden cooling/heating adjustment
- eligible during normal play and AT play

## Important EV consequence

"Basic mechanics are the same" does **not** mean the original Kiseki payout percentages can remain unchanged automatically.

Changing GOD from 1/16384 to 1/8192 doubles its long-run trigger frequency. If its reward stays Kiseki-like, that adds a large amount of EV.

Therefore the project must keep two concepts separate:

1. **gameplay baseline**: Kiseki
2. **economic fit**: recalculated for Piri with GOD fixed at 1/8192

The final six-setting payout curve is not considered solved until the complete simulator reproduces it with the 1/8192 GOD rule.

No ordinary GG bell-streak V-stock is included.
