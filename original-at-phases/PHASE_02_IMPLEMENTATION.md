# HYPER_DOG Phase 02 — Implementation evidence

**State: IN_PROGRESS — stop logic implemented and locally verified. Real Paper/Fabric Minecraft runtime acceptance not yet demonstrated.**
**Date: 2026-11-07 JST.** No changes to existing slot machine logic.

## Added Java sources

- `common/src/main/java/jp/pirijuggler/common/reel/HyperDogReelControl.java`: exact 21×3 physical strips, five lines, visual role evaluation, role-specific legal triplet catalogue, future-safe choice on all later stop orders/indices; 0–4 slip MISS/GRAPE/REPLAY versus 0–20 guaranteed rare/BAR/AT_ENTRY_RED7/CZ.
- `common/src/main/java/jp/pirijuggler/common/reel/HyperDogReelImageMapping.java`: dedicated seven-symbol `gui/original_at/symbols/*.png` asset namespace without altering shipped symbol resources.
- `paper/src/main/java/jp/pirijuggler/paper/reel/HyperDogRound.java`: authoritative stop history and sequence rejection; snapshot/restore; CZ outer auto stops and center manual stop; AT red-seven automatic presentation stops.
- `common/src/test/java/jp/pirijuggler/common/reel/HyperDogReelAcceptance.java`: runnable exhaustive stop-history acceptance.
- `common/src/test/java/jp/pirijuggler/common/reel/HyperDogReelControlTest.java`: JUnit wrapper, seam and red/BAR/rainbow stop checks, invalid input rejection.
- `paper/src/test/java/jp/pirijuggler/paper/reel/HyperDogRoundTest.java`: JUnit save/restore, sequence, CZ and AT-entry policies.

## Local execution evidence

- Java 21 compilation of new algorithm + its pre-existing public utility APIs (represented by matching minimal stubs) succeeded. Full project compilation is checked separately by CI.
- `java -Xmx768m jp.pirijuggler.common.reel.HyperDogReelAcceptance` output:
  - 12 role/presentation profiles PASS individually;
  - 6 stop orders × 21³ input-index triplets × 12 profiles = **666,792 legal final histories PASS**;
  - each normal role stop in 0–4 symbols; rare/bonus/display in 0–20 symbols;
  - all 12 role profiles exactly match the enumerated legal final stop counts in the approved 21-symbol reel design;
  - Paper-side round snapshot/restore/CZ/RED7 local audit PASS.
- This local test does not simulate the production Minecraft networking path or actual client display.

## Existing production safeguards

No changes to `SkillStopControl`, `FixedReels`, `StopSolver`, `ReelRound`, `MachineService`, `MachineType`, `SlotScreen`, saved machine state, JUGGLER/GOD engines, mobile services, or shipped user art. Current implementation is additive and no HYPER_DOG instance is selectable yet.

## Pending Phase 02 acceptance

The new common solver and isolated Paper-side round have **not yet been wired into the production machine dispatcher and Fabric cabinet**, because HYPER_DOG's normal-game engine and screen are separate later Phase work. Therefore the original acceptance clause demanding a **real Paper/Fabric stop-path capture** (input, all three reels, restart and end-to-end player view) has not been performed. The seven user-supplied symbol PNGs are also not in the repo. **Do not set Phase 02 to COMPLETE on this evidence alone.**

**GitHub Actions Piri build PASS** for all new Java source/tests through commit `c94e0a9bc357` (run [42371550379](https://github.com/Pasta0719/PiriJuggler/actions/runs/42371550379), conclusion `success`). The Paper-side tests also passed in run [42371489302](https://github.com/Pasta0719/PiriJuggler/actions/runs/42371489302). Later changes to phase documentation do not introduce game code. This verifies the production build and JUnit test task, **not the real Minecraft player stop path**.

## Next concrete integration

Preserve the newly tested solver and round API; connect `HYPER_DOG` in Paper's machine creation/session/persistence path, pass server-authoritative pressedIndex/stopIndex/slip/duration to Fabric, draw the dedicated seven texture paths, and capture a real 3-stop Paper/Fabric runtime trace. Reuse the 666,792 matrix as a regression test. Never declare unexecuted runtime verification PASS.
