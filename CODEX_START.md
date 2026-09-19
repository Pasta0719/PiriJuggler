# CODEX_START.md — v4

`SPEC.md`が製品仕様の正本。Phase12–14のRemote Machine Visual / Hall Audioについては `docs/REMOTE_MACHINE_VISUAL_SPEC.md` を追加の仕様正本として必ず併読する。Phase05完了後のユーザー承認hotfixについては `docs/POST_PHASE05_HOTFIX.md` を必ず併読し、同文書が明示的に変更した範囲ではhotfix内容を最新仕様として扱う。次回SPEC整合更新時に正本へ統合する。
`AUDIT_REPORT.md`はv3からの修正理由であり、仕様値の正はSPEC.mdと上記承認hotfix。

## 開始時の引き継ぎ確認

最初に `SESSION_HANDOFF.md`、`docs/POST_PHASE05_HOTFIX.md`、`IMPLEMENTATION_STATUS.md` 冒頭の現状とPhase表、`SPEC_CONFLICT.md` の解消状況を読む。会話履歴や旧作業フォルダを前提にしない。

ユーザーが確認・引き継ぎ文書更新だけを依頼したrunでは、Phase選択・IN_PROGRESS化・次Phase実装を行わず、その依頼だけを完了する。実装続行を依頼されたrunで下の通常runへ進む。

過去のBLOCKED、未適用patch、旧テスト件数、各Phase終了時の「次Phase未着手」は履歴。現在の状態はPhase表と最新の完了記録で判断し、解消済み事項を再承認待ちに戻さない。引き継ぎ文書にある追加のSPEC参照章も、対応Phaseの読むものに含める。

## 最初にmigration判定

repoに既存実装があり、`IMPLEMENTATION_STATUS.md`にPhase01 COMPLETEまたはPhase02 BLOCKEDがある一方、`v4 migration applied`記録が無ければ、最初に`MIGRATION_FROM_V3.md`を実行する。
そのrunではmigration preflightだけ行い、Phase02実装へ進まない。

## 通常run

1. IMPLEMENTATION_STATUS.mdを読む。
2. `docs/POST_PHASE05_HOTFIX.md` の未検証事項があれば、後続Phaseを始める前にその回帰検証を行う。
3. IN_PROGRESSがあればそのPhase。なければ最小NOT_STARTED Phase。
4. 対応phase fileを全文読む。
5. 指定SPEC章 + `RUNTIME_ACCEPTANCE.md`を読む。
6. statusをIN_PROGRESS。
7. 実装。
8. unit/integration test。
9. `./gradlew test`。
10. `./gradlew build`。
11. そのPhaseの実Minecraft Runtime Acceptanceを実施。
12. evidenceを保存。
13. 全完了条件PASS後だけCOMPLETE。
14. 次Phaseへ進まず終了。

## COMPLETE禁止

- runtime acceptance未実施
- 「ゲーム上では未確認」
- TODO/FIXME/stub/仮実装
- test/build failure
- SPEC conflict未解消

runtime environmentが無ければBLOCKEDにして停止。

SPEC内の真正面の矛盾を発見した場合だけCONFLICT形式で停止する。実装都合や好みで質問しない。
