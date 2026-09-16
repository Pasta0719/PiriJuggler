# IMPLEMENTATION_STATUS.md

このファイルはCodexが各Phase終了時に更新します。

## 現在の再開情報 — 2026-09-16確認

- Phase09はCOMPLETE。runtime evidenceは `runtime-evidence/PHASE_09/REPORT.md`。
- Phase10はIN_PROGRESS。仕様横断精査とproduction実装は完了し、`runtime-evidence/PHASE_10/REPORT.md` にbuild/runtime待ちとして記録した。
- Phase10でSPEC 68章の「Admin Screen closeでAdminSession失効」に対してC2S packetが欠落している矛盾を確認。ユーザー承認により `ADMIN_CLOSE=16` を追加し、`docs/spec-amendments.json` に記録した。
- Phase10実装HEADはまだユーザー環境でbuild未確認。Phase09で最後に確認済みのbuild HEADは `1be4668f981ed051478941cd39e6e77447113353`。
- Phase10は実Minecraft Runtime Acceptance未実施のためCOMPLETE禁止。
- 旧履歴中のPhase06–08状態欄は過去の未更新記録を含むため、Phase10作業では遡及変更しない。

## Phase 状態

| Phase | 状態 | 概要 |
|---|---|---|
| 01 | COMPLETE | Gradle / Paper / Fabric / Protocol Handshake |
| 02 | COMPLETE | SQLite / Machine Registration / Session |
| 03 | COMPLETE | Fabric Slot Screen / Input / Base UI |
| 04 | COMPLETE | Fixed Reels / 9261 Precompute / Stop Solver |
| 05 | COMPLETE | RNG / Normal Game / Replay / Simulator |
| 06 | NOT_STARTED | Piri Chance / Premium / Audio / BIG / REG |
| 07 | NOT_STARTED | Vault / CREDIT / Held Medals / Medal Bundle |
| 08 | NOT_STARTED | Prize Exchange / Vault Exchange / Transactions |
| 09 | COMPLETE | Data Lamp / Graph / Piri Chain |
| 10 | IN_PROGRESS | Admin / Settings / Startup Allocation / Events |
| 11 | NOT_STARTED | Suspend / Restart Recovery / Hardening / Final Tests |

Allowed states:
`NOT_STARTED`, `IN_PROGRESS`, `COMPLETE`, `BLOCKED`

## Current work

```text
Current phase: 10
Last completed phase: 09
Active implementation: production code complete; build/runtime verification pending
Open blockers: none in implementation; local build and Runtime Acceptance remain
Next implementation phase: 11 only after Phase10 COMPLETE
```

## Build status

```text
Latest verified HEAD: 1be4668f981ed051478941cd39e6e77447113353 (Phase09)
Latest build-jars.bat main build: PASS (Phase09; BUILD SUCCESSFUL in 28s; 27 actionable tasks)
Latest runtime helper build: PASS (Phase09; BUILD SUCCESSFUL in 3s; 8 actionable tasks)
Phase10 current HEAD: NOT YET VERIFIED by local build
Phase10 evidence draft: runtime-evidence/PHASE_10/REPORT.md
```

## Implementation notes

Codexは各Phase終了時にここへ追記してください。
長文の日記にはせず、後続Phaseが必要とする事実だけを残してください。

## Phase 01 — COMPLETE（v3当時の履歴。後続のv4 migrationで補完済み）

### 変更ファイル

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, Gradle Wrapper一式。
- `common/build.gradle.kts`, `common/src/main/java/jp/pirijuggler/common/protocol/` とcodec/SpecLockテスト。
- `paper/build.gradle.kts`, `paper/src/main/java/jp/pirijuggler/paper/` のplugin/config/network/threading/action、`plugin.yml`, `config.yml` と受入テスト。
- `fabric/build.gradle.kts`, `fabric/src/main/java/jp/pirijuggler/fabric/` のclient/payload/handshake/日本語ErrorCode mapping、`fabric.mod.json` と統合・payloadテスト。
- `docs/spec-lock.json`, `docs/extract_spec_lock.py`, `docs/verify_phase01_artifacts.py`, `docs/PHASE_01_FOUNDATION.md`。

### 重要な確定事項

