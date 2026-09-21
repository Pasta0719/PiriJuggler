# Piri GOD role contract v1

Status: **LOCKED — GOD Phase 01**

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
| MISS | 1/5.2 | 0 medal, no replay | any physical three-reel stop window that matches none of the locked winning/replay/premium formations and forms no straight/diagonal GOD/RED7/BLUE7/YELLOW7 line; selection remains within the ordinary 0..4-frame slip policy | left-first unless a state-specific nav exists | ordinary miss | REFERENCE_BACKED odds / PIRI_SPECIFIC presentation-control classification |
| UPPER_BLUE7 | 1/7.9 | replay | blue7 line on upper row | left-first | replay | REFERENCE_BACKED |
| MIDDLE_BLUE7 | 1/109.2 | replay | blue7 line on middle row | left-first | replay | REFERENCE_BACKED |
| ORDERED_YELLOW7 | 1/1.7 | AT/navigation state: 15 medals when the shown order is completed; ordinary normal play: winning order is concealed and the accepted left-first miss-side 0/1-medal calibration applies | lower yellow-7 A / navigated yellow result when acquired; normal-play miss-side form must not masquerade as a 15-medal acquisition | obey displayed push order when navigation exists; ordinary normal play accepts the locked left-first path; wrong instructed input is rejected by Piri | high-frequency ordered yellow whose realized payout is state/navigation dependent | REFERENCE_BACKED role concept / PIRI_SPECIFIC normal miss-side calibration and input-safety policy |
| LOWER_YELLOW7 | 1/18.4 | 3 medals | **lower-row yellow line with the center reel using the yellow symbol immediately below RED7; equivalently the published example has center RED7 on the middle row** | left-first | 3-medal lower-yellow B | REFERENCE_BACKED |
| RISING_YELLOW7 | 1/186.2 | 15 medals | rising/right-up yellow line | left-first | rare yellow | REFERENCE_BACKED |
| MIDDLE_YELLOW7 | 1/963.8 | 15 medals | middle-row yellow line | left-first | rare yellow | REFERENCE_BACKED |
| COMMON_YELLOW7 | 1/1524.1 | 15 medals | **lower-row yellow line with the center reel using the blue7-adjacent A pattern; published example has center BLUE7 on the middle row** | left-first unless state nav applies | common 15-medal lower-yellow A | REFERENCE_BACKED |
| GAIA_BELL | 1/37.6 | 1 medal | yellow small-V | **right-first navigation is shown for this role** | Gaia-history role | REFERENCE_BACKED |
| RED7_FAKE | 1/936.2 | replay | deterministic fake-RED presentation: at least one RED7 is visible, but it must not form RED7 straight and must not match SP/GOD or any paying yellow formation | left-first | replay / fake premium pattern | REFERENCE_BACKED odds+replay identity; PIRI_SPECIFIC presentation |
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


## Piri v1 stop-control policy

Classification: **PIRI_SPECIFIC**, because a complete Kiseki press-index control table is not publicly available in the sources currently locked.

This policy is mandatory for GOD Phase 02:

1. There must be exactly one shared function/convention that maps:
   - reel index
   - middle stop index
   - visible row TOP/MIDDLE/BOTTOM
   to the actual visible symbol.
2. Server stop control and Fabric rendering must use that same convention. They may not maintain separate +/- row arithmetic.
3. A role describes a **visible formation constraint**, never a raw array offset.
4. On each reel stop, search normal slip distance 0..4 and choose the nearest middle-stop index satisfying that role's visible-row constraint.
5. If multiple candidates exist at the same nearest distance, choose deterministically by strip order; no RNG is allowed in presentation control.
6. A role may not change payout/replay because a representative visual target is inconvenient.
7. A role may not silently display another paying role's formation.
8. Ordinary non-premium role control keeps the normal 0..4 slip search unless a separate source-backed rule says otherwise. If no ordinary-role stop within 0..4 satisfies its locked visible formation, do not silently substitute an unrelated result.
9. Premium roles GOD / RED7 / SP are an explicit Piri exception: their locked visible result takes priority over the normal 0..4 slip window. If necessary, the reel may slip beyond 4 frames by the minimum deterministic amount required to produce GOD straight / RED7 straight / RED7-RED7-GOD. This long-slip behavior is PIRI_SPECIFIC and must not be represented as exact Kiseki reel control.
10. Runtime tests compare the **three visible rows**, not just the middle stop index.

### Required lower-yellow assertions

For every legal press index 0..19 and for all three reels:

- LOWER_YELLOW7 must resolve within 0..4 frames to a final three-row presentation satisfying the 3-medal B form.
- COMMON_YELLOW7 must resolve within 0..4 frames to a final three-row presentation satisfying the 15-medal A form.
- Those two final three-row presentations must not be identical.
- payout must be 3 and 15 respectively.
- renderer and server must report the same final visible symbols.

This is a Piri control policy, not a claim that the hidden Kiseki control table is identical.


## Piri input-order safety policy

Classification: **PIRI_SPECIFIC**.

Reference sources warn that non-left-first play and navigation mistakes may cause penalties, but the complete hidden penalty behavior is not published. Piri therefore does not invent a penalty table.

