# 新規セッションへの引き継ぎ

確認日: 2026-09-14（Phase05 BAR確定目対応完了後）。この文書は現在の実装と承認済み変更の案内であり、製品仕様の正は [SPEC.md](SPEC.md)。会話履歴・旧作業用コピーがなくても、このリポジトリから再開できる。

## 現在地と読む順序

1. [CODEX_START.md](CODEX_START.md) の依頼範囲と開始規則を確認する。
2. [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) 冒頭とPhase表を確認する。Phase01–05はCOMPLETE、Phase06–11はNOT_STARTED。作業中Phase、既知の未解消BLOCKED、承認待ちはない。
3. この文書と [SPEC_CONFLICT.md](SPEC_CONFLICT.md) の解消状況を読む。
4. 実装続行を依頼された場合だけ、最小NOT_STARTEDである [Phase06指示書](phases/PHASE_06_BONUS_EFFECTS.md)、指定SPEC章、[RUNTIME_ACCEPTANCE.md](RUNTIME_ACCEPTANCE.md) のPhase06を読む。この確認runでは新しいPhaseを開始していない。
5. Phase06の指定章に加え、今回までの変更が関わるSPEC第5、9–14、18、33–34、92、120、132、138章も読む。1runで1Phase、完了後は次Phaseへ進まず終了する。

`Current phase: 05` は最後に扱ったPhaseで、作業中という意味ではない。過去のBLOCKED、未実施、各Phase終了時の「次Phase未着手」は履歴。最新状態はPhase表と末尾のPhase05「BAR確定目対応」で判断する。未適用の提案patchを再適用しない。

## 既に解消・実装済みの変更

| 事項 | 引き継ぐ確定内容 | 正本・詳細 |
|---|---|---|
| v4移行 | 旧Phase01を引き継ぎ、実Minecraft Runtime Acceptance不足を補完。`v4 migration applied` 記録済み。再移行・Phase01破棄・statusのテンプレート上書きは不要 | [移行記録](docs/V4_MIGRATION.md)、[実機証跡](runtime-evidence/PHASE_01/REPORT.md) |
| Phase03 BLOCKED解除 | 第120章の単語間隔20pxを正として、第138章を4 logical columnsへ修正。4×5px=20px | SPEC第120/138章、[UI実装](docs/PHASE_03_CLIENT_UI.md) |
| 音源生成中止 | 実音源はユーザーが別途提供する。実装側は所定ファイル名のOGGを受け入れて再生する。今回追加した自動生成音源・生成コード・Vorbis依存/タスクは削除済み。内容の推測・代替生成をしない | SPEC第32/106/129章、[音源仕様](docs/USER_AUDIO.md) |
| Phase05 BLOCKED解除 | 第101章の秘匿を優先し、PUBLIC_STATE用の明示的mappingを実装。Paper内部GameState・DB値・第18/131章の遷移は維持 | SPEC第92/101章、下表 |
| リール方向反転 | 通常はindex減少・画面下向き。プレミア逆回転の最初500msはindex増加・画面上向き、その後300msで通常方向の18 symbols/secへ加速。RESUME・slip・停止補間も同期。固定配列/行定義は維持 | SPEC第5/25/33/34/90/132章、[リール実装](docs/PHASE_04_REELS.md) |
| BAR確定目 | 単独BIG/REGの両方でBAR-BAR-BARを出現可能にした。0枚、Piri Chance点灯、権利保持、種類非公開。ハズレでは有効5ラインすべてでBAR揃いを禁止 | SPEC第9–14/30/92章、[通常ゲーム実装](docs/PHASE_05_GAME_LOGIC.md) |

Phase03/05のBLOCKEDは、それぞれ承認済みSPEC反映→IN_PROGRESS復帰→実装・test/build/runtime全PASS→COMPLETEまで完了している。

### 内部状態と公開状態

