# HYPER_DOG — PHASE 03 Machine identity, normal game, and six-mode lottery

Status: **IN_PROGRESS — NORMAL_ENGINE_AND_RUNTIME_ACCEPTANCE**

## 実装対象
- 既存機種を維持した独立新機種型・Paper専用ゲームエンジン・設定1〜6。旧GODの未完了部分は再利用しない。
- 有料3BET、REPLAY後0BET、役別配当、6モード初期値、単独AT倍率、役連続当選、役重複、昇格先表、ハズレ時転落をv5と一致させる。
- AT当選時に保存する元モード・移行後モードの規約、同一ゲームで単独・役重複・BARが競合する場合の優先順位を検証。
- 新機種が未完成の間は一般プレイヤー向け選択肢へ誤公開しない。実試験は専用fixture経路で行い、本番既存機種の抽選を変えない。

## 完了条件
1. 全設定の確率表・連続役3/4/5G・特殊強役・リプレイ無料の境界テスト。
2. 同一seedでモデルとの逐次役判定・状態遷移比較、および再現可能なサーバーランダムストリーム。
3. 保存→再起動→復帰で現在モード・連続役・当選状態が一致。
4. 実Paper+Fabric packet経路の正常ゲーム進行テスト、既存エンジンの回帰PASS。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
