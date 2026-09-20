# GOD 1/8192 override — exact payout accounting direction

A correction to the earlier quick estimate:

Treating "3000 expected medals from one GOD" as an instantaneous +3000 net-medal reward and dividing by 3 medals/game gives a useful rough EV scale, but it is **not an exact machine payout calculation**.

GOD creates additional AT games. Those games add both:
- wagered medals to the payout denominator
- paid medals to the payout numerator

Therefore the exact new payout curve cannot be obtained by simply adding a fixed number of percentage points to Kiseki's published payout percentages.

The project now has explicit cycle accounting for an added premium trigger. The final calculation must use the complete NORMAL/GG/G-ZONE/SGG/Z state model so premium-added play time is included.

## Locked production interpretation

The user's direction is now interpreted literally:

- gameplay baseline = Kamigami no Kiseki
- ordinary GG bell-chain V-stock = excluded
- GOD trigger = independent 1/8192 instead of Kiseki's 1/16384
- do **not** silently nerf published Kiseki mechanics merely to force the original payout curve

The resulting Piri payout curve is therefore allowed to be higher than Kiseki's. We will calculate that curve from the finished state model rather than forcing it in advance.

The earlier +6.1 point number remains only a rough premium-EV scale under simplified assumptions; it is not a locked target.