- Java 21、Gradle 8.14.3（公式distribution SHA-256固定）、Loom 1.10.5。Minecraft/Paper 1.21、Loader 0.16.14、Fabric API 0.102.0+1.21、Yarn 1.21+build.9はSPECどおり。
- Paper/Fabricの配布jarにcommonクラスを直接同梱。Gson 2.10.1は両platform提供、SnakeYAML 2.2はPaper提供。Fabricにpaper実装・設定・spec-lockを同梱しない。
- `piri:main` のcustom payloadはPIRI envelopeそのもの。追加の配列長prefixなし。UTF-8/JSON/VarInt/長さ/magic/packet IDを検証。
- clientはチャンネル登録を確認後にHELLOを1回送信。envelopeとpayload両方のprotocol=1を検証し、ACK前・不一致・切断時は台利用不可。通常のmodVersion文字列差だけでは拒否しない。
- serverの利用可否入口は `PiriJugglerPlugin.canUseSlot(UUID)`。実configが無効ならSEVEREで記録し利用拒否。未定義のERROR/ACTION_REJECTED JSONフィールドは追加していない。
- DBは専用single-thread executor、Simulatorは別のfixed-size-1 executor。callbackはmain thread。ゲーム状態へのアクセスもmain threadで検査。
- `ActionGate` はsessionごとに使用し、保存したlastClientSequenceで初期化。古い/同値は無返信で破棄。BUSYはACTION_REJECTED/BUSYで拒否しqueueしない。STOPはcurrent spinId一致必須。
- 金銭/STOP開始時にBUSY leaseを取得し、main-thread callbackのfinallyでcloseする。失敗時の解除もテスト済み。
- configは第124章全文と第20章の72 role weightsを転記。欠損キーをデフォルト補完で隠さず検証。仕様固定JSONは36章のhashと後続Phaseに必要な表を保持。
- SPECは数値のDB schema versionを定義していない。`database.schemaVersion` はnullと説明を記録し、数値は推測しない。Phase 01はDB schemaを操作しない。
- 指定フォルダにGit管理情報は存在しなかった。既存SPEC/Phase指示書は変更していない。

### 完了条件と検証

- [x] `./gradlew test` 相当の `.\gradlew.bat test --console=plain` がexit 0。
- [x] `./gradlew build` 相当の `.\gradlew.bat build --console=plain` がexit 0。
- [x] Paper jar生成: `paper/build/libs/piri-juggler-paper-1.0.0.jar`。
- [x] Fabric jar生成: `fabric/build/libs/piri-juggler-fabric-1.0.0.jar`。
- [x] common jar生成: `common/build/libs/piri-juggler-common-1.0.0.jar`。
- [x] envelope encode/decodeテスト（最大32767 bytes、VarInt境界、UTF-8、不正JSON等）。
- [x] protocol mismatchテストとPaper/Fabric両handler間handshakeテスト。
- [x] config validationテスト（初期値、欠損、合計、範囲、不正YAML、pattern等）。
- [x] spec-lockにprotocolVersion、全33 packet IDs、固定constants。全19 ErrorCodeも照合。
- [x] 本番コードにTODO/FIXME/stub/未実装例外なし。
- テスト計65件: common 18 / paper 42 / fabric 5。失敗0、error0、skip0。
- `python docs/verify_phase01_artifacts.py`: exit 0。Java 21 class、metadata、common同梱、Fabric remap、server-only code/data隔離を確認。
- 実ゲームのPaperサーバー起動とFabricクライアント接続による確認は未実施。自動テストは実装された双方のhandshakeとFabric payload codecまで検証。

Phase 02以降は未着手。次回はCODEX_START.mdに従いPhase 02を選択する。


## v4 migration preflight — COMPLETE
既存Phase01コードと履歴を旧リポジトリから引継ぎ。旧Phase02のIN_PROGRESSは中断時の履歴であり、このrunではPhase02作業を行わない。下記の全検証PASSを確認し、移行を完了した。



### v4 migration applied

