# Phase03 Runtime Acceptance — PASS

実行: 2026-09-13T01:32:46.085269+00:00 ～ 2026-09-13T01:34:12.386097+00:00（UTC）。
Run: `20260913T013246Z`。Minecraft1.21 / Paper1.21-130 / Fabric Loader0.16.14 / API0.102.0+1.21 / Yarn1.21+build.9 / Java21。
実Paperと実Fabricへビルド済み本番JARを読み込み、localhost:25587、PiriRuntimeTest、隔離runtimeフォルダで実施した。server/clientともexit0、未処理例外0。

## 仕様変更

- 第120章の20pxを正として第138章を4 logical columnsへ変更。4×5px=20px。SPEC_CONFLICT.mdに解消済みとして記録。
- ユーザー指示により第32/129章をユーザー提供OGG方式へ変更。今回追加した10生成OGG・AudioAssetGenerator・生成タスク・Vorbis依存と中間classを削除。
- SoundEvent IDと対応名10種を維持。音源は未配置で検証。実音源の内容・品質・聴取はPhase03の完成条件ではない。
- `user-audio/` の必要なOGGだけを配置し `build-jars.bat` で再ビルドできる。完成JARは `dist/`。コード変更は不要。日本語手順は `BUILD_JARS.md`。

## 手順と結果

Unit/integration **111件 PASS**: common20 / paper73 / fabric16 / asset-tools2（失敗・error・skip0、既存99件を保持）。
`gradlew.bat test --console=plain` exit0、`gradlew.bat build --console=plain` exit0、`build-jars.bat --no-pause` exit0。
ログ: `gradle-test.log`、`gradle-build.log`、`build-jars.log`。画像再生成SHAと保存済み9 PNGのSHAを照合した。

実キーイベントは `Keyboard.onKey`、実マウスイベントは `Mouse.onMouseButton` をテスト専用helperから呼び、本番SlotScreen/input/networkを通ってPaperへ到達することを検査した。helperの直接送信でゲーム入力を代用していない。

| Runtime assertion | 結果 |
|---|---|
| actual screenshot contains all three reel symbol columns: open-off | PASS |
| actual production SlotScreen opens from real Button session | PASS |
| HUD hidden and mouse cursor visible | PASS |
| all 10 SoundEvents registered with user audio absent | PASS |
| real Keyboard.onKey edge/repeat -> SPACE_ACTION | PASS |
| real Keyboard.onKey edge/repeat -> STOP_LEFT | PASS |
| real Keyboard.onKey edge/repeat -> STOP_CENTER | PASS |
| real Keyboard.onKey edge/repeat -> STOP_RIGHT | PASS |
| real Keyboard.onKey edge/repeat -> LOAN | PASS |
| real Keyboard.onKey edge/repeat -> INSERT_MEDALS | PASS |
| real Keyboard.onKey edge/repeat -> CASH_OUT | PASS |
| Minecraft KeyBinding rebound LEFT to A | PASS |
| right mouse button ignored | PASS |
| actual mouse event hits logical stop rectangle | PASS |
| T opens chat; chat Escape returns without CLOSE_REQUEST | PASS |
| actual screenshot contains all three reel symbol columns: on-reels-status | PASS |
| S2C display fixture reaches public model without mutating server assets | PASS |
| actual screenshot contains all three reel symbol columns: packet-animation | PASS |
| SPIN_START REEL_STOP NOTICE and missing-audio replay path stay healthy | PASS |
| actual screenshot contains all three reel symbol columns: letterbox | PASS |
| aspect-preserving viewport and mouse mapping after resize | PASS |
| Escape sends one CLOSE_REQUEST and closes on server SESSION_END | PASS |
| every real action has only identity and strictly increasing sequence | PASS |

## 実画面

