# Piri GOD role contract v1

Status: **DRAFT — source consolidated for GOD Phase 01**

This document defines semantic role outcomes. It deliberately does **not** invent a complete press-index control table where public sources publish only representative stop forms.

## Source-backed common play rules

- Normal play: left reel first; free stop afterward. Non-left-first play may incur a penalty.
- AT: follow navigation when navigation appears. Otherwise left-first/free play remains the safe baseline.
- Public stop-form pages explicitly state that displayed forms are examples, not full control tables.

Primary sources:
- https://1geki.jp/slot/l_milliongod_kiseki/2/
- https://nana-press.com/kaiseki/machine/1112/34745/
- https://www.p-world.co.jp/machine/database/10424

## Semantic role contract

| Internal role | Reference odds | Wager result | Visible result required by Piri | Navigation | Gameplay meaning | Classification |
|---|---:|---|---|---|---|---|
| MISS | 1/5.2 | 0 medal, no replay | non-winning combination | left-first unless a state-specific nav exists | ordinary miss | REFERENCE_BACKED odds / PIRI_SPECIFIC presentation fallback |
| UPPER_BLUE7 | 1/7.9 | replay | blue7 line on upper row | left-first | replay | REFERENCE_BACKED |
| MIDDLE_BLUE7 | 1/109.2 | replay | blue7 line on middle row | left-first | replay | REFERENCE_BACKED |
| ORDERED_YELLOW7 | 1/1.7 | 15 medals when correctly navigated | lower yellow-7 A / navigated yellow result; must be visually distinct from the 3-medal B form by center-reel symbol identity | obey displayed push order | high-frequency navigated yellow | REFERENCE_BACKED concept; exact miss presentation remains implementation-specific unless source-backed |
| LOWER_YELLOW7 | 1/18.4 | 3 medals | **lower-row yellow line with the center reel using the yellow symbol immediately below RED7; equivalently the published example has center RED7 on the middle row** | left-first | 3-medal lower-yellow B | REFERENCE_BACKED |
| RISING_YELLOW7 | 1/186.2 | 15 medals | rising/right-up yellow line | left-first | rare yellow | REFERENCE_BACKED |
| MIDDLE_YELLOW7 | 1/963.8 | 15 medals | middle-row yellow line | left-first | rare yellow | REFERENCE_BACKED |
| COMMON_YELLOW7 | 1/1524.1 | 15 medals | **lower-row yellow line with the center reel using the blue7-adjacent A pattern; published example has center BLUE7 on the middle row** | left-first unless state nav applies | common 15-medal lower-yellow A | REFERENCE_BACKED |
| GAIA_BELL | 1/37.6 | 1 medal | yellow small-V | **right-first navigation is shown for this role** | Gaia-history role | REFERENCE_BACKED |
| RED7_FAKE | 1/936.2 | replay | exact Kiseki press-by-press form not publicly locked in current sources; must not look like a completed RED7 straight | left-first | replay / fake premium pattern | REFERENCE_BACKED odds+replay identity; PIRI_SPECIFIC safe presentation |
| RED7 | 1/6900 | 15 medals | RED7 straight | left-first; representative form only unless exact control is sourced | enters SGG | LOCKED_PIRI odds; REFERENCE_BACKED form/benefit |
| GOD | reference 1/16384, Piri 1/8192 | 15 medals | GOD straight | left-first; representative form only unless exact control is sourced | PGG/GOD stage + locked Piri benefit | LOCKED_PIRI odds; REFERENCE_BACKED form/baseline benefit |
| SP | 1/65536 | 15 medals | middle RED7 / RED7 / GOD | left-first; representative form only unless exact control is sourced | special GG/stock behavior by state | LOCKED_PIRI odds; REFERENCE_BACKED form/behavior |

## Critical lower-yellow distinction

The production implementation must preserve the distinction below:

### 15-medal lower yellow A
- lower-row yellow line
- NanaPress published example: center reel middle row is BLUE7
- 1geki calls this **下段黄7A（15枚）**

### 3-medal lower yellow B
- lower-row yellow line
- the center yellow used is the one immediately below RED7 on the physical strip
- NanaPress published example: center reel middle row is RED7
- 1geki calls this **下段黄7B（3枚）**

Therefore these two outcomes may never settle with identical three-row presentation in Piri.

Sources:
- https://1geki.jp/slot/l_milliongod_kiseki/2/
- https://nana-press.com/kaiseki/machine/1112/
- https://nana-press.com/kaiseki/machine/1112/34745/

## Representative form vs control-table rule

Public analysis pages describe the above as representative stop forms. They do not publish a complete press-position-by-press-position control table.

Therefore Piri v1 locks this distinction:

- **semantic visible formation is authoritative**
- **complete real-machine press-index control is UNKNOWN**
- Piri must never claim its stop-control table is exact Kiseki control unless a complete source is later obtained
- implementation may use a PIRI_SPECIFIC deterministic control algorithm, but it must preserve:
  - physical strip membership
  - max-slip policy chosen by the spec
  - source-backed role line/form
  - payout/replay agreement
  - push-order agreement
- if those constraints cannot all be met from a press position, the spec/test must define the fallback before coding; code may not silently improvise

## Runtime acceptance for each forced role

A forced-role test passes only when all of these agree in the same game:

1. internal role
2. navigation requirement
3. final visible reel rows
4. pay/replay display
5. credited medals/replay handling
6. state transition/benefit
7. persisted recovery state

This contract is the basis of GOD Phase 02 tests.
