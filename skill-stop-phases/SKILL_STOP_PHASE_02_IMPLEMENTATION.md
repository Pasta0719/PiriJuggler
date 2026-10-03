# SKILL STOP Phase 02 implementation
Status: IN_PROGRESS — GitHub push許可済み、Fabric/実機受入未完了
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
- 共通42テスト、Paper279テスト: failures/errors=0。
- 実装した停止制御79条件×6押し順×21³=4,389,714履歴PASS。
- 法則停止、4コマ上限、第三入力行き詰まり、未成立役、BAR、誤種ボーナス、Fテンパイ、正しい入賞のビタ、払い出しを検査。
- Paper結合6テスト: 8/4/10枚、6種類1枚役のBIGビタ入賞、1枚取得後持越し、REG誤種否定、F、再開履歴。
- :common:test :paper:test :paper:jar build PASS (Java21/Gradle8.14.3)。

## 未検証 / 完了条件
- Fabric compile/test/remapJarと実Minecraft受入は未完了。
- ローカルFabricビルドはLoom CurrentPlatform.isUnixDomainSocketsSupportedでOperation not permitted。
- 実機runner: runtime-test-support/run_skill_stop_phase02.py。
- 専用CI: .github/workflows/skill-stop-phase02-runtime.yml。通常CI環境で変更を検証する。
- 実際のPaper+FabricでG/C/P払い出し、入力滑り、1枚役からBIGビタ入賞、画面停止一致、内部役非公開を確認する。

## 後続Phaseとの境界
本Phaseは停止制御と入賞判定。チャレンジ抽選/成功+3G/残りG管理の接続はPhase03。
現時点のボーナス進行は20/8Gの通常Gのみ。採用表を読み込むことだけで完成版113%を検証したことにしない。
Phase03/04/05まで完成する前の一般利用向け完成版とは扱わない。

## 操作
- /piri machine create SKILL_STOP または /piri machine type <id> SKILL_STOP。
- 管理者の検証用 /piri skillrole <id> <ROLE|clear> [A|B|C|D|E|F] は空席時の次通常レバーだけに作用する。
- 既存製品JAR名 piri-juggler-paper-1.0.0.jar / piri-juggler-fabric-1.0.0.jar を維持。

## 現在のブロック
2026-10-03: 実装はローカルGit 1d2b825に保存済み。skill-stop-phase02ブランチへのpushは自動承認審査で拒否。
理由: 実装の承認だけではこのGitHubリモートへのソース送信が明示的に承認されていないと判定された。
リモートにブランチは作成されていないことをread-onlyで確認済み。専用CIは未実行。
ユーザーのpush許可後に検証用ブランチを送信し、Fabric buildと実Minecraft受入を実行する。
Phase02 COMPLETE、main反映、完成版JAR提供はまだ行わない。

2026-10-03 20:21 JST: ユーザーが指定リモートへのpushを許可。承認待ちは解消。CLIにGitHub認証がないため接続済みGitHub APIで検証ブランチへ送信する。