- Runtime実行: 2026-09-12T04:39:31.331114+00:00 ～ 2026-09-12T04:41:10.276891+00:00。
- 旧 `PiriJuggler_Codex_Workflow` のPhase01実装・テスト・状態履歴を、このv4フォルダへ引継ぎ。テンプレート上書きなし、旧ソース/テストの削除なし。旧フォルダも保存。
- Phase01範囲でHELLO/ACKのtype除去、ERROR/PROTOCOL_MISMATCH応答、SEQUENCE_OLD拒否、TOKEN_REVIEW_REQUIRED、v4 config validationを修正。初期config・spec-lockをv4に同期（DB schema version 4）。
- `runtime-test-support` のPaper/Fabric補助コードはopt-inのテスト用module。本番jarに含まれない。
- Unit/Integration: 74件、失敗0、error0、skip0。test/buildはともにexit 0。
- 実Paper 1.21-130 + 実Fabric 1.21 / Loader 0.16.14 / API 0.102.0+1.21、PiriRuntimeTest、localhost接続を確認。
- 正常run: HELLO/HELLO_ACK protocol1、server/clientともgameplayAllowed=true。
- 不一致run: protocol2のHELLOにERROR(PROTOCOL_MISMATCH)、server/clientともgameplayAllowed=false。
- 両runの未処理例外0、server/client exit 0。実画面PNG保存済み。
- Evidence: `runtime-evidence/PHASE_01/REPORT.md`, `result.json`, `server.log`, `client.log`, `screenshots/`。
- 引継ぎhash照合: `runtime-evidence/PHASE_01/migration-provenance.json`。成果物hashは同result.jsonに記録。
- Artifact検証: `python docs/verify_phase01_artifacts.py` PASS（Java21/metadata/common同梱、Fabric remap、server専用コード隔離、runtime helper非同梱）。
- 詳細手順・変更ファイル: `docs/V4_MIGRATION.md`, `docs/PHASE_01_FOUNDATION.md`。
- v3開発DBは未検出。DB削除・schema操作は実施していない。
- ユーザー指定に従い、このrunはmigration preflightで終了。Phase02の実装は未着手。Phase02行のIN_PROGRESSは旧作業停止時からの履歴を保持したもので、このrunで進めたものではない。



## Phase 02 — COMPLETE (v4)

- SQLite JDBC schema v4（13 tables / active location unique index）、専用DB thread・WAL/FULL/FK/busy timeout、旧schema起動拒否、transaction rollback、shutdown flush/checkpointを実装。
- JVM start timeで営業期間を識別。同一JVM reloadは再利用、真の再起動は旧ready sessionをSAFE化して新period/stats/graphを追加。現在profileの初期設定割当を同一transactionで実施。
- OP向けmachine create/redefine/remove/list/infoとkey give。Button raytrace 5.0、重複座標拒否、soft delete、台ID非再利用、busy時の変更拒否。world blockは本番pluginから変更しない。
- Session全28 snapshot columnsを保持。player/machineの二重所有を拒否。GRACE exact resume、SAFE resume、disconnect/close、空session削除と未完cashout保護を実装。資産をwalletへ移動しない。
- PDC台鍵とmemory-only AdminSession入口、OP/owner/machine/sequence/expiry検証。FabricにOPEN/PUBLIC/ADMIN受信状態を追加。SESSION_ENDはsessionIdだけ。
- Unit/integration **99件 PASS**（common20 / paper73 / fabric6、既存74件を保持）。test/build exit0。Artifact検証PASS。
- 実Paper 1.21-130 + 実Fabric 1.21 / Loader0.16.14 / API0.102.0+1.21でPhase02 **19 assertions PASS**。2クライアント同時接続、実Button登録・右クリック、別JVM restart、占有拒否、redefine/remove、履歴保持、SQLite整合性を確認。全server/client exit0、未処理例外0。
- Runtime: 2026-09-12T14:12:52.064263+00:00 ～ 2026-09-12T14:16:06.978017+00:00。途中のテスト補助競合・判定タイミングのFAILもattemptsへ保持し、修正後に全項目を再実行。
- 同一buildでPhase01正常/mismatch実Minecraft回帰テストもPASS。migration時の既存evidenceは変更していない。
- Evidence: `runtime-evidence/PHASE_02/REPORT.md`, `result.json`, `server.log`, `client.log`, `screenshots/`, `gradle-test.log`, `gradle-build.log`。
- Phase01 regression evidence: `runtime-evidence/PHASE_02_PHASE01_REGRESSION/REPORT.md`, `result.json`。
- 実装詳細/再現手順: `docs/PHASE_02_DATABASE_MACHINE.md`。Schemaと指定章をspec-lockへ抽出（58章hash）。
- 後続への境界: Phase02が作成するゲーム状態はSEATED_READY。未完ゲームのForce SettlementはPhases04–06のゲーム実装を用いてPhase11で統合する。現段階では未完snapshotの権利を消さず、ロック解放と期間切替を拒否する。Slot ScreenはPhase03、Admin mutations/UIはPhase10。
- **Phase03には進まず終了。**

