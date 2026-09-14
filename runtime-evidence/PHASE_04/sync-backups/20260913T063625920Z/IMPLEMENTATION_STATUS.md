# IMPLEMENTATION_STATUS.md

このファイルはCodexが各Phase終了時に更新します。

## Phase 状態

| Phase | 状態 | 概要 |
|---|---|---|
| 01 | COMPLETE | Gradle / Paper / Fabric / Protocol Handshake |
| 02 | COMPLETE | SQLite / Machine Registration / Session |
| 03 | COMPLETE | Fabric Slot Screen / Input / Base UI |
| 04 | IN_PROGRESS | Fixed Reels / 9261 Precompute / Stop Solver |
| 05 | NOT_STARTED | RNG / Normal Game / Replay / Simulator |
| 06 | NOT_STARTED | Piri Chance / Premium / Audio / BIG / REG |
| 07 | NOT_STARTED | Vault / CREDIT / Held Medals / Medal Bundle |
| 08 | NOT_STARTED | Prize Exchange / Vault Exchange / Transactions |
| 09 | NOT_STARTED | Data Lamp / Graph / Piri Chain |
| 10 | NOT_STARTED | Admin / Settings / Startup Allocation / Events |
| 11 | NOT_STARTED | Suspend / Restart Recovery / Hardening / Final Tests |

Allowed states:
`NOT_STARTED`, `IN_PROGRESS`, `COMPLETE`, `BLOCKED`

## Current work

```text
Current phase: 04
Last completed phase: 03
```

## Build status

```text
Last ./gradlew test: PASS (Phase03 v4; 111 tests; exit0; runtime-evidence/PHASE_03/gradle-test.log)
Last ./gradlew build: PASS (Phase03 v4; exit0; runtime-evidence/PHASE_03/gradle-build.log; build-jars.bat also exit0)
```

## Implementation notes

Codexは各Phase終了時にここへ追記してください。
長文の日記にはせず、後続Phaseが必要とする事実だけを残してください。

## Phase 01 — COMPLETE

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
- `python docs/verify_phase01_artifacts.py`: exit 0。Java 21 class、metadata、common同梱、Fabric intermediary remap、server-only code/data隔離を確認。
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
- Artifact検証: `python docs/verify_phase01_artifacts.py` PASS（Java21/metadata/common同梱/Fabric remap/server専用コード隔離/runtime helper非同梱）。
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

## Phase 03 — BLOCKED: SPEC_CONFLICT_WORD_GAP

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
