# GOD 1/8192 override — exact payout accounting direction

Treating "3000 expected medals from one GOD" as an instantaneous +3000 net-medal reward and dividing by 3 medals/game is only a rough EV scale, not an exact machine payout calculation.

GOD creates additional AT games. Those games add both:
- wagered medals to the payout denominator
- paid medals to the payout numerator

The exact payout must therefore be calculated from the complete NORMAL/GG/G-ZONE/SGG/Z state model.

## Locked design constraints

- gameplay baseline = Kamigami no Kiseki
- ordinary GG bell-chain V-stock = excluded
- GOD trigger = independent 1/8192
- setting-independent GOD probability
- payout design is target-first

## Target-first rule

The production payout curve must be selected first.

Then:
1. GOD remains fixed at independent 1/8192.
2. Kiseki-style mechanics are retained as far as possible.
3. probabilities/distributions are fitted so the completed machine reaches the selected payout curve.
4. no parameter is changed merely by feel.
5. if a Kiseki reference probability conflicts with the target EV after the 1/8192 change, it becomes a fitting variable rather than an untouchable constant.

The published Kiseki payout curve is currently a benchmark, not an automatically locked Piri target.

The earlier +6.1 point figure is only a simplified premium-EV scale and must not be used as the final payout result.
