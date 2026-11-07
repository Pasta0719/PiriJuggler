# ORIGINAL AT — PHASE 05 Existing controls, cabinet, indicators and tiny display

Status: **NOT_STARTED — SPEC_LOCKED**

## 実装対象
- 現行 `SlotLayout.java` の LEVER→BET→左中右停止ボタン、リール直下CREDIT/BET/PAY/MEDALS、データランプは既存と同配置。MAX BETを端へ移すUI改変禁止。
- 中央3リール、左右上に告知ランプ各1灯（既存告知`lampOn`のタイミングを維持）。左側に状態3灯と上乗せ小液晶、右側に期待度ランプ3灯を配置。**画面の全座標・ランプ色・点滅・液晶表示時間は [IMPLEMENTATION_DECISIONS_V1.md](IMPLEMENTATION_DECISIONS_V1.md) の10〜17項で固定。**
- 通常時とAT中の残り枚数・`+N`・`TOTAL N`を表示できるUI状態を追加。**右演出は正の新規上乗せがあるGだけ**、0枚なら消灯。下位1GのTOTALと上位6GのTOTAL、6G目上乗せとのFIFO順まで実装。操作ラベル以外の固定装飾文字を置かない。
- 独立機種の画面は `MachineScreenFactory` で新クラスへルーティングし、既存 `SlotScreen` / `GodScreen` を変えずに実装する。

## 完了条件
1. 既存操作部・データランプ・資産導線と位置/クリック領域が一致する比較スクリーンショット。
2. 16:9以外の画面リサイズ、クリック位置、キー操作、押し順、占有/離席の実動作確認。
3. 機種単独で点灯/消灯、液晶欠損表示、数値桁あふれ、状態更新・再着席のスクリーンショット証跡。
4. 既存4系統の画面/クリック回帰PASS。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。
