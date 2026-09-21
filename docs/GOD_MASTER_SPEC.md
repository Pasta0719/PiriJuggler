# Piri GOD master specification

Status: **DRAFT — GOD Phase 01**
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
| GOD probability | exact independent 1/8192 | LOCKED_PIRI |
| GOD setting dependence | none; settings 1–6 identical for the GOD flag | LOCKED_PIRI |
| GOD state dependence | none; independent draw in every eligible state | LOCKED_PIRI |
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
| payout fitting | normal-side unpublished/calibration parameters only | LOCKED_PIRI |

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

## 4. Role outcome contract — MUST be completed before implementation

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

Until this table is complete, GOD Phase 02 remains blocked.

### Current provisional outcome knowledge

| Role | Payout/replay | Visible formation | Navigation | Status |
|---|---|---|---|---|
| UPPER_BLUE7 | replay / 0 medal payout | upper blue-7 line | normal | formation needs final source lock |
| MIDDLE_BLUE7 | replay / 0 | middle blue-7 line | normal | formation needs final source lock |
| RED7_FAKE | replay / 0 | exact form not yet locked | normal | UNKNOWN visible control |
| LOWER_YELLOW7 | 3 medals | **not locked yet** | normal | UNKNOWN until source+reel-chart reconciliation |
| ORDERED_YELLOW7 | 15 when navigation succeeds; miss behavior requires lock | **not locked yet** | push-order where applicable | partial |
| RISING_YELLOW7 | 15 medals | rising yellow-7 form | normal | needs final source lock |
| MIDDLE_YELLOW7 | 15 medals | middle yellow-7 line | normal | needs final source lock |
| COMMON_YELLOW7 | 15 medals | **not locked yet** | normal | UNKNOWN until source+reel-chart reconciliation |
| GAIA_BELL | 1 medal | small-V style form | right-first behavior currently intended | needs final source lock |
| RED7 | 15 medals | red-7 straight representative form | normal | reference form known; full press table UNKNOWN |
| GOD | 15 medals | GOD straight | normal | reference form known |
| SP | 15 medals | RED7 / RED7 / GOD representative form | normal | reference form known; full press table UNKNOWN |
| MISS | 0 | non-winning | normal | exact visual control UNKNOWN |

### Explicit unresolved issue: 3-medal vs 15-medal yellow

Current runtime observation from the development build:

- forced `LOWER_YELLOW7` displayed a yellow line and paid 3.
- forced `COMMON_YELLOW7` displayed the same apparent yellow line and paid 15.

This is an **implementation inconsistency**, not accepted specification.

No further reel-control implementation should be changed until the exact visible distinction is established from source-backed reel chart / stop-form information and written here as LOCKED.

---

## 5. Reel strip and stop control

### Known

- physical/reference reel count recorded in project: 20 stops per reel.
- symbols currently represented: GOD, RED7, BLUE7, YELLOW7, MILLION, two-cell DEKA MILLION.
- normal physical slip constraint currently targeted: maximum 4 frames.

### Not yet locked

- exact mapping between published reel-chart numbering and client top/middle/bottom rendering.
- exact lower-yellow A/B stop forms.
- exact press-index-specific control table for all roles.
- whether every representative premium form is reachable from every press index within four frames.

Rule: representative screenshots/forms must not be promoted into a full press-specific control table without evidence.

---

## 6. Gameplay phases

Intended persisted high-level phases:

- NORMAL
- GG
- G_ZONE
- SGG
- SGG_COMEBACK
- Z_ZONE
- Z_GAME

These phase names are accepted as the Piri state model. Exact transition probabilities must be separately classified.

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

- Z-ZONE zero-yellow failure -> 50% D-loop is PIRI_SPECIFIC.
- exact Gaia->Z rate currently used in code must not be called reference behavior unless sourced.
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

Exact production transition tables, history-hit tables and setting substitutions must be individually locked before implementation audit. Existing code values are not automatically accepted spec.

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
