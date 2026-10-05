# Pachinko implementation phases

Status: implementation COMPLETE through Phase 10.

## Locked baseline specification

- Machine family: `PACHINKO`, added to the existing Paper/Fabric project and distributed in the same jars as the existing machines.
- Game presentation: physical/役物-first pachinko. No full LCD animation system is required.
- Normal play: left-side start entry launches the visible left kurun sequence.
- Effective initial jackpot probability from a valid left start: **1/319**.
- Start prize: **1 ball**.
- Reference spin rate: **17 starts / 1000 yen (250 balls)**.
- Reference spin rate is NOT the break-even border.
- Initial jackpot allocation:
  - **40%: 450 balls, normal end**
  - **60%: 1500 balls + RUSH**
- RUSH continuation: **81%**.
- Right-side jackpot payout allocation:
  - **97%: 1500 balls**
  - **3%: 3000 balls**
- Right side is represented by a dedicated kurun/役物 flow.
- Ball visuals must be observable by nearby players, not only by the seated player.
- Ball visuals are client-rendered deterministic motion synchronized from server-owned state. Do not implement each ball as a Bukkit entity with per-tick collision physics.
- Exact ST / loop-type presentation is replaceable behind the RUSH rules, but it MUST preserve the locked 81% effective continuation unless the machine spec is explicitly revised.

## Locked economics

The right-side average payout is:

`1500 * 0.97 + 3000 * 0.03 = 1545 balls`

Expected additional right-side wins after entering an 81% continuation loop:

`0.81 / (1 - 0.81) = 4.2631578947`

Expected gross payout per initial jackpot:

`0.40 * 450 + 0.60 * (1500 + 4.2631578947 * 1545) = 5031.947368 balls`

With a one-ball start prize, the equivalent 4-yen break-even border is derived as:

`250 / (5031.947368 / 319 + 1) = 14.9039029 starts / 1000 yen`

Therefore:

- **Equivalent border: about 14.9 / 1000 yen**
- **17 / 1000 yen is intentionally above border**
- Machine payout must change naturally with measured spin rate; do not directly multiply a configured “machine payout percentage”.
- The model used by code is:
  - net ball cost/start = `250 / measuredSpinsPer1000 - 1`
  - expected input/initial jackpot = `319 * netBallCostPerStart`
  - payout rate = `expectedGrossPayoutPerInitialJackpot / expectedInput * 100`

Current theoretical examples:
- 14.0 spins/1000: about 93.58%
- 14.9039 spins/1000: 100%
- 15.0 spins/1000: about 100.69%
- 16.0 spins/1000: about 107.86%
- 17.0 spins/1000: about 115.09%
- 18.0 spins/1000: about 122.39%

These are theoretical long-run economics, not short-session guarantees.

# Phase 01 - Machine family and economics foundation

Implementation:
- Add stable `MachineType.PACHINKO`.
- Add a pachinko package independent from slot reel semantics.
- Encode every locked probability/payout value in one authoritative specification class.
- Implement derived border and payout-rate calculations.
- Add automated tests for the 14.9039 border, 1545 right average and reference-rate payout.
- Do not register a fake live engine until it can reject/accept pachinko actions without corrupting slot session state.

Completion criteria:
- Paper compiles.
- Existing slot machine types remain unchanged in behavior.
- Economics tests pass.
- No hard-coded “14.9” is used as the source of truth; it is derived.

# Phase 02 - Pachinko durable runtime and actions

Implementation:
- Add a pachinko runtime state serialized through machine runtime JSON.
- State must cover at minimum: mode, balls held/loaned, total fired, total starts, current ball sequence id, current presentation phase, initial-hit state, RUSH state, RUSH wins, current payout, cumulative payout and last activity.
- Define machine-neutral pachinko actions for fire/start/role presentation without reusing slot BET/LEVER semantics internally.
- Register a `PachinkoGameEngine` only after its transitions are durable through the existing commit path.
- Reconnect/resume must reconstruct the same authoritative state.

Completion criteria:
- Create/open/close/reconnect cannot duplicate or lose held balls.
- A server restart cannot erase an already committed jackpot/RUSH state.
- Existing slot sessions are unaffected.

