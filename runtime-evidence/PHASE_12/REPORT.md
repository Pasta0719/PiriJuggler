# PHASE 12 Runtime Acceptance Report

Date: 2026-09-20

Status: **PASS / COMPLETE**

## Build
- Production source/runtime baseline SHA: `eefc74d419c3fd9a08190b065c229e28d7a14d42`
- Test-only spectator probe enhancement SHA: `4f09d4e3698e5e82fdb19120c7ddf92bee61b8b1`
- User ran `build-jars.bat`: production tests/build PASS.
- Runtime test helper build PASS.

## Real Paper + Fabric evidence
- Fresh startup/in-range: `SNAPSHOT=42 CACHE=42 LAST_MACHINE=42`.
- Range leave (>35 blocks): `REMOVE=42 CACHE=0`.
- Re-entry: `SNAPSHOT=126 CACHE=42`.
- 30s idle: counters unchanged; no snapshot/remove/gameplay churn; `CACHE=42`.
- Real play public events:
  - SPIN/STOP observed.
  - NOTICE observed: `NOTICE=1`.
  - BONUS start observed: `BONUS=1`.
  - BONUS end observed: `BONUS=2`.
- World change to Nether: `REMOVE=84 CACHE=0`.
- Return to Overworld: fresh snapshots rebuilt, `CACHE=42`.
- Spectator reconnect: fresh 42-machine cache rebuilt.
- Stale-spin test: spectator disconnected during spin, owner completed spin while spectator was offline, reconnect result `CACHE=42 SPINNING_CACHE=0`.
- Runtime machine mutation:
  - create machine 43 -> `CACHE=43 LAST_MACHINE=43`.
  - redefine machine 43 -> SNAPSHOT incremented while `CACHE=43`.
  - remove machine 43 -> `REMOVE=1 CACHE=42`.

## Code/static verification used for remaining security/edge criteria
- Remote payloads are rebuilt from an allow-listed public-state shape in `RemoteMachineSync`; hidden setting/internal role/premium/stopHints/RNG/Vault/heldMedals are not forwarded.
- `BONUS_PENDING` does not expose BIG/REG through public bonus mode; type remains NONE until public BIG/REG state.
- `ADMIN_SET_ENABLED` success calls `remote.machineChanged(machine)`, providing immediate interested-client refresh.
- Malformed remote payload handling is isolated in `RemoteMachineRegistry` and removes only the affected cached machine rather than invalidating the gameplay session/handshake.
- Protocol mismatch behavior remains covered by the established Phase01 regression evidence; Phase12 runtime used protocol version 2 successfully.

## Result
Phase12 Remote Public State / Interest Sync acceptance is complete. Phase13 remains NOT_STARTED.