| Paper内部GameState（維持） | PUBLIC_STATE.gameState |
|---|---|
| BONUS_PENDING_BIG / BONUS_PENDING_REG | BONUS_PENDING |
| BONUS_ENTRY_BETTED_BIG / BONUS_ENTRY_BETTED_REG | BONUS_ENTRY_BETTED |
| BONUS_ENTRY_SPINNING_BIG / BONUS_ENTRY_SPINNING_REG | BONUS_ENTRY_SPINNING |

[PublicGameState](common/src/main/java/jp/pirijuggler/common/protocol/PublicGameState.java) と [Session.publicGameState()](paper/src/main/java/jp/pirijuggler/paper/session/Session.java) の網羅的switchを使用する。内部enumを通信JSONへ直接serializeしない。入賞ゲーム第3STOPで777または77BARが実表示され、種類が判明した後だけBIG_READY/BETTED/SPINNING・REG_READY/BETTED/SPINNINGを公開できる。

BAR-BAR-BARは種類が判明した扱いにしない。公開BONUS_PENDING、bonusCount=0、BONUS_STARTなし。REG入賞形SEVEN-SEVEN-BARは変更していない。

### BAR候補と回転方向の接続上の注意

[StopCatalogue](paper/src/main/java/jp/pirijuggler/paper/reel/StopCatalogue.java) は全9,261形でBARの有効ラインを別に評価する。MISS 5,242形＋厳密BAR8形＝BONUS 5,250形を、単独BIG/REG共通で使用する。BAR形は他小役・チェリー・777・77BARと混在しない。CHERRY/PIERO重複役は従来の2枚/14枚を維持し、0枚BAR形へ置き換えない。抽選weightは変更していない。

候補はslip→rank→左中右index順。BONUSのBAR形は成立ラインrank0–4、MISS形はrank5。slipは `(pressedIndex-targetStopIndex+21) mod 21`。Fの単独BIG用候補もBONUSへ同期済み。BはCHERRY_BIG限定を維持。

[ReelMotion](common/src/main/java/jp/pirijuggler/common/reel/ReelMotion.java) をPaperとFabricで共有する。停止アニメーションは整数の終点へ厳密に固定し、0番停止で位相が21近傍に残る丸め誤差を修正済み。方向変更前の式や古い候補数へ戻さない。

## 実装済みの範囲と未実装の境界

| 範囲 | 現在の実装 |
|---|---|
| Phase01 | Gradle/Paper/Fabric基盤、PIRI envelope、HELLO/ACKとprotocol拒否、config validation、専用DB/Simulator executor。v4修正済み |
| Phase02 | SQLite schema v4の13 tables、WAL/transaction、台登録、占有、GRACE/SAFE session、営業期間、PDC台鍵/管理入口 |
| Phase03 | SlotScreen、9 PNG、固定UI/bitmap文字、入力/sequence、描画、ユーザーOGG読み込み・SoundEvent登録・再生、JAR作成導線 |
| Phase04 | 固定リール、全9,261評価、StopSolver/ReelRound、RTT補正、3 motion profile。方向とBAR候補はPhase05で更新済み |
| Phase05 | 台別/event/Simulator RNG分離、LEVER時1回抽選、通常BET・held不足分補充・STOP・CREDIT優先払出し・無料REPLAY・ボーナス権利保持・公開mapping・Simulator |

[NormalGame](paper/src/main/java/jp/pirijuggler/paper/game/NormalGame.java) の変更は [GameStore](paper/src/main/java/jp/pirijuggler/paper/database/GameStore.java) がsession/sequence/停止位置/統計/graph/UUID受領記録を同じDB transactionで保存し、成功後に公開する。DB callbackまでBUSY。rollback時は直前の確定状態とreelを維持する。既存metadataのGAME_TXキーで冪等性を保証し、schema v4は変更していない。

次の機能は今後のPhaseで接続するため、完成済み扱いにしない。

