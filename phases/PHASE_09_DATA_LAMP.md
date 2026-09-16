# PHASE 09 — Data Lamp / Graph / Piri Chain

Status: COMPLETE
Evidence: `runtime-evidence/PHASE_09/REPORT.md`

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
- public remote machine data: `/piri data` で全稼働台の当日実績一覧、`/piri data <machineId>` で当日実績・直近bonus history詳細をチャット表示
- public remote dataに設定値そのものは表示しない

## 追加完了条件
- current period only
- 100/101 boundary
- `/piri data` は台へ着席・物理アクセスせず一般プレイヤーが使用可能
- `/piri data <machineId>` は total/current G、BIG/REG/合算実績確率、差枚、最大差枚、直近bonus historyを表示
- Phase09 runtime acceptance全部PASS

## 終了時
`runtime-evidence/PHASE_09/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
