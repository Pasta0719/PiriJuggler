# HYPER_DOG — Phase 02 final acceptance

**Status: COMPLETE — 2026-11-08 (JST). Phase 03 remains NOT_STARTED.**

## Evidence run

- Branch: \`plan/original-at-machine-20261107\`
- Workflow: [GitHub Actions #42442118865](https://github.com/Pasta0719/PiriJuggler/actions/runs/42442118865) — **SUCCESS**.
- Retained runtime artifact: [hyperdog-phase02-real-runtime](https://github.com/Pasta0719/PiriJuggler/actions/runs/42442118865/artifacts/13602760151).
- Independent full build: [Piri build #42442122289](https://github.com/Pasta0719/PiriJuggler/actions/runs/42442122289) — **SUCCESS**.
- Runtime run ID: \`20261108T032023Z\`, UTC; verified \`result.json\`, \`server-result.json\`, \`client-result.json\`, \`client.log\`, \`server.log\`, and actual Minecraft screenshot \`hyperdog-phase02-57-rounds.png\`.
- Reproduction: \`xvfb-run -a -s "-screen 0 1280x720x24" python runtime-test-support/run_hyperdog_phase02.py\` after the workflow's production JAR, helper JAR and Paper 1.21 preparations.

## Verified results

| Category | Outcome |
| --- | --- |
| Real Paper 1.21 / Fabric 1.21 handshake | PASS |
| Server-authoritative reel spins | **57 / 57** |
| Server physical stops delivered to client | **171 / 171** |
| Client-initiated inputs | **143** |
| Server automatic stops | **28** |
| Rejected valid client inputs | **0** |
| Paper stop indexes exactly equal Fabric display stop indexes | **57 / 57** |
| Approved symbol texture resources | **7 / 7 loaded**, 256×256 RGBA PNG |
| Three reel columns × three rows visible | PASS — actual screenshot inspected |
| 9 valid resource paths per displayed stop | PASS |
| Real network replay after Paper snapshot JSON restoration | PASS |
| Client-side probe errors | **0** |
| Exhaustive stop-history Java acceptance | 12 profiles × 6 orders × 21³ = **666,792 histories** PASS |
| Whole project JUnit regressions | **539 tests, 0 failures, 0 errors, 1 skipped** |
| Production build / runtime-helper build | PASS |
| Existing machine game logic changes | NONE |
| Phase 03 | NOT_STARTED |

### The actual graphical result

The first acceptance attempt [#42441559637](https://github.com/Pasta0719/PiriJuggler/actions/runs/42441559637) demonstrated all 57 Fabric stops, but exposed two test defects: the client terminated before Paper committed the 57th final result, and the preview copied only the top-left 126×126 source region from each 256×256 user PNG. The fix renders the **entire** source image scaled to the cell, with a six-second post-completion hold. The succeeding run [#42442118865](https://github.com/Pasta0719/PiriJuggler/actions/runs/42442118865) checked real screenshot evidence and passed all runtime assertions. No previous failed attempt is counted as acceptance.

## Phase isolation and remaining work

The real Paper plugin and Fabric Minecraft client were started using an **isolated, test-only Phase 02 role/session fixture**. It transmits authentic \`Envelope\` packets over Minecraft plugin messaging and drives the production \`HyperDogStopSession\`, production \`HyperDogClientReels\`, and dedicated reel preview screen. This confirms the **Phase 02 reel and stop control path**, not the full playable HYPER_DOG machine.

**Not claimed by Phase 02:** production \`MachineService\` machine registration, normal-game mode lottery and game economics (Phase 03), AT/CZ full accounting and persistence integration (Phase 04), full Fabric cabinet and controls (Phase 05), later effects/admin/release phases. No HYPER_DOG production machine is selectable in the hall yet.

The seven original user-approved figures are committed only under \`fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/\`. They have already passed packaging, resource-manager and real display checks. No changes to original JUGGLER, GOD, SKILL_STOP, management, mobile assets or production machine economy.

**Phase 02 acceptance gate: SATISFIED within the signed-off reel-control scope.**
