# Piri GOD gameplay state contract v1

Status: **DRAFT — GOD Phase 01**

This document defines state semantics and classifies each transition. Existing code is not authority.

## State set

Piri persists these high-level states:

- NORMAL
- GG
- G_ZONE
- SGG
- SGG_COMEBACK
- Z_ZONE
- Z_GAME

## NORMAL

Reference-backed:
- GG initial hit depends on front mode, role, and role history.
- six front modes exist: LOW_A, LOW_B, NORMAL, HEAVEN_PREP, HEAVEN, SUPER_HEAVEN.
- a separate 裏天国 axis exists in the reference machine.
- blue/yellow history can trigger GG draw when the same symbol chains 3+.
- normal ceiling is 1480G.
- reset ceiling distribution is 510G 15.2%, 1000G 20.3%, 1480G 64.5%.

Piri locked:
- GOD is drawn independently at exact 1/8192, independent of setting/state.
- ordinary GG bell-chain V stock is excluded.

Not yet frozen for economy fitting:
- exact normal GG-hit probabilities that Piri may tune.
- Piri implementation of 裏天国 if the full table is not reconstructed.
- exact mode-transition probabilities where source tables are incomplete.

## GOD / PGG entry

Reference baseline:
- reference GOD odds = 1/16384.
- GOD straight is a 15-medal result.
- reference benefit: GOD stage 50G + three further GG sets = four GG-equivalent sets total.
- reference also grants a strong/high-continuation loop; public source does not identify the exact A/B/C/D class.

Piri locked override:
- probability = exact 1/8192.
- loop class = D.
- total guaranteed GG-equivalent sets = 4.

Transition:
NORMAL/GG eligible game + GOD -> GOD stage/first GG-equivalent set -> remaining three guaranteed GG sets queued -> loop-D processing according to loop contract.

## RED7 / SGG entry

Reference-backed:
- RED7 odds = 1/6900.
- RED7 straight is a 15-medal result.
- RED7 enters SGG.
- SGG: one set 10–100G.
- continuation >=75%.
- after the SGG game portion, enter a 3G comeback zone.
- 5th-multiple sets have improved game-count distribution.

Piri must not collapse SGG to ordinary GG.

## SP

Reference-backed odds:
- 1/65536.

Reference-backed state effects:
- NORMAL / GG: 50% for one GG stock + loop-D.
- Gaia stage: GG draw 100%.
- GG preparation: GG-stock draw 50% + loop-D context.
- SGG preparation / SGG: continuation stock 3 at 100%.
- SGG comeback normal set: continuation 100%, 50G:100G = 1:1.
- SGG comeback 5th-multiple set: 100G guaranteed.

Piri implementation must branch by state; SP may not have one universal transition.

## GG

Reference-backed:
- 1 set = 50G.
- pure increase about +7.0 medals/G.
- average one plain GG set about 350 medals.
- role + front mode drive stock draw during GG.
- GG ends into G-ZONE.

Piri locked:
- no ordinary GG bell-chain V-stock rule.

## G-ZONE

Reference-backed:
- up to 5G after GG.
- stock/continuation presentation occurs here.
- if no continuation route resolves, return to normal flow after the G-ZONE window.

Exact liquid-display number-pattern presentation is client/presentation scope, not the authoritative stock calculation.

## Z-ZONE

Reference-backed:
- entry via 0 alignment / specific promotions including some Gaia-stage GG hits.
- base window 5G + alpha.
- full navigation.
- yellow appearance about 1/1.4.
- yellow appearance holds the countdown.
- challenge counter is consumed by miss/blue; five consumed -> failure -> GG.
- five consecutive/filled yellow results -> GG stock + Z-GAME.
- rising yellow or middle yellow can count as immediate success / five-yellow equivalent.
- if failure occurs with zero yellow appearances, 50% chance of loop-D.

Important correction:
The zero-yellow 50% loop-D rule is REFERENCE_BACKED, not Piri-specific.

## Z-GAME

Reference-backed:
- full navigation.
- yellow appearance about 1/1.4.
- each yellow adds one GG stock.
- miss or blue ends Z-GAME.
- on end, route to GG with accumulated stock.

## Gaia stage

Reference-backed:
- separate from front mode.
- reached via Gaia-bell progression.
- role-dependent GG draw rates are published.
- SP is 100% GG during Gaia stage.
- setting-1 Gaia-stage GG -> Z-ZONE rate is published as 15.0%; source notes setting dependence may exist.

Therefore:
- a universal flat Gaia->Z 15% across all settings is **not** authentic unless explicitly chosen as PIRI_SPECIFIC.
- Phase 03 must use a sourced setting table if available, otherwise a labelled Piri table.

## Loop stock

Reference-backed classes:
- A = 1%
- B = 25%
- C = 50%
- D = 80%

Reference-backed processing:
- each game/loop check references the held loop probability to create another GG stock; on failure that loop ends.
- 天国 / 超天国 GG hits receive stronger loop-class distributions.
- 裏天国 GG hit does not use the ordinary loop-stock grant.
- zero-yellow Z-ZONE special can award D.
- SP can award D.

