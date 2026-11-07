# PiriJuggler オリジナルAT新機種 — Phase実装仕様

**計画状態：初期実装仕様を確定済み／ゲームコード実装は未着手。** 作成日 2026-11-07。ユーザーの実機確認後の演出微調整は可能。

このフォルダは既存の `phases/`, `next-machine-phases/`, `skill-stop-phases/` を置き換えない。新しい「AT直撃・下位/上位AT・上位CZ」の専用計画であり、引退した単体GOD機種とは別物。

## 読む順番
1. [SPEC_LOCK.md](SPEC_LOCK.md) — ユーザーが決めた機能・ゲーム性の正本。
2. [IMPLEMENTATION_DECISIONS_V1.md](IMPLEMENTATION_DECISIONS_V1.md) — 演出競合・UI・機種型など**全項目の確定判断**。
3. [REEL_DESIGN_V1.md](REEL_DESIGN_V1.md) — 21コマ×3本の具体配列・4コマ通常／確定引込み。
4. [PRESENTATION_RULES_V1.json](PRESENTATION_RULES_V1.json) — 色・点灯数・演出時刻・座標の実装用JSON。
5. [reference/pirijuggler_new_machine_calibrated_v5.json](reference/pirijuggler_new_machine_calibrated_v5.json) と[シミュレーター](reference/piri_simulate_full_v1.py) — 数値の固定実装基準。
6. [PHASE_MAP.json](PHASE_MAP.json) — 8 Phase の順序と依存関係。
7. [PHASE_01_DESIGN_FREEZE.md](PHASE_01_DESIGN_FREEZE.md) から順に、**そのPhaseだけ**を実行。既存 `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md` は常に優先。

## Phase02リール配列の先行設計

[REEL_DESIGN_V1.md](REEL_DESIGN_V1.md) に**21コマ×3本の具体的な配列・停止例・停止制御案**を先行記録。配列JSONと9,261通りを列挙する静的検証スクリプトも追加済み。これは実装完了や4コマ滑り保証ではない。図柄画像のみユーザー提供で、配列・ソルバーは開発側が担当する。

## 実行順
| Phase | 作業 | 成果物 |
| --- | --- | --- |
| 01 | 設計正本確定・v5数値の配置・未定義点の最終確認 | 実装可能な仕様と抽選テーブル |
| 02 | 独立リール・停止制御 | 新機種の図柄・役・停止制御の検証 |
| 03 | 通常時・6モード・当選抽選 | 新機種専用の確率エンジン |
| 04 | 下位/上位AT・初期差枚・BAR・CZ・引き戻し | 遊技エンジン一式 |
| 05 | 既存操作部を継承した筐体画面 | Fabricの新機種UI |
| 06 | 告知・期待度ランプ・第三停止フラッシュ・初期TOTAL・CZ虹7 | 演出完成 |
| 07 | データランプ・管理画面・観覧/遠隔・音・復帰統合 | 既存システムとの接続 |
| 08 | v5数値の実装照合・実Minecraft総合受入・回帰 | 完成判定と証跡 |

**依存関係**：01→02→03→04→05→06→07→08。01の後、ユーザーが図柄画像を未提供でも02の**配列設計・停止制御のロジック実装は進める**。ただし02の最終Fabric描画受入は実画像なしではCOMPLETEにしない（表示部分のみBLOCKED）。**リール配列表をユーザーに要求しない。** 各Phase内で必要な実ランタイム検証ができない場合も同様。

## 設計の位置づけ
- 【ユーザー確定】ゲームの構造・枚数・出目・CZ・ランプの色順・4コマ通常と確定引込みは変更不可。
- 【初期実装値を決定】演出のタイミング/色割合/ランプ位置/重複時の表示順/音/ID/素材契約まで**全件決定**。実機を見て違和感があった部分だけ、ユーザー指示により改訂する。
- 【数値の実装正本】v5 JSONを固定。機械割は独立シミュレーションの報告値であり、Paper実装で再照合する。
- 【実行待ち】Phase 01〜08はまだ未着手。既存のコード・既存Phase・`CODEX_START.md`の入口は変更していない。**設計の未確定は0件**であり、残っているのはPhaseの実装とユーザー図柄画像の受領、実Minecraftでの受入だけ。

