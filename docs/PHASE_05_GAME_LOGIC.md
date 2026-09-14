# Phase05 — 通常ゲームとSimulator

Paper内部の `Session.GameState` は仕様どおり保持します。通信時は `Session.publicGameState()` の網羅的switchで `PublicGameState` に変換し、入賞前のBIG/REGを区別しません。第3STOPで777または77BARが実際に表示され、種類が判明した後のBIG/REG状態は公開できます。第92章の承認済みmappingをspec-lockにも保存しています。

## 通常ゲーム

`NormalGame` はPaper main threadで次の変更を作り、`GameStore` が既存SQLiteの専用executorで保存します。

- Spaceで3BET。CREDIT不足分だけheldMedalsから移動し、なお不足なら拒否します。Vault貸出や物理メダル投入は自動実行しません。
- 次のSpaceでLEVER。現在の台設定を読み、1回だけ内部役を抽選して保存します。STOPで再抽選しません。
- STOPはPhase04の `ReelRound` / `StopSolver` とサーバー時刻・pingを使います。停止済みリールだけを固定します。
- 第3STOPで小役払出し、REPLAY_READY、BONUS_PENDING_BIG/REGへ遷移します。REPLAYの次ゲームはBET消費0、表示BETは3、PAYは0です。
- 払出しはCREDITの50枚までを優先し、残りをheldMedalsへ加えます。long overflowは残高へ反映しません。

各actionのsession、sequence、台の停止位置、G数・差枚・最大差枚、結果graph point、UUID取引受領記録を同じDB transactionで確定します。既存metadataに `GAME_TX:<UUID>` の受領記録を保持し、schema v4のtableは増やしません。保存失敗時は前の確定snapshotを維持します。停止計算は未確定の独立コピーに対して行い、DB rollback時にlive reelを進めません。

保存成功後にACTION_ACCEPTED / REEL_STOP / PUBLIC_STATE等を送ります。DB完了callbackまで追加操作はBUSYで拒否します。CLOSEだけは既存の規則に従って進行中actionの完了後に処理します。早すぎるSTOPや資金不足の操作も、同じsequenceで再実行できないよう記録します。

LEVERのSPIN_START送信直前にmotion開始時刻を設定します。離席時は3本の位相を保存し、猶予中の再開ではRESUME_NORMALを送ります。停止済みリールと内部役は保持します。

## 抽選とSimulator

`RoleWeights` は設定ごとの12役の整数累積weightを使います。重複役に別のボーナス抽選は行いません。`RandomStreams` はSecureRandomのmaster seedから台別・event・SimulatorのSplittableRandomを分離します。Simulator commandは追加のSecureRandom seedを使い、実ゲームのstreamを消費しません。seedをpacketやlogへ出しません。

OP用コマンド:

```text
/piri simulator <setting:1..6> <normal games:1..100000000>
```

専用workerで実行し、normalSpins、paidNormalSpins、replay、小役4種、BIG、REG、totalBet、totalPayout、payoutPercent、netを返します。通常BET、無料REPLAY、入賞BET1、BIGの20×2BET/280枚、REGの8×2BET/112枚を含めます。重複小役の払出しは1回だけ加算します。計算用のボーナス総額処理は実台の自動消化ではありません。

## 検証

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build packagePiriJars --console=plain
.\gradlew.bat -PruntimeAcceptance=true -PruntimeScenario=phase05-game -PruntimeEvidencePhase=PHASE_05 :runtime-test-client:build :runtime-test-paper:build --console=plain
python runtime-test-support/run_phase05.py
```

固定seed `0x504952494A554747` の6設定×1,000万ゲーム結果は `paper/build/reports/simulator-10m.json` に出力します。目標との差は各±0.20ポイント以内を必須とします。固定seed注入はテストだけです。

実機ランナーは専用localhost:25589のPaperと実Fabricを使います。通常ゲームを継続して検証するためテスト用configの設定1のみボーナスweightをMISSへ移し、通常ゲーム100回以上・REPLAY・全停止順を実キー/パケットで遊技します。通常の製品configは変更しません。最後にテスト用設定2のCHERRY_BIGを引き、Paper内部と公開状態の分離を確認します。

音源や資金供給機能が未完成でも通常ゲームを検証できるよう、配布対象外helperが空のテストsessionへ初期資金を設定します。それ以降のBET・LEVER・STOP・払出しはすべて本番handlerを通ります。helperは抽選・結果を代行しません。設定変更fixtureは台がSUSPENDED_SAFEで空いている時だけ使用します。

## 後続Phaseとの境界

Phase05は通常ゲーム、ボーナス当選後の権利保持、公開状態mapping、Simulatorまでです。実台のボーナス入賞ゲーム・BIG/REG手動消化・プレミア抽選と演出はPhase06で接続します。期限切れやプロセス再起動時の未完ゲームForce SettlementはPhase11の範囲で、未完権利のあるDBを無断で消したり解決済み扱いにしたりしません。

音声内容を生成しません。ユーザーが `user-audio/` にOGGを配置し、`build-jars.bat` で自由に再ビルドする方式を維持しています。音源未配置でもtest/build/実機受入が成功します。


## Phase05で承認された回転方向変更

通常はindex減少・画面下向き。プレミア逆回転の最初の500msはindex増加・画面上向き、その後300msで通常方向の速度18へ加速する。RESUMEと停止アニメーションも通常方向。配列・行定義は維持し、slipは `(pressedIndex-targetStopIndex+21) mod 21`。SPEC第5/25/33/34/90/132章と共有ReelMotionを同期した。

同一JARの全78回転・234 STOP・3 motion profileの回帰証跡は `runtime-evidence/PHASE_05_REEL_REGRESSION/`。通常/逆回転/復帰の実クライアント位相を時系列観測する。これは既存リール部品の確認であり、Phase06のプレミア抽選やボーナス消化は未着手。


## BARボーナス確定目

BAR-BAR-BARはBIG/REG共通の当選告知出目です。単独BIG/REGは、ハズレ形5,242件と厳密BAR形8件の共通BONUS候補を使用します。払出し0枚、Piri Chance点灯、内部BONUS_PENDING_BIG/REGを保持し、PUBLIC_STATEはBONUS_PENDINGだけを送ります。BAR揃いでは種類を公開せず、BONUS_STARTを発火しません。

MISSでは5本の有効ラインすべてでBAR揃いを禁止します。小役重複は従来の2枚/14枚を保持し、BARの0枚出目へ置き換えません。REGの入賞形SEVEN-SEVEN-BARも維持します。BAR形は成立ラインのrank、ハズレ形はrank5を使い、滑り・左中右indexとの比較順と抽選weightは変更しません。

全9,261 tripletの独立判定と全722,358 STOP系列、BIG/REG×8出目×6停止順の96ゲームDB試験に加え、実Minecraftでも各種別を100%にした配布対象外configを使います。helperは表示位相に合わせて実キーを押すだけで、STOPの位相・結果をPaperへ送信したり、抽選・払出しを代行したりしません。

```powershell
$env:PIRI_BAR_BONUS = "BIG"
python runtime-test-support/run_phase05_bar.py
$env:PIRI_BAR_BONUS = "REG"
python runtime-test-support/run_phase05_bar.py
```

証跡: `runtime-evidence/PHASE_05_BAR_BIG/`、`runtime-evidence/PHASE_05_BAR_REG/`。通常100ゲームの実機試験でも、全5ラインのBAR不成立を確認します。
