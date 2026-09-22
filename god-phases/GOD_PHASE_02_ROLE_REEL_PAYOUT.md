# GOD PHASE 02 — Role / Reel / Payout Implementation

Status: **IN_PROGRESS — AUTOMATED GATES PASS; RUNTIME EVIDENCE PENDING**

Implement only the locked role contract from `docs/GOD_MASTER_SPEC.md` and `docs/GOD_ROLE_CONTRACT_V1.md`.

## Scope
- internal role -> payout/replay
- internal role -> visible formation
- navigation/order enforcement
- physical slip constraints
- Paper/Fabric stop agreement
- forced-role test command verification

No gameplay-state or economy redesign in this phase.

## Phase-02 audit state

The previous freeze was reopened on 2026-09-22 after a source audit found one real Phase-02 mismatch: normal/no-nav ORDERED_YELLOW7 could credit 1 medal while displaying a generic MISS visual family. That mismatch is now resolved with a dedicated `ORDERED_YELLOW7_ONE` presentation. The mapping is explicitly **PIRI_SPECIFIC** and is not claimed as exact Kiseki hidden control.

See `docs/GOD_PHASE_02_SOURCE_AUDIT_2026-09-22.md`.

A complete hidden real-machine press-index control table is not public. Where that table is unavailable, the explicitly labelled Piri control policy below is the final Phase-02 implementation rule rather than a placeholder waiting for another guess.

## Audited role matrix

| Role | Wager result | Frozen visible result | Input/order | Slip/control |
|---|---|---|---|---|
| MISS | 0, no replay | variable safe miss window; no GOD/RED7/BLUE7/YELLOW7 straight or diagonal and no locked special/paying role form | LEFT first, then C/R free | ordinary 0..4; persisted per-spin presentation selector may vary among safe candidates |
| UPPER_BLUE7 | replay | BLUE7 upper-row straight | LEFT first | ordinary 0..4 |
| MIDDLE_BLUE7 | replay | BLUE7 middle-row straight | LEFT first | ordinary 0..4 |
| ORDERED_YELLOW7 — normal/no nav | 0 or 1 medal under provisional calibration | 0-medal branch uses safe miss/こぼし; 1-medal branch uses dedicated `ORDERED_YELLOW7_ONE` marker: LEFT BOTTOM=BLUE7 / CENTER MIDDLE=YELLOW7 / RIGHT TOP=BLUE7. This exact marker is **PIRI_SPECIFIC** and not claimed as authentic Kiseki control | LEFT first | ordinary 0..4 Piri control; exact 0/1 split remains Phase-04 calibration |
| ORDERED_YELLOW7 — AT/nav | 15 medals | lower-row yellow A acquisition form | exact displayed order | ordinary 0..4; wrong instructed input rejected without consuming/redrawing role |
| LOWER_YELLOW7 | 3 medals | lower-row yellow B; source example has center-middle RED7 | LEFT first | ordinary 0..4 |
| RISING_YELLOW7 | 15 medals | rising/right-up YELLOW7 straight | LEFT first | ordinary 0..4 |
| MIDDLE_YELLOW7 | 15 medals | YELLOW7 middle-row straight | LEFT first | ordinary 0..4 |
| COMMON_YELLOW7 | 15 medals | lower-row yellow A; source example has center-middle BLUE7 | LEFT first unless state nav explicitly applies | ordinary 0..4 |
| GAIA_BELL | 1 medal | YELLOW7 small-V | RIGHT first is source-confirmed; second/third order is not published | Piri currently enforces RIGHT first, then permits either remaining reel; ordinary 0..4 |
| RED7_FAKE | replay | current-machine representative example is middle RED7 / RED7 / miss; source explicitly says the stop form changes when aiming near DEKA-MILLION. Prefer the representative form when reachable; otherwise use a RED7-visible safe replay fallback that cannot equal RED7 straight, SP, GOD, a BLUE7 replay line, or a paying yellow result | LEFT first | ordinary 0..4; persisted per-spin presentation selector may vary among legal fake-RED forms |
| RED7 | 15 medals | RED7 straight | LEFT first | premium visible-form exception: deterministic >4 slip allowed only when needed |
| GOD | 15 medals | GOD straight | LEFT first | premium visible-form exception: deterministic >4 slip allowed only when needed |
| SP | 15 medals | middle RED7 / RED7 / GOD | LEFT first | premium visible-form exception: deterministic >4 slip allowed only when needed |

## Source status that must not be silently changed

- 1geki current-machine stop page: representative BLUE7/YELLOW7/Gaia/SP/RED7/GOD forms; all displayed forms are examples, not a complete control table.
- NanaPress current-machine stop page/summary: lower-yellow A/B distinction and representative formations.
- パチ＆スロ必勝本 current-machine flag chart: RED7_FAKE is a replay/blue-family flag, representative middle RED7/RED7/miss, and its stop form changes when aiming near DEKA-MILLION.
- P-WORLD / 1geki / NanaPress / 必勝本: locked current-machine role denominators and payout/replay identities used by the role contract.

## Presentation selector rule

For variable MISS and RED7_FAKE presentation only:
- one selector is created at lever-on and persisted with the pending game;
- repeated identical press timing may resolve to different legal visible results across different games;
- within one game, server hints, accepted stop packets, recovery, and Fabric final rendering must all resolve from the same selector;
- selector choice cannot change internal role, payout, replay, navigation, stock/benefit, or any gameplay-state transition;
- no reroll occurs after a reel has stopped.

For fixed semantic forms, the selector is irrelevant.

## Exit gate

Phase 02 remains incomplete until:
1. normal ORDERED_YELLOW7 1-medal visual mapping is explicitly resolved and implemented — **DONE (`ORDERED_YELLOW7_ONE`, PIRI_SPECIFIC)**,
2. unit/exhaustive tests pass for every resolved row — **DONE on current CI baseline**,
3. Paper/Fabric final visible symbols agree — **automated server-authoritative stop-index coverage added; final real-client evidence still required**,
4. recovery settles to the same resolved result family — **DONE for dedicated one-medal form and covered by recovery tests**,
5. forced-role runtime evidence is collected for every row — **PENDING**.

Runtime evidence may reveal an implementation bug; it does **not** convert UNKNOWN reference behavior into an authentic specification.
