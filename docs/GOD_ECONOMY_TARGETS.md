# Piri GOD — economic targets and fitting order

## Locked starting benchmark

The first target payout curve is:

| Setting | Target payout |
|---:|---:|
| 1 | 97.2% |
| 2 | 99.1% |
| 3 | 102.1% |
| 4 | 106.9% |
| 5 | 111.7% |
| 6 | 114.6% |

The initial GG baseline is 50 games at +7.0 net medals/game, therefore one plain GG set is +350 net medals before any stock continuation.

The GOD flag is fixed independently at exactly 1/8192 on every eligible game, all settings. It is not allowed to inherit a mode-dependent probability.

## Budget rule

Every positive payout source gets an explicit long-run EV budget before concrete hit probabilities are chosen:

- independent GOD
- normal GG initial hit
- loop/set stock
- bell-chain V stock
- SGG/red-7 path
- any retained Z path

For each source:

EV contribution per game = trigger probability per game * expected net medals per trigger

Those contributions, ordinary-symbol return, and normal-game losses must reconcile to the setting target.

## Bell-chain rule

Bell-chain V-stock parameters are not selected by appearance or nostalgia.

We first decide the maximum share of total GG-side EV that bell-chain V stock may consume.
Only then do we solve for a visible bell-chain threshold and stock probability that fit that budget under the actual GG bell probability.

This is why the final bell-chain count is deliberately still unset.

## Next numeric unknowns

Before gameplay code:
1. expected net value of one GOD hit
2. normal-game base return and average games per GG initial hit per setting
3. loop-stock distribution
4. GG bell probability under the high-pure-increase system
5. bell-chain V-stock EV allocation
6. SGG/red-7 EV
7. optional Z-system EV

All seven are to be fit against the six payout targets and then verified by long-run simulation.