## 履歴: Phase03の旧BLOCKED — SPEC_CONFLICT_WORD_GAP（解消済み）

- CODEX_START.md、Phase03指示書、指定SPEC章とRuntime Acceptanceを読んでpreflightを実施。Current phaseを03へ更新。
- 第120章（4095–4096行）はglyph scale5 / word gap20px、第138章（4882行）はword gap=3 logical columns。同じPIRI CHANCEの単語間隔が20pxと15pxになり同時成立しない。
- 第118/130章とCODEX_START.mdの指示に従いCONFLICTとして停止。最小修正案は第138章のword gapを4 logical columnsへ変更して20pxへ揃えること。SPECは未変更、承認待ち。
- Evidence: `runtime-evidence/PHASE_03/REPORT.md`, `result.json`, `word-gap.patch`（提案のみ、未適用）。
- Phase03の本番コード・生成アセットは未変更。test/build/runtimeは未実施。直前の99 tests / build / Phase02 runtime PASS記録は前Phaseのものとして保持。
- Phase01/02のCOMPLETEと既存実装・証跡を維持。Phase04には進んでいない。


### Phase03再開 — SPEC_CONFLICT_WORD_GAP 解消済み
ユーザー承認によりSPEC第138章の単語間隔を4列へ変更（scale5で20px）。SPEC_CONFLICT.mdへ解消を追記し、Phase03をIN_PROGRESSへ戻した。Phase03だけを実施する。

## Phase03 — COMPLETE (v4 / ユーザー提供音源方式)

- 第138章の単語間隔を4列（scale5で20px）へ修正し、SPEC_CONFLICT.mdに解消済みを追記した。
- Java2D AssetGeneratorと9 PNG、5×7 bitmap glyph、v3図柄座標の完全転記・参照元保存、画像SHAテストを追加した。
- SlotScreen、固定座標・等比倍率・黒余白、linear filtering、ランプglow、リール補間とserver stopIndex、status、7segment、HUD非表示、T chat、rebind可能STOPキー、key edge / mouse100ms debounce、ESC一度送信・2000ms画面だけtimeoutを実装した。
- 遅延PUBLIC_STATEによるsequence再使用を防止し、RESUMEの停止済みリールを固定する。資産はPUBLIC_STATEを正とし、クライアントで仮増減しない。
- ユーザー指示に従い第32/129章の音源生成要求を撤回。今回追加した生成音源10個・生成コード・Vorbis依存・生成タスクを削除。Fabricはユーザー提供OGGの読み込み / SoundEvent10種登録 / 再生だけを行う。未配置でもbuild/test/runtime成功。
- `user-audio/` へ必要な音源を配置し `build-jars.bat` で自由に再コンパイルできる。`dist/` にPaper/Fabric JAR。日本語手順: `BUILD_JARS.md`, `user-audio/README.md`, `docs/USER_AUDIO.md`。
- Unit/integration 111件PASS（common20/paper73/fabric16/asset-tools2、既存99件維持）。test/buildと配布バッチexit0。
- 実Paper/FabricのPhase03受入23項目PASS。実キー/マウス入力、GUI実画像、全リールのpixel存在、HUD抑制、chat、音源欠損時継続、server closeを確認。初回のscissor不具合もattempt履歴に残し、修正後の全再実行でPASS。
- 同一JARでPhase01正常/mismatch実Minecraft回帰もPASS。common/Paper jarはPhase02完了時と同一SHA。helperは本番jarへ非同梱。
- 表示fixtureはテスト専用helperによる公開packet。ゲーム抽選や資産移動の完成を意味せず、Phase04以降のゲーム実装は行っていない。data history/graphはPhase09で統合する。
- Evidence: `runtime-evidence/PHASE_03/REPORT.md`, `result.json`, `screenshots/`, `artifact-verification.json`, `runtime-evidence/PHASE_03_PHASE01_REGRESSION/REPORT.md`。
- 実装・再現手順: `docs/PHASE_03_CLIENT_UI.md`。
- **Phase03で終了。Phase04はNOT_STARTEDのまま。**


## Phase04 — COMPLETE (v4 / 完了当時の記録。候補数と方向はPhase05で更新)

