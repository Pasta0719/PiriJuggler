# Phase05 Runtime Acceptance — PASS

実行日時（UTC）: 2026-09-14T01:06:09.640219+00:00 ～ 2026-09-14T01:12:10.915843+00:00

Paper 1.21-130 / Minecraft 1.21 / Fabric Loader 0.16.14 / Fabric API 0.102.0+1.21 / Yarn 1.21+build.9 / Java 21。localhost:25589の隔離環境で本番のPaper/Fabric JARをロードした。

Unit/integration 144件PASS（common24 / paper99 / fabric19 / asset-tools2）。既存124件を維持。Gradle test / build / packagePiriJars / helper buildはexit0。実機114項目PASS、全server/client exit0、未処理例外0。

## 承認済み仕様変更と実装

- SPEC第92章へ公開状態mappingを追記。第18/131章の内部状態遷移とDB保存値を維持し、第101章の秘匿を優先する。
- common/PublicGameStateとSessionの明示的switchを追加。入賞前のBIG/REGを公開状態名から除外する。種類が視覚的に判明した後のBIG/REG状態は公開可能。
- 台別RNG・event・Simulatorのstream分離、整数weightによるLEVER時1回抽選、通常BET/STOP/払出し/無料REPLAYを実装。
- CREDIT不足時にheldMedalsから不足分だけ補充。上限50超過はheldへ送り、long overflowで不正残高を確定しない。
- GameStoreがsession/sequence/停止位置/G数/差枚/最大差枚/結果graphとUUID受領記録を1 transactionで保存。DB callbackまでBUSY。
- 重複BET/払出し防止と、結果transactionを意図的に失敗させたrollbackを単体・SQLite統合テストで確認。
- 離席時の位相snapshot保存と猶予内RESUMEを統合し、停止済みリール・内部役を保持。

## BARボーナス確定目

BAR-BAR-BARをBIG/REG共通の0枚確定目として追加。MISS候補5,242件と厳密BAR形8件をBONUS候補5,250件にまとめ、単独BIG/REGの両方で使用する。BAR形は他の小役/777/77BAR/チェリーと混在しない。払出し0枚、Piri Chance点灯、公開BONUS_PENDINGで種類を秘匿し、BONUS_STARTを発火しない。

有効5ライン上のBARを全9,261形で評価し、MISS・小役・入賞候補から除外。CHERRY 1,510件、PREMIUM_B 774件へ候補数を更新。重複役の2枚/14枚、抽選weight、内部GameState、REG入賞形SEVEN-SEVEN-BARは維持する。

BIG/REG×8形×6停止順の96ゲームDB統合試験で、0枚・点灯・権利保持・非公開を確認。実MinecraftでもBIG/REG各100%のテストconfigで、実クライアントの見えている位相に合わせてSTOPキーを押し、BAR揃いと各条件を確認した。位相や停止結果をC2Sへ送る仕組みは追加していない。通常100ゲームの試験でも、各ゲームの全5ラインでBAR不成立を独立確認した。

BAR実機証跡はPHASE_05_BAR_BIG / PHASE_05_BAR_REG。補助コードと強制weightは本番JARに含まれない。

## 回転方向の反転

ユーザー指示により、SPEC第5/25/33/34/90/132章を同期。通常はindex減少（画面下向き）、プレミア逆回転の最初500msはindex増加（画面上向き）、その後300msで通常方向の18 symbols/secへ加速する。RESUME・slip計算・停止補間も通常方向に揃え、固定配列と上下段定義を維持した。

追加3試験を含む全144件で境界の連続性・方向・0を跨ぐ停止・全722,358停止系列を検証。同一JARを用いた実Minecraft回帰で、3 profileの表示位相の時系列、全78回転/234 STOPの滑りと最終表示一致、5コマ以上の滑りを確認した。既存Phase04証跡は保存し、新しい証跡をPHASE_05_REEL_REGRESSIONへ保存した。

初回の反転後Runtimeで0番停止の小数丸め誤差（表示位相が21近傍に残る）を検出。停止補間の終点を整数で保持し、完了時に厳密なstopIndexへ固定した。全21終点×209小数開始位相の回帰試験を追加し、全実機試験を再実行。失敗runもattemptsに保持する。

これは既存リール制御部品の方向変更。実台のプレミア抽選や自動演出はPhase06で接続する。

## 実機確認

通常ゲーム100回、うちREPLAY 15回、paid normal spins 85回。各ゲームで実キー→本番handler→DB確定→実画面の停止位置を照合。

テスト初期資金は配布対象外helperが空のsessionへ設定した。100ゲーム継続のため隔離configの設定1だけボーナスweightをMISSへ移した。製品configは変更していない。その後の全BET/LEVER/STOP/払出しは本番コードで処理した。

最後に台をSUSPENDED_SAFEで開放してからテスト設定2へ切り替え、CHERRY_BIGを実抽選した。Paper内部はBONUS_PENDING_BIG、PUBLIC_STATEはBONUS_PENDINGとなり、BONUS_STARTはまだ送信されないことを確認。公開packet全体からinternalRole/setting/seed/bonusTypeと入賞前の種類付き状態名が漏れないことを確認した。

`/piri simulator 1 100000` を実コマンドで実行し、100,000 normal spins・総BET・払出し・netの応答と実台資産の不変を確認。今回のruntime Simulatorはテスト用configのweightを使用する。標準6設定の機械割は下の固定seed試験で検証した。

## 6設定 × 1,000万ゲーム

seedは仕様指定のテスト値0x504952494A554747。許容差は±0.20 percentage point。production/commandのseedは公開しない。

