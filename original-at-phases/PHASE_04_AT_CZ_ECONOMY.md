# HYPER_DOG — PHASE 04 Lower and upper AT, initial games, BAR, CZ, comeback

Status: **COMPLETE — 2026-11-09 JST / v5 / Paper+Fabric+別JVM SQLite実機受入 PASS**

[Phase04実機・保存復元の完了証跡](PHASE_04_RUNTIME_ACCEPTANCE.md)を正本として参照。対象コード[`1bb4aa8c`](https://github.com/Pasta0719/PiriJuggler/commit/1bb4aa8c9275c43929b0619a247449dd08d26551)。

## 実装対象
- 下位初期無料1Gの150枚保証・役加算、下位AT差枚消化・レア役上乗せ・CZ権利累積。
- 上位初期無料5G（各+100保証）、上位上乗せ5倍・MISS書換、強役+2000例外。累計差枚に上限なし。
- 上位CZは0BET/0払出で最大5G、通常5G総計50%、保証権利対応。上位CZ失敗時の終了先は「下位経由」なら下位初期再実行、「上位経由」なら完全終了。
- BAR通常/下位で上位直行、BAR権利・持ち越し・複数ストック、上位BARで次回CZ成功保証＋初期レア役権利。
- 完全AT終了時の保存モードからの再抽選と32G引き戻し。BET、払い出し、差枚、権利、段階、ストップ済み情報を既存DBの原子的永続化経路に載せる。

## 完了条件
1. 0BET特殊区間と通常有料Gを混同しない。リプレイ無料権利が特殊区間の前後に漏れない。
2. 初期150/500保証、+2000枠、連上乗せ、CZ権利2/3個目、上位CZ成功・失敗、BAR持越し、32Gの全経路テスト。
3. アプリ終了/再起動/離席/別ユーザー占有で差枚・ストック・抽選結果・財産・未完了ゲームが消えない実Minecraft受入。
4. v5とのサンプル役列・差枚計算一致、既存機種・Vaultへの影響なし。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
