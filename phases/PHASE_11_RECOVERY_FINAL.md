# PHASE 11 — Suspend / Restart Recovery / Hardening / Final Tests

## 読むもの
- `../SPEC.md` sections: 42, 45, 70, 71, 78, 79, 80, 81, 82, 83, 88, 94, 95, 98, 99, 100, 101, 106, 107, 108, 113, 114, 115, 116, 117, 118, 126, 127, 130, 133, 134, 135, 136, 137
- `../RUNTIME_ACCEPTANCE.md` Phase11

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
- disconnect grace
- force settlement
- restart recovery
- security
- full audit/regression

## 追加完了条件
- FINAL_VERIFICATION all40 PASS
- no TODO/FIXME/stub
- Phase11 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_11/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