- Phase06: 実台のボーナス入賞、BIG/REG手動消化、プレミア抽選・演出・音event制御。既存のリール/音声再生部品とSimulator内のボーナス総額計算は、この実台機能の完成を意味しない。
- Phase07–10: 経済/メダル連携、景品交換、データランプ/グラフUI、管理機能/イベント統合。先行実装済みのheld補充・統計保存・管理入口を利用する。
- Phase11: 期限切れ・再起動時の未完ゲームForce Settlementと回復統合。未完権利のあるsnapshotを消さず、現状の起動/ロック保護を維持する。開発DBを含め、権利のあるDBを無断で削除しない。

## 現物と照合済みの完了証跡

2026-09-14の最新Phase05 JARに対し、以下の保存済みPASSとSHA-256を照合した。この引き継ぎ確認runでは製品コード・SPEC・JARを変更せず、test/build/runtimeを再実行していない。

| 検証 | 結果・証跡 |
|---|---|
| Unit/Integration | 144件PASS: common24 / paper99 / fabric19 / asset-tools2。`*/build/test-results/test/TEST-*.xml` と `runtime-evidence/PHASE_05/gradle-test.log` |
| Build/package/helper | すべてexit0。`runtime-evidence/PHASE_05/gradle-build.log`, `helper-build.log`, `artifact-verification.json`。distと受入JARのSHA一致 |
| 通常ゲーム | 実Minecraft 100ゲーム、REPLAY15回、114 assertions PASS。残高/DB/表示/BAR不成立/公開状態/Simulatorコマンド確認。[レポート](runtime-evidence/PHASE_05/REPORT.md) |
| リール回帰 | 全78回転・234 STOP、3方向profile、102 assertions PASS。全停止系列722,358とF第2停止7,938条件の検証。[レポート](runtime-evidence/PHASE_05_REEL_REGRESSION/REPORT.md) |
| BAR BIG/REG | 各実Minecraftで9 assertions PASS。BAR実表示、0枚、点灯、種類秘匿、権利保持。96ゲームDB統合試験もPASS。[BIG](runtime-evidence/PHASE_05_BAR_BIG/REPORT.md) / [REG](runtime-evidence/PHASE_05_BAR_REG/REPORT.md) |
| 接続回帰 | 同一JARで実HELLO/ACK正常とprotocol不一致拒否がPASS。[レポート](runtime-evidence/PHASE_05_PHASE01_REGRESSION/REPORT.md) |
| Simulator | 固定テストseed `0x504952494A554747`、6設定×1,000万ゲームで全目標±0.20ポイント以内。[結果](runtime-evidence/PHASE_05/simulator-10m.json)。本番seedは非公開 |

実機用の強制weight/初期資金/キー操作helperは `runtime-test-support/` に隔離し、本番JARに含めない。通常100ゲームは専用configの設定1でボーナスweightをMISSへ移して継続し、最後にCHERRY_BIGを確認した。BAR BIG/REGはそれぞれ単独役100%の隔離config。製品標準configは維持している。成功結果だけでなく、失敗attemptや修正前バックアップも保存済み。

## JARと音源

[BUILD_JARS.md](BUILD_JARS.md) の手順で `user-audio/` にユーザーのOGGを置き、`build-jars.bat` を実行すると `dist/` にPaper/Fabric配布JARができる。ソース編集後も同じ手順を使える。resource pack差し替えも可能。同名ならuser-audioを直接resources配置より優先する。10種類のファイル名・ID・用途は [docs/USER_AUDIO.md](docs/USER_AUDIO.md) と [user-audio/README.md](user-audio/README.md) にある。

現在のOGGは未配置。音源の品質・内容をRuntime Acceptanceの完成条件にせず、欠損時は無音で継続する。音声合成・エンコード・代替生成を再導入しない。配布は `dist/piri-juggler-paper-1.0.0.jar` と `dist/piri-juggler-fabric-1.0.0.jar`。共通コードは両JARへ同梱済み。

## このPCでの検証再開手順

