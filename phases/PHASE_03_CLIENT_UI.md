# PHASE 03 — Fabric Slot Screen / Input / Base UI

## 読むもの
- `../SPEC.md` sections: 3, 23, 32, 51, 52, 53, 54, 55, 56, 57, 84, 85, 86, 87, 89, 90, 91, 92, 109, 112, 119, 120, 121, 122, 129, 138, 137
- `../RUNTIME_ACCEPTANCE.md` Phase03

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
- AssetGenerator
- ユーザー提供OGGの読み込み / SoundEvent登録 / 再生（音声内容は生成しない）
- Slot Screen
- HUD/input/mouse
- packet rendering

## 追加完了条件
- asset SHA
- actual screenshots
- Phase03 runtime acceptance全部PASS


## 終了時
`runtime-evidence/PHASE_03/REPORT.md`とIMPLEMENTATION_STATUSへevidence pathを記載し、COMPLETEにして停止。
