# PHASE 06 — Piri Chance / Premium / Audio / BIG / REG

## 読むもの
- `../SPEC.md` sections: 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 35, 36, 37, 38, 39, 89, 90, 91, 101, 104, 106, 128, 129, 131, 137
- `../RUNTIME_ACCEPTANCE.md` Phase06

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
- Piri notice
- premium A-F eligibility
- tenpai
- bonus entry
- BIG/REG
- bonus display roles

## 追加完了条件
- B only CHERRY_BIG
- BIG20/280 REG8/112
- Phase06 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_06/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
