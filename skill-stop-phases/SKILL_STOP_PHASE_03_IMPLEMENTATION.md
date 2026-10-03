# SKILL STOP Phase03 implementation
Status: COMPLETE — 全体CI・実Minecraft受入202/202 PASS
Updated: 2026-10-03

## 実装
- BIG20G、REG8G。ボーナス入賞Gは消化せず図柄払出0。
- ボーナスレバーでBIG1/15、REG1/9のチャレンジを抽選。対象BAR/ベル/ピエロ各1/3をその場で固定。
- 追加Gでも同じ抽選。全成功時の平均Gは20/(1-3/15)=25、8/(1-3/9)=12。
- 第三停止で成功失敗にかかわらず14枚。残り-1、成功+3の後に0G終了判定。累計払出閾値を終了条件にしない。
- 指定外図柄は失敗だが蹴らない。チャレンジは777/77BARだけを否定。通常ボーナス表示はG/Cの自動揃いを使用。
- 保存先は既存machine_state_jsonのskillRemaining/skillChallenge。役、spin_id、入力/停止/押し順も既存原子トランザクションで固定。
- Phase02ボーナス途中snapshotは累計14枚払出から残りGへ移行し、抽選を引き直さない。
- 当選持越し、1BET再遊技の次G無料を保存。小役取りこぼしでBIG権利を消さない。
- 未完了SKILL_STOPはgrace満了/idle/サーバー再起動で自動清算しない。SUSPENDED_GRACEの台権利を保持して本人の復帰を待つ。
- 未完了台の回復清算はRECOVERY_REQUIREDで拒否し、遊技完了後に通常清算する。他機種の自動清算は従来通り。
- 再起動をまたぐ未完了snapshotは元source_business_period_idで入出金/履歴を一貫させる。
- PUBLIC_STATEには残りGと回転中に固定された対象だけを追加。通常内部役・持越しBIG/REGは公開しない。

## 検証
SQLiteを通す結合8テストPASS: BIG20/REG8終了、REG残1成功と追加G再成功、指定外BAR失敗/14枚、第三停止再送/二重commit、BIG最終Gの実DB再起動と排他、grace/idle/直接再着席、1枚役取りこぼし持越し、連続リプレイ無料、自然抽選率/対象比率。
既存Phase02結合7テストPASS。共通/Paper全testとPaper/runtime helperビルドPASS。
実機runnerはrun-skill-stop-phase03-runtime.bat。CIは.github/workflows/skill-stop-phase03-runtime.yml。
実Paper/FabricでBIG23G（成功1回）、REG8G（全失敗）、最終Gの部分停止/プロセス再起動/復帰、追加G指定外失敗、1BET無料再遊技を検査。
検証対象ソース: `fa81e18a5e4c52c7e2a0144b2324f10bf29fa3ae`。
全体Windows CI: https://github.com/Pasta0719/PiriJuggler/actions/runs/37123297201
実Minecraft CI: https://github.com/Pasta0719/PiriJuggler/actions/runs/37123294908
共通/Paper/Fabric実行373件PASS（Paperのopt-in1件skip）、実機202/202 PASS。
証拠: runtime-evidence/SKILL_STOP_PHASE_03/REPORT.md と result.json。元パケット/ログ/画像はCI artifact。
実機runnerのnull項目読み取りを修正後、全シナリオを最後まで再実行してPASS。製品コードの差し替えによる省略なし。

## 管理者の再現操作
- /piri skillbonus <id> <AUTO|BAR|BELL|PIERO|clear>: 次のボーナスレバーのみ固定。
- /piri skillrole <id> <ROLE|clear> [NONE|A|B|C|D|E|F]: 次の通常または持越しレバー。持越しでは新規小役のみ許可。
- 両操作ともSKILL_STOP限定、台にACTIVEプレイヤーがいる間は拒否。空席の保存台でも使用できる。

## 後続Phase
機械割98.5〜113%の実装検証/調整はPhase04。
対象PNGの点灯/消灯、成功音、残Gの画面/観覧表示はPhase05。本Phaseは保存する対象と残Gを提供する。
Phase04以降はこのrunでは開始しない。既存Paper/Fabric JAR名を維持。

## 完了記録
2026-10-03: Phase03全完了条件PASSを確認してCOMPLETEへ更新。Phase04以降は開始しない。