# Phase 03 - Ball lending, firing and measured rotation

Implementation:
- 1000-yen equivalent loan unit = 250 balls.
- One fired ball consumes one held ball.
- Valid start entry returns the one-ball start prize.
- Start-entry frequency is driven by a configurable physical routing/start-entry model whose observed result is measured as starts per 250 balls.
- Store actual fired balls and actual starts; compute measured spins/1000 from those counters.
- Do not fudge jackpot probability to compensate for poor/good rotation.

Completion criteria:
- Changing only routing/start-entry tuning changes observed spins/1000 and therefore long-run payout rate.
- Jackpot remains 1/319 per valid start regardless of rotation.
- 17 is available as the initial/reference tuning target.

# Phase 04 - Left kurun and initial jackpot

Implementation:
- Every valid start produces a server-owned deterministic left-kurun presentation event.
- The visible ball path is generated from synchronized sequence/seed/timing data.
- The final effective V result is 1/319 per valid start.
- Nearby observers receive the same sequence and see the same V/OUT result.
- V result commits before any payout/RUSH transition is exposed.

Completion criteria:
- Player and observers agree on the same ball, timing and V/OUT.
- Reconnect during the presentation resumes or reconstructs the committed result without reroll.
- Statistical test confirms 1/319 within tolerance.

# Phase 05 - Initial payout and RUSH entry

Implementation:
- Initial V allocation is exactly:
  - 40% -> 450 balls, normal end
  - 60% -> 1500 balls, enter RUSH
- Allocation is decided exactly once and persisted.
- Payout presentation may stream visually, but balance effects must be idempotent.

Completion criteria:
- Forced/statistical tests verify 40:60 allocation.
- Disconnect/restart during payout cannot duplicate payout.
- 450 outcome never enters RUSH; 1500 RUSH outcome always does.

## Verified implementation status — 2026-10-05

- Phase 03: COMPLETE — durable 250-ball loan/fire/start accounting, one-ball start prize, measured rotation from actual counters, routing-only payout-rate sensitivity, fixed 1/319 odds, and 17 reference tuning are covered by production tests; Piri build and Runtime helper build pass at `3e4616b`
- Phase 04: COMPLETE — server-owned deterministic left-kurun event, synchronized Fabric owner/observer rendering, committed V/OUT reconstruction on reconnect, and seeded 1/319 statistical verification are covered; Piri build and Runtime helper build pass at `3e4616b`
- Phase 05: COMPLETE — forced and statistical 40:60 allocation, 450 normal / 1500 RUSH mapping, durable idempotent payout, and reconnect duplicate-payout rejection are covered; Piri build and Runtime helper build pass at `3e4616b`
- Phase 06: COMPLETE — dedicated right-kurun flow, exact 81% continuation, 97:3 right payout allocation, 1545-ball expected right payout, clean RUSH failure and reconnect-safe decisions are covered by production/statistical tests
- Phase 07: COMPLETE — the selected probability-loop presentation resolves exactly to the locked 81%, while finite-ST and LT-style strategies remain behind the same production abstraction and tests
- Phase 08: COMPLETE — deterministic owner/observer left/right kurun rendering, snapshot reconstruction, culling/cleanup and multi-machine observation are implemented without per-ball Bukkit entities
- Phase 09: COMPLETE — durable production counters, measured rotation vs theoretical border UI, production-spec simulator, DB-restart persistence and explicit machine-type-safe statistics reset are implemented and tested
- Phase 10: COMPLETE — all non-pachinko regression gates passed on production-equivalent `cb4a08da`; commit `e3a0fdf6` changed only `runtime-test-support/run_pachinko_phase10.py`, and its real Paper + two Fabric acceptance passed every required owner/observer, 40:60, 81%, right allocation, reconnect, restart, multi-machine and simultaneous-slot isolation check

# Phase 06 - Right kurun, 81% continuation and payout allocation

Implementation:
- Add dedicated right-side kurun sequence.
- Effective continuation probability per completed RUSH continuation decision is 81%.
- On a successful continuation/right jackpot:
  - 97% -> 1500 balls
  - 3% -> 3000 balls