Piri locked:
- GOD uses D.

## Transition precedence

Piri v1 authoritative precedence for simultaneously eligible top-level premium flags:

1. GOD
2. RED7
3. SP
4. ordinary role/state draw

Classification: PIRI_SPECIFIC deterministic arbitration needed because Piri draws GOD independently at 1/8192.

This precedence must be explicit in tests and recovery; no second premium draw may replace a resolved premium role.

## Settlement ordering

One game must follow:

1. authoritative role draw
2. navigation requirement determination
3. reel stop completion
4. role payout/replay settlement
5. role/state effect
6. state transition / stock / loop update
7. persistence
8. public/client update

Payout may never be calculated from a separate random path after the role has been resolved.

## Recovery

A pending resolved role survives restart/reconnect.

Recovery must not:
- redraw the role
- change payout
- change navigation order
- change visible target formation
- re-trigger a one-shot forced role

Recovery completes the same game outcome or restores the exact remaining stop state.

## Sources

- Modes: https://1geki.jp/slot/l_milliongod_kiseki/43/
- Modes/history: https://nana-press.com/kaiseki/machine/1112/35719/
- History: https://nana-press.com/kaiseki/machine/1112/35720/
- GG: https://1geki.jp/slot/l_milliongod_kiseki/81/
- Premium roles: https://1geki.jp/slot/l_milliongod_kiseki/5/
- PGG: https://nana-press.com/kaiseki/machine/1112/35728/
- SP: https://nana-press.com/kaiseki/machine/1112/37071/
- SGG: https://nana-press.com/kaiseki/machine/1112/35727/
- Z-ZONE: https://nana-press.com/kaiseki/machine/1112/35725/
- Z-GAME: https://nana-press.com/kaiseki/machine/1112/35726/
- Loop stock: https://nana-press.com/kaiseki/machine/1112/36532/
- Reset: https://nana-press.com/kaiseki/machine/1112/35716/


## User acceptance status

Accepted during Phase 01 review:
- GOD probability: exact independent 1/8192 across settings/states.
- GOD benefit: current Piri design retained — GOD stage 50G + 3 GG sets (4 total) + D loop.
- GG basic performance: 50G/set, about +7 medals/G.
- G-ZONE: max 5G after GG; continue if stock exists, otherwise return to normal.
- RED7 / SGG flow: RED7 1/6900 -> SGG; SGG set 10–100G with 75%+ continuation concept; 3G comeback section between sets; after SGG fully ends, return to GG.

- SP role behavior accepted: use the source-backed state-specific behavior (normal/GG 50% GG stock + D loop; Gaia stage GG 100%; SGG prep/SGG continuation stock 3; SGG comeback normal-set continuation 100% with 50G/100G 1:1; 5th-multiple comeback 100G).

- Z-ZONE / Z-GAME behavior accepted: 5G+alpha base; yellow about 1/1.4; yellow holds countdown; five yellow results succeeds into GG stock + Z-GAME; rising/middle yellow direct-success behavior retained; zero-yellow failure has 50% loop-D; Z-GAME adds one GG stock per yellow and ends on miss/blue into GG.

- Normal front-mode structure accepted: LOW_A / LOW_B / NORMAL / HEAVEN_PREP / HEAVEN / SUPER_HEAVEN plus separate 裏天国 axis; use source-backed structure, and treat unpublished fine-grained transition probabilities as Piri calibration values rather than authentic Kiseki data.

- Ceiling/reset accepted: normal ceiling 1480G; reset ceiling distribution 510G 15.2%, 1000G 20.3%, 1480G 64.5%.

- Ordinary GG yellow/bell streak V-stock mechanic rejected and remains disabled; yellow-related rewards remain in their separate Z-ZONE/Z-GAME and other source-backed routes.

- Normal-role processing accepted: role occurrence and GG-hit processing are separate. After a role is drawn, evaluate GG hit from current front mode × role, then apply any role-history and mode-transition processing according to the locked tables. Role probabilities themselves are not to be distorted merely to emulate GG hit rates.

- GG-hit announcement flow accepted: normal/history/mode-based GG hits are held internally and enter a source-backed precursor period rather than transitioning to GG immediately. GOD and RED7 are exceptions and enter PGG/SGG without normal precursor handling. Precursor-game distributions must follow published Kiseki data where available; they are not to be collapsed into immediate GG.

- Precursor-state processing accepted: ordinary role drawing continues during precursor games; existing pending GG entitlement is preserved. GOD/RED7/SP and other stronger state effects may resolve during precursor without deleting the previously earned GG entitlement; premium handling may temporarily take precedence, but queued entitlement must remain durable.

- Conditional Piri rule accepted for precursor re-hit handling: if a new ordinary GG-winning trigger occurs while a GG entitlement is already pending in precursor, retain it as +1 queued GG stock rather than discarding it. This rule is PIRI_SPECIFIC and remains conditional on Phase 04 economy simulation confirming the locked payout targets and fun-preservation guardrails; if it breaks those constraints, reopen this rule instead of forcing other mechanics to become dull.

