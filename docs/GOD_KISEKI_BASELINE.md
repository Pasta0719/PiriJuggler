# Piri GOD — Kamigami no Kiseki baseline

## Production direction

Piri GOD uses **Million God: Kamigami no Kiseki as the gameplay baseline**.

The design intent is:

> Keep the Kiseki-style structure and feel, make GOD an independent 1/8192 draw, and fit the numeric probabilities to an explicit Piri payout target.

The rejected Gaisen-style ordinary GG bell-streak V-stock is not included.

## Baseline mechanics to preserve

Reference mechanics/values from Kamigami no Kiseki:

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
- normal play uses role/mode/history based GG draws
- published AT initial-hit benchmark:
  - setting 1: 1/533
  - setting 2: 1/420
  - setting 3: 1/496
  - setting 4: 1/338
  - setting 5: 1/455
  - setting 6: 1/295
- published Kiseki GOD probability: 1/16384
- published Kiseki RED 7 probability: 1/6900

These published probabilities are reference inputs, not automatically immutable Piri production numbers.

## Intentional Piri invariant

- GOD = exact independent 1/8192
- same probability at settings 1–6
- no mode dependence
- no hidden cooling/heating adjustment

## Economic rule

The Piri payout target must be chosen explicitly before final fitting.

Changing GOD from 1/16384 to 1/8192 changes the EV budget. The remaining numeric probabilities must then be solved against the chosen Piri target while preserving Kiseki-style mechanics as closely as possible.

No ordinary GG bell-streak V-stock is included.
