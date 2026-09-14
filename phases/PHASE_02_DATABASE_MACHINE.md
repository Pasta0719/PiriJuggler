# PHASE 02 — SQLite / Machine Registration / Session

## 読むもの
- `../SPEC.md` sections: 42, 63, 64, 65, 66, 67, 68, 70, 71, 78, 79, 84, 94, 95, 97, 127, 135, 137
- `../RUNTIME_ACCEPTANCE.md` Phase02

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
- schema v4
- business period base
- machine create/redefine/remove/list/info
- session lifecycle/lock
- machine key/admin session入口

## 追加完了条件
- duplicate location rejection
- same player second session rejection
- Phase02 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_02/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
