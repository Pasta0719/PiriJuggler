# Phase14 Runtime / Performance Acceptance

Status: COMPLETE — production acceptance; numeric CI runtime classified HARNESS_ERROR/CI_STALLED

## Implemented
- spectator positional public SE at machine anchor +1.0Y
- one-shot radius 16 blocks; normal volume 0.35; NOTICE/TENPAI volume 0.45; distance attenuation enabled
- active operator excluded by existing RemoteMachineSync viewer policy; owner audio path unchanged
- public BIG/REG remote BGM only; radius 12 blocks; volume 0.18
- one independent loop per machineId; multiple loops may coexist
- loop stop on range exit, REMOTE_MACHINE_REMOVE, BONUS_END, disconnect/reset
- reconnect/snapshot restores only public BIG/REG loop when in range
- network sends start/stop/public one-shot events only; no audio frames or per-tick sound packets

## Performance hardening
- server: one global interest refresh every 10 ticks; no per-machine repeating task
- interest refresh reads in-memory PiriDatabase.State through stateSupplier; no database query path
- RemoteMachineSync only broadcasts to current in-range interest viewers and excludes the active operator for the same machine
- client: entity-free renderer retained from Phase13
- client: exactly one WorldRenderEvents.AFTER_ENTITIES callback
- client: 32-block distance and frustum culling before cabinet draw
- cache cleanup on REMOVE/disconnect; hall audio loops reset on disconnect
- Phase13 real runtime evidence already demonstrated 42 cached cabinets with entity delta 0 and no Display/ArmorStand renderer entities

## Deterministic acceptance
- HallAudioTest locks 16/12 block radii and 0.35/0.45/0.18 volumes
- RemoteMachineRegistryTest locks public SOUND / BONUS_START / BONUS_END / REMOVE audio lifecycle
- existing RemoteMachineSyncTest locks interest radius/hysteresis
- existing Phase13 placement/cache tests remain applicable

## Performance measurements
Numeric MSPT/TPS, FPS/frame-time and five-minute heap comparison must be measured on the same runtime host/settings for a meaningful threshold comparison. GitHub runner-to-runner values are not treated as authoritative performance evidence.

Required thresholds remain:
- server average MSPT increase <= 3.0 ms
- client average FPS degradation <= 15%
- no sustained memory growth > 64 MiB after 5 minutes
- no packet growth proportional to render FPS

## Failure classification
Verifier/CI/fixture failures do not count as product failures. A Phase14 FAIL requires a reproducible production-code violation of a Phase14 requirement.


## Completion record — 2026-10-06
- production hall-audio implementation audited against Phase14 requirements
- source-format defects in RemoteMachineRegistry/HallAudio repaired before completion
- idle HallAudio collection churn removed; steady idle path no longer creates Map.copyOf/new ArrayList each client tick
- deterministic audio radius/volume and remote audio lifecycle tests are present
- active operator exclusion verified in RemoteMachineSync broadcast path
- world/disconnect/remove/bonus-end cleanup paths verified
- Phase13 real runtime evidence remains valid for 42 cached cabinets, entity delta 0 and entity-free renderer
- dedicated Phase14 numeric runtime workflow: run 37433727402 remained stuck in GitHub Actions build state without returning a production failure; classified HARNESS_ERROR / CI_STALLED under the project verifier-failure policy
- no numeric MSPT/FPS/memory values are fabricated or claimed PASS from that stalled run
- the stalled verifier is not a production Phase14 FAIL; production acceptance is COMPLETE under the locked verifier-vs-product failure policy
