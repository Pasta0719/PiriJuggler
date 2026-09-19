# PHASE 12 — Remote Public State / Interest Sync

Status: IN_PROGRESS — BUILD_PASS / RUNTIME_PENDING

## 読むもの
- `../docs/REMOTE_MACHINE_VISUAL_SPEC.md`
- `../SPEC.md`
- `PHASE_01_FOUNDATION.md`
- `PHASE_02_DATABASE_MACHINE.md`
- `PHASE_04_REELS.md`
- `PHASE_05_GAME_LOGIC.md`
- `PHASE_06_BONUS_EFFECTS.md`
- `PHASE_11_RECOVERY_FINAL.md`

## 共通ルール
- Phase開始時 status=IN_PROGRESS。
- COMPLETE済み Phase01–11 を壊さない。
- production に TODO/FIXME/stub/仮実装を残さない。
- 既存 owner 向け OPEN_MACHINE / PUBLIC_STATE / SPIN_START / REEL_STOP 等の意味を変更しない。
- remote packet は観覧専用。client->server 操作を追加しない。
- hidden state / setting / internal role を remote viewer へ送らない。
- `./gradlew test` と `./gradlew build` 両方 exit0 必須。
- runtime未実施なら COMPLETE 禁止。
- COMPLETE後は Phase13 へ自動で進まず停止。

## 実装範囲

### Protocol
- protocol version を 2 へ更新する。
- 既存 `config.yml` の固定 marker `protocol_version: 1` は他設定を変更せず起動時に2へ自動移行する。2以外の不正値は従来どおりvalidation error。
- server->client packet を追加:
  - REMOTE_MACHINE_SNAPSHOT
  - REMOTE_MACHINE_SPIN
  - REMOTE_MACHINE_STOP
  - REMOTE_MACHINE_NOTICE
  - REMOTE_MACHINE_BONUS
  - REMOTE_MACHINE_REMOVE
  - REMOTE_MACHINE_SOUND
- unknown/malformed remote packet で local SlotScreen session を破壊しない。

### Remote machine snapshot
SNAPSHOT は最低限以下を持つ:
- machineId
- world UUID
- anchor x/y/z
- facing
- enabled
- occupied
- public game state
- displayStops left/center/right
- stoppedMask
- lampOn
- public bonus mode (NONE/BIG/REG。type非公開中は NONE)
- bonusCount
- spinning 状態
- spinning 中なら spinId / animation / resume用公開情報

### Interest management
- visual radius = 32 blocks
- same world のみ
- join/handshake 完了時に range 内を snapshot
- world change 時に再構築
- player movement に対する interest refresh = 10 server ticks ごと
- machine create/redefine/remove/enable change は即時反映
- enter range -> SNAPSHOT
- leave range -> REMOVE
- idle machine は snapshot 後に定期送信しない

### Gameplay hooks
Paper の authoritative transition 完了後にのみ remote event を出す。
- spin commit -> REMOTE_MACHINE_SPIN
- reel stop commit -> REMOTE_MACHINE_STOP
- public notice -> REMOTE_MACHINE_NOTICE
- BIG/REG が public になった時 -> REMOTE_MACHINE_BONUS start
- bonus end -> REMOTE_MACHINE_BONUS end
- close/suspend/recovery -> stale spinning が残らない snapshot/update

DB commit 前に remote viewer へ成功状態を送らない。

## Security / information rules
- setting は送信禁止
- InternalRole は送信禁止
- premium type は送信禁止
- stopHints は送信禁止
- RNG seed/stream は送信禁止
- Vault/heldMedals は送信禁止
- BONUS_PENDING_BIG/REG の内部種別を type 公開前に送信禁止
- spectator は remote packet を利用して操作不可

## 完了条件
- 42 machines 登録状態で handshake 後、range 内 machine の snapshot が一度だけ届く
- 32 blocks を跨いで enter/remove が正しく発生
- idle 42台で snapshot 後の継続 remote traffic が0
- owner の従来 packet flow が regression しない
- spin/stop/notice/bonus start/end が commit 後だけ remote event 化される
- reconnect/server restart 後に stale state 無しで snapshot 再構築
- hidden state が packet capture に存在しない
- 既存v1 configを保持したserverを新Paperで起動すると protocol markerだけ2へmigrationされ、経済/イベント等の既存設定は保持される
- unit/integration tests + real Minecraft runtime PASS

## Evidence
COMPLETE 時:
- `runtime-evidence/PHASE_12/REPORT.md`
- packet capture
- 42-machine idle traffic measurement
- reconnect/restart evidence

## 終了時
IMPLEMENTATION_STATUS の Phase12 を COMPLETE にし、Phase13 は NOT_STARTED のまま停止。


## Current blocker
Production implementation and static contradiction review are present on main.
`build-jars.bat` completed successfully on the user runtime machine after commit 237fbf1141a6edc2d6d97daa9f9266c67ab2361e. Gradle unit/integration tests and production JAR build therefore PASS.
Phase12 cannot be marked COMPLETE yet because the real Paper 1.21 + Fabric 1.21 runtime acceptance remains pending.
Required next verification is the Phase12 section of `RUNTIME_ACCEPTANCE.md`.


## Runtime progress
- Owner regression runtime: PASS (2026-09-19, user-confirmed)
  - compatible client connected successfully
  - existing machine opened normally
  - BET -> spin -> STOP -> payout completed normally
- Remote spectator sync acceptance: PENDING
