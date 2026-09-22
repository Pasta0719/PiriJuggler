# GOD Phase 02 source audit — 2026-09-22

Status: **AUDIT COMPLETE — IMPLEMENTATION CORRECTIONS REQUIRED**

Purpose: re-audit the Phase-02 role / reel / payout contract from current-machine public sources, independent of current code. Existing code is not evidence.

## Source set used

Tier A/B current-machine sources:
- 1geki: https://1geki.jp/slot/l_milliongod_kiseki/2/
- NanaPress: https://nana-press.com/kaiseki/machine/1112/34745/
- NanaPress summary: https://nana-press.com/kaiseki/machine/1112/
- P-WORLD: https://www.p-world.co.jp/machine/database/10424
- パチ＆スロ必勝本: https://p.hisshobon.jp/vpage/2761/3

Important source limitation shared by 1geki / NanaPress / 必勝本:
- published stop forms are examples, not a complete press-index control table.

## Audit classification

- REFERENCE_CONFIRMED: directly supported by current-machine source.
- PIRI_DECISION: intentional implementation rule used where source is incomplete.
- UNKNOWN_REFERENCE: public source does not resolve the real-machine detail.
- IMPLEMENTATION_MISMATCH: current code conflicts with the audited contract or presents unsupported behavior as if reference-authentic.

## Role-by-role audit

| Role | Odds | Payout / replay | Source-confirmed visible behavior | Source-confirmed order | Unknown / Piri-only area | Current implementation audit |
|---|---:|---|---|---|---|---|
| MISS | 1/5.2 reference | 0, no replay | ordinary miss exists; no complete miss-stop catalogue is published | no nav: LEFT first; normal play can continue sequential/hassami after LEFT | exact miss catalogue, exact slip/control, number of stop forms | **PIRI_DECISION**: safe-miss catalogue. 339 visible forms is a Piri implementation count, not a real-machine count |
| UPPER_BLUE7 / normal replay | 1/7.9 | replay | upper-row BLUE7 straight is a published example | no nav: LEFT first | exact press-index control; complete alternative forms | fixed semantic upper-line form is acceptable as **PIRI_DECISION based on reference example** |
| MIDDLE_BLUE7 | 1/109.2 | replay | middle-row BLUE7 straight is a published example | no nav: LEFT first | exact press-index control; complete alternative forms | fixed semantic middle-line form is acceptable as **PIRI_DECISION based on reference example** |
| ORDERED_YELLOW7 | 1/1.7 | 15 medals when acquired; 必勝本 confirms some ordered-15-yellow outcomes can instead produce a 1-medal role | lower-yellow A is the published 15-medal acquisition example; 必勝本 also publishes multiple 1-medal role examples | nav present: obey nav. no-nav normal play: LEFT first | exact winning-order distribution; exact wrong-order 0/1 mapping; which 1-medal form maps to each wrong order | **IMPLEMENTATION_MISMATCH**: current normal-path 1-medal settlement displays an arbitrary MISS-family result. A 1-medal payout must not be visually presented as an arbitrary 0-medal miss. Exact mapping remains UNKNOWN_REFERENCE and needs an explicit Piri decision before finalization |
| LOWER_YELLOW7 / lower B | 1/18.4 | 3 medals | lower-row YELLOW7; center-reel yellow is the one below RED7; NanaPress example has center-middle RED7 | no nav: LEFT first | exact press-index control; all alternate forms | semantic form is REFERENCE_CONFIRMED; exact control is PIRI_DECISION |
| RISING_YELLOW7 | 1/186.2 | 15 medals | rising/right-up YELLOW7 straight example | no nav: LEFT first | exact press-index control; all alternate forms | semantic form is REFERENCE_CONFIRMED; exact control is PIRI_DECISION |
| MIDDLE_YELLOW7 | 1/963.8 | 15 medals | middle YELLOW7 straight example | no nav: LEFT first | exact press-index control; all alternate forms | semantic form is REFERENCE_CONFIRMED; exact control is PIRI_DECISION |
| COMMON_YELLOW7 / lower A | 1/1524.1 | 15 medals | lower-row YELLOW7 A; NanaPress example has center-middle BLUE7 | no nav: LEFT first unless another state explicitly navigates | exact press-index control; all alternate forms | semantic form is REFERENCE_CONFIRMED; exact control is PIRI_DECISION |
| GAIA_BELL | 1/37.6 | 1 medal | YELLOW7 small-V example | **RIGHT first is source-confirmed** | public source does **not** establish a fixed second/third order | current engine is actually correct here: it enforces RIGHT first only, then allows either remaining reel. Any claim that real machine is fixed RIGHT→LEFT→CENTER is false |
| RED7_FAKE | 1/936.2 | replay | 必勝本 example shows middle RED7 / RED7 / miss; source explicitly says aiming near DEKA-MILLION changes the stop form | no nav: LEFT first | complete alternate-form set; exact press-index mapping; real-machine count of patterns | **PIRI_DECISION**: current broad safe fake-RED family. The implementation count (e.g. 99 reachable visible patterns under current controller) is not a reference-machine count and must never be presented as one |
| RED7 | 1/6900 | 15 medals | RED7 straight example; RED7 routes to SGG outside Phase 02 | no nav: LEFT first | exact press-index control; whether representative straight is forced from every press position | Piri long-slip guarantee is **PIRI_DECISION**, not authentic hidden control |
| GOD | reference 1/16384; Piri override 1/8192 | 15 medals | GOD straight example | no nav: LEFT first | exact press-index control; whether representative straight is forced from every press position | 1/8192 and long-slip guarantee are **PIRI_DECISION** |
| SP | 1/65536 | 15 medals | middle RED7 / RED7 / GOD example | no nav: LEFT first | exact press-index control; whether example is forced from every press position | Piri long-slip guarantee is **PIRI_DECISION** |

