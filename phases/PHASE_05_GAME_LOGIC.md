# PHASE 05 — RNG / Normal Game / Replay / Simulator

## 読むもの
- `../SPEC.md` sections: 16, 17, 18, 19, 20, 21, 22, 33, 40, 41, 51, 57, 59, 61, 88, 100, 103, 104, 105, 106, 113, 114, 128, 131, 132, 137
- `../RUNTIME_ACCEPTANCE.md` Phase05

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
- GameState
- normal BET/LEVER/STOP
- held refill
- RNG
- payout/replay
- simulator

## 追加完了条件
- 10M fixed seed tolerance
- no client RNG/internal leak
- Phase05 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_05/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
