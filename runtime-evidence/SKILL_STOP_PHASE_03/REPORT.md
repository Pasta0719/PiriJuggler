# SKILL STOP Phase03 runtime acceptance
Status: PASS
Date: 2026-10-03
Source commit: `fa81e18a5e4c52c7e2a0144b2324f10bf29fa3ae`

- Actual Paper server and Fabric Minecraft client, scenario `skill03-main`, Java21 and Xvfb.
- Runtime: https://github.com/Pasta0719/PiriJuggler/actions/runs/37123294908
- Full Windows root test/packagePiriJars/runtime-helper build: https://github.com/Pasta0719/PiriJuggler/actions/runs/37123297201
- Runtime: 202/202 assertions PASS; result.json records every assertion.
- Common/Paper/Fabric JUnit: 42 / 288 / 44 discovered, zero failures/errors; one Paper opt-in test skipped. 373 executed tests PASS.
- Exhaustive controller: 4,389,714 histories PASS (the existing Phase02 invariant suite).
- Complete packets, logs, JUnit XML and four screenshots: Actions artifact `skill-stop-phase03-runtime-evidence`, ID11274163409.

## Real runtime coverage
- BIG20 initial games; final BAR success keeps3G; total23 fourteen-medal payouts before remaining0 ends.
- Paper and Minecraft processes both restart after two stops of the final challenge. Same spin ID, draw, target, input/stop history, remaining1 and stopped mask3 survive. Third stop awards14 and adds3 once.
- An added game draws BELL; correctly aligned BAR stays, fails and pays14. Added games continue the same draw path.
- All-failure REG ends after8 payouts.
- A missed BIG stays pending; consecutive actual replay wins make the next1BET game free and retain BIG. Replay first-stop law preserved.
- Public remaining/target match saved state; normal internal role and premium fields stay private; real keyboard input matches transmitted pressedIndex.

## Verified production artifacts
Paper SHA256: `eedf5569d3a0848e955bbfc2aefd98a3e9f349a4ea63fa11bd26a63a3cb94b2a`
Fabric SHA256: `57720cb715f2413f14a2a362a9291940d2798c0de94601c179d737b1503839d1`
Existing filenames retained. The Fabric production artifact is identical to Phase02; the new state/payout logic is in Paper. Final follow-up changes are documentation/evidence only.

SQLite unit integration additionally covers grace expiry, idle suspension, explicit reseating, machine exclusion, repeated third stop and repeated transaction receipt, wrong target failure, BIG20/REG8 exhaustion and additional-game successes.

Phase04 full-strategy machine return remains separate. Challenge PNG/sound and remaining-game screen/cabinet presentation are Phase05; this acceptance checks the fixed public state supplied to them.
