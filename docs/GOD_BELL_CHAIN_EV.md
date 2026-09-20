# GOD bell-chain V-stock EV model

The bell-chain mechanic is now modeled from expected value first.

For one GG set:
- GG length: configurable (current baseline 50 games)
- bell probability: not locked yet
- visible streak threshold: not locked yet
- V-stock award chance at/above threshold: solved from EV budget
- one stock's net value: derived from the GG/set model

A non-bell resets the streak.
A bell extends it.
Every bell whose resulting streak is at least the threshold is a qualifying V-stock opportunity.

The implementation computes the exact finite-horizon expectation over the GG set rather than Monte Carlo estimation.

This lets us ask the correct question:

"If bell-chain V-stock is allowed to contribute X net medals of EV per GG set, and the actual GG bell probability is B, what award chance is permitted at threshold N?"

instead of first choosing "5 bells = 100%" and discovering later that high pure increase has made the machine overpay.

The threshold itself remains a design variable. Gaisen used a strong reward for long yellow-7 streaks during GG, including a confirmed reward at five consecutive yellow 7s; that is inspiration, not a copied numeric rule for Piri GOD.
