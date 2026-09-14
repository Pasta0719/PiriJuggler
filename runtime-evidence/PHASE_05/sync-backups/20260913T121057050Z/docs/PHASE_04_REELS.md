# Phase04 — 固定リールと停止制御

`common/reel/FixedReels` が全3本・各21コマの配列をコード定数で保持します。Fabric表示とPaper判定は同じ配列を使います。配列や停止ルールを設定ファイルで変更する機能はありません。

## 候補と停止位置

`StopCatalogue` は全9,261組について5ラインと左リールのチェリーを評価し、不正な入賞の組合せも含めて保存します。厳密候補の件数は次のとおりです。

| 表示役 | 候補数 |
|---|---:|
| GRAPE | 750 |
| BELL | 50 |
| PIERO | 20 |
| REPLAY | 525 |
| CHERRY | 1,514 |
| MISS | 5,250 |
| BIG_ENTRY | 10 |
| REG_ENTRY | 10 |
| PREMIUM_B | 782 |

`StopSolver.choose` は停止済みリールだけを固定し、slip、targetRank、左・中・右indexの順に選びます。次回のSTOPでは残る候補を再評価します。プレミアムFは第2停止で実際のSEVENテンパイを除外し、テンパイ音eventが必要であることを返します。BとFの内部役の適格条件は `InternalRole` に集約しています。

`ReelEngine` は起動時に候補最低数、通常500,094通り、F 166,698通りの全停止順・全押下indexを検証します。Fの第2停止7,938条件を含みます。失敗時は診断をSEVEREへ記録し、台利用を拒否します。

## 後続Phaseからの呼出し

Paperのmain thread上で `ReelRound` を作成し、`begin(System.nanoTime())` の戻り値を直ちに送信します。STOP受信時のnanoTimeと `Player.getPing()` を `receive` に渡します。受信内容はsessionId / machineId / clientSequenceのみで、spinIdと回転開始時刻はサーバーが保持します。早期STOPもsequenceを消費します。

`ReelMotion` はNORMAL / REVERSE_500MS / RESUME_NORMALの式とRTT補正を共有します。FabricはSPIN_STARTの受付待ち時間を守り、REEL_STOPで受け取った最終位置へ補間します。RESUMEは停止済みリールを固定し、レバー音を再発火しません。

Phase04は停止制御の部品までです。抽選、BET・払出し、ゲーム状態の永続化、プレミアム演出の制御と音event送信は後続Phaseのゲームコントローラーが接続します。現在の通常操作でゲームを開始できるという意味ではありません。

## 検証の再実行

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat -PruntimeAcceptance=true -PruntimeScenario=phase04-reels -PruntimeEvidencePhase=PHASE_04 :runtime-test-client:build :runtime-test-paper:build --console=plain
python docs/preflight_phase04.py
python runtime-test-support/run_phase04.py
```

実機ランナーはPhase01でSHAを確認済みのPaper 1.21-130を使用し、localhost:25588の専用サーバーと実Fabricクライアントを起動します。9表示役×6停止順とFの3表示役×6停止順、計72回転・216 STOPを検証します。テスト専用controllerが役を選び、実際のキー入力とパケットを本番の `ReelRound` に渡します。helperは配布JARへ含まれません。テストでの役指定や表示は資産移動を行いません。

証跡は `runtime-evidence/PHASE_04/` に保存します。各試行は `attempts/` に残ります。

## 音源とJAR

実音源はユーザーが別途提供します。`user-audio/` に既存SoundEvent名のOGGを置き、ルートの `build-jars.bat` を実行すると `dist/` のFabric JARに同梱されます。音源未配置でもビルドとテストは成功します。内容の生成や代替音源の作成は行いません。詳しくは `BUILD_JARS.md` と `user-audio/README.md` を参照してください。