- commonの固定リール定数とReelMotionをPaper判定・Fabric表示で共有。全9,261評価、厳密候補と停止済みindexによる絞り込みを追加。
- StopSolverはslip→rank→左中右indexの決定論的順序。全候補を維持し、以後のSTOPで再選択。InternalRoleに重複役の表示とB/F適格条件を集約。
- 起動時に通常500,094通り / F 166,698通り（第2停止7,938条件）を全検証。最低候補数不足・到達不能はSEVEREを記録して台利用を拒否。
- サーバー所有ReelRoundに時刻・全RTT補正・停止受付閾値・sequence・owner/session検証を実装。FabricはSPIN_START受付待機とRESUMEの音再発火防止を統合。
- SPEC第106章に残っていた音源内容のテスト要求を、既に承認済みのユーザー提供OGG方式に統一。音声内容の生成は行わない。user-audio / build-jars.bat / dist構造を維持。
- Unit/integration 124件PASS（common23/paper82/fabric17/asset-tools2、既存111件を保持）。test/build/packagePiriJars/helper build exit0。成果物検証PASS。
- 実Minecraftで72回転・216 STOP（全9役×全6順＋F3役×全6順）を確認。全最終位置一致、5+ slip、F非テンパイ、3profile、音源欠損継続、12枚の実画像を確認。全プロセスexit0、未処理例外0。
- 同一JARによるPhase01正常/mismatch実Minecraft回帰PASS。helperは本番JARに含まれない。
- Phase04の本番実装はリール制御部品。テスト用役選択controllerから実パケットで検証した。通常の抽選/BET/払出し/永続化はPhase05以降が接続する。
- Evidence: `runtime-evidence/PHASE_04/REPORT.md`, `result.json`, `screenshots/`, `artifact-verification.json`, `runtime-evidence/PHASE_04_PHASE01_REGRESSION/REPORT.md`。
- 再現・接続手順: `docs/PHASE_04_REELS.md`。元ファイルは個別SHA検査と変更前バックアップ付きで反映した。
- **Phase04で終了。Phase05はNOT_STARTED。**


## 履歴: Phase05の旧BLOCKED — BONUS_TYPE_PUBLIC_STATE（解消済み）

- 新しいCODEX_START指示に基づき、最小NOT_STARTEDのPhase05を選択。migration適用済みを確認し、Phase05指示書・指定SPEC章・Runtime Acceptanceを読んだ。
- preflightで第18/92/131章のGameState公開と第101章のBIG/REG種類秘匿が両立するための公開用変換規則が未定義と判明。現行Session.publicState()が内部状態名を送るため、BONUS_PENDING_BIG/REGから入賞前に種類が識別できる。
- 第118/130章に従ってCONFLICTを記録し、製品実装前にBLOCKED。公開用状態3種類を定義する最小差分を作成したがSPECへは適用していない。
- 第20章weightと実BET/payoutによる6設定の理論機械割は第22章の記載と一致することを独立計算で確認した。確率や払い出しの調整は行っていない。
- Evidence: `SPEC_CONFLICT.md`, `runtime-evidence/PHASE_05/REPORT.md`, `result.json`, `bonus-public-state.patch`（提案のみ）。
- Phase05の製品コード・test/build・実Minecraft受入は未実施。Build statusの124件PASSは前回Phase04の記録であり、Phase05のPASSではない。
- Phase01–04のCOMPLETE・既存実装・配布JAR・証跡を保持。**Phase06には進まない。**


### Phase05再開 — BONUS_TYPE_PUBLIC_STATE解消済み

ユーザー承認により第92章に公開状態mappingを明記し、第18/101章から参照。内部GameStateと第131章の遷移は維持する。PublicGameStateを明示的に使い、Phase05のみ続行する。Phase06には進まない。


## Phase05 — COMPLETE (v4 / BAR追加修正前の検証記録)