リポジトリは `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED`。検証ランナー、helperソース、SPEC/画像の固定値、Paper実機JAR、証跡はすべてここにある。旧リポジトリ、会話の作業用stage、外部outputsフォルダからソースをコピーし直す必要はない。

Java 21/JDK、Gradle Wrapper 8.14.3、Loom 1.10.5、Paper 1.21-130、Minecraft 1.21、Loader 0.16.14、Fabric API 0.102.0+1.21、Yarn 1.21+build.9。実機試験にはWindowsのOpenGLデスクトップが必要。依存取得時はネット接続が必要になる。

このPCでは2026-09-14確認時点で `python` はPATHに無く、`py -3` も利用可能なPythonを検出しなかった。前回検証に使用したPythonは次の場所にある（Python 3の標準ライブラリだけを使用）。Codex runtimeの場所が変わった場合は利用可能なPython 3へ読み替える。

```powershell
Set-Location -LiteralPath 'C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED'
$piriPython = 'C:\Users\PC_user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$env:PYTHONIOENCODING = 'utf-8'
& $piriPython --version
java -version
.\gradlew.bat test --console=plain
.\gradlew.bat build packagePiriJars --console=plain
.\gradlew.bat -PruntimeAcceptance=true -PruntimeScenario=phase05-game -PruntimeEvidencePhase=PHASE_05 :runtime-test-client:build :runtime-test-paper:build --console=plain
```

Paper JARは `runtime-evidence/PHASE_01/work/downloads/paper-1.21-130.jar` に配置済み。SHA-256は `ab9bb1afc3cea6978a0c03ce8448aa654fe8a9c4dddf341e7cbda1b0edaa73f5` で、各runnerが起動前に照合する。持ち運び時にもこのファイルと証跡を保持する。

現在のPhase05回帰試験を再実行する必要がある場合は次を順番に実行する。各コマンドの失敗時は修正してから後続へ進む。共有localhostポート/実画面を使うため同時起動しない。runnerは隔離serverで `eula=true`、`online-mode=false` を使用する。通常のMinecraft profileは変更しない。

```powershell
$env:PIRI_PHASE04_EVIDENCE = 'PHASE_05_REEL_REGRESSION'
& $piriPython runtime-test-support/run_phase04.py
& $piriPython runtime-test-support/run_phase05.py
$env:PIRI_PHASE01_EVIDENCE = 'PHASE_05_PHASE01_REGRESSION'
& $piriPython runtime-test-support/run_phase01.py
$env:PIRI_BAR_BONUS = 'BIG'
& $piriPython runtime-test-support/run_phase05_bar.py
$env:PIRI_BAR_BONUS = 'REG'
& $piriPython runtime-test-support/run_phase05_bar.py
& $piriPython docs/verify_phase05_artifacts.py
```

使用ポートは接続回帰25585、リール回帰25588、通常ゲーム/BAR25589。`run_phase01.py` の既定出力は移行時のPHASE_01なので、上記の回帰用環境変数を省略しない。再実行すると各回帰フォルダの最新result/logを更新するため、必要な過去証跡を保持し、REPORTも新しいrunに合わせて更新する。Phase06以降のrunner/fixture追加は、そのPhaseの実装を依頼された時に行う。

`docs/extract_spec_lock.py` は読取専用検査ではなく、spec-lockとconfig/schema/client-uiの生成ファイルを書き換える。今回は107章のhashを読取専用で検査し、再生成はしていない。将来承認されたSPEC変更時だけ差分を確認して再生成する。

## 今回の引き継ぎ確認

[確認レポート](runtime-evidence/SESSION_HANDOFF/REPORT.md) と [照合結果](runtime-evidence/SESSION_HANDOFF/verification.json) に、文書導線、SPEC-lock、保存済みtest/runtime結果、JAR、移行/BLOCKED解消、音源欠損、必要ファイルの存在確認を保存した。新Phaseへの状態変更や製品実装は行っていない。
