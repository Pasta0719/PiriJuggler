# HYPER_DOG — PHASE 07 Admin, data lamp, remote view, mobile, audio, recovery

Status: **IN_PROGRESS — SPEC_LOCKED / 実Minecraft全項目受入前**

**CURRENT_NUMERIC_REFERENCE / HYPER_DOG：唯一の現行数値正本は [pirijuggler_new_machine_calibrated_v7.json](reference/pirijuggler_new_machine_calibrated_v7.json)（v7）。** `PHASE_MAP.json.numeric_reference` と一致させ、抽選・配当・上乗せ・機械割はv7に従う。v5/v6は旧Phase・較正履歴専用。

> Phase06の実機受入46/46 PASSを前提に着手。**現行v7数値・旧機種・保存済み権利を不変**とする。現在HYPER_DOGの通常ホール公開・新規作成は引き続き無効で、総合公開はPhase08の受入まで行わない。独立の履歴・同期・音・復帰の実装を段階的に検証し、全完了条件の証跡が揃うまでCOMPLETEとしない。

## 実装対象
- `HYPER_DOG`の設定投入・種類変更・ホール粗利運営、データランプ履歴/AT初当たり/上位・CZ・BAR・引き戻し記録と差枚集計を整合させる。**データランプ上段の主カウンターはB/Rの代わりに「AT初当たり」「上位CZ回数」「上位AT回数（初回突入と継続をどちらも加算）」の3項目**。AT初当たりは通常からのAT開始（32G引き戻しを含む）、上位CZはCZへの挑戦開始1回につき1、上位ATは`UPPER_INITIAL`に入るたび1（CZ成功による再突入も加算）として、1GごとのCZゲーム数とは混同しない。既存のBIG/REGに虚偽集計しない。機種表示名は`HYPER_DOG`だが筐体には文字を置かない。
- 第二Fabric観覧・遠隔ワールド筐体レンダラ・スマホ遠隔の状態反映。遠隔へ内部モード/次の抽選結果/設定の非公開情報を漏らさない。
- 新しいOGGは要求しない。**既存`bet/lever/stop/notice/notice_strong/payout/tenpai/bonus_start/bonus_end`を [IMPLEMENTATION_DECISIONS_V1.md](IMPLEMENTATION_DECISIONS_V1.md) 第26項の割当で再利用**。登録済みOGGが欠けている場合その音だけ無音フォールバック、遊技と受入は停止しない。追加BGMなし。
- リスタート/切断/回線遅延/重複パケット/タイムアウトと復帰、現金メダル/貸出/精算/実出玉率で資産喪失ゼロを実証。

## 完了条件
1. 設定1〜6変更、店舗自動配分、データランプ履歴、コイン残高、上位CZ履歴の集計が一致。
2. 実プレイヤー1＋観覧1＋スマホ遠隔の表示同期と不正操作拒否、旧機種の同期回帰PASS。
3. 途中上乗せ・CZ中央停止前・TOTAL表示直前の各中断復元テスト。
4. 既存環境の世界観覧パフォーマンスや音環境を劣化させない証跡。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
