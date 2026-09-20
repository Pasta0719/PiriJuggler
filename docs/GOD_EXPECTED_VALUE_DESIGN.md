# Piri GOD — expected-value-first design

Gameplay/UI implementation is intentionally blocked until the long-run economics are defined.

## Fixed design decisions
- GOD flag is an independent per-game draw at exactly 1/8192.
- The machine should use a high-pure-increase GG as its base rather than copying Gaisen verbatim.
- GG keeps a visible bell-streak V-stock mechanic, but its thresholds/probabilities are derived from an allowed EV budget rather than chosen by feel.
- Presentation, lamps, sounds and reel effects are downstream of the economics.

## EV model
The first model is a finite-state Markov reward model.

For each gameplay state we define:
- transition probability to every next state
- expected wager medals per game in that state
- expected payout medals per game in that state

From the stationary state distribution we compute long-run payout percentage:

payoutPercent = 100 * longRunPayoutPerGame / longRunBetPerGame

Initial states to parameterize:
- NORMAL
- GG
- G_ZONE
- SGG
- Z_ZONE / Z_GAME if retained

GOD, red-7/SGG entry, loop/set stock and bell-streak V-stock are represented as transition/reward contributions, not visual features.

## Parameter-fitting order
1. target payout percentages for settings 1..6
2. wager and ordinary-symbol return in NORMAL
3. GG games/set and net/pure increase
4. independent GOD 1/8192 reward distribution
5. normal GG initial-hit contribution
6. loop/set-stock contribution
7. bell-streak V-stock EV budget
8. SGG and other premium paths
9. solve/tune parameters until all six settings meet target payout
10. only then freeze reel/UI/audio behavior

No production GOD engine should be written until this model has a stable parameter set.
