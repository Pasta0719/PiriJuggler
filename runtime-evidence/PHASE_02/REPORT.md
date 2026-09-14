# Phase02 Runtime Acceptance — PASS

実行: 2026-09-12T14:12:52.064263+00:00 ～ 2026-09-12T14:16:06.978017+00:00（UTC）

Minecraft 1.21 / Paper 1.21-130 / Fabric Loader 0.16.14 / Fabric API 0.102.0+1.21 / Java 21。
localhost 127.0.0.1:25586、Phase02専用の隔離ワールド。PiriRuntimeTest と PiriRuntimeTest2 の実Fabricクライアントを使用。

自動テスト: **99件 PASS**（common 20 / paper 73 / fabric 6）。失敗・error・skipは0。
`gradlew.bat test --console=plain` と `gradlew.bat build --console=plain` はexit 0。実Paperは2プロセス、実Fabricは3起動ともexit 0。未処理例外0。

## 受入結果

| 確認項目 | 結果 |
|---|---|
| 実ワールドにButtonを配置 | PASS |
| 実プレイヤーの視線とcreateコマンドで登録 | PASS |
| 同一座標の重複登録を拒否 | PASS |
| 実セッション作成とOPEN_MACHINE / PUBLIC_STATE受信 | PASS |
| 登録Buttonのvanilla動作をキャンセル | PASS |
| 同一プレイヤーの2台目着席を拒否 | PASS |
| 占有中も台鍵によるAdminSession入口を開く | PASS |
| 切断時のGRACE snapshot永続化 | PASS |
| プロセス終了後のDB登録保持 | PASS |
| 実JVM再起動後も登録保持・新営業期間へ切替 | PASS |
| 旧セッションをSAFE化して保持 | PASS |
| SAFE再開時に同じsessionIdと現在営業期間を使用 | PASS |
| 2台の実Fabric同時接続で占有台の右クリックを拒否 | PASS |
| 空セッション終了後に別プレイヤーが着席可能 | PASS |
| 実Buttonへredefineして台ID・設定を保持 | PASS |
| remove後も実Buttonを維持 | PASS |
| 削除済み台IDを再利用しない | PASS |
| redefine/remove後も過去期間の統計・履歴を保持 | PASS |
| SQLite integrity / foreign keys正常 | PASS |

## 再現手順

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat -PruntimeAcceptance=true build --console=plain
python runtime-test-support/run_phase02.py
```

1. テスト補助moduleが実ワールドにStone Buttonを配置し、実プレイヤーをOPにする。
2. 実クライアントから視線更新、createコマンド、右クリックを送信し、本番pluginのDB commitと本番modの受信状態を照合。
3. クライアントとサーバーを正常終了。同じDBとworldで別のPaper JVMを起動し、登録・営業期間・セッションを照合。
4. 2台の実Fabricを同時接続して占有拒否・席の解放・再定義・削除・ID非再利用を確認。
5. 終了後のSQLite integrity、foreign keys、過去統計・履歴を確認。

操作補助・観測コードはruntime-test-supportだけに存在し、本番jarには含めない。ゲーム画面・管理画面UIは後続Phaseの範囲。

## Build SHA-256

| Artifact | SHA-256 |
|---|---|
| common | `bdcf0f21d0964f8e628d97baa16ad9fa2568cfbc730f884bccb92f469d53a2df` |
| paper | `95f4d8d9975056dcef5e99aa1f3914a1ae0a70a34f209191bb419157e5be8ed5` |
| fabric | `95c9def1d40db371af04f43e339082ed68644bf334c9bb2fe1621ad134eb1965` |

## 証跡

- 実行別詳細: `attempts/20260912T141252Z/`（command、各段階snapshot、client/server結果）
- 集約ログ: `server.log`, `client.log`
- 判定と起動コマンド・build hash: `result.json`
- build/testログ: `gradle-test.log`, `gradle-build.log`
- 実Minecraft screenshot: `screenshots/`
- artifact検証: `verify_phase01_artifacts.log`, `verify_phase02_artifacts.log`

## Phase01回帰テスト

同じ本番buildで 2026-09-12T14:16:57.390276+00:00 ～ 2026-09-12T14:20:07.562737+00:00（UTC）に再実行。
正常HELLO/ACKとprotocol不一致による利用拒否を、実Paper/Fabricで両方PASS。過去migration evidenceは上書きせず保持。
証跡: `../PHASE_02_PHASE01_REGRESSION/result.json`, `server.log`, `client.log`。

## 修正と後続Phase

途中runはWindowsでのテスト制御・観測ファイルの競合、クライアント応答を待つ前のテスト判定、コマンド間の視線条件によりFAILとなり、各attemptを保存した。観測書込を再試行可能にし、操作指示を操作ごとの不変ファイルに変更。各操作の直前に実クライアントの視線を設定し、終了時にはサーバーcommitとクライアント応答の両方を待って、上記runで全項目を再実行した。
SESSION_ENDはSPECどおりsessionIdだけのpayloadに修正し、クライアントの終了処理も照合した。
Phase02が作成するゲーム状態はSEATED_READY。未完ゲームのForce Settlementはリール・抽選・ボーナスを実装した後のPhase11で統合する。この段階では新しいゲーム実装由来の未完snapshotを検出した場合、権利を消去せずロック解放と期間切替を拒否する。
Phase03には進まず、このPhase02 runで終了。