- ユーザー承認の公開mappingをSPEC第92章に追加。第18/131章の内部状態・DB値を維持し、common/PublicGameStateとSession.publicGameState()の明示的switchで種類秘匿を実装。SPEC_CONFLICTは解消済み。
- RandomStreamsでSecureRandom masterから台別・event・Simulator streamを分離。RoleWeightsの整数1e9抽選はLEVER時だけ。STOPで再抽選せず、重複小役は1回払出し。
- 通常BET、held不足分補充、LEVER/STOP、CREDIT優先payout、無料REPLAY、BONUS_PENDING権利保持を実装。NormalGameの未確定snapshotはDB成功後に公開。
- GameStoreでsession/sequence/停止位置/G数/差枚/最大差枚/graph/UUID受領記録を1 transactionに保存。metadataのGAME_TXキーによるidempotency、専用DB executor callbackまでBUSY、ロールバック時の確定state維持を検証。schema v4の13 tablesは変更なし。
- `/piri simulator <setting> <games>` を専用workerで実装。通常/REPLAY/入賞BET/BIG・REG総BETとgross payoutを含む。runtime seedは非公開。
- 固定seed0x504952494A554747で6設定各10M試験が目標±0.20ポイント以内。142 tests PASS（common24/paper97/fabric19/asset-tools2、既存124件保持）。test/build/packagePiriJars/helper build exit0。
- 実Paper/Fabricで通常100ゲーム以上・実REPLAY・全停止順・BUSY拒否・CREDIT/BET/PAY・DB統計・表示一致を確認。実Simulator 100000コマンドも完了。
- テスト設定によるCHERRY_BIG実抽選でPaper内部BONUS_PENDING_BIGと公開BONUS_PENDINGを確認。client packetに内部役/設定/seed/入賞前BIG・REG状態の漏洩なし。全プロセスexit0、未処理例外0。同一JARのPhase01正常/mismatch実Minecraft回帰PASS。
- 通常回転を画面下向き、プレミア逆回転の最初500msを上向きへ反転。SPEC第5/25/33/34/90/132章、共有位相/slip、停止補間を同期。全72回転/216 STOPと3 profileの実client位相traceを同一JARで再確認（PHASE_05_REEL_REGRESSION）。固定配列・図柄行定義を維持。
- 音源生成なし。OGG未配置で全検証成功、user-audio/build-jars.bat/dist構成を維持。helperは本番JAR非同梱。
- Evidence: `runtime-evidence/PHASE_05/REPORT.md`, `result.json`, `simulator-10m.json`, `artifact-verification.json`, `screenshots/`, `runtime-evidence/PHASE_05_PHASE01_REGRESSION/REPORT.md`。実装・再現: `docs/PHASE_05_GAME_LOGIC.md`。
- 後続境界: 実台のボーナス入賞・消化/プレミアはPhase06。期限切れ/再起動Force SettlementはPhase11。未完権利の起動・ロック保護を維持。
- **Phase05で終了。Phase06はNOT_STARTED。**


## 履歴: Phase05追加修正の開始 — BAR揃い（直後の完了記録で完了済み）

ユーザー指示によりPhase05を再開。BAR-BAR-BARをBIG/REG共通の0枚ボーナス確定目へ変更し、MISS候補から除外する。直前の142 tests / Runtime PASSは追加修正前の記録として保持する。修正後の全検証完了まではIN_PROGRESS。Phase06には進まない。


## Phase05 — COMPLETE (v4 / BAR確定目対応)

