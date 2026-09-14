# PHASE 04 — Fixed Reels / 9261 Precompute / Stop Solver

## 読むもの
- `../SPEC.md` sections: 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 30, 31, 34, 90, 106, 132, 137
- `../RUNTIME_ACCEPTANCE.md` Phase04

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
- fixed reels
- 9261
- strict candidates
- deterministic solver
- premium helpers
- motion pressedIndex integration

## 追加完了条件
- strict candidate counts
- all stop orders/pressed indices
- Phase04 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_04/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
