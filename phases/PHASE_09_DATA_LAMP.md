# PHASE 09 — Data Lamp / Graph / Piri Chain

## 読むもの
- `../SPEC.md` sections: 58, 59, 60, 61, 62, 70, 71, 93, 99, 100, 106, 111, 122, 123, 133, 137
- `../RUNTIME_ACCEPTANCE.md` Phase09

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
- period stats
- history
- graph/LTTB
- piri-chain
- data lamp UI

## 追加完了条件
- current period only
- 100/101 boundary
- Phase09 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_09/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
