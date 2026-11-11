# HYPER_DOG Phase08 — 技術検証・本番公開判断（2026-11-09）

**状態：TECHNICAL_GATES_PASSED / USER_PRESENTATION_REVIEW_PENDING（Phase08はIN_PROGRESS）**

## 判定と範囲

既存の正本 `reference/pirijuggler_new_machine_calibrated_v7.json` を変更せず、最終数値検証、Paper実装のv7整合、全モジュールビルド、Paper/Fabric旧機種回帰、実Paper本番GameEngineの30回スピンをGitHub Actionsで再検査し、**対象の実行結果はすべてSUCCESS**。

ただし、ユーザーが自分で遊んで演出・音・テンポを最終確認する条件が未受理なので、本PhaseをCOMPLETEとしない。 `main` へのマージ、Draft PR解除、一般公開・常時有効化は実施しない。 `piri.hyperdog.phase08Preview` はテスト専用の明示的なJVMオプトインで、標準値は無効。既存台の `machine type` 変更によるHYPER_DOG選択もブロックする。

## 最新同一コミットの受入証跡

**受入対象 Git SHA：`8e33b8fbd4823c828d37896a256cff7cb89a71af`**。

| 項目 | GitHub Actions | 判定 |
|---|---|---|
| 実Paper 1.21本番GameEngine/SQLite/30ゲーム・停止・残高保存 | [Phase08 native](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602789015) ／ [ログ・DB証跡](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602789015/artifacts/13666308465) | SUCCESS |
| v7数値正本ガード | [v7 guard](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602797080) | SUCCESS |
| 全モジュール・旧機種を含むビルドとJUnit | [Piri build](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602797017) | SUCCESS |
| Phase02・リール実Paper/Fabric | [Phase02](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602789242) | SUCCESS |
| Phase03・通常時実Paper/Fabric | [Phase03](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602788992) | SUCCESS |
| Phase04・AT/CZ実Paper/Fabric | [Phase04](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602788682) | SUCCESS |
| Phase05・専用筐体実Paper/Fabric | [Phase05](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602788685) | SUCCESS |
| Phase06・実際の演出と画像受入 | [Phase06](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602788900) | SUCCESS |
| Phase07・管理/グラフ/remote/mobile、再起動回帰 | [Phase07](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602789408) ／ [2 JVM証跡](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602789408/artifacts/13666094724) ／ [Fabric/mobile証跡](https://github.com/Pasta0719/PiriJuggler/actions/runs/42602789408/artifacts/13665963894) | SUCCESS |

## 数値独立ホールドアウト

Phase08用の独立数値評価は、後続実装変更前のv7正本で[全ジョブSUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42591005571)。以後もSHA固定のv7整合ガードとPaper実装JUnitを最新コミットで再確認している。

- 設定1〜6について各設定 **2 × 120,000,000 有料ゲーム**の独立シードを使い、採用基準を事前固定した機械割目標からの絶対差1.25ポイント以内で全設定PASS。[再現出力](https://github.com/Pasta0719/PiriJuggler/actions/runs/42591005571/artifacts/13661223413)。
- 上位ATの平均純獲得は、各設定で目標約4,000枚に対し3,600〜4,400枚の受入区間でPASS。[再現出力](https://github.com/Pasta0719/PiriJuggler/actions/runs/42591005571/artifacts/13660769362)。
- Paperのv7 SHA照合、役別3区間払出、設定別モード単発当選、連続役、役抽選分布、上位CZ、上限2,000枚、リール端のindex wrapを独立JUnitで検証。[テスト証跡](https://github.com/Pasta0719/PiriJuggler/actions/runs/42591005571/artifacts/13661471098)。

## Phase08追加実装・判明した不具合

1. `HyperDogGameEngine` を実本番用Paper `GameEngines` に追加。遊技開始/入力/権威ある停止/確定結果/復帰をPaper側だけで計算し、Fabricに内部抽選は渡さない。
2. HYPER_DOG用の `GameStore -> HyperDogStateStore` 書込み経路を追加。従来BIG/REG計数の混入を避け、同一DBトランザクションにメダル残高・リール状態・イベントジャーナルを保持。既存4機種用のGameStore分岐は温存。
3. プレイヤーのメダル貸出・投入・精算後のウォレットと遊技エンジンの調停、および初回BETの記録を補修。
4. 実Paper 30ゲーム試験で旧テストが「物理30スピン=有料30G」と誤判定。リプレイの無料スピンはBETゼロであるため、**有料ゲーム数と物理停止回数を分離**してアサートを修正し、再実行でSUCCESS。
5. `machine create HYPER_DOG` は `-Dpiri.hyperdog.phase08Preview=true` をテスト用に指定したときだけ許可し、通常運用では `MACHINE_NOT_RELEASED` 維持。公開用の認可フローは最終レビューまで解放しない。

## 未受入の唯一の人手チェック

演出/音/レバー感覚/停止テンポ/ユーザー好みの表示について、ユーザーが自分でゲームを遊び、承認または修正点を提示すること。自動CIは表現への個人的な好みを代替できない。公開解除・Draft PR変更は明示指示の後に別途行う。

**結論：技術受入PASS。Phase08 COMPLETEおよび一般公開はユーザーレビュー待ち。**
