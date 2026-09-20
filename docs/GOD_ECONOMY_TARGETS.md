# Piri GOD — economic target selection

## Reference benchmark

Kamigami no Kiseki's published payout curve is retained as a reference benchmark:

| Setting | Kiseki benchmark |
|---:|---:|
| 1 | 97.2% |
| 2 | 99.1% |
| 3 | 102.1% |
| 4 | 106.9% |
| 5 | 111.7% |
| 6 | 114.6% |

This curve is **not yet automatically the locked Piri production target**.

The production curve must be passed explicitly through `GodPayoutCurve`.

## Fixed Piri invariants

- GG baseline: 50 games
- GG pure increase baseline: +7.0 net medals/game
- one plain GG set: +350 net medals
- GOD: exact independent 1/8192
- ordinary GG bell-streak V-stock: excluded

## Fitting rule

Once the six-setting target curve is selected, all remaining EV sources are fitted against it:

- normal GG initial hit
- loop/set stock
- SGG/red-7 path
- retained Z path
- other premium routes

The Kiseki structure is the gameplay baseline, but numeric probabilities may need adjustment because Piri fixes GOD at 1/8192.

No production GOD engine should be considered economically complete until long-run simulation matches the selected target curve.
