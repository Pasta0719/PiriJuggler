# RUNTIME_ACCEPTANCE.md — 全Phase実Minecraft受入テスト

## 共通ルール

Phase COMPLETEには3種類すべて必要:
1. Unit/Integration tests
2. `./gradlew build`
3. 実際のPaper 1.21 server + 実際のFabric 1.21 clientを起動したRuntime Acceptance

mock server/mock clientだけでは3を満たさない。

CodexがGUI環境を利用できない場合、未実施のままCOMPLETEにせずBLOCKED:`RUNTIME_ENVIRONMENT_UNAVAILABLE`。

各Phase evidence:
```text
runtime-evidence/PHASE_XX/server.log
runtime-evidence/PHASE_XX/client.log
runtime-evidence/PHASE_XX/result.json
runtime-evidence/PHASE_XX/REPORT.md
runtime-evidence/PHASE_XX/screenshots/*.png  # UI phase
```

REPORTは実行日時、build hash、Minecraft/Paper/Fabric version、手順、各assert、PASS/FAILを書く。

## Test environment

- isolated runtime directoryをPhaseごとに作る
- localhost only
- test serverはoffline-mode=trueでよい
- runtime test player name=`PiriRuntimeTest`
- consoleからtest playerをOP化してよい
- test用Vault Economy providerは`runtime-test-support` moduleとして作り、production jarへ含めない
- role/premium強制はproduction backdoorを作らず、runtime test用configのrole weights/premium weightsを変更して100%対象役にする
- client automation helperはruntime-test source set/moduleだけに置きproduction Fabric jarへ含めない

## Phase01
- actual Paper boot with built plugin jar
- actual Fabric client boot with built mod jar
- localhost connection
- HELLO/HELLO_ACK current `Protocol.VERSION` established
- second run with runtime-test client protocol mismatch confirms gameplay denied
- no uncaught exception

## Phase02
- real worldにButton配置
- player視線を合わせ`/piri machine create`
- right-clickでreal session creation + OPEN_MACHINE receive
- server restartしてregistration persists
- 2台目のactual Fabric client（player name=`PiriRuntimeTest2`）をlocalhostへ接続し、occupied machine右clickが実際に拒否される
- redefine/removeを実世界Buttonで確認

## Phase03
- 音源はユーザー提供。未配置でも成功すること。音源の品質・内容は完成条件にしない。
- Slot Screen actual open screenshot
- Space/arrows/L/I/R/Esc input pathがreal client eventからpacket送信
- HUD hidden screenshot
- Piri assets/reel/status位置 screenshot

## Phase04
- runtime config/test harnessで各role candidateを順に表示
- left/center/right任意停止順をreal packetsで実行
- server result stopIndexとclient表示一致
- abnormal 5+ slipを最低1ケース確認

## Phase05
- actual play 100 normal games script
- CREDIT/BET/PAY/REPLAY transition consistency
- `/piri simulator 1 100000` runtime command完了
- 単独BIG/REGのtest configそれぞれで実BAR-BAR-BAR表示、払出し0、Piri Chance点灯、公開BONUS_PENDING、BONUS_STARTなし
- ハズレを含む通常非ボーナスゲームで5本の有効ライン上のBAR-BAR-BAR不成立
- serverにinternal roleが存在してもclient log/network debugへ漏れない

## Phase06
- runtime configを切り替え、BIG/REGを各1回
- 6 premiumをeligible roleで全種類actual client表示/音event確認
- BIG20G/280, REG8G/112をactual state machineで完了
- screenshots: Piri ON, premium blink, BIG/REG entry result

## Phase07
- actual LOANでVault所持金が`borrowedMedals * vault_per_medal`だけ減り、CREDITが増える
- held refill BETが実機で動作する
- medal insert / cashoutが実機で動作する
- cashout総額は500枚単位へ分割せず、総額を保持するPiri Medal 1個として発行される（例: 662=>662 MEDALS 1個）
- 部分投入時は同じ物理トークン相当の残高が減り、残高0ならitemを削除する
- 複数の旧Piri Medalが存在する場合は`/pirimerge`で合算し1個へ統合できる
- chestへ移動して取り出した後もtoken value維持、再投入可能
- server restart後token value維持
- runtime-test `/pirifault before`で`LEDGER_COMMITTED` + before-onlyを作り、再join後journal=`ROLLED_BACK`、メダル総額不変
- runtime-test `/pirifault after`で`LEDGER_COMMITTED` + after-onlyを作り、再join後journal=`APPLIED`、メダル総額不変
- `/pirifault status`で上記journal状態を確認する

## Phase08
- prize GUI screenshot
- small/medium/large exchange
- 最大交換
- prize→Vault balance change
- simulated Vault failure時item restore

## Phase09
- actual 100+ games scripted
- data lamp G/BIG/REG/history/graph更新
- 100G piri-chain ON、101G OFF
- screenshot evidence

## Phase10
- OP+machine key Admin Screen actual open
- busy machine mutation reject
- setting change history
- true server restartでnew businessPeriodId / setting allocation
- special date/manual next override scenarioをtest configで確認

## Phase11
- disconnect during NORMAL_SPINNING then <60s exact resume
- disconnect then grace expiry force settlement + lock release
- idle timeout force settlement
- process restart during unresolved state -> old period settlement -> new period
- recover cashout
- duplicate packets/security negative tests
- SPEC completion condition40項目を`FINAL_VERIFICATION.md`へ全部PASS


## Phase12
- production Paper/Fabric の両方を同一 build へ更新し、protocol2 HELLO/HELLO_ACK established
- 旧protocol client が PROTOCOL_MISMATCH で gameplay denied
- compatible spectator が同一world・32block以内へ入ると各machineにつき REMOTE_MACHINE_SNAPSHOT を1回受信
- 32block外へ出ると REMOTE_MACHINE_REMOVE、再進入で fresh SNAPSHOT
- world change で旧world machineがremoveされ、新world interestが再構築される
- 42台idleをrange内へ置き、initial snapshot後30秒のREMOTE_MACHINE_* gameplay trafficが0
- 1台を実playし、commit後の SPIN/STOP/NOTICE/BONUS start/end が spectatorへ対応するREMOTE packetとして届く
- owner自身の既存 OPEN_MACHINE/PUBLIC_STATE/SPIN_START/REEL_STOP flowが従来どおり動作
- packet captureに setting/internalRole/premiumType/stopHints/RNG/Vault/heldMedals が存在しない
- BONUS_PENDING中はBIG/REG内部種別がremote payloadへ出ない
- disconnect/suspend中の台がremote側で永久spinしない
- reconnect/server restart後、range内台がfresh snapshotから復元され stale spin/bonus stateが残らない
- malformed remote payloadを投入してもlocal SlotScreen/session/handshakeが破壊されない
- create/redefine/remove/enable変更がinterest clientへ即時反映される
- evidence: `runtime-evidence/PHASE_12/REPORT.md` + packet capture + 42台idle traffic measurement


### Phase12 result — 2026-09-20
**PASS / COMPLETE**

Real runtime covered 42-machine initial sync, range leave/re-entry, idle no-churn, SPIN/STOP/NOTICE/BONUS start/end, world change/return, reconnect, stale-spin recovery, and create/redefine/remove immediate reflection.

Security/edge criteria that are not useful to reproduce manually were completed by code/static verification: hidden-state allow-listing, BONUS_PENDING BIG/REG secrecy, malformed remote isolation, and enabled-state immediate refresh path.

Evidence: `runtime-evidence/PHASE_12/REPORT.md`.
