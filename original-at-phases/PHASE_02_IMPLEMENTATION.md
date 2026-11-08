# HYPER_DOG Phase 02 — Implementation evidence

> **2026-11-08 FINAL ACCEPTANCE: PASS / Phase 02 COMPLETE.** See [PHASE_02_RUNTIME_ACCEPTANCE.md](PHASE_02_RUNTIME_ACCEPTANCE.md) and successful [real Paper/Fabric run #42442118865](https://github.com/Pasta0719/PiriJuggler/actions/runs/42442118865). Older "pending/unverified" paragraphs below are retained as dated implementation history and are superseded by this verified run. Full hall machine registration and economic game engine are not part of Phase 02.


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

The new common solver and isolated Paper-side round have **not yet been wired into the production machine dispatcher and Fabric cabinet**, because HYPER_DOG's normal-game engine and screen are separate later Phase work. Therefore the original acceptance clause demanding a **real Paper/Fabric stop-path capture** (input, all three reels, restart and end-to-end player view) has not been performed. The seven approved symbol PNGs were subsequently committed in `e950b32df36d8065b726e6499e2a5082af0c7bba`; real Fabric cabinet rendering remains unverified. **Do not set Phase 02 to COMPLETE on this evidence alone.**

**GitHub Actions Piri build PASS** for all new Java source/tests through commit `c94e0a9bc357` (run [42371550379](https://github.com/Pasta0719/PiriJuggler/actions/runs/42371550379), conclusion `success`). The Paper-side tests also passed in run [42371489302](https://github.com/Pasta0719/PiriJuggler/actions/runs/42371489302). Later changes to phase documentation do not introduce game code. This verifies the production build and JUnit test task, **not the real Minecraft player stop path**.

## Next concrete integration

Preserve the newly tested solver and round API; connect `HYPER_DOG` in Paper's machine creation/session/persistence path, pass server-authoritative pressedIndex/stopIndex/slip/duration to Fabric, draw the dedicated seven texture paths, and capture a real 3-stop Paper/Fabric runtime trace. Reuse the 666,792 matrix as a regression test. Never declare unexecuted runtime verification PASS.


## Continuation — Paper/Fabric transport contract (2026-11-07)

**New isolated source files**
- \`paper/src/main/java/jp/pirijuggler/paper/reel/HyperDogStopSession.java\` — server-owned per-spin stop protocol over existing \`Envelope\` / \`PacketType\`, client input validation, separate client sequence and automatic transitions, monotonic input rejection, support for replaying the last 3 stops after a reconnect, and JSON snapshot serialization.
- \`fabric/src/main/java/jp/pirijuggler/fabric/ui/HyperDogClientReels.java\` — client-side stop and symbol display model, consuming only SPIN_START and REEL_STOP after HYPER_DOG OPEN_MACHINE; 3-column/3-row symbols and dedicated PNG paths without revealing the server's internal role.
- \`fabric/src/test/java/jp/pirijuggler/fabric/ui/HyperDogClientReelsTest.java\` — 12-role Paper packet → EnvelopeCodec → Fabric model integration tests, including correct stops, roundtrip reconnect, no hidden role transmitted, duplicate and foreign-spin packets.
- \`original-at-phases/validate_symbol_assets.py\` — validates seven user-supplied PNGs against 256×256 RGBA specification, CRC, complete PNG structure. Does not create substitute graphics.

**Updated production receiver**
- \`fabric/src/main/java/jp/pirijuggler/fabric/PiriJugglerClient.java\` attaches an isolated HYPER_DOG renderer data model to existing authenticated incoming packets only when the new machine type is open; the existing slot screen dispatch and existing machine rendering paths are not changed.
- \`HyperDogRound\` now keeps server automatic CZ/AT stop events out of the client's sequence counter. In a timed CZ spin, left auto-stops at 500ms, right at 700ms, only center accepts player input after 900ms. The AT red-seven presentation completes automatic stops at 720ms and reserves 320ms before unlocking a next game.
- The adapter is deliberately **not yet** registered as a production GameEngine and never creates a playable HYPER_DOG machine. Full economic game-engine/session integration requires exact Phase03–04 logic; the cabinet requires Phase05. Adding the new enum alone now would advertise an unusable machine and is explicitly avoided. The actual Paper→Minecraft networking transport and final 7-PNG Fabric rendering are **not yet proven in a live Minecraft session**.

**Failure and fix tracked**
- Two initial CI runs \`42378504168\` and \`42378613943\` failed one new test: AT red-seven manual input after the three auto-stops returned \`ALREADY_STOPPED\` rather than \`INVALID_STATE\`. The server adapter's validation priority was corrected in commit \`5636c2c99bbd\`.
- Subsequent \`Piri build\` jobs were started for the corrected server adapter and isolated Fabric receiver. Record conclusive success/failure separately; no CI job is counted PASS while still in progress.

**Safety boundary**
- Previously supported machine game engines, probabilities, stored data, and persistent machine types are unchanged. Only the authenticated Fabric network receiver has an existing-file modification; that change is a guarded, additive HYPER_DOG-only hook.
- Final Phase 02 status remains \`IN_PROGRESS\` until real Paper/Fabric Minecraft runtime capture; all seven approved PNG assets are now present, in accordance with the locked completion criteria.


## Verified CI for continuation

- [Piri build run 42379006836](https://github.com/Pasta0719/PiriJuggler/actions/runs/42379006836) — **SUCCESS**, fixes red-seven auto-stop validation order in \`5636c2c99bbd\`.
- [Piri build run 42379156812](https://github.com/Pasta0719/PiriJuggler/actions/runs/42379156812) — **SUCCESS**, verifies \`d8827c0d6d63\` (Fabric receiver hook, 12-profile Paper↔Fabric envelope roundtrip JUnit, all existing test suites, runtime helper build and production packaging).
- These CI successes verify source tests and packaging, NOT real Minecraft Paper/Fabric gameplay acceptance. Do not conflate a Gradle runtime-helper *build* with running a runtime scenario.


## Approved HYPER_DOG symbol artwork — 2026-11-07

The user approved recolored variations of their original orange long-body cat-like figure. All seven **actual PNG files** are committed at `fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/`, without modifying existing machine textures:

| Filename | Appearance |
| --- | --- |
| `replay.png` | Orange |
| `red7.png` | Aqua/cyan (symbol's legacy name does **not** indicate red appearance) |
| `rainbow7.png` | Rainbow gradient |
| `bar.png` | Purple |
| `grape.png` | Gold/yellow, distinguished from orange replay |
| `cherry.png` | Red |
| `watermelon.png` | Green |

Each file is 256×256, RGBA8, transparent-background PNG. Committed at [e950b32](https://github.com/Pasta0719/PiriJuggler/commit/e950b32df36d8065b726e6499e2a5082af0c7bba). The `HyperDogReelImageMapping` paths map directly to these files; the dedicated Fabric cabinet screen has not yet been integrated, so asset presence is not proof of visible gameplay.
