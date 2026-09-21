# Piri GOD master specification

Status: **LOCKED — GOD Phase 01**
Authority: canonical GOD specification document.
Implementation must not be changed to "make this document true" until the relevant item is explicitly LOCKED.

## 1. Classification

Every gameplay statement must have exactly one classification:

- **LOCKED_PIRI** — intentional Piri rule, fixed unless user changes it.
- **REFERENCE_BACKED** — documented reference-machine behavior/value with source support.
- **PIRI_SPECIFIC** — implementation-owned behavior used where exact reference data is unavailable.
- **UNKNOWN** — insufficient evidence. Do not invent.
- **REJECTED** — explicitly excluded from Piri GOD.

Existing code is **not** a source classification.

---

## 2. Locked Piri invariants

These are already selected and remain authoritative.

| Item | Specification | Class |
|---|---|---|
| GOD probability | exact per-game marginal 1/8192 inside the mutually-exclusive whole-game role draw | LOCKED_PIRI |
| GOD setting dependence | none; settings 1–6 identical for the GOD flag | LOCKED_PIRI |
| GOD state dependence | none; 1/8192 marginal probability is invariant across eligible states | LOCKED_PIRI |
| GOD reward | GOD stage 50G + 3 additional GG sets; total 4 GG-equivalent sets, plus D-loop stock | LOCKED_PIRI |
| RED7 probability | 1/6900 | LOCKED_PIRI |
| SP probability | 1/65536 | LOCKED_PIRI |
| GG length | 50G per set | LOCKED_PIRI |
| GG pure increase target | approximately +7 medals/G | LOCKED_PIRI |
| G-ZONE | max 5G | LOCKED_PIRI |
| loop A/B/C/D | 1% / 25% / 50% / 80% | LOCKED_PIRI |
| normal ceiling | 1480G | LOCKED_PIRI |
| reset ceiling | 510G 15.2%, 1000G 20.3%, 1480G 64.5% | LOCKED_PIRI |
| ordinary GG bell-chain V stock | disabled | REJECTED |
| payout targets settings 1–6 | 97.2 / 99.1 / 102.1 / 106.9 / 111.7 / 114.6% | LOCKED_PIRI |
| payout fitting | only explicitly PIRI_SPECIFIC / unpublished calibration parameters may move; source-backed and LOCKED_PIRI mechanics stay fixed unless the spec is explicitly reopened | LOCKED_PIRI |

Source baseline: `docs/GOD_PRODUCTION_SPEC_V1.md`, `docs/GOD_PRODUCTION_TARGET.md`.

---

## 3. Reference role baseline

Published-reference denominators currently recorded in the repository:

| Internal role | Reference denominator | Current spec status |
|---|---:|---|
| MISS | 1/5.2 | REFERENCE_BACKED |
| UPPER_BLUE7 | 1/7.9 | REFERENCE_BACKED |
| MIDDLE_BLUE7 | 1/109.2 | REFERENCE_BACKED |
| ORDERED_YELLOW7 | 1/1.7 | REFERENCE_BACKED |
| LOWER_YELLOW7 | 1/18.4 | REFERENCE_BACKED |
| RISING_YELLOW7 | 1/186.2 | REFERENCE_BACKED |
| MIDDLE_YELLOW7 | 1/963.8 | REFERENCE_BACKED |
| COMMON_YELLOW7 | 1/1524.1 | REFERENCE_BACKED |
| GAIA_BELL | 1/37.6 | REFERENCE_BACKED |
| RED7_FAKE | 1/936.2 | REFERENCE_BACKED |
| RED7 | 1/6900 | LOCKED_PIRI |
| GOD | reference 1/16384; Piri exact 1/8192 | LOCKED_PIRI |
| SP | 1/65536 | LOCKED_PIRI |

Important: rounded denominators are not automatically a complete categorical distribution. A production draw algorithm must be separately specified and tested.

Source baseline: `docs/GOD_ROLE_MODE_BASELINE.md`.

---

## 4. Role outcome contract — LOCKED for implementation

For every internal role the locked spec must define all of:

