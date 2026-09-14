# Phase04 Runtime Acceptance — PASS

実行日時（UTC）: 2026-09-13T06:28:06.759580+00:00 ～ 2026-09-13T06:32:23.645764+00:00

Paper 1.21-130 / Minecraft 1.21 / Fabric Loader 0.16.14 / Fabric API 0.102.0+1.21 / Yarn 1.21+build.9 / Java 21。localhost:25588の隔離環境で配布用の本番JARを実際にロードした。

Unit/integration 124件 PASS（common23 / paper82 / fabric17 / asset-tools2）。既存111件を維持。test / build / packagePiriJars / helper buildはexit0。実機92項目PASS。全server/client exit0、未処理例外0。

## 実装と確認範囲

- 固定3本×21コマ、全9,261評価、9種類の厳密候補を保持。起動時の最低候補数・全停止順検証PASS。
- 通常500,094通りとプレミアムF 166,698通りを検証。F第2停止7,938条件の空集合なし。
- 停止済みリールだけを固定し、slip / targetRank / 左・中・右indexの順で決定。全押下indexと到達可能な部分状態のtie-breakを独立した比較方法でテスト。
- RTT全量の0–250ms補正、3回転profileの式・受付境界、早期STOPのsequence消費、owner/sessionと余計なpayloadの拒否を確認。
- 実機で9表示役×6停止順とFの3表示役×6停止順、計72回転・216 STOPを実行。実Keyboardイベントから本番STOP処理へ通信し、サーバーstopIndexとクライアント表示phaseの一致を確認。
- 5コマ以上のslip、Fの実テンパイ0と音event要求、HUD抑制、実画像12枚の全3リール描画、音源欠損時の継続、最後のセッション終了を確認。
- 同一JARでPhase01正常handshakeとprotocol mismatchの実Minecraft回帰もPASS。

## テスト境界

役選択と回転開始はopt-inのテスト専用controllerを使った。本番のReelRoundとStopSolverを実パケットで呼び出し、最終位置を実クライアントから観測した。RNG、BET、払出しやゲーム状態の永続化は後続Phaseの範囲であり、今回完成したという意味ではない。テストhelperは本番JARへ含まれず、テスト中のcreditとheldMedalsは0を維持した。

音声内容は生成していない。実音源はユーザー提供OGGを受け入れる方式を維持し、未配置で全検証を実施した。9 PNGのSHA、10 SoundEvent ID、自由なJAR再ビルド構成も成果物検証で確認した。

## JAR SHA-256

| JAR | SHA-256 |
|---|---|
| common | `276a6c308723619b38c86a2096e15556b3dd49f262d8891af1c9b58903027fa6` |
| paper | `b66727bd71f446a1f27a83806ae8e5801d972bee83296f9fffe02d6865d02994` |
| fabric | `59afd97c366c36d986869b37dd7cb55fe2b9fb101575e5190c178b39ba24a358` |

## 実機assertions

