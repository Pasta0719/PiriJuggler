# Phase 01 v4 migration — Runtime Acceptance PASS

実行開始: 2026-09-12T04:39:31.331114+00:00
実行終了: 2026-09-12T04:41:10.276891+00:00

移行先: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED`
引継ぎ元: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow`

既存Phase 01実装・テスト・状態履歴を引き継ぎ、v4仕様に必要な差分を修正した。Phase 02の実装は行っていない。

## 実行環境

- Java 21 / Minecraft 1.21 / Paper 1.21 build 130
- Fabric Loader 0.16.14 / Fabric API 0.102.0+1.21 / Yarn 1.21+build.9
- actual Paper server + actual Fabric client。localhost 127.0.0.1:25585、online-mode=false、PiriRuntimeTest。
- 本番Paper/Fabric jarを使用。観測・protocol不一致注入はruntime-test-support専用。

## Assertions

| 検証 | 結果 | 証拠 |
|---|---|---|
| 実Paperで本番プラグイン起動 | PASS | normal/server.log |
| 実Fabricで本番MOD起動・描画 | PASS | normal/client.log、screenshots/ |
| localhost接続、HELLO/HELLO_ACK protocol 1 | PASS | normal/server-result.json、normal/client-result.json |
| 2回目のprotocol 2でERROR(PROTOCOL_MISMATCH) | PASS | mismatch/client-result.json |
| 不一致時にserver/client両側のgameplayAllowed=false | PASS | mismatch/server-result.json、mismatch/client-result.json |
| 未処理例外なし・両プロセス正常終了 | PASS | 各result.json、各log |
| Unit/Integration 74件・失敗0・skip0 | PASS | 各moduleのbuild/test-results/test/ |
| ./gradlew test / build（Windows .bat） | PASS | migration-v4-test.log、migration-v4-build.log |

## Build hashes (SHA-256)

- common: `bdcf0f21d0964f8e628d97baa16ad9fa2568cfbc730f884bccb92f469d53a2df`
- paper: `acf163ed5306acbc49f2c5ae3ff16da8495666f0b11932b2993e9b01b9672288`
- fabric: `a3252a6743e455afcce5ed48938d0e3760e6dbafa4fbe86fc91a41640d34c59e`

## Exact commands and scenario steps

本番ビルド後にテスト補助jarをビルドし、以下のPython runnerでnormal、mismatchの順に実行した。

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat -PruntimeAcceptance=true :runtime-test-client:remapJar :runtime-test-paper:jar --console=plain
python runtime-test-support/run_phase01.py
```

### normal

Server cwd: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED\runtime-evidence\PHASE_01\work\server-normal`
Client cwd: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED`

server argv:
```json
[
  "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.12.101-hotspot\\bin\\java.EXE",
  "-Xms512M",
  "-Xmx1536M",
  "-Dfile.encoding=UTF-8",
  "-Dpiri.runtime.serverResult=C:\\Users\\PC_user\\Documents\\PiriJuggler_Codex_Workflow_v4_AUDITED\\runtime-evidence\\PHASE_01\\normal\\server-result.json",
  "-jar",
  "C:\\Users\\PC_user\\Documents\\PiriJuggler_Codex_Workflow_v4_AUDITED\\runtime-evidence\\PHASE_01\\work\\downloads\\paper-1.21-130.jar",
  "nogui"
]
```
client argv:
```json
[
  "cmd.exe",
  "/d",
  "/c",
  "C:\\Users\\PC_user\\Documents\\PiriJuggler_Codex_Workflow_v4_AUDITED\\gradlew.bat",
  "-PruntimeAcceptance=true",
  "-PruntimeScenario=normal",
  ":runtime-test-client:runClient",
  "--console=plain"
]
```

1. built pluginを隔離serverへ配置して起動。
2. built modを含む実FabricをQuick Playでlocalhostへ接続。
3. 本番handshake処理後のpacketと利用可否を両側で観測。
4. screenshotを保存しclient/serverを正常停止。

### mismatch

Server cwd: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED\runtime-evidence\PHASE_01\work\server-mismatch`
Client cwd: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED`

server argv:
```json
[
  "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.12.101-hotspot\\bin\\java.EXE",
  "-Xms512M",
  "-Xmx1536M",
  "-Dfile.encoding=UTF-8",
  "-Dpiri.runtime.serverResult=C:\\Users\\PC_user\\Documents\\PiriJuggler_Codex_Workflow_v4_AUDITED\\runtime-evidence\\PHASE_01\\mismatch\\server-result.json",
  "-jar",
  "C:\\Users\\PC_user\\Documents\\PiriJuggler_Codex_Workflow_v4_AUDITED\\runtime-evidence\\PHASE_01\\work\\downloads\\paper-1.21-130.jar",
  "nogui"
]
```
client argv:
```json
[
  "cmd.exe",
  "/d",
  "/c",
  "C:\\Users\\PC_user\\Documents\\PiriJuggler_Codex_Workflow_v4_AUDITED\\gradlew.bat",
  "-PruntimeAcceptance=true",
  "-PruntimeScenario=mismatch",
  ":runtime-test-client:runClient",
  "--console=plain"
]
```

1. built pluginを隔離serverへ配置して起動。
2. built modを含む実FabricをQuick Playでlocalhostへ接続。
3. 本番handshake処理後のpacketと利用可否を両側で観測。
4. screenshotを保存しclient/serverを正常停止。

## 引継ぎと再現性

旧ソース・テストの17ファイルは同一hash、13ファイルはv4差分修正。削除なし。追加3ファイル。詳細はmigration-provenance.json。

初回preflightではv3 spec-lockとv4 SPECの差を検出した。仕様どおり再抽出しテストの明示的変更部分を更新した。
ランチャー準備時に検出したGradle依存順・Windows runDir・テスト専用Mixin配置を修正し、失敗ログはattempts/へ保持した。
テスト用clientのsimulationDistanceをMinecraftの許容値5へ修正し、正常・不一致の両scenarioを再確認した。
両scenarioの結果とlogは最終成功実行のもの。runtime helperはproduction jarに同梱していない。
v3開発DBは存在せず、DB削除・schema作成・Phase 02コード追加は実施していない。