- ユーザー承認の公開mappingをSPEC第92章に追加。第18/131章の内部状態・DB値を維持し、common/PublicGameStateとSession.publicGameState()の明示的switchで種類秘匿を実装。SPEC_CONFLICTは解消済み。
- RandomStreamsでSecureRandom masterから台別・event・Simulator streamを分離。RoleWeightsの整数1e9抽選はLEVER時だけ。STOPで再抽選せず、重複小役は1回払出し。
- 通常BET、held不足分補充、LEVER/STOP、CREDIT優先payout、無料REPLAY、BONUS_PENDING権利保持を実装。NormalGameの未確定snapshotはDB成功後に公開。
- GameStoreでsession/sequence/停止位置/G数/差枚/最大差枚/graph/UUID受領記録を1 transactionに保存。metadataのGAME_TXキーによるidempotency、専用DB executor callbackまでBUSY、ロールバック時の確定state維持を検証。schema v4の13 tablesは変更なし。
- `/piri simulator <setting> <games>` を専用workerで実装。通常/REPLAY/入賞BET/BIG・REG総BETとgross payoutを含む。runtime seedは非公開。
- 固定seed0x504952494A554747で6設定各10M試験が目標±0.20ポイント以内。144 tests PASS（common24/paper99/fabric19/asset-tools2、既存124件保持）。test/build/packagePiriJars/helper build exit0。
- 実Paper/Fabricで通常100ゲーム以上・実REPLAY・全停止順・BUSY拒否・CREDIT/BET/PAY・DB統計・表示一致を確認。実Simulator 100000コマンドも完了。
- テスト設定によるCHERRY_BIG実抽選でPaper内部BONUS_PENDING_BIGと公開BONUS_PENDINGを確認。client packetに内部役/設定/seed/入賞前BIG・REG状態の漏洩なし。全プロセスexit0、未処理例外0。同一JARのPhase01正常/mismatch実Minecraft回帰PASS。
- 通常回転を画面下向き、プレミア逆回転の最初500msを上向きへ反転。SPEC第5/25/33/34/90/132章、共有位相/slip、停止補間を同期。全78回転/234 STOPと3 profileの実client位相traceを同一JARで再確認（PHASE_05_REEL_REGRESSION）。固定配列・図柄行定義を維持。
- BAR-BAR-BARをBIG/REG共通の0枚確定目へ追加。BONUS候補5,250件（MISS5,242＋厳密BAR8）で選択し、MISSとその他候補のBAR成立を禁止。Piri Chance点灯、公開BONUS_PENDING、ボーナス種別・BONUS_START非公開。REGのSEVEN-SEVEN-BARは維持。96ゲームDB試験とBIG/REG各実Minecraft試験PASS（PHASE_05_BAR_BIG / PHASE_05_BAR_REG）。
- 音源生成なし。OGG未配置で全検証成功、user-audio/build-jars.bat/dist構成を維持。helperは本番JAR非同梱。
- Evidence: `runtime-evidence/PHASE_05/REPORT.md`, `result.json`, `simulator-10m.json`, `artifact-verification.json`, `screenshots/`, `runtime-evidence/PHASE_05_PHASE01_REGRESSION/REPORT.md`。実装・再現: `docs/PHASE_05_GAME_LOGIC.md`。
- 後続境界: 実台のボーナス入賞・消化/プレミアはPhase06。期限切れ/再起動Force SettlementはPhase11。未完権利の起動・ロック保護を維持。
- **Phase05で終了。Phase06はNOT_STARTED。**

## Phase09 — COMPLETE (2026-09-16)

- current-period stats / bonus history / graph / Piri Chain / data lamp UIを実Minecraftで確認。
- 100GでPiri Chain継続、101Gで解除を確認。
- `/piri data` と `/piri data <machineId>` を着席・物理アクセスなしで一般プレイヤーから利用でき、設定値そのものを公開しないことを確認。
- `/piri data <machineId>` で total/current G、BIG/REG/合算実績確率、差枚、最大差枚、直近bonus historyを確認。
- 最新HEAD `1be4668f981ed051478941cd39e6e77447113353` で `build-jars.bat` PASS。main build と runtime helper build はともに `BUILD SUCCESSFUL`。
- Evidence: `runtime-evidence/PHASE_09/REPORT.md`。
- **Phase09で終了。Phase10には進まない。**

## Phase10 — IN_PROGRESS (implementation complete, verification pending)

- SPEC指定章・Phase10 Runtime Acceptanceを横断確認。Admin Screen close通知だけ通信ID欠落の矛盾があり、ユーザー承認で `ADMIN_CLOSE=16` を追加。通常playの `CLOSE_REQUEST=10` は変更しない。
- Fabric Admin Screenを実装。id / setting / current-period stats / active profile / autoSetting / enabled / latest30 setting history / busyを表示し、setting / auto / enabled / daily resetを操作可能。
- serverは全admin packetでAdminSession ID / OP / owner / machine / sequenceを再検証。5分無操作・disconnect・restart・ADMIN_CLOSEで失効。busy中は閲覧のみ許可しmutationを拒否。
- canonical `/piri setting <id> <1-6>` を実装し、same settingは履歴なしno-op、history reasonはSPECどおり `MANUAL`。旧 `/piri machine setting` は互換aliasとして同じ処理へ統合。
- `/piri reset daily <id|all>` を実装。個別は現在period stats/history/graphだけresetしsetting/history/player assetsは維持。allは対象中1台でもbusyなら全体拒否し、対象全台を予約して1 transactionでreset。
- `/piri event status|next <profile>|next clear` を実装。既存startup allocationのmanual-next / special / weekday / default優先、pattern→guarantee順、eligible条件、transaction rollbackとnext override消費を再確認。
- 追加tests: `AdminStoreTest`, `StartupAllocationIntegrationTest`, `AdminSessionsTest`, Fabric `ClientSessionTest`, approved spec amendment merge。
- Evidence draft: `runtime-evidence/PHASE_10/REPORT.md`。
- **まだbuild/runtime未検証のためPhase10 COMPLETEではない。**
