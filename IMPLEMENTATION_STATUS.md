# IMPLEMENTATION_STATUS.md

このファイルは各Phase終了時の現在状態を示す。

## Current status — 2026-09-19

PiriJuggler Phase01–11 はすべて COMPLETE。
Remote Machine Visual / Hall Audio は仕様確定済み。Phase12 は実装中、Phase13–14 は未実装。

| Phase | 状態 | 概要 |
|---|---|---|
| 01 | COMPLETE | Gradle / Paper / Fabric / Protocol Handshake |
| 02 | COMPLETE | SQLite / Machine Registration / Session |
| 03 | COMPLETE | Fabric Slot Screen / Input / Base UI |
| 04 | COMPLETE | Fixed Reels / 9261 Precompute / Stop Solver |
| 05 | COMPLETE | RNG / Normal Game / Replay / Simulator |
| 06 | COMPLETE | Piri Chance / Premium / Audio / BIG / REG |
| 07 | COMPLETE | Vault / CREDIT / Held Medals / Medal Bundle |
| 08 | COMPLETE | Prize Exchange / Vault Exchange / Transactions |
| 09 | COMPLETE | Data Lamp / Graph / Piri Chain |
| 10 | COMPLETE | Admin / Settings / Startup Allocation / Events |
| 11 | COMPLETE | Suspend / Restart Recovery / Hardening / Final Tests |
| 12 | IN_PROGRESS | Remote Public State / Interest Sync — build PASS, runtime acceptance pending |
| 13 | NOT_STARTED | Entity-Free World Cabinet Renderer |
| 14 | NOT_STARTED | Positional Hall Audio / Performance / Hardening |

## Existing final verification

- Phase11 Runtime Acceptance: PASS
- Security negative test: PASS
- Recover cashout: PASS
- Disconnect grace / expiry / idle / restart recovery: PASS
- `FINAL_VERIFICATION.md`: **40 / 40 PASS**
- Final Phase11 evidence: `runtime-evidence/PHASE_11/REPORT.md`
- Phase11 instruction status: `phases/PHASE_11_RECOVERY_FINAL.md` = COMPLETE

## Locked future specification

- Master spec: `docs/REMOTE_MACHINE_VISUAL_SPEC.md`
- Phase12: `phases/PHASE_12_REMOTE_STATE_SYNC.md`
- Phase13: `phases/PHASE_13_WORLD_CABINET_RENDERER.md`
- Phase14: `phases/PHASE_14_HALL_AUDIO_PERFORMANCE.md`

Important locked decisions:
- display entities 0
- custom BlockEntity 0
- registered machine button position/facing remains physical anchor
- Paper owns truth; Fabric only renders public state
- remote client cannot control machines
- no hidden setting/internal role/bonus type leaks
- 42-machine performance acceptance required before Phase14 COMPLETE

## Completion / next work

Current phase: 12
Last completed phase: 11
Open blockers: Phase12 real Paper+Fabric runtime acceptance remains
Next planned phase: Phase12 verification/completion
Phase13 start condition: Phase12 COMPLETE only

Phase12 production implementation is implemented and Gradle build/tests PASS, but not COMPLETE until real Paper+Fabric runtime acceptance PASS. Phase13 world rendering and Phase14 hall audio are still NOT_STARTED.
