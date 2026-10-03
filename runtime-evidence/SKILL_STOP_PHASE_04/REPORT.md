# SKILL STOP Phase04 Runtime/Economy Acceptance
Status: PASS / COMPLETE

検証source: `84d8e0efd9e45548339821d7f3e3954d40957eb6`。後続変更は完了記録・確率表・証拠だけ。

- 実制御/研究全履歴2,222,640の停止・払出・再遊技・入賞一致。
- 実JAR用整数重みと保存解析の重み一致、投入/払出別解析一致。
- 設定1〜6完全攻略機械割: 98.500010 / 100.000012 / 101.999979 / 104.999971 / 108.999988 / 113.000021%。
- 成功率0/50/80/100%、24ケース各25,000完了ボーナスサイクル、通常抽選計114,942,108回の独立抽選。最大|z|2.8831、4標準誤差条件PASS。95%近似区間は22/24で解析値を含む。
- root test/packagePiriJars/runtime helper build PASS: https://github.com/Pasta0719/PiriJuggler/actions/runs/37125687542
- 実Paper/Fabric回帰202/202 PASS: https://github.com/Pasta0719/PiriJuggler/actions/runs/37125672609
- JUnit共通42/Paper287/Fabric44の373実行PASS。Paper opt-in1件skip、failure/error0。

Phase04は経済検証ツール・操作表・設定別証拠の追加。ゲーム制御・抽選値を変更していないため、Phase03実機シナリオをこのsourceで再実行。BIG23G、最終G成功+3、追加G抽選・指定外失敗、REG8G終了、Paper/Client再起動で途中停止復元、連続持越し再遊技無料と役秘匿を再確認。結果の元シナリオ名はSKILL_STOP_PHASE_03。

証拠artifact: `skill-stop-phase04-runtime-evidence` ID11274638209（上記run、2027-01-01まで）。元result、packet/log、画面4枚、JUnit XML、解析・独立集計JSON、実機に使用したPaper/Fabric JARを含む。CI解析/独立集計はローカル保存結果と一致。

配布JARは実機使用artifactから取得し、元resultのSHA256と照合:

|JAR|SHA256|
|---|---|
|Paper|`eedf5569d3a0848e955bbfc2aefd98a3e9f349a4ea63fa11bd26a63a3cb94b2a`|
|Fabric|`57720cb715f2413f14a2a362a9291940d2798c0de94601c179d737b1503839d1`|

攻略/投入払出/全役整数確率: skill-stop-phases/research/production/economy-analysis.json。
独立集計: skill-stop-phases/research/production/economy-simulation.json。
Phase05 PNG/成功音/残G画面表示は未着手。
