# PHASE 10 — Admin / Settings / Startup Allocation / Events

Status: COMPLETE

## 読むもの
- `../SPEC.md` sections: 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 86, 87, 94, 97, 100, 102, 103, 104, 106, 135, 137
- `../RUNTIME_ACCEPTANCE.md` Phase10
- `../docs/spec-amendments.json` Phase10 approved amendment

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

## 承認済み仕様補完 — 2026-09-16

SPEC 68章はAdmin Screen closeでAdminSession即失効を要求する一方、86/87章にはそれをserverへ通知するC2S packetが存在しなかった。
ユーザー承認により `ADMIN_CLOSE=16` を追加する。

```json
{
  "adminSessionId": "uuid",
  "machineId": 1,
  "adminSequence": 1
}
```

通常play用 `CLOSE_REQUEST=10` は流用しない。ADMIN_CLOSEも他のadmin packetと同様にOP / owner / machineId / sequenceを毎回再検証し、成功時にmemory-only AdminSessionを即破棄する。承認内容の機械可読記録は `docs/spec-amendments.json`。

## 実装状況
- [x] AdminSession 5分期限 / OP / owner / machine / sequence再検証
- [x] OP + machine keyでADMIN_STATEを取得しFabric Admin Screenを開く
- [x] setting 1..6 / autoSetting / enabled / current-period resetをAdmin Screenから要求
- [x] busy machineは閲覧可、mutating admin actionは拒否
- [x] Admin Screen close -> ADMIN_CLOSE -> AdminSession即失効
- [x] manual settingは即時反映、stats resetなし、同値no-op、history reason=MANUAL
- [x] `/piri setting <machineId> <1-6>`
- [x] `/piri reset daily <machineId|all>`。allはbusyが1台でもあれば全体拒否
- [x] `/piri event status`, `/piri event next <profile>`, `/piri event next clear`
- [x] startup profile priority / pattern先行 / guarantees後補正 / eligible条件
- [x] next-start overrideはstartup allocation transaction成功時だけ消費
- [x] Unit/Integration test追加
- [x] local Gradle test/build PASS確認（ユーザーWindows環境の `build-jars.bat` で main build / runtime helper build とも BUILD SUCCESSFUL。Phase10 runtimeで使用したPaper jarも再build/redeploy済み）
- [x] Phase10 Runtime Acceptance PASS

## Runtime確認済み — 2026-09-16
- [x] OP + machine key Admin Screen actual open
- [x] busy machine mutation reject（occupied中の `/piri setting 1 6` が `MACHINE_OCCUPIED`、離席後もsetting不変）
- [x] setting change history（MANUAL row確認）
- [x] true server restart / startup allocation path（新しい `SERVER_START` history確認。businessPeriodId UUIDそのものはAdmin UI非表示）
- [x] special date profile（test configで実profile切替確認、試験後 `special_dates: {}` に復元済み）
- [x] manual next override priority + one-shot consumption

`RUNTIME_ACCEPTANCE.md` のPhase10は上記5項目（special date/manual nextは1項目）を要求しており、すべて実Minecraftで確認済み。

Admin Screen close直後のold adminSessionId拒否はSPEC/実装要件としてproduction wiringとunit testで確認済みだが、`RUNTIME_ACCEPTANCE.md` Phase10の追加runtime項目ではないためCOMPLETEを妨げない。

## 追加完了条件
- [x] pattern then guarantees
- [x] next override transactional
- [x] Phase10 runtime acceptance全部PASS

## 終了時
`runtime-evidence/PHASE_10/REPORT.md` にevidenceを記載し、Phase10をCOMPLETEとして停止。
