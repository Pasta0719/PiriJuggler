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
- SGG/red-7 path
- any retained Z path
- any other premium route

Ordinary GG bell-streak V-stock is explicitly excluded from the production design.

For each source:

EV contribution per game = trigger probability per game * expected net medals per trigger

Those contributions, ordinary-symbol return, and normal-game losses must reconcile to the setting target.

## Next numeric unknowns

Before gameplay code:
1. expected net value of one GOD hit
2. normal-game base return and average games per GG initial hit per setting
3. loop-stock distribution
4. SGG/red-7 EV
5. optional Z-system EV
6. any other retained premium route

All are to be fit against the six payout targets and then verified by long-run simulation.
