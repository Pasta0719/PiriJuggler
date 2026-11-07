# ORIGINAL AT — PHASE 08 Numerical verification and final real-Minecraft release gate

Status: **NOT_STARTED — PLAN_DRAFT**

## 実装対象
- v5数値正本とPaperの全乱数判定・モード遷移・上乗せ・BET/配当・連続役・特別区間の実装を自動照合。
- 設定1〜6で長期独立シードの機械割検証。以前のv5目標値 97.0/99.3/102.5/104.8/107.4/116.0% と基準の統計的なばらつきを明記し、テスト後の都合の良い設定だけを採らない。
- リールの全合法停止結果・投入/払い出し・上位CZ50%・上位初期6G目TOTAL・右ランプ保証・虹2000プレミア・BAR権利持越しと復帰を通しで実機検証。
- 既存機種（JUGGLER/JUGGLER_GOD/JUGGLER_GOD_EXTREME/SKILL_STOP）の抽選・操作・DB・経済・管理・ワールド表示・モバイルへの回帰を検証。

## 完了条件
1. `./gradlew test`、`./gradlew build`、必要なGitHub Actionsすべて成功。
2. v5全役確率・6設定の長期シミュレーション結果をレポートし、許容誤差・CI・シード・有料G母数を残す。
3. 実Paper＋Fabric本番JARでプレイし、全AT/CZ/エラー/復元/観覧/残枚数/演出の受入PASS、画像・ログ証跡あり。
4. 既存機種の保護監査と新機種の有効化/作成/設定変更・終了をPASS。
5. ユーザーの「実機を見て修正」のレビューで受理されるまで暫定承認演出を最終固定としない。未受理ならCOMPLETEを宣言せず修正Phaseを設ける。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
