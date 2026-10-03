# SKILL STOP Phase 02 implementation
Status: COMPLETE — 停止制御・全体ビルド・実Minecraft受入PASS
Updated: 2026-10-03

ユーザー承認: Phase02を完了し、現行mod/pluginと同じJARの更新として実装する。
SKILL_STOPはMachineTypeとGameEngineRegistryへ追加し、既存台のリール配列/役確率/制御は変更しない。

## 実装範囲
- 共通: SkillStopReels / SkillStopRole / SkillStopHistory / SkillStopControl。
- 5ラインと最大4コマ、第二停止の第三入力安全性、指定第一停止法則、ビタ入賞保護、追加1枚役の引き込み、プレミアFの第二テンパイ否定。
- 通常BAR/未成立役/誤種ボーナスの否定、普通の左中段チェリーの下段落とし、プレミアB中段ビタ許可。
- 持越し時は通常出目法則を維持し、1BET小役を再抽選。入賞したリプレイの次回BETは無料。
- チャレンジ制御本体はボーナス一直線だけを蹴り、指定以外が揃っても失敗、14枚を返す。
- ボーナス通常Gの制御本体はブドウ/チェリーの自動揃い。通常4コマ制限を適用しない。
- Paper: SkillStopGame / SkillStopRoundを既存停止パケット/トランザクション/SQLiteへ接続。
- 入力/停止/押し順の履歴をmachine_state_jsonへ保存し、再開時に復元。
- 通常の採用整数抽選表を専用resourceに格納。既存機種configの確率は変更しない。
- Fabric: 既存SlotScreenと実物筐体renderへ機種別の配列を追加。既存PNGを使用。

## 検証済み
- 共通・Paper・Fabricのroot test / packagePiriJars、runtime helpersのビルドPASS。
- 停止制御79条件×6押し順×21³=4,389,714履歴PASS。研究コードとは別に実装を全入力列挙。
- Paper結合7テスト: 8/4/10枚、6種類1枚役BIGビタ、1枚取得後持越し、REG誤種否定、F、履歴復元、実SQLite grace再着席。
- Fabric入力4テスト: 実際のEnvelopeCodecで押した位置が送信されること、元Envelopeの不変性を検証。
- 実Paper/Fabric受入33/33 PASS: 実キー入力、4コマ滑り、G/C/P払出8/4/10、ONE_CDからBIG入賞、画面/DB停止一致、内部役非公開。
- 全体Windows CI: https://github.com/Pasta0719/PiriJuggler/actions/runs/37120918659
- 実Minecraft CI: https://github.com/Pasta0719/PiriJuggler/actions/runs/37120916132
- 検証対象ソース: `78911f2a6983896a87a3c6710473cabe541856fe`。証拠: `runtime-evidence/SKILL_STOP_PHASE_02/REPORT.md` と `result.json`、上記CI artifact。

## 実機検証で修正した結合不具合
`Envelope.payload()`はコピーを返すため、既存SlotUiのコピーへの追記ではpressedIndexが送信されなかった。
変更したbodyから新しいEnvelopeを作り、実際に押した位置を保持する。既存プロトコルは同じ。
SKILL_STOP再着席時はmachine_state_jsonを保持し、入力履歴を復元する。

## 後続Phaseとの境界
本Phaseは停止制御と入賞判定。チャレンジ抽選/成功+3G/残りG管理の接続はPhase03。
Phase02完了時点のボーナス進行は20/8Gの通常Gのみだった。最新の接続状況はSKILL_STOP_PHASE_03_IMPLEMENTATION.mdを参照。採用表を読み込むことだけで完成版113%を検証したことにしない。
Phase03/04/05まで完成する前の一般利用向け完成版とは扱わない。

## 操作
- /piri machine create SKILL_STOP または /piri machine type <id> SKILL_STOP。
- 管理者の検証用 /piri skillrole <id> <ROLE|clear> [NONE|A|B|C|D|E|F] は空席時の次通常レバーだけに作用する。
- 既存製品JAR名 piri-juggler-paper-1.0.0.jar / piri-juggler-fabric-1.0.0.jar を維持。

## 完了記録
2026-10-03: 指定GitHubへの送信承認後に検証ブランチ/PRを作成。全体CIと実機CI PASSを確認してCOMPLETEへ更新。
Phase03以降は開始しない。
