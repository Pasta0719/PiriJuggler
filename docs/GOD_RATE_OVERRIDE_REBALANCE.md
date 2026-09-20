# Piri GOD — where the 1/8192 EV delta can be removed

Build status before this step: successful.

The GOD-rate override adds positive EV. We should not randomly lower multiple probabilities until the complete state model exists.

For now the project records the exact one-source rebalance equation.

If all non-GOD mechanics are kept at the Kiseki baseline and the entire added GOD EV is absorbed only by the normal GG initial-hit rate:

removedInitialRatePerGame
= extraGodNetPerGame / expectedNetPerNormalGgInitial

where:

extraGodNetPerGame
= expectedNetPerGod * (1/8192 - 1/16384)

The adjusted initial-hit rate is:

adjustedRate = originalRate - removedInitialRatePerGame

and the new odds are:

adjustedOdds = 1 / adjustedRate

This is not yet the final production choice. It is a diagnostic boundary:
- if normal GG alone can absorb the delta with plausible odds, that is one option;
- if it cannot, EV must also be removed from loop stock, SGG/Z paths, or another source;
- no hidden probability is guessed to make the numbers fit.

The average net value of one normal GG initial remains deliberately unresolved until the full GG/loop-stock model is defined.
