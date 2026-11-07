# ORIGINAL AT — PHASE 01 Design lock, approval, and numerical source

Status: **NOT_STARTED — PLAN_DRAFT**

## 実装対象
- 会話で確定した仕様と試作承認案の差分を、`SPEC_LOCK.md`へ反映してレビュー承認を得る。
- `pirijuggler_new_machine_calibrated_v5.json` と `piri_simulate_full_v1.py` をSHA256照合のうえ専用 `reference/` に追加し、全確率・役払い出し・BET・0BET・状態遷移・上乗せ表・BAR権利・引き戻しの構造検証を実行。
- 実装で解釈が分かれる箇所（新機種名/ID、役重複AT当選時の赤7揃い告知と払出役の両立、予告なしG、初期TOTALと同G上乗せの重なり等）をまとめてユーザーに確認し、根拠のない数値を追加しない。**基本4コマ、レア役・BAR・虹7・AT赤7の確定引込みはユーザー決定済み**。
- 素材一覧と必要サイズ/透過/名前空間を確定。ユーザーが作る図柄の納品を待つ。

## 完了条件
1. 全仕様に【確定】【試作OK】【数値候補】【未確定】が付いている。
2. v5正本SHAが一致し、抽選分布和100%・6設定機械割目標・モード遷移の整合を自動テストで確認する。
3. 既存変更保護の影響表、新機種独立ID、図柄入力契約、後続Phaseの受入表をユーザーが確認済み。
4. 文書設計Phaseなのでゲーム実機テスト対象外。コード変更も行わない。次Phaseを自動開始しない。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
