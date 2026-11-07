# ORIGINAL AT — PHASE 02 Reels, symbols, and server-authoritative stops

Status: **NOT_STARTED — PLAN_DRAFT**

## 実装対象
- ユーザー提供の図柄を専用名前空間に格納。既存JUGGLERのPNGを上書きしない。
- 3列×3段における物理リール配列、停止テーブル、役ごとの有効ライン、押し順と停止タイミング、外れ出目を専用実装。
- 弱チェリーの2連形、強チェリーの3連形、チャンス目の中段赤7/BAR/赤7、強チャンス目の中段赤7/BAR/虹7を強制できる停止テーブルを構築。CZ用の中段虹7×3、BAR演出も別途整理。
- 1回のレバーONでサーバーが内部役を確定し、3停止で再抽選しない。意図しないBAR・虹7の揃いを禁止。ユーザー入力の位置・押し順を無視した見せかけ出目にしない。

## 完了条件
1. 全役について停止可能性を証明し、取りこぼし・矛盾する押し順・役非成立時の偽入賞を検出する網羅テスト。
2. Minecraft Paper+Fabricの実停止経路で入力、3列図柄、停止位置、復帰の記録を保存。
3. 図柄未提供、承認した出目が配列上で再現できない、または停止表が不一致ならBLOCKED（勝手な代用図柄・役変更はしない）。
4. 既存FixedReels / SkillStopReels / JugglerGod停止ロジックの回帰テストPASS。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
