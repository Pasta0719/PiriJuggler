# PHASE 10 — Admin / Settings / Startup Allocation / Events

Status: IN_PROGRESS

## 読むもの
- `../SPEC.md` sections: 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 86, 87, 94, 97, 100, 102, 103, 104, 106, 135, 137
- `../RUNTIME_ACCEPTANCE.md` Phase10

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
- AdminSession/UI
- setting history
- business period startup
- profiles/patterns/guarantees

## 追加完了条件
- pattern then guarantees
- next override transactional
- Phase10 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_10/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