- `screenshots/phase03-open-off.png`: 実Buttonから作成したsession、SlotScreen、3列×3段図柄、消灯、credit/medals0。
- `screenshots/phase03-on-reels-status.png`: 表示fixtureのPiri ON、7揃い、CREDIT32/BET3/PAY14/MEDALS442、DATA_LAMP数字。
- `screenshots/phase03-packet-animation.png`: サーバー指定停止位置への表示。
- `screenshots/phase03-letterbox.png`: 1100×700、等比倍率・黒余白、同じ論理hitboxからのmouse送信。
- `screenshots/phase03-chat-only-on-t.png`: Tでのみchatが表示される。chatのESCでは台へ戻り、CLOSE_REQUESTは出ない。

目視確認済み。ScreenshotAudit.javaは実PNGを読み取り、各リール領域の図柄pixelの存在も検査した。HUDのhotbar/crosshair/status/XP/scoreboard描画がSlotScreen中に呼ばれた回数0をテストhelperで観測した。

## 検証範囲

Phase03の実Paperはゲーム開始・入金・精算をまだ実装しておらず、CLOSE以外の操作はINVALID_STATEを返す。本テストは入力が本番画面から実packetとして届くことを検査する。32/442の残高、7揃い、点灯、SPIN/REEL_STOP/DATA_LAMP等はテスト専用Paper helperが実S2Cで送る表示fixtureであり、ゲーム抽選・資産移動の完成を意味しない。serverの実資産は0のままであることを確認した。ゲーム処理は後続Phase、data history/graphはPhase09で統合する。

音源未配置で全10 SoundEventの登録、再生要求経路、欠損時の無音継続を確認した。ユーザー提供OGGの実音内容を推測・生成していない。

初回run `20260913T012531Z` は通信19項目がPASSした後、目視でリールが空白になるscissor座標不具合を発見した。同attempt/result.jsonへvisualReview FAILを追記して保持。GUI座標へ変換する修正と画像pixel検査を加え、今回の23項目を再実行してPASSした。

同一JARのPhase01正常/mismatch実Minecraft回帰テストもPASS。別フォルダ `runtime-evidence/PHASE_03_PHASE01_REGRESSION/` に保存した。common/PaperのJAR SHAはPhase02完了時と同一である。

## 本番JAR SHA-256

| Artifact | SHA-256 |
|---|---|
| common | `bdcf0f21d0964f8e628d97baa16ad9fa2568cfbc730f884bccb92f469d53a2df` |
| paper | `95f4d8d9975056dcef5e99aa1f3914a1ae0a70a34f209191bb419157e5be8ed5` |
| fabric | `29655f0c2de22c828bb8619ba844922912c1fcd236a56d180953804a04d63cc3` |

`dist` のPaper/Fabric JARも上記と同一。`artifact-verification.json` でJava21、metadata、remap、helper非同梱、画像SHA、ユーザー音源入力との一致、音声生成処理除去、実runtime hash一致を照合した。

## 画像 SHA-256

| Image | SHA-256 |
|---|---|
| lamp/piri_chance_off.png | `6b65f410dcc847636ea0caef2c2de2d8d9a964845bf6cadbe96fe7daa7f0cf60` |
| lamp/piri_chance_on.png | `0a16a5705a50e832fa960f20cb78f472096b3b951333ef9a4efde2a5ab988ad3` |
| symbols/bar.png | `5ad5f4782e516dd95f80fdaf72e974605976f9a10a4850d308165953a1e189c0` |
| symbols/bell.png | `c7f382f934dc6b7704b498a366265ed41c6fa9092a6af440d8230b23a1fbeede` |
| symbols/cherry.png | `4bd8112d6558617d1cebf874cf9bee278a7c710315e714158cac95a4eaa2ad9f` |
| symbols/grape.png | `abbbdec4633e20d46ef5d6ea3425a4d1c7a6820f6abff543d8ca99479b958bc2` |
| symbols/piero.png | `b5bd20c8c76f9fafe63ba4c6218fb46db0aba073b970ad974dd927ad84b2e84f` |
| symbols/replay.png | `9b688d3e44858ffadf5e3690975a846d0e06daf72516cd13ff8d0784c8033e4c` |
| symbols/seven.png | `106a2389a3281515312ca8f2739a5b4f3e3ffc311dc7f1190afaa603b845e696` |

Phase03をCOMPLETEとし、次Phaseへ進まず終了する。
