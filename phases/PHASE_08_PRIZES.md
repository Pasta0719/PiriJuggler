# PHASE 08 — Prize Exchange / Vault Exchange / Transactions

## 読むもの
- `../SPEC.md` sections: 48, 49, 50, 94, 95, 96, 97, 106, 108, 113, 114, 115, 134, 136, 137
- `../RUNTIME_ACCEPTANCE.md` Phase08

## 共通ルール
- Phase開始時status=IN_PROGRESS。
- COMPLETE済みPhaseを壊さない。
- productionにTODO/FIXME/stub/仮実装を残さない。
- 既存testを削除して通さない。
- `./gradlew test`と`./gradlew build`両方exit0必須。
- `RUNTIME_ACCEPTANCE.md`の該当Phaseを実MinecraftでPASS必須。
- runtime evidence保存必須。
- runtime未実施ならCOMPLETE禁止。
- COMPLETE後は次Phaseへ進まず停止。

## 実装範囲
- Prize GUI
- DP max
- inventory capacity
- Vault exchange journal

## 追加完了条件
- Vault failure restore
- REVIEW_REQUIRED crash window test
- Phase08 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_08/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