1. wager behavior
2. medal payout
3. replay yes/no
4. visible 3-reel formation
5. valid press order / navigation
6. allowed slip window
7. gameplay transition / stock effect
8. public display event
9. source/classification

The semantic role contract is complete for Phase 02. Exact hidden real-machine press-index control remains unavailable, but the accepted PIRI_SPECIFIC deterministic stop-control policy resolves that gap without claiming authenticity.

### Locked semantic outcome knowledge

| Role | Payout/replay | Visible formation | Navigation | Status |
|---|---|---|---|---|
| UPPER_BLUE7 | replay / 0 medal payout | upper blue-7 line | left-first | REFERENCE_BACKED |
| MIDDLE_BLUE7 | replay / 0 | middle blue-7 line | left-first | REFERENCE_BACKED |
| RED7_FAKE | replay / 0 | deterministic fake-RED form that cannot equal RED7 straight, SP, GOD, or a paying yellow result | left-first | REFERENCE_BACKED semantics / PIRI_SPECIFIC presentation |
| LOWER_YELLOW7 | 3 medals | lower-row yellow B; center reel uses the yellow directly below RED7, published example center-middle RED7 | left-first | REFERENCE_BACKED |
| ORDERED_YELLOW7 | 15 medals when correctly navigated in AT; normal-play miss-side settlement uses the locked 0/1-medal calibration path | lower-yellow A / navigated yellow result | state navigation; normal play hides ordinary winning order | REFERENCE_BACKED role concept / PIRI_SPECIFIC normal miss-side calibration |
| RISING_YELLOW7 | 15 medals | rising/right-up yellow line | left-first | REFERENCE_BACKED |
| MIDDLE_YELLOW7 | 15 medals | middle yellow line | left-first | REFERENCE_BACKED |
| COMMON_YELLOW7 | 15 medals | lower-row yellow A; published example center-middle BLUE7 | left-first unless state nav applies | REFERENCE_BACKED |
| GAIA_BELL | 1 medal | yellow small-V | right-first navigation | REFERENCE_BACKED |
| RED7 | 15 medals | RED7 straight | left-first | REFERENCE_BACKED visible form/benefit; Piri may exceed ordinary 4-frame slip to guarantee form |
| GOD | 15 medals | GOD straight | left-first | LOCKED_PIRI odds / REFERENCE_BACKED visible form; Piri may exceed ordinary 4-frame slip |
| SP | 15 medals | RED7 / RED7 / GOD | left-first | REFERENCE_BACKED visible form/behavior; Piri may exceed ordinary 4-frame slip |
| MISS | 0 | deterministic non-winning form that matches no locked winning/replay/premium result | left-first | PIRI_SPECIFIC presentation |

### Resolved specification: 3-medal vs 15-medal lower yellow

The source-backed semantic distinction is now locked:

- 15-medal lower yellow A: lower-row yellow line; NanaPress example identifies center-middle BLUE7.
- 3-medal lower yellow B: lower-row yellow line using the center yellow immediately below RED7; NanaPress example identifies center-middle RED7.

The development build that displayed these as visually identical is therefore confirmed wrong and is not a specification source.

What remains UNKNOWN is the complete real-machine press-index control table, not the semantic stop-form distinction.

See `docs/GOD_ROLE_CONTRACT_V1.md`.

---

## 5. Reel strip and stop control

### Known

- physical/reference reel count recorded in project: 20 stops per reel.
- symbols currently represented: GOD, RED7, BLUE7, YELLOW7, MILLION, two-cell DEKA MILLION.
- ordinary-role physical slip target: maximum 4 frames. GOD / RED7 / SP are an explicit Piri exception: when needed to guarantee their locked visible premium forms, deterministic slip beyond 4 frames is allowed.

### Locked semantic convention

- TOP/MIDDLE/BOTTOM in the spec refer to the **visible window rows**, not array arithmetic.
- lower-yellow A/B forms are semantically locked in `docs/GOD_ROLE_CONTRACT_V1.md`.
- implementation tests must assert visible rows first; internal array offsets are an implementation detail derived afterward.

### Still not publicly known

- complete press-index-specific control table for all roles.
- exact slip/control behavior for every press position.
- whether every representative premium form is reachable from every press index under the real machine's control.