| 設定 | 目標% | 実測% | 差（ポイント） | 結果 |
|---|---:|---:|---:|---|
| 1 | 97.8 | 97.935271 | +0.135271 | PASS |
| 2 | 99.4 | 99.500609 | +0.100609 | PASS |
| 3 | 101.0 | 100.991116 | -0.008884 | PASS |
| 4 | 103.4 | 103.580304 | +0.180304 | PASS |
| 5 | 106.0 | 105.855842 | -0.144158 | PASS |
| 6 | 111.4 | 111.225732 | -0.174268 | PASS |

SimulatorはREPLAYの次BET0、ボーナス入賞BET1、BIG 20×2BET/280枚、REG 8×2BET/112枚、重複小役の1回払出しを含む。実台のボーナス自動消化は実装していない。

## JAR SHA-256

| JAR | SHA-256 |
|---|---|
| common | `6fb82ca6eff8b196e5bbe78b05773f25da4f18db832b935c0153119ec96b582a` |
| paper | `6262e2ae966b28ed7c54efb9eaeeed4f7d9fb44ae8dd3fc8eb9cf07edc53e2db` |
| fabric | `1704d04bc9e1a285e2b8d7046ea3eb817a990964fea0749891d53ec9a17f9bfe` |

## 実機assertions

| 項目 | 結果 |
|---|---|
| empty session BET rejects without loan or insertion | PASS |
| DB transaction BUSY rejects the second real input without double BET | PASS |
| normal game 1: BET / payout / replay / durable stats / displayed stops | PASS |
| actual screenshot contains all three reel symbol columns: first-game | PASS |
| normal game 2: BET / payout / replay / durable stats / displayed stops | PASS |
| actual screenshot contains all three reel symbol columns: replay-ready | PASS |
| normal game 3: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 4: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 5: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 6: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 7: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 8: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 9: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 10: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 11: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 12: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 13: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 14: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 15: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 16: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 17: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 18: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 19: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 20: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 21: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 22: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 23: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 24: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 25: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 26: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 27: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 28: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 29: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 30: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 31: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 32: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 33: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 34: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 35: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 36: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 37: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 38: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 39: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 40: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 41: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 42: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 43: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 44: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 45: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 46: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 47: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 48: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 49: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 50: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 51: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 52: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 53: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 54: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 55: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 56: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 57: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 58: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 59: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 60: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 61: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 62: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 63: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 64: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 65: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 66: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 67: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 68: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 69: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 70: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 71: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 72: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 73: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 74: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 75: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 76: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 77: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 78: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 79: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 80: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 81: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 82: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 83: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 84: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 85: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 86: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 87: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 88: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 89: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 90: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 91: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 92: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 93: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 94: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 95: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 96: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 97: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 98: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 99: BET / payout / replay / durable stats / displayed stops | PASS |
| normal game 100: BET / payout / replay / durable stats / displayed stops | PASS |
| at least 100 normal games include real replay | PASS |
| credit and heldMedals remain within their allowed ranges | PASS |
| actual /piri simulator 1 100000 completes without changing live assets | PASS |
| bonus outcome remains only on Paper during spin | PASS |
| actual screenshot contains all three reel symbol columns: bonus-pending-public | PASS |
| real bonus pending exposes generic public state and preserves internal BIG | PASS |
| all gameplay packets and client network observations contain no internal fields or bonus-specific pending state | PASS |
| real gameplay C2S payload has only identity and sequence | PASS |
| user audio absent remains supported | PASS |
| closing pending bonus retains its state and assets | PASS |

## コマンドと証跡

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build packagePiriJars --console=plain
.\gradlew.bat -PruntimeAcceptance=true -PruntimeScenario=phase05-game -PruntimeEvidencePhase=PHASE_05 :runtime-test-client:build :runtime-test-paper:build --console=plain
$env:PIRI_PHASE04_EVIDENCE = "PHASE_05_REEL_REGRESSION"
python runtime-test-support/run_phase04.py
python runtime-test-support/run_phase05.py
$env:PIRI_PHASE01_EVIDENCE = "PHASE_05_PHASE01_REGRESSION"
python runtime-test-support/run_phase01.py
$env:PIRI_BAR_BONUS = "BIG"
python runtime-test-support/run_phase05_bar.py
$env:PIRI_BAR_BONUS = "REG"
python runtime-test-support/run_phase05_bar.py
python docs/verify_phase05_artifacts.py
```

- result.json: 全assert、ゲームごとの前提・確定残高・公開状態、exact process commands、version、SHA、exit。
- server.log / client.log / attempts / screenshots: 実サーバー・クライアント証跡。
- simulator-10m.json: 標準6設定の試験結果。
- artifact-verification.json: テスト件数、Java21/JAR/remap/common同梱、helper非同梱、画像9枚SHA、音源10 IDとOGG未配置の照合。
- preflight-conflict/: 承認前のBLOCKED記録と提案差分。SPEC_CONFLICT.md末尾に解消済み記録。
- sync-backups/: 反映前ファイルとSHA計画。
- ../PHASE_05_REEL_REGRESSION/REPORT.md: 反転後の全リールケースと通常/逆回転/RESUMEの表示位相時系列。
- ../PHASE_05_PHASE01_REGRESSION/REPORT.md: 同一JARによる接続・不一致拒否の回帰。

## 後続Phaseとの境界

Phase05は通常ゲーム、ボーナス権利の保持・公開mapping、Simulatorまで。実台のボーナス入賞・BIG/REG消化・プレミア抽選と演出はPhase06、未完ゲームの期限切れ/再起動Force SettlementはPhase11で接続する。未完権利を捨てず、既存の起動/ロック保護を維持している。

音声内容の生成は行わない。ユーザー提供OGG / user-audio / build-jars.bat / dist方式を維持。音源未配置で全検証PASS。

**Phase05 COMPLETE。Phase06はNOT_STARTEDのまま終了。**