- A failed continuation ends RUSH.
- 3000 payout is one 3000 allocation for statistics even if its visual payout is presented as two 1500 blocks.
- RUSH implementation exposes a strategy seam so a later ST-style or loop-style presentation can implement the same effective locked economics.

Completion criteria:
- Long-run tests verify 81%, 97:3 and expected right payout 1545.
- RUSH failure transitions cleanly to normal state.
- No extra continuation is granted by reconnect or delayed packets.

# Phase 07 - ST / kakuhen loop presentation layer

Implementation:
- Select and implement the concrete modern-pachinko presentation model without changing the locked economics.
- Supported engine abstraction must be capable of:
  - finite ST attempts,
  - probability-loop/kakuhen state,
  - LT-style promotion/challenge,
  while one concrete mode is chosen for this machine.
- The selected mode must resolve to 81% effective RUSH continuation.
- Any LT-like visual/challenge state is a presentation/state-machine rule, not an excuse to change the locked payout model silently.

Completion criteria:
- Formula proving effective continuation = 81% is documented next to the selected parameters.
- Simulation and production engine use the same parameters.
- No hidden alternative continuation path exists.

# Phase 08 - Fabric visual system and spectator synchronization

Implementation:
- Render balls, left/right kurun and V/OUT motion client-side.
- Server sends event identity, authoritative outcome, seed, start time and phase changes; do not stream every coordinate every tick.
- New nearby viewers receive a snapshot sufficient to reconstruct an in-progress presentation.
- Apply distance culling and lifecycle cleanup.
- Do not create a real server entity for every visual ball.

Completion criteria:
- Seated player and at least one observer see materially identical timing/outcome.
- Joining observation mid-sequence produces the current phase rather than restarting/randomizing it.
- Leaving render range removes visual objects without altering server state.
- Multi-machine observation remains stable.

# Phase 09 - Data display, history and simulation

Implementation:
- Track starts, fired balls, measured spins/1000, initial jackpots, 450/1500 initial allocations, RUSH entries, RUSH wins, 1500/3000 right allocations, total payout and difference balls.
- Add pachinko simulator using the production probability specification.
- Simulator accepts rotation/spins-per-1000 as an input and reports expected/observed payout rate and difference.
- Data UI must distinguish theoretical equivalent border from observed rotation.

Completion criteria:
- Simulator at about 14.9039 converges to 100% payout.
- 17 rotation converges to about 115.09% under the locked model.
- Production counters survive restart and reset only through explicit reset behavior.

# Phase 10 - Runtime acceptance and regression gate

Required acceptance:
- Paper build passes.
- Fabric build passes.
- All existing slot tests pass.
- Pachinko unit/statistical tests pass.
- Real-client left kurun is visible to player and observer.
- Initial V, 40:60 allocation, RUSH entry, right continuation and 97:3 payout are verified.
- Disconnect/reconnect tests cover normal play, left-kurun presentation, initial payout, RUSH and right-kurun presentation.
- Restart persistence is verified.
- Two or more pachinko machines can run independently.
- Existing slot machines can run simultaneously with pachinko without shared-state contamination.

The pachinko feature is not considered complete until Phase 10 passes.


## Final verification — 2026-10-05

- Production/regression baseline: `cb4a08da42c75798a03c6f19fe1f0c1ea2638600` — Piri build, Runtime helper, SKILL STOP Phase 02/03/04/05, NEXT Phase 02/04/05 all SUCCESS
- Final Phase 10 acceptance: `e3a0fdf66b63123bc6f0a1ae7f131e734feb5ddd` — only the Phase 10 test runner changed from the production/regression baseline; `Pachinko Phase 10 real runtime` SUCCESS
- Phase 10 verified real-client acceptance includes player + observer left-kurun identity/outcome, mid-sequence reconstruction, initial 40:60 paths, RUSH/right decisions, forced 1500/3000 production resolution, reconnect across normal/left/initial payout/RUSH/right states, Paper restart persistence, two independent pachinko machines, and simultaneous slot gameplay with no pachinko shared-state contamination

**Pachinko feature status: COMPLETE through Phase 10.**
