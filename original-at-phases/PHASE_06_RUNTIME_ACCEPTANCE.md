# HYPER_DOG Phase06 — 実Minecraft演出受入証跡

**Status: COMPLETE（2026-11-09）**

**確認対象コミット**：`6f0cfbd172e29941b05daf6f57e2f2fedcd31da6`（Phase06演出実装・テスト修正版）。この文書はCIで確認できた事実を記録する。受入記録の追加はゲーム抽選・数値・コードを変更しない。

## 結果（同一コミットのGitHub Actions）

| 受入項目 | CI結果 |
|---|---|
| Phase02 リール・全13役・57ゲーム・Paper/Fabric出目一致 | [SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570807592) |
| Phase03 通常ゲーム・実Paper/Fabric | [SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570807568) |
| Phase04 下位/上位AT・CZ・保存/復帰 | [SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570807454) |
| Phase05 専用筐体・マウス/キーボード・画面比率 | [SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570807510) |
| **Phase06 演出・実Minecraft統合受入** | **[SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570807519)** |
| 製品ビルド・JUnit | [SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570812350) |
| v7現行数値参照のCIガード | [SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570812434) |

Phase06実Minecraftワークフローは**受入チェック46/46 PASS**、実際のPaper 1.21とFabric 1.21でプレイ・GUI入力・リール表示・スクリーンショットを検証した。

- 70ゲーム以上の実Minecraft停止操作、BET/LEVER回数、Fabric/Paper停止出目一致、受付拒否0件を確認。
- 正の新規上乗せに対する右ランプ1/2/3灯、上乗せなし消灯、第三停止フラッシュを確認。演出色・灯数の承認済み確率表との一致は `python original-at-phases/verify_phase06_presentation.py` による独立検査PASS。Java側の配当保証と虹2000枚専用条件はJUnitでも検証。
- 元役の精算後に行う**赤7自動揃い**、上位由来32G引き戻し時の**虹7手動3停止**（CZとは別処理）、通常CZの左右自動停止を検証。
- 上位初期5GのTOTAL表示・第6Gの通常上乗せとの表示FIFO、表示中の入力ロック、退出/再着席、従来筐体との比較、16:9・4:3・横長の描画を検証。
- 赤7・虹7・CZ・フラッシュ・3灯・TOTALの**6場面実フレームPNG**に加え、画面比率別等のPNGを検証。証拠のログ・結果・画像・テスト成果物：[Phase06ワークフローartifact](https://github.com/Pasta0719/PiriJuggler/actions/runs/42570807519/artifacts/13652774925)。

## 変更履歴と権限範囲

- 最初の実装：[`7e3b8d7b`](https://github.com/Pasta0719/PiriJuggler/commit/7e3b8d7b8e502ce234af870cc5c30fdbe198e4fb)
- Phase06の専用起動・13役回帰・演出仕様照合修正：[`4fa1a4e9`](https://github.com/Pasta0719/PiriJuggler/commit/4fa1a4e92517584105676919da1c74688f0bf35e)
- Phase02の新虹7停止の許可対象を補完：[`6f0cfbd1`](https://github.com/Pasta0719/PiriJuggler/commit/6f0cfbd172e29941b05daf6f57e2f2fedcd31da6)

**現行数値正本**：`original-at-phases/reference/pirijuggler_new_machine_calibrated_v7.json`。v5/v6は履歴用。Phase06の完了は数値較正完了・機種公開を意味しない。**Phase07/08はNOT_STARTED、Draft PR・新機種未公開を維持**。演出の見た目・時間・好みは、ユーザーが実際にプレイした後の追加指示で調整可能。Phase07の着手は別途明示承認を必要とする。
