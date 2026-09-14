# PHASE 07 — Vault / CREDIT / Held Medals / Medal Token

## 読むもの
- `../SPEC.md` sections: 40, 41, 42, 43, 44, 45, 46, 47, 51, 83, 94, 95, 96, 98, 103, 106, 108, 113, 114, 115, 127, 133, 134, 136, 137
- `../RUNTIME_ACCEPTANCE.md` Phase07

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
- Vault loan
- credit/held
- physical token ledger
- cashout
- recover
- inventory operations

## 追加完了条件
- no wallet credit/held
- duplicate token no double spend
- Phase07 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_07/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
