# GOD EV fitting — degrees of freedom

The six target payout percentages do not uniquely determine the machine.

Before choosing visible probabilities, the economic model separates these inputs:

- base-game net medals/game
- expected net value of one normal GG initial hit
- expected net value of one independent GOD hit
- SGG contribution per game
- bell-chain V-stock contribution per game
- loop-stock contribution per game
- other premium contribution per game

Once those are fixed, the setting-specific normal GG initial hit rate is solved as the residual required to meet the target payout.

For each setting:

targetNetPerGame
= baseNetPerGame
+ GOD_EV_perGame
+ SGG_EV_perGame
+ bellV_EV_perGame
+ loopStock_EV_perGame
+ otherPremium_EV_perGame
+ normalGgInitialRate * expectedNetPerNormalGgInitial

Therefore:

normalGgInitialRate
= (targetNetPerGame - fixedContributions) / expectedNetPerNormalGgInitial

This prevents arbitrary tuning of initial-hit odds before the payout budget is known.

No final numeric initial-hit odds are locked yet because the remaining macro inputs are intentionally not being guessed.
