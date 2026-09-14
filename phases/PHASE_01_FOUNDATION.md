# PHASE 01 — Gradle / Paper / Fabric / Protocol Handshake

## 読むもの
- `../SPEC.md` sections: 1, 2, 84, 85, 86, 87, 88, 94, 103, 104, 113, 114, 115, 125, 126, 130, 137
- `../RUNTIME_ACCEPTANCE.md` Phase01

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
- Gradle multi-project
- Paper/Fabric/common build
- protocol envelope + HELLO
- config validation
- spec-lock base

## 追加完了条件
- Phase01 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_01/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