These unknown reference-machine details must not be filled by claiming authenticity. Phase 02 will use the already accepted labelled PIRI_SPECIFIC deterministic control policy: ordinary roles use the locked normal slip policy, while GOD/RED7/SP may exceed 4 frames by the minimum deterministic amount required to guarantee their locked visible forms.

Rule: representative screenshots/forms must not be promoted into a full press-specific control table without evidence.

---

## 6. Gameplay phases

Persisted gameplay model:

Primary phases:
- NORMAL
- GG_PREP
- GG
- G_ZONE
- SGG
- SGG_COMEBACK
- Z_ZONE
- Z_GAME

Durable overlays/flags:
- ordinary-GG precursor countdown / pending GG queue
- post-G-ZONE latent-continuation countdown
- Gaia-stage state
- Zeus-mode state
- front mode and rear mode

Precursor and latent-continuation presentation do not erase the underlying GG entitlement. Exact transition probabilities remain source-backed or explicitly PIRI_SPECIFIC as classified in the state contract.

### Locked / accepted mechanics

- GG = 50G.
- G-ZONE = up to 5G.
- RED7 routes to SGG.
- SGG set length uses 10–100G concept and >=75% continuation reference.
- Z-ZONE base = 5G.
- Z-ZONE yellow appearance target around 1/1.4.
- five consecutive yellow appearances -> GG award + Z-GAME.
- Z-GAME is a GG-stock acquisition route.

### Piri-specific / requires explicit label

- Z-ZONE zero-yellow failure -> 50% D-loop is REFERENCE_BACKED.
- Gaia-stage GG -> Z-ZONE promotion has published data and setting dependence; a flat 15% for all settings must not be called authentic. 1geki reports 15.0% for setting 1 and notes likely setting dependence.
- any normal mode-transition probability not in a cited published table is PIRI_SPECIFIC/calibration.

---

## 7. Front modes / history

Reference front modes currently represented:

- LOW_A
- LOW_B
- NORMAL
- HEAVEN_PREP
- HEAVEN
- SUPER_HEAVEN

Mode existence is REFERENCE_BACKED.

Published transition/history/setting tables are authoritative where available. Any genuinely unpublished cells are already classified as PIRI_SPECIFIC Phase-04 calibration parameters in the state contract and therefore do not block Phase 02/03 implementation. Existing code values are not automatically accepted spec.

---

## 8. Economy

Locked target payouts:

| Setting | Target |
|---:|---:|
| 1 | 97.2% |
| 2 | 99.1% |
| 3 | 102.1% |
| 4 | 106.9% |
| 5 | 111.7% |
| 6 | 114.6% |

Economy must be validated by long-run simulation after role/state behavior is correct.

Forbidden fitting methods:

- changing GOD below/above locked 1/8192
- weakening locked GOD reward
- weakening loop classes
- reducing GG 50G structure merely to hit payout
- reintroducing ordinary GG bell-chain V stock
- cosmetic/random payout independent of resolved role

---

## 8A. Fun-preservation guardrails

User acceptance condition for payout fitting:

**The target payout curve may not be achieved by tuning the machine to a noticeably boring level.**

This means Phase 04 must preserve gameplay frequency/variety first, and only use economy knobs that do not materially flatten the experience.

Mandatory guardrails:

- Do not reduce GOD, RED7, SP, Z-ZONE, Z-GAME, SGG, loop behavior, or GG length/pure-increase below their locked values.
- Do not remove or heavily suppress source-backed mode movement, history draws, Gaia routes, rare-role hits, or visible chance routes merely to hit payout.
- Do not make low settings achieve target payout mainly by long stretches of near-zero event frequency.
- Prefer distributing required adjustment across normal-side hit rates/mode transitions/history probabilities rather than crushing one visible mechanic.
- Preserve meaningful setting differences without making lower settings feel dead and higher settings feel like a different game.
- Before accepting a fitted curve, Phase 04 must publish event-frequency comparisons by setting (GG initial hit, Z entry, SGG entry, premium occurrence, average interval between meaningful events) alongside payout.
- If a target payout can only be reached by violating these guardrails, stop and reopen the design instead of forcing the numbers.