- When no navigation is active, the first accepted stop must be LEFT.
- After LEFT is accepted, CENTER/RIGHT may be accepted in either order unless a role/state explicitly requires an order.
- When navigation is active, only the currently instructed reel stop is accepted.
- A wrong stop input is rejected and the reel remains spinning.
- Wrong input does not redraw the role, alter payout, create a 1-medal surrogate, or consume the one-shot forced role.
- Client and server show the same navigation state.
- Recovery must preserve the remaining required stop order.

This applies to **wrong instructed input** only: a wrong button press is rejected and must never be converted into a synthetic 1-medal result. It does **not** replace the separately locked normal-play ORDERED_YELLOW7 miss-side calibration, where concealed navigation followed by valid left-first play may settle to 0 or 1 medal.

- User accepted Piri input-order safety policy: normal left-first; navigation accepts only instructed reel; wrong input is rejected without stopping reel, redrawing/changing role or payout, or consuming forced-role tests.

- User accepted the yellow-role visual/payout consistency rule: 3-medal and 15-medal yellow outcomes must have distinct source-backed visible formations; server stop control and Fabric rendering must share one visible-row convention; no silent fallback to a visually incorrect result is permitted.

- User accepted premium visible-result policy: GOD must visibly resolve as GOD straight, RED7 as RED7 straight, and SP as RED7/RED7/GOD. Press-position/slip behavior for forcing these forms is PIRI_SPECIFIC and must not be claimed as exact Kiseki control.

- User accepted replay semantics/presentation: UPPER_BLUE7 and MIDDLE_BLUE7 are true replay outcomes; RED7_FAKE is also replay. Replay grants the next game without consuming a new bet, and RED7_FAKE must not visually match RED7 straight, SP, GOD, or a paying yellow formation.

- Premium-role draw model accepted: GOD, RED7, and SP are mutually exclusive role outcomes within a single game. The production draw must use one exclusive role-selection process rather than three independent boolean draws with a GOD > RED7 > SP tie-breaker. Therefore simultaneous premium hits must be structurally impossible, and the old precedence rule is rejected for the final implementation.

- Whole-game role draw model accepted: every game resolves to exactly one mutually exclusive role outcome across MISS, blue-7 roles, yellow-7 roles, Gaia bell, RED7 fake, RED7, GOD, and SP. Published role probabilities are used directly where source-backed; any residual needed because rounded published denominators do not sum exactly to 100% must be handled as an explicitly PIRI_SPECIFIC allocation rule rather than being presented as an authentic hidden machine value.

- Normal/AT role-frequency policy accepted: unless a state-specific role-frequency difference is explicitly source-backed, the same role occurrence probabilities are used across normal play and GG/AT states. GG net increase is produced by AT navigation and payout realization (especially ordered yellow-7 acquisition), not by inventing higher rare-role frequencies. State-dependent differences belong in post-role benefits/effects, not in unsupported role-rate distortion.

- Ordered-yellow concealment policy accepted for normal play: an internally selected ordered-yellow role does not expose its winning navigation during ordinary normal play. The player is handled as a left-first normal-play input, and the visible stop/payout outcome must be produced from the source-backed left-first behavior. During GG/AT, the winning order is shown by navigation so the 15-medal acquisition path can be realized. Exact normal-play left-first stop/payout mapping remains a separate source-lock item and must not be invented.

- Gaia-yellow normal-play navigation accepted: GAIA_BELL / Gaia-yellow occurs at the published setting-common rate 1/37.6 and, when it occurs in normal play, its right-first navigation is shown. Ordinary ordered 15-medal yellow remains non-navigated in normal play unless another source-backed exception applies. Gaia-yellow handling is source-backed, not Piri-specific.

- Normal ordered-yellow left-first payout calibration accepted: when ordinary ordered 15-medal yellow is internally selected in normal play without a navigation exception, process the left-first outcome rather than awarding 15 medals. Public information indicates a miss-side outcome can include a 1-medal role; the exact 0/1-medal split is not public, so that split is PIRI_SPECIFIC and will be calibrated in Phase 04 against the published normal-game base (~30.8G/50 medals) without altering the locked role occurrence rates.

- Premium slip exception accepted by user: for GOD / RED7 / SP, preserving the locked visible premium formation is more important than the ordinary 4-frame slip limit; allow deterministic >4-frame slip when required. Ordinary-role control remains subject to its normal slip rules unless separately specified.


## Phase-01 role-draw final lock

- All roles, including GOD / RED7 / SP, participate in one mutually-exclusive categorical draw.
- GOD's Piri override is an exact 1/8192 marginal probability, not a separate boolean roll layered on top of another role.
- RED7 and SP likewise cannot coexist with another role in the same game.
- Rounded public denominators that do not sum exactly to 100% are reconciled only by an explicit PIRI_SPECIFIC residual-allocation method; role probabilities may not be silently renormalized in a way that changes the locked premium marginals.


## Production categorical MISS cell

Because the published rounded denominators exceed 100% when every row, including MISS 1/5.2, is treated as an exact mutually-exclusive category, production does **not** use 1/5.2 as an exact categorical MISS probability.

Piri preserves the locked/non-MISS marginals and assigns MISS the residual:
- production MISS probability ≈ 0.1866666891
- effective denominator ≈ 1/5.3571422

This is PIRI_SPECIFIC residual allocation and is not claimed as the hidden Kiseki MISS value.
