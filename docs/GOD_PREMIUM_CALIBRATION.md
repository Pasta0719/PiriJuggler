# Piri GOD — premium calibration without guessing hidden loop rates

The published Kamigami no Kiseki information establishes:

- GOD = 1/16384
- GOD reward = GG stock 4 + strong loop stock
- expected medals = 3000+ 

The exact strong-loop distribution is not publicly established in the sources currently used by this project. Therefore Piri does not invent a fake "the loop is exactly X%" rule just to make the spreadsheet close.

Instead, the EV model can calibrate against the published aggregate expectation.

At the current baseline:

- 1 GG-equivalent = 50G * +7.0 = +350 net medals
- guaranteed four GG-equivalents = +1400
- a 3000-medal calibration point leaves +1600 medals attributable to the aggregate effect of loop stock and any included premium-path value
- +1600 / 350 = 4.5714 additional GG-equivalents on average

That **4.5714 is an economic equivalent, not a claim that the actual machine literally gives 4.5714 extra stocks**.

## Effect of Piri's 1/8192 override

If one GOD hit is calibrated to 3000 net medals, changing its rate from 1/16384 to 1/8192 adds:

- +3000 / 16384 = +0.18310546875 net medals/game
- at 3 medals wagered per normal game: +6.1035 payout percentage points

This is already large enough that the rest of the Kiseki probabilities cannot all be copied unchanged while retaining the original payout curve.

The next fitting stage must therefore remove approximately this additional EV from non-GOD sources, or accept a deliberately higher payout curve.