| 確認項目 | 結果 |
|---|---|
| production session and HUD | PASS |
| startup verifies 9261 candidates and exhaustive solver | PASS |
| real STOP / strict candidate / display agreement: grape-lcr | PASS |
| actual screenshot contains all three reel symbol columns: grape-lcr | PASS |
| real STOP / strict candidate / display agreement: grape-lrc | PASS |
| real STOP / strict candidate / display agreement: grape-clr | PASS |
| real STOP / strict candidate / display agreement: grape-crl | PASS |
| real STOP / strict candidate / display agreement: grape-rlc | PASS |
| real STOP / strict candidate / display agreement: grape-rcl | PASS |
| real STOP / strict candidate / display agreement: bell-lcr | PASS |
| actual screenshot contains all three reel symbol columns: bell-lcr | PASS |
| real STOP / strict candidate / display agreement: bell-lrc | PASS |
| real STOP / strict candidate / display agreement: bell-clr | PASS |
| real STOP / strict candidate / display agreement: bell-crl | PASS |
| real STOP / strict candidate / display agreement: bell-rlc | PASS |
| real STOP / strict candidate / display agreement: bell-rcl | PASS |
| real STOP / strict candidate / display agreement: piero-lcr | PASS |
| actual screenshot contains all three reel symbol columns: piero-lcr | PASS |
| real STOP / strict candidate / display agreement: piero-lrc | PASS |
| real STOP / strict candidate / display agreement: piero-clr | PASS |
| real STOP / strict candidate / display agreement: piero-crl | PASS |
| real STOP / strict candidate / display agreement: piero-rlc | PASS |
| real STOP / strict candidate / display agreement: piero-rcl | PASS |
| real STOP / strict candidate / display agreement: replay-lcr | PASS |
| actual screenshot contains all three reel symbol columns: replay-lcr | PASS |
| real STOP / strict candidate / display agreement: replay-lrc | PASS |
| real STOP / strict candidate / display agreement: replay-clr | PASS |
| real STOP / strict candidate / display agreement: replay-crl | PASS |
| real STOP / strict candidate / display agreement: replay-rlc | PASS |
| real STOP / strict candidate / display agreement: replay-rcl | PASS |
| real STOP / strict candidate / display agreement: cherry-lcr | PASS |
| actual screenshot contains all three reel symbol columns: cherry-lcr | PASS |
| real STOP / strict candidate / display agreement: cherry-lrc | PASS |
| real STOP / strict candidate / display agreement: cherry-clr | PASS |
| real STOP / strict candidate / display agreement: cherry-crl | PASS |
| real STOP / strict candidate / display agreement: cherry-rlc | PASS |
| real STOP / strict candidate / display agreement: cherry-rcl | PASS |
| real STOP / strict candidate / display agreement: miss-lcr | PASS |
| actual screenshot contains all three reel symbol columns: miss-lcr | PASS |
| real STOP / strict candidate / display agreement: miss-lrc | PASS |
| real STOP / strict candidate / display agreement: miss-clr | PASS |
| real STOP / strict candidate / display agreement: miss-crl | PASS |
| real STOP / strict candidate / display agreement: miss-rlc | PASS |
| real STOP / strict candidate / display agreement: miss-rcl | PASS |
| real STOP / strict candidate / display agreement: big_entry-lcr | PASS |
| actual screenshot contains all three reel symbol columns: big_entry-lcr | PASS |
| real STOP / strict candidate / display agreement: big_entry-lrc | PASS |
| real STOP / strict candidate / display agreement: big_entry-clr | PASS |
| real STOP / strict candidate / display agreement: big_entry-crl | PASS |
| real STOP / strict candidate / display agreement: big_entry-rlc | PASS |
| real STOP / strict candidate / display agreement: big_entry-rcl | PASS |
| real STOP / strict candidate / display agreement: reg_entry-lcr | PASS |
| actual screenshot contains all three reel symbol columns: reg_entry-lcr | PASS |
| real STOP / strict candidate / display agreement: reg_entry-lrc | PASS |
| real STOP / strict candidate / display agreement: reg_entry-clr | PASS |
| real STOP / strict candidate / display agreement: reg_entry-crl | PASS |
| real STOP / strict candidate / display agreement: reg_entry-rlc | PASS |
| real STOP / strict candidate / display agreement: reg_entry-rcl | PASS |
| real STOP / strict candidate / display agreement: premium_b-lcr | PASS |
| actual screenshot contains all three reel symbol columns: premium_b-lcr | PASS |
| real STOP / strict candidate / display agreement: premium_b-lrc | PASS |
| real STOP / strict candidate / display agreement: premium_b-clr | PASS |
| real STOP / strict candidate / display agreement: premium_b-crl | PASS |
| real STOP / strict candidate / display agreement: premium_b-rlc | PASS |
| real STOP / strict candidate / display agreement: premium_b-rcl | PASS |
| real STOP / strict candidate / display agreement: premium-f-miss-lcr | PASS |
| actual screenshot contains all three reel symbol columns: premium-f-miss-lcr | PASS |
| real STOP / strict candidate / display agreement: premium-f-miss-lrc | PASS |
| real STOP / strict candidate / display agreement: premium-f-miss-clr | PASS |
| real STOP / strict candidate / display agreement: premium-f-miss-crl | PASS |
| real STOP / strict candidate / display agreement: premium-f-miss-rlc | PASS |
| real STOP / strict candidate / display agreement: premium-f-miss-rcl | PASS |
| real STOP / strict candidate / display agreement: premium-f-cherry-lcr | PASS |
| actual screenshot contains all three reel symbol columns: premium-f-cherry-lcr | PASS |
| real STOP / strict candidate / display agreement: premium-f-cherry-lrc | PASS |
| real STOP / strict candidate / display agreement: premium-f-cherry-clr | PASS |
| real STOP / strict candidate / display agreement: premium-f-cherry-crl | PASS |
| real STOP / strict candidate / display agreement: premium-f-cherry-rlc | PASS |
| real STOP / strict candidate / display agreement: premium-f-cherry-rcl | PASS |
| real STOP / strict candidate / display agreement: premium-f-piero-lcr | PASS |
| actual screenshot contains all three reel symbol columns: premium-f-piero-lcr | PASS |
| real STOP / strict candidate / display agreement: premium-f-piero-lrc | PASS |
| real STOP / strict candidate / display agreement: premium-f-piero-clr | PASS |
| real STOP / strict candidate / display agreement: premium-f-piero-crl | PASS |
| real STOP / strict candidate / display agreement: premium-f-piero-rlc | PASS |
| real STOP / strict candidate / display agreement: premium-f-piero-rcl | PASS |
| 5+ symbol slip observed in real packets | PASS |
| 72 runtime rounds cover every role and stop order | PASS |
| STOP wire contains only server identity and monotonic sequence | PASS |
| user audio remains optional and all 10 SoundEvents are registered | PASS |
| test reel selection did not change production financial state | PASS |
| production close works after all reel rounds | PASS |

## 証跡・再現

- `result.json`: 各assertの時刻、STOP request/response、最終表示、JAR hash、起動コマンド。
- `server.log`, `client.log`, `screenshots/`。
- `preflight-enumeration.json`: 本番と別のPython列挙による候補数・F条件照合。
- `artifact-verification.json`: JAR構成・124件テスト・PNG SHA・音源なし同梱状態。
- `sync-backups/`: ファイル単位の変更前バックアップとSHA計画。
- `../PHASE_04_PHASE01_REGRESSION/REPORT.md`: handshake回帰。
- 再現手順: `../../docs/PHASE_04_REELS.md`。

ステージングでテストhelperのMixin型変換を修正後にビルド成功。実機の全試行はattemptsへ保存する。前回Phase03までの証跡は保持した。

**Phase04 COMPLETE。Phase05はNOT_STARTEDのまま終了。**
