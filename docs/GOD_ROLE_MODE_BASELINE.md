# Piri GOD — source-backed role and mode baseline

The target-first EV model now has a concrete role-frequency baseline instead of free placeholder probabilities.

Published Kamigami no Kiseki setting-common role odds are represented in code:

| role | reference odds |
|---|---:|
| miss | 1/5.2 |
| upper blue 7 | 1/7.9 |
| middle blue 7 | 1/109.2 |
| ordered yellow 7 | 1/1.7 |
| lower yellow 7 | 1/18.4 |
| rising yellow 7 | 1/186.2 |
| middle yellow 7 | 1/963.8 |
| common yellow 7 | 1/1524.1 |
| Gaia bell | 1/37.6 |
| red-7 fake | 1/936.2 |
| red 7 | 1/6900 |
| GOD | 1/16384 reference, **1/8192 in Piri** |
| SP | 1/65536 |

The six published front-side modes are also explicit:

- LOW_A
- LOW_B
- NORMAL
- HEAVEN_PREP
- HEAVEN
- SUPER_HEAVEN

These are now available to the future state simulator and production engine.

Important: role odds are not the whole machine. GG hit probability also depends on mode, role-history draws, mode transitions, Z paths, loop stock and premium routes. Unknown tables remain unknown rather than being filled with guesses.