## Input-order audit

### No navigation
Current-machine sources support:
- LEFT first is the safe/default play.
- P-WORLD explicitly allows sequential or hassami play, so after LEFT, CENTER/RIGHT need not be fixed to one order.

Therefore Piri rule:
- first accepted reel = LEFT
- after LEFT = CENTER or RIGHT
is compatible with the public play guidance.

### Generic navigation
Current-machine sources support:
- when navigation appears, follow it.

They do **not** publish:
- the full hidden order-distribution table for every ordered-yellow flag.

Therefore the production choice to generate one of six complete orders is **PIRI_DECISION** unless a current-machine source for the distribution is added.

### GAIA_BELL
Current-machine sources support only:
- RIGHT is first.

They do not support:
- RIGHT→LEFT→CENTER as a real-machine fixed three-reel order.

Current code uses RIGHT first and then leaves the remaining two free, so **no code change is required for this item**. The earlier statement that RIGHT→LEFT→CENTER was fixed was not supported.

## Stop-control audit

Public sources explicitly say stop forms are examples. They do not provide a complete 20×press-position hidden control table.

Therefore all of the following are Piri implementation policy, not authentic reference-machine facts:
- ordinary 0..4-frame search algorithm
- nearest-target selection
- per-spin presentation selector
- MISS safe-candidate universe
- RED7_FAKE safe fallback universe
- premium >4-frame forced-form exception
- visible-pattern counts derived from the current Piri reel/control implementation

These are permitted only if clearly labelled PIRI_DECISION.

## Confirmed implementation problem

### ORDERED_YELLOW7 normal/no-nav

Current behavior:
- internal ORDERED_YELLOW7
- normal/no-nav
- provisional 0 or 1 medal
- visible role forced to MISS

Problem:
- current-machine 必勝本 explicitly states that multiple 1-medal roles exist and that some occur from ordered 15-medal yellow.
- therefore a game that actually credits 1 medal should not be represented by an arbitrary generic 0-medal MISS visual family.

What remains unknown:
- exact wrong-order mapping
- exact ratio of 0-medal miss/こぼし vs 1-medal role
- exact 1-medal form selected by each hidden ordered-yellow condition

Required resolution:
- do not guess the real mapping.
- before Phase 02 can be frozen again, choose an explicit labelled Piri mapping for normal ORDERED_YELLOW7:
  - 0-medal branch -> safe miss/こぼし family
  - 1-medal branch -> one of the source-published 1-medal visible families
- keep the exact branch probability as Phase-04 calibration unless a source publishes it.

## Items that are NOT implementation bugs after this audit

- GAIA_BELL engine logic: RIGHT first only, then either remaining reel — compatible with known source.
- UPPER/MIDDLE blue replay semantic forms.
- lower-yellow A/B distinction.
- rising / middle yellow semantic forms.
- GOD / RED7 / SP semantic premium forms, provided forced long-slip is labelled Piri-specific.
- RED7_FAKE replay identity and variable-form concept, provided its Piri pattern count is not claimed as authentic.

## Phase-02 audit verdict

Phase 02 must **not** be called fully frozen yet.

Blocking item:
1. normal ORDERED_YELLOW7 1-medal visual mapping is unresolved / incorrectly represented in current code.

Non-blocking UNKNOWN_REFERENCE items that may remain Piri-specific:
- exact miss control
- exact fake-RED control/count
- exact ordered-yellow order distribution
- exact Gaia second/third order
- exact press-index control for all representative forms

Once the ordered-yellow normal visual mapping is explicitly chosen and implemented/tested, Phase 02 can be frozen again without pretending the remaining hidden control details are reference-authentic.
