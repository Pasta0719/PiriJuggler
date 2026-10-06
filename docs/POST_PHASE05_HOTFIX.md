# Post-Phase05 approved hotfix — 2026-09-14

この文書は、Phase05完了後のユーザー実機試遊で判明・承認された修正を記録する。Phase06は開始しない。

## 1. STOP時の逆走・ギュン対策

通常回転の論理方向は既存どおり **index減少方向（画面下向き）** を維持する。Paperの論理slipも既存どおり次式を維持する。

```text
slip = (pressedIndex - targetStopIndex + 21) mod 21
```

Fabricの停止表示は、STOP packet受信時点の現在visual phaseから **index減少方向だけ** に進み、反対方向へのshortcutを禁止する。

packet到着が遅れてクライアント表示がserver計算時点より先へ進んでいた場合、同じtargetStopIndexの次の周回位置を選ぶ。このときserver指定durationMsをそのまま使うと約1周を短時間で補間して「ギュン」「逆走のような見え方」になるため、停止表示速度が通常回転速度28 symbols/secを超えない最小durationまでFabric側だけ延長する。

- 最終stopIndexはPaper指定を厳守する。
- Paperの成立役、slip、stop solver、payoutは変更しない。
- Fabricは結果を決めない。
- 方向は常に通常回転と同じindex減少方向。
- wrap時も逆方向へ補間しない。

実装箇所:
- `common/.../ReelMotion.normalStopEndpoint`
- `common/.../ReelMotion.visualDurationMs`
- `fabric/.../SlotViewState` の `REEL_STOP`

## 2. 正式リーチ目

通常ゲームでBONUS非成立なのに、見た目上BONUS確定に見える停止形を出さない。

5本の有効LINE上で、次の8形を正式リーチ目とする。

```text
SEVEN-BAR-SEVEN
SEVEN-BAR-BAR
BAR-SEVEN-SEVEN
BAR-SEVEN-BAR
BAR-BAR-SEVEN
BAR-BAR-BAR
PIERO-SEVEN-PIERO
PIERO-BAR-PIERO
```

`SEVEN-SEVEN-SEVEN` はBIG入賞形、`SEVEN-SEVEN-BAR` はREG入賞形であり、通常ゲームの正式リーチ目には含めない。

左リール中段CHERRYは、既存どおり `CHERRY_BIG` のPremium B専用特殊形とし、汎用リーチ目には含めない。

制御:
- BONUS非成立の `MISS / REPLAY / GRAPE / CHERRY / BELL / PIERO` 候補では、上記正式リーチ目を含む最終停止形を禁止する。
- `BIG_ENTRY / REG_ENTRY / PREMIUM_B` でも汎用リーチ目を混在させない。
- 単独BIG/REGのBONUS表示候補では正式リーチ目を許可するが、毎回強制はしない。
- `CHERRY_BIG / CHERRY_REG / PIERO_BIG / PIERO_REG` は既存小役入賞を優先し、リーチ目を強制して小役払い出しを壊さない。
- リーチ目そのものに追加払い出しはない。
- リーチ目からBIG/REG種類を公開しない。
- 先ペカ25% / 後ペカ75%およびpremium告知タイミングは変更しない。リーチ目完成自体を即時点灯トリガーにしない。
- GRAPE / BELL / PIERO / REPLAY等の単なる2リールテンパイハズレは今回の正式リーチ目に含めない。

9261停止形の事前計算で `winningReachLines` を保持し、全5LINEを判定する。

固定リール上の列挙結果:
- 正式リーチ目を1本以上含むraw triplet: 160
- strict BONUS候補として利用可能なリーチ目triplet: 124
- BONUS候補総数: 5250
- MISS候補: 5126
- CHERRY候補: 1502
- PREMIUM_B候補: 758

## 検証状態

このhotfixはGitHub上のsourceとunit-test/self-testへ反映した時点では、ユーザーPCでの `gradlew test` / `build` / 実Minecraft runtime acceptanceをまだ再実行していない。

したがって、Phase05までの過去の144 tests PASSやruntime evidenceを、このhotfix後buildのPASS根拠として流用しない。ローカルへpull後に再検証し、PASS後だけ新しい証跡として記録する。


## 2026-10-03 — 停止入力位置の送信修正と回帰結果
既存SlotUiはEnvelope.payload()のコピーにpressedIndexを追記しており、送信に残らなかった。
SlotInput.withPressedIndexで新しいEnvelopeを生成し、押した位置を実送信する。プロトコル変更なし。
実EnvelopeCodecの送受信テスト4/4と全root test/package/runtime-helper CI PASS（https://github.com/Pasta0719/PiriJuggler/actions/runs/37120918659）。
実SKILL_STOPクライアントでも12指定入力すべてが送信pressedIndexと一致、実停止15件の4コマ上限を確認（https://github.com/Pasta0719/PiriJuggler/actions/runs/37120916132）。
証拠: runtime-evidence/SKILL_STOP_PHASE_02/REPORT.md。他の既存機種の全実機シナリオを再実施したとの意味ではない。


## 2026-10-03 — SKILL STOP Phase03保存の機種限定対応
SKILL_STOP未完了状態だけはgrace/idle/起動時の自動清算を避け、固定済みチャレンジ・残りG・当選権利・停止履歴・無料再遊技と台ロックを保持する。
回復清算も未完了SKILL_STOPにはRECOVERY_REQUIREDを返す。他機種の清算/設定/役確率/停止制御は変更しない。
既存GameStoreの原子トランザクションとreceiptで14枚/追加3Gを一度だけ確定。
全root test/package/runtime helper CI PASS（https://github.com/Pasta0719/PiriJuggler/actions/runs/37123297201）、実Paper/Fabric202/202 PASS（https://github.com/Pasta0719/PiriJuggler/actions/runs/37123294908）。
証拠: runtime-evidence/SKILL_STOP_PHASE_03/REPORT.md。他機種の全実機シナリオ再実施を意味しない。
