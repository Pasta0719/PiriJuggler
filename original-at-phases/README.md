> **CURRENT_NUMERIC_REFERENCE / HYPER_DOG：唯一の現行数値正本は [pirijuggler_new_machine_calibrated_v7.json](reference/pirijuggler_new_machine_calibrated_v7.json)。** `PHASE_MAP.json.numeric_reference` に従い、Phase06〜08、Paper実装・検証・シミュレーションではv7を参照する。v5/v6は履歴監査専用。[v7承認仕様](POST_PHASE05_UPPER_4K_V7.md)に変更理由を記録。

> **2026-11-09 追加承認・最新v7：上位AT平均約4,000枚**。上位有料ATのレア役上乗せを弱チェ/スイカ8倍・チャンス目9倍・強チェ/強チャンス目10倍（単一+2000枚まで）に強化する。上位初期500枚以上、通常CZ50%、実測有料上位純増約12枚/G、小役・BAR・下位ATのゲーム性は維持。設定別機械割のため、通常レア役重複AT抽選に共通0.85倍補正と設定別単独AT倍率再調整を採用（リプレイ/ブドウ4連20%・5連100%は変更なし）。数値と調整理由は[POST_PHASE05_UPPER_4K_V7.md](POST_PHASE05_UPPER_4K_V7.md)。最新数値正本は[calibrated_v7 JSON](reference/pirijuggler_new_machine_calibrated_v7.json)。v6/v5は履歴と回帰のため残す。Phase06–08未開始・未公開。

> **2026-11-09 現行進捗**：HYPER_DOG Phase01～Phase05は**COMPLETE**。Phase03は[通常時実機受入](PHASE_03_RUNTIME_ACCEPTANCE.md)、Phase04は[AT/CZ・SQLite別JVM復帰受入](PHASE_04_RUNTIME_ACCEPTANCE.md)、Phase05は[Fabric筐体・入力・各画面比率の実機受入](PHASE_05_RUNTIME_ACCEPTANCE.md)を参照。Phase06～08はNOT_STARTED。PRはDraft・未マージ、新機種は未公開。

**2026-11-09 追加仕様**：上位AT→上位CZ失敗後の32有料G引き戻しは**虹7手動3停止→上位AT初期5G**へ復帰。設定別単独AT倍率を再調整した[新v6数値・再シミュレーション](POST_PHASE05_UPPER_COMEBACK_V6.md)は当時の履歴。現行正本は上記v7 JSON。Phase06の実リール演出完成は未着手、Draft・未公開を維持。

# PiriJuggler HYPER_DOG — Phase実装仕様

**実装状態：Phase01～05 COMPLETE、Phase06～08 NOT_STARTED。** 設計作成日 2026-11-07。既存機種の機能・データを保護し、承認された演出仕様を維持する。

このフォルダは既存の `phases/`, `next-machine-phases/`, `skill-stop-phases/` を置き換えない。新しい「AT直撃・下位/上位AT・上位CZ」の専用計画であり、引退した単体GOD機種とは別物。

## 読む順番
1. [SPEC_LOCK.md](SPEC_LOCK.md) — ユーザーが決めた機能・ゲーム性の正本。
2. [IMPLEMENTATION_DECISIONS_V1.md](IMPLEMENTATION_DECISIONS_V1.md) — 演出競合・UI・機種型など**全項目の確定判断**。
3. [REEL_DESIGN_V2.md](REEL_DESIGN_V2.md) — 現行21コマ×3本の配列・混合リーチ目禁止・通常4コマ／レア役確定引込み。旧v1は履歴。
4. [PRESENTATION_RULES_V1.json](PRESENTATION_RULES_V1.json) — 色・点灯数・演出時刻・座標の実装用JSON。
5. [pirijuggler_new_machine_calibrated_v7.json](reference/pirijuggler_new_machine_calibrated_v7.json) と[v7シミュレーター](reference/piri_simulate_upper_comeback_v7.py) — **現行の数値実装・検証基準**。旧v5/v6は履歴専用。
6. [PHASE_MAP.json](PHASE_MAP.json) — 8 Phase の順序と依存関係。
7. [PHASE_01_DESIGN_FREEZE.md](PHASE_01_DESIGN_FREEZE.md) から順に、**そのPhaseだけ**を実行。既存 `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md` は常に優先。

## リール・通常ゲーム・AT/CZ・筐体UIの実機証跡

Phase02は [v2リール実機受入](PHASE_02_V2_RUNTIME_ACCEPTANCE.md)、Phase03は [通常時6モードの実機完了証跡](PHASE_03_RUNTIME_ACCEPTANCE.md)、Phase04は [AT/CZ・BAR・32G引戻し・SQLite別JVM復帰の実機完了証跡](PHASE_04_RUNTIME_ACCEPTANCE.md)、Phase05は [専用Fabric筐体・実入力・解像度別の実機完了証跡](PHASE_05_RUNTIME_ACCEPTANCE.md) を参照。ランプ・フラッシュ等の演出完成はPhase06、管理/周辺統合はPhase07、総合受入/公開はPhase08の対象。

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
| 08 | v7現行数値の実装照合・実Minecraft総合受入・回帰 | 完成判定と証跡 |

**依存関係**：01→02→03→04→05→06→07→08。01の後、ユーザーが図柄画像を未提供でも02の**配列設計・停止制御のロジック実装は進める**。ただし02の最終Fabric描画受入は実画像なしではCOMPLETEにしない（表示部分のみBLOCKED）。**リール配列表をユーザーに要求しない。** 各Phase内で必要な実ランタイム検証ができない場合も同様。

## 設計の位置づけ
- 【ユーザー確定】ゲームの構造・枚数・出目・CZ・ランプの色順・4コマ通常と確定引込みは変更不可。
- 【初期実装値を決定】演出のタイミング/色割合/ランプ位置/重複時の表示順/音/ID/素材契約まで**全件決定**。実機を見て違和感があった部分だけ、ユーザー指示により改訂する。
- 【数値の実装正本】[pirijuggler_new_machine_calibrated_v7.json](reference/pirijuggler_new_machine_calibrated_v7.json)を現行唯一の基準として固定。v5/v6は履歴。独立シミュレーションの機械割はPaper実装でも再照合する。
- 【実行待ち】Phase06～08の実装・実Minecraft受入。図柄7点は受領済みで描画を検証済み。Phase01～05は証跡付きでCOMPLETE、既存Phaseと`CODEX_START.md`の規範は維持。

