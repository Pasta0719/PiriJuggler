# MIGRATION_FROM_V3.md

対象: v3 workflowでPhase01を既にCOMPLETEし、Phase02開始時にSPEC矛盾でBLOCKEDしたrepo。

## 絶対にしないこと

- Phase01の実装コードを捨てて最初から作り直さない。
- 現在の`IMPLEMENTATION_STATUS.md`をtemplateで上書きしない。
- v3 SPECの矛盾をCodex判断で片方だけ採用しない。

## 手順

1. このv4 packageの`SPEC.md`, `CODEX_START.md`, `RUNTIME_ACCEPTANCE.md`, `PHASE_MAP.json`, `phases/`をrepoへ上書きする。
2. 現在repoの`IMPLEMENTATION_STATUS.md`は残す。
3. statusのPhase01がCOMPLETEなら維持する。
4. Phase02がBLOCKEDなら`IN_PROGRESS`へ戻す前に「v4 migration preflight」を実行する。

### v4 migration preflight

- `./gradlew test`
- `./gradlew build`
- Phase01 Runtime Acceptanceを`RUNTIME_ACCEPTANCE.md`どおり実施
- runtime evidence `runtime-evidence/PHASE_01/REPORT.md`を作成
- Phase01実装がv4 packet IDs/envelope/config validationと不一致ならPhase01範囲だけ修正
- 再度test/build/runtime acceptance

全部PASS後:
- IMPLEMENTATION_STATUSへ`v4 migration applied`を追記
- Phase02をIN_PROGRESSにする
- v4 PHASE_02指示書でPhase02を続行

v3の開発DBが既に作られている場合、v4 schema migration対象外なのでtest/dev DBをバックアップ後削除してschema v4で再作成する。production dataはまだ存在しない前提。