- Role-history draw policy accepted: use published yellow-history hit rates as-is; use published blue-history hit rates where available; Gaia bell acts as the published white-7 substitute for both histories. If settings 3–6 blue-history rates remain unpublished, treat only those missing values as PIRI_SPECIFIC economy-tuning parameters. Published white-7 history special behavior, including loop-D reward on qualifying history-hit conditions, is retained.

- Mode-transition policy accepted: use published front/normal-mode transition rates as source-backed values where available. Any genuinely unpublished transition cells are PIRI_SPECIFIC tuning parameters only, and Phase 04 must not flatten or freeze mode movement merely to force target payout; fun-preservation guardrails remain binding.

- Gaia-stage GG processing accepted: while in Gaia stage, use the published Gaia-specific role-by-role GG hit table instead of layering the ordinary front-mode GG draw on top. Use the published Gaia-specific history draw table as well. These source-backed Gaia tables are not to be replaced by the old flat Piri probability.

- Setting-difference placement policy accepted: keep role occurrence probabilities setting-common unless a specific source-backed exception exists. Express setting differences primarily through GG initial-hit processing, history-hit tables, front/normal-mode behavior, Z-ZONE promotion/selection, loop/continuation-related tables, and other published setting-sensitive state mechanics. Preserve published values as-is; only genuinely unpublished cells may be PIRI_SPECIFIC Phase-04 tuning parameters.

- Setting-character policy accepted: preserve the published machine-level tendency that even settings lean relatively toward lighter GG initial hits while odd settings lean relatively toward stronger Z-ZONE/loop-side behavior. Do not flatten this distinction during Phase 04 payout fitting; use published setting-specific loop/Z tables where available and mark only unpublished cells as PIRI_SPECIFIC.

- Loop/Z setting-character refinement accepted: where published, setting-specific GG-loop and Z-ZONE selection/promotion tables are authoritative; preserve the machine's odd/even character rather than treating higher setting as uniformly stronger in every sub-system. Unpublished cells remain PIRI_SPECIFIC Phase-04 tuning values only.

- GG-internal stock-draw policy accepted: during GG, keep the published six front-mode structure and resolve additional GG stock draws from the current front mode plus the established role. Do not invent a separate opaque GG-only stock probability when source-backed mode/role tables exist; only genuinely unpublished cells may be PIRI_SPECIFIC.

- GG-preparation stock-draw policy accepted: model GG preparation as an independent gameplay state from initial GG hit until the published preparation-end cue (e.g. ×・?・?) resolves. During that state, use the published preparation-specific role × front-mode stock-draw table; do not reuse the in-GG 50G stock table. After preparation ends, begin the 50G GG set.

- G-ZONE processing accepted: G-ZONE is primarily an existing-stock/continuation announcement state (max 5G), not a reuse of the ordinary in-GG stock table. When stock is already held, use the published role-based Z-ZONE promotion table during G-ZONE. If stock is not announced within G-ZONE, allow the published-style latent continuation behavior into normal play rather than forcing immediate failure at game 5. Premium roles retain their own role-specific handling.

- G-ZONE/latent continuation timing accepted: use the published G-ZONE stock-announcement timing tables, including Zeus-mode-dependent distributions where source-backed. If a held stock is not announced during the 5G G-ZONE, transition to normal-stage latent continuation and use the published 1–31G post-G-ZONE latent announcement distribution rather than treating the 5G boundary as failure.

- Zeus-mode policy accepted: use the published Zeus-mode internal behavior as source-backed, including super-high front-mode behavior, stock-preserving GG loop semantics, continuation when stock is acquired, and the published 50% continuation behavior when no new stock is acquired. Use Zeus-specific G-ZONE announcement distributions where published. Zeus-mode entry probability itself remains unpublished and therefore PIRI_SPECIFIC for Phase-04 fitting; it must not be represented as an authentic hidden value.

- Advantage-section / ending policy accepted: model setting-change resets and the machine's published advantage-section/ending flow, including post-ending transition into G-ZONE and source-backed reset-state behavior. Published reset/initialization facts are authoritative. Any unpublished cut condition, post-cut stock quantity, or loop-strength distribution is PIRI_SPECIFIC for Phase-04 fitting and must not be represented as an authentic hidden machine value.

- Reset Gaia-target policy accepted: on setting change / advantage-section reset, redraw the next Gaia-stage trigger target from the published reset-specific Gaia-bell-count distribution rather than the ordinary Gaia-mode table. Treat the published reset distribution as source-backed and distinct from normal Gaia progression.

- Reset mode-initialization policy accepted: setting change / advantage-section reset must initialize both front mode and rear mode. The exact post-reset front/rear mode distribution remains unpublished; therefore those initial distribution cells are PIRI_SPECIFIC Phase-04 tuning values, used to fit morning behavior, initial-hit frequency, and payout without being represented as authentic hidden machine values.
