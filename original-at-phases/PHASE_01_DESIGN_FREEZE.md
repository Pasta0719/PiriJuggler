# HYPER_DOG — PHASE 01 Design lock, approval, and numerical source

Status: **NOT_STARTED — SPEC_LOCKED**

## 実装対象
- 会話で確定した仕様と実装判断確定表が矛盾しないことを監査し、`SPEC_LOCK.md`、`IMPLEMENTATION_DECISIONS_V1.md`、`PRESENTATION_RULES_V1.json`を正本として固定する。**本件について追加の設計質問は設けない。**
- **専用`reference/`に登録済み**の`pirijuggler_new_machine_calibrated_v5.json`と`piri_simulate_full_v1.py`を所定SHA256で再検査し、全確率・役払い出し・BET・0BET・状態遷移・上乗せ表・BAR権利・引き戻しの構造検証を実行。必要に応じてシミュレーターを同一seedで再実行。
- **[IMPLEMENTATION_DECISIONS_V1.md](IMPLEMENTATION_DECISIONS_V1.md) に全件決定済み**。機種ID`HYPER_DOG`、役重複ATの`元役停止→無償赤7再回転`、上乗せがないGは右演出なし、6G目TOTAL→+N、音・画像契約・CZロック・各ランプをそのまま実装する。**基本4コマ、レア役・BAR・虹7・AT赤7の確定引込みはユーザー決定済み**。新しい確認事項を実装都合で作らず、数値も独断で変更しない。
- 素材は**256×256 RGBAの図柄7枚**に確定。画像がなくてもリール配列とソルバーは図柄IDで先行でき、実画像受入のみ納品後に行う。

## 完了条件
1. **実装上の未確定選択肢が0件**。過去の試作承認案には、実装判断確定表の数値/順序を適用する。
2. v5正本SHAが一致し、抽選分布和100%・6設定機械割目標・モード遷移の整合を自動テストで確認する。
3. 既存変更保護の影響表、新機種独立ID`HYPER_DOG`、図柄入力契約、後続Phaseの受入条件が決定表と一致することを監査。
4. 文書設計Phaseなのでゲーム実機テスト対象外。コード変更も行わない。次Phaseを自動開始しない。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
