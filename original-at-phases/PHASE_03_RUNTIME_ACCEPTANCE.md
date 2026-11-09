# HYPER_DOG Phase03 — 通常ゲーム・6モード実機受入

**2026-11-09 JST / COMPLETE**

受入対象コード：[`0c8820b`](https://github.com/Pasta0719/PiriJuggler/commit/0c8820b17dc6af1a5b39fe4baa085aecc7cb031c)（Phase03）。

## 必須受入条件

1. **設定1〜6、6モード、3BET／REPLAY後0BET、3〜5連続役・強役・BARと単独/重複AT判定**：`HyperDogNormalGameTest` と本番v5 JSONのSHA256照合がPASS。
2. **確率・モード遷移の独立モデル逐次照合**：Java全6設定×250G＝1,500GをPython `verify_phase03_trace.py` と比較し `HYPER_DOG_PHASE03_INDEPENDENT_PYTHON_V5_MODEL_PARITY=PASS`。
3. **保存→再起動→復帰**：BET済み、停止待ち、REPLAY_READY、AT_PENDINGの4チェックポイントをJSON保存し、独立した新しいJavaプロセスで読み直して25Gの続きを実行、RNG・モード・累積クレジット・進行状態の一致をJUnitで確認。実Paper/Fabricのネットワークセッション中のエンジン/停止制御復元もPASS。
4. **実Paper/Fabric・既存回帰**：Paper 1.21 build 130＋Fabric 1.21の100ゲーム・300停止、7図柄実ロードと描画、100スピンID/停止結果一致、BET/LEVERとリプレイ0BET、公開情報の秘匿、クレジット収支一致、実スクリーンショット取得が全てPASS。現行Phase02 v2の57ラウンド再受入・全体Gradleビルドと回帰もSUCCESS。

## 今回追加した資産保護

`HyperDogNormalGame.bet()` と `finish()` の64bit演算は、一式の算術チェック後にのみクレジット/投入/払出台帳/ゲーム数を更新。オーバーフロー3条件の例外時に途中精算されないことを回帰テストで検証。

## GitHub Actions 証跡

- [Phase03 実Paper/Fabric 100ゲーム — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42539067947) / [証跡 Artifact #13640871574](https://github.com/Pasta0719/PiriJuggler/actions/runs/42539067947/artifacts/13640871574)：ログ、JSON、サーバー/クライアント結果、スクリーンショット、JUnit。
- [Phase02 v2 リール57ラウンド再検証 — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42539067979)。
- [Piri build、全体テスト — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42539072288)。

## 完了判定の境界

- 本Phaseは**独立した通常時抽選エンジン**と実Minecraftフィクスチャの受入。`HYPER_DOG`を製品の公開機種として選べることを意味しない。新機種作成・切替は `MACHINE_NOT_RELEASED` で拒否したまま。
- 下位/上位AT、CZ、差枚精算、BAR、32G引戻し、製品DB/セッション復帰統合はPhase04以降。新筐体と演出、遠隔はPhase05〜07、総合完成はPhase08。
- `one_phase_per_run=true` に従いPhase04は **NOT_STARTED** のまま。既存機種、保存済みデータ、完成済み仕様を変えず、Draft PRは未マージ。
