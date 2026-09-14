# CODEX_START.md — v4

`SPEC.md`が唯一の製品仕様。
`AUDIT_REPORT.md`はv3からの修正理由であり、仕様値の正はSPEC.md。

## 最初にmigration判定

repoに既存実装があり、`IMPLEMENTATION_STATUS.md`にPhase01 COMPLETEまたはPhase02 BLOCKEDがある一方、`v4 migration applied`記録が無ければ、最初に`MIGRATION_FROM_V3.md`を実行する。
そのrunではmigration preflightだけ行い、Phase02実装へ進まない。

## 通常run

1. IMPLEMENTATION_STATUS.mdを読む。
2. IN_PROGRESSがあればそのPhase。なければ最小NOT_STARTED Phase。
3. 対応phase fileを全文読む。
4. 指定SPEC章 + `RUNTIME_ACCEPTANCE.md`を読む。
5. statusをIN_PROGRESS。
6. 実装。
7. unit/integration test。
8. `./gradlew test`。
9. `./gradlew build`。
10. そのPhaseの実Minecraft Runtime Acceptanceを実施。
11. evidenceを保存。
12. 全完了条件PASS後だけCOMPLETE。
13. 次Phaseへ進まず終了。

## COMPLETE禁止

- runtime acceptance未実施
- 「ゲーム上では未確認」
- TODO/FIXME/stub/仮実装
- test/build failure
- SPEC conflict未解消

runtime environmentが無ければBLOCKEDにして停止。

SPEC内の真正面の矛盾を発見した場合だけCONFLICT形式で停止する。実装都合や好みで質問しない。