Classification: LOCKED_PIRI design constraint.

## 9. Source hierarchy

When resolving a disputed spec item:

1. manufacturer / official material when available
2. major pachislot analysis sources already used by the project (1geki, NanaPress, DMM, P-WORLD where applicable)
3. cross-source agreement
4. if unavailable: UNKNOWN
5. only when intentionally designed: PIRI_SPECIFIC

A blog-only claim must not silently become REFERENCE_BACKED.

---

## 10. Implementation authority

After GOD Phase 01 becomes LOCKED:

- tests are written from this spec.
- production code is implemented to satisfy those tests/spec.
- runtime behavior is compared to this spec.
- if runtime differs, implementation is fixed.
- this spec is changed only by explicit design/source decision, never just because current code behaves differently.

---

## 11. Phase 01 exit criteria

GOD Phase 01 may be marked COMPLETE only when:

- every role has a complete outcome contract.
- 3-medal and 15-medal yellow visible distinctions are source-backed and locked.
- reel chart orientation/index convention is explicitly defined.
- push-order rules are explicitly defined.
- premium visible forms vs unreachable press positions are specified.
- all transition tables are either source-backed, PIRI_SPECIFIC, or UNKNOWN.
- no UNKNOWN item needed by Phase 02 remains.
- user has accepted the locked spec.

Until then, GOD Phase 02 is BLOCKED.


---

## 12. Source-backed corrections locked during Phase 01

Research pass dated 2026-09-21 established:

- Lower yellow A/B are visually distinguishable by the center-reel symbol identity; they are not allowed to be identical in production.
- Z-ZONE zero-yellow failure 50% -> loop D is published reference behavior.
- SP 1/65536 and normal/GG 50% GG-stock + loop-D behavior are published reference behavior.
- Gaia bell small-V is associated with right-side navigation in published stop-form guidance.
- Public stop-form pages explicitly describe forms as examples; they do not provide a complete press-index control table.
- Six front modes are source-backed and there is also a separate 裏天国 axis in the reference machine.
- Z-ZONE is full-nav, yellow ~1/1.4, yellow holds countdown, five yellow successes route to Z-GAME, rising/middle yellow can direct-success.
- Z-GAME stocks one GG per yellow and ends on miss/blue.
- SGG is 10–100G/set, >=75% continuation, followed by a 3G comeback zone.
- PGG baseline is GOD stage 50G + three further GG sets (four total) plus a strong/high-continuation loop; the exact reference loop class is unpublished. Piri intentionally fixes it to D.

See `docs/GOD_SOURCE_REGISTRY.md` for source URLs and `docs/GOD_ROLE_CONTRACT_V1.md` for the semantic role contract.


## User acceptance note

- Setting payout curve 97.2 / 99.1 / 102.1 / 106.9 / 111.7 / 114.6% accepted **with the explicit condition that fitting must not make the game boring by excessively suppressing event frequency or variety**.

- Premium visible-form priority locked: GOD, RED7, and SP may exceed the ordinary 4-frame slip window when necessary to guarantee GOD straight, RED7 straight, and RED7/RED7/GOD respectively. This is PIRI_SPECIFIC control behavior, not an authenticity claim.


## Phase-01 final arbitration lock

- One game produces exactly one mutually-exclusive role outcome. GOD/RED7/SP cannot collide as simultaneous flags.
- The obsolete `GOD > RED7 > SP` tie-breaker is rejected.
- GOD and RED7 bypass ordinary precursor announcement and enter their premium route next game.
- Existing pending ordinary GG / latent continuation survives a premium detour and remains queued.
- SP uses only its state-specific published behavior; it does not invoke a generic priority rule.
- Ordinary GG re-hits while one is pending become queued GG entitlement under the accepted conditional Piri rule.
- Premium visible forms take priority over the ordinary 4-frame slip limit; ordinary roles retain normal slip constraints.
- Complete real-machine press-index control remains UNKNOWN as reference data, but it is not an implementation blocker because the deterministic PIRI_SPECIFIC control policy is locked.
