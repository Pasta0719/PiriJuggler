# PHASE 14 — Positional Hall Audio / Performance / Hardening

Status: NOT_STARTED
Depends on: Phase13 COMPLETE

## 読むもの
- `../docs/REMOTE_MACHINE_VISUAL_SPEC.md`
- `PHASE_12_REMOTE_STATE_SYNC.md`
- `PHASE_13_WORLD_CABINET_RENDERER.md`
- existing Fabric `PiriSounds`

## 共通ルール
- owner 向け既存 audio timing/volume を変更しない。
- remote audio は duplicate 再生しない。
- hidden result を音で漏らさない。
- continuous per-tick sound packet を作らない。
- performance acceptance を通さない限り COMPLETE 禁止。

## 実装範囲

### Positional SE
REMOTE_MACHINE_SOUND を受信した spectator client は absolute world position から音を鳴らす。

origin:
- machine anchor center + up*1.0

radius / volume:
- one-shot SE radius 16 blocks
- normal SE volume 0.35
- notice/tenpai volume 0.45
- distance attenuation ON

active operator は同じ machine の remote sound 対象から除外。
本人は既存 owner audio のみ聞く。

### Positional bonus BGM
- BIG/REG public start 後だけ開始
- radius 12 blocks
- external volume 0.18
- machineId ごとに loop instance を分離
- multiple bonus machines は同時存在可
- range exit / REMOVE / BONUS_END / disconnect / world change で該当 loop stop
- packet は start/stop event のみ。音声 frame を network 送信しない

### Performance hardening
42台を基準に計測。

Server rules:
- per-machine repeating task 0
- idle remote state repeating broadcast 0
- database query from interest refresh 0; memory state を利用
- interest refresh は 10 ticks
- out-of-range viewer へ visual packet 0
- out-of-range viewer へ audio packet 0

Client rules:
- entity 0
- one render callback
- frustum + distance culling
- idle state で per-frame collection churn を避ける
- remote cache は REMOVE/disconnect/world change で解放

## Runtime acceptance scenarios

### A. 42 idle machines
- 42台を視界/範囲内に置く
- initial snapshot 完了後30秒計測
- remote gameplay packet traffic = 0
- entity count delta = 0

### B. 42 spinning machines
- simultaneous visual spin
- server TPS/MSPT before/after を記録
- client FPS/frame time before/after を記録
- crash/stutter/leak の有無を記録

Acceptance threshold:
- server average MSPT increase <= 3.0 ms versus same scene with remote rendering/sync disabled
- client average FPS degradation <= 15% on the same machine/settings/render distance
- no sustained memory growth > 64 MiB after 5 minutes steady-state comparison
- no packet growth proportional to render FPS

### C. Audio
- operator hears existing normal audio exactly once
- spectator within range hears reduced positional sound
- spectator outside radius hears none
- moving away attenuates/stops as specified
- two bonus machines can run remote BGM independently
- BONUS_END stops only the matching machine BGM

### D. Recovery
- reconnect while BIG/REG active -> correct visual snapshot + appropriate current remote BGM only if in range
- server restart -> no stale loop, no stale spinning reels
- machine remove/redefine -> stale renderer/audio removed

## 完了条件
- all Phase14 runtime scenarios PASS
- no regression in Phase01–13 tests
- `./gradlew test` and `./gradlew build` exit0
- runtime evidence complete

## Evidence
- `runtime-evidence/PHASE_14/REPORT.md`
- packet counts
- entity counts
- server MSPT/TPS measurement
- client FPS/frame-time measurement
- memory measurement
- audio distance test

## 終了時
IMPLEMENTATION_STATUS の Phase14 を COMPLETE にして停止。
