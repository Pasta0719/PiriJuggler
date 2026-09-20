# GOD EV fitting — degrees of freedom

The six-setting payout target must be explicit; a reference benchmark is not silently treated as the production target.

Before choosing visible probabilities, the economic model separates these inputs:

- base-game net medals/game
- expected net value of one normal GG initial hit
- expected net value of one independent GOD hit
- SGG contribution per game
- loop-stock contribution per game
- other premium contribution per game

Ordinary GG bell-chain V-stock is excluded.

For each setting:

targetNetPerGame
= baseNetPerGame
+ GOD_EV_perGame
+ SGG_EV_perGame
+ loopStock_EV_perGame
+ otherPremium_EV_perGame
+ normalGgInitialRate * expectedNetPerNormalGgInitial

Therefore:

normalGgInitialRate
= (targetNetPerGame - fixedContributions) / expectedNetPerNormalGgInitial

This prevents arbitrary tuning before the EV budget is known.

No final numeric initial-hit odds are locked until the complete target curve and macro EV inputs are selected.
