# Phase03 実装と再現手順

第138章のword gapをユーザー承認により4 logical columnsへ変更した。描画倍率5で20pxとなり、第120章と一致する。第32章・129章の音源生成要求はユーザー指示により廃止し、実音源をユーザーが提供する方式へ統一した。

## 実装

- `asset-tools`: Java2DのAssetGenerator、9個のARGB PNG、保存済みPNGと再生成PNGのSHA検証。v3第121章の本文とshape dataを保存し、spec-lockへ組み込む。自作5×7 bitmap glyph以外のfontは生成に使わない。
- v3が未指定のPIERO collarの三角形分割はface直径を4等分し、指定y195からsubject下端へ構成する。REPLAY tail tipは指定cubicの終端半分をde Casteljau分割する。指定済み座標・色は変えない。これはv3に存在しない固定値の転記ではなく、未指定部分の描画上の構成方法である。
- `SlotScreen`: 1920×1080の一様拡大縮小と黒余白、固定panel/control位置、線形texture filtering、Piriランプとglow、3列×3段リール、サーバーPUBLIC_STATEのstatus、データランプ数字の7segment表示。
- `SlotInput`: keydown edge、Minecraft設定変更可能な停止キー、左clickと100ms debounce、identity+sequenceだけの送信。STOP index/phase/spinIdを送らない。遅延PUBLIC_STATEで送信済みsequenceを再使用しない。
- `SlotViewState`: 公開されたサーバー値だけを保持。受信時刻を起点にNORMAL/REVERSE/RESUMEを表示し、REEL_STOPのstopIndexへ到達する。RESUMEではstoppedMaskの停止済みリールを維持する。
- SlotScreen中はHUDと遮蔽するtoastを隠し、Tでのみchatを表示する。chatのESCは台へ戻る。台のESCはCLOSE_REQUESTを一度だけ送り、SESSION_END/SUSPENDEDを待つ。2000ms timeoutは画面だけを閉じ、PUBLIC_STATEや資産を変更しない。
- `PiriSounds`: 既存10 SoundEventを登録。ユーザー提供OGGをresource managerで確認して再生する。欠損時は無音で継続し、生成・代替音源は持たない。`user-audio/` の指定名OGGはビルド時にFabric JARへ取り込む。詳細は `BUILD_JARS.md`、`docs/USER_AUDIO.md`。

## 検証と境界

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
.\build-jars.bat --no-pause
.\gradlew.bat -PruntimeAcceptance=true -PruntimeScenario=phase03-ui -PruntimeEvidencePhase=PHASE_03 :runtime-test-client:build :runtime-test-paper:build --console=plain
python runtime-test-support/run_phase03.py
```

Java21/Paper1.21/Fabric1.21の実環境を使用する。テスト補助はopt-in moduleのみで、本番JARへ含めない。実Keyboard.onKey/Mouse.onMouseButtonから本番Screenを通り、実Paperへ到達したパケットを観測する。実スクリーンショットは目視に加えてJavaのScreenshotAuditで各リール内の図柄pixelを検査する。

Phase03ではゲーム抽選・精算処理を追加していない。現時点のPaperはCLOSE以外のゲーム操作をINVALID_STATEで拒否する。受入テストの値32/442、7揃い、点灯、停止アニメーション、DATA_LAMPの値はテスト専用Paper helperが実S2C通信で送る明示的な表示fixtureであり、抽選や資産移動が完成した証跡ではない。実sessionのcredit/heldMedalsが0のままであることも検査する。履歴・graph等のData Lamp全機能はPhase09で統合する。

音源は未提供のため、登録・再生要求・欠損時継続を確認する。音源内容・品質や実音の聴取を完成条件にしていない。提供された音はMinecraftのOGG decoderで読み込まれる。

Evidence: `runtime-evidence/PHASE_03/REPORT.md`、`result.json`、`screenshots/`。初回の通信検査後に発見したscissor座標の不具合もattemptsへ保存し、修正後に画面検査を追加して全項目を再実行した。
