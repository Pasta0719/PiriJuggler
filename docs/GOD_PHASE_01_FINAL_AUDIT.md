# GOD Phase 01 final audit

Status: **COMPLETE — SPEC LOCK ONLY**

Date: 2026-09-21

This audit closes GOD Phase 01. No production gameplay/reel/client behavior code is changed by this audit.

## Canonical locked documents

- `docs/GOD_MASTER_SPEC.md`
- `docs/GOD_ROLE_CONTRACT_V1.md`
- `docs/GOD_STATE_MACHINE_SPEC_V1.md`
- `docs/GOD_SOURCE_REGISTRY.md`

## Exit-gate result

### Role draw
- One game resolves to exactly one mutually-exclusive role.
- GOD has exact Piri marginal probability 1/8192 across eligible settings/states.
- RED7 = 1/6900; SP = 1/65536.
- The previous independent GOD/RED7/SP booleans and `GOD > RED7 > SP` tie-breaker are rejected for final implementation.
- Rounded published denominators are not silently treated as a complete exact 100% table; residual allocation is explicitly PIRI_SPECIFIC.

### Reel / visible-result contract
- 3-medal lower-yellow B and 15-medal lower-yellow A must be visibly distinct.
- Server stop control and Fabric rendering must share one visible-row convention.
- Ordinary-role control uses the locked normal slip policy.
- GOD / RED7 / SP may exceed the ordinary 4-frame slip window by the minimum deterministic amount needed to guarantee their locked premium visible form.
- Complete hidden Kiseki press-index control remains unavailable as reference data, but this is **not** an implementation blocker because the accepted deterministic PIRI_SPECIFIC control policy is locked.

### Payout / replay
- Blue-7 replay and RED7_FAKE replay semantics are locked.
- Normal ordered-yellow hides the ordinary winning order and uses the accepted left-first miss-side 0/1-medal calibration path.
- GG/AT ordered-yellow uses navigation to realize the 15-medal path.
- Gaia-yellow/right-first navigation is retained at the published 1/37.6 role rate.

### State machine
- Ordinary GG hits use precursor handling; they do not jump directly into GG.
- GOD and RED7 bypass ordinary precursor handling and enter their premium route on the next game.
- Existing pending GG / latent continuation is never silently deleted by a later premium route.
- SP uses its state-specific published behavior; there is no generic premium priority shortcut.
- Ordinary re-hits while GG is pending become queued GG entitlement under the accepted conditional Piri rule.
- GG preparation, G-ZONE, post-G-ZONE latent continuation, Z-ZONE/Z-GAME, SGG/comeback, Gaia, Zeus, front/rear modes, reset/ceiling behavior and advantage-section/ending policy are classified and locked at semantic level.

### Economy boundaries
- Payout targets: 97.2 / 99.1 / 102.1 / 106.9 / 111.7 / 114.6%.
- Fun-preservation guardrails remain binding.
- Source-backed values are not to be distorted just to fit payout.
- Unpublished cells are labelled PIRI_SPECIFIC and are Phase-04 tuning parameters.
- If the accepted precursor re-hit rule prevents fitting without making the game dull, reopen that Piri-specific rule rather than damaging locked source-backed mechanics.

## Remaining UNKNOWN reference data

The following may remain UNKNOWN as **reference-machine internals** without blocking implementation:
- complete real-machine press-index control table
- exact hidden slip behavior at every press index
- genuinely unpublished transition/calibration cells explicitly assigned to Phase 04

These are not allowed to be presented as authentic Kiseki values.

## Phase-02 readiness

Phase 02 can generate role/reel/payout tests directly from the locked semantic contract without guessing gameplay semantics.

Phase 01 ends here. Do not begin Phase 02 implementation in the same phase-close operation.
