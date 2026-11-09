# HYPER_DOG Phase07 — 実Minecraft管理・データランプ・遠隔・音・復帰受入証跡

**Status: COMPLETE（2026-11-09）**

## 確認コード・合格したCI

実装・検証コード基準：`7a47d25ee37c546360630d252aa9587f27a68b1f`。このコミットではPhase07の2つの実ランタイムジョブがともに **SUCCESS** となり、全製品ビルドとv7基準ガードも **SUCCESS**。

- [Phase07 — 実Paper 2独立JVM再起動、実Fabric 2クライアント＋スマホHTTP：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868687)
- [実行証跡 — Paper再起動・SQLite・台帳](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868687/artifacts/13658801181)
- [実行証跡 — Fabric 2名・画面PNG・認証スマホHTTP](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868687/artifacts/13658443820)
- [製品ビルド・単体/統合試験：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584875169)
- [v7数値正本ガード：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584875163)
- [Phase02：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868764) / [Phase03：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868713) / [Phase04：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868675) / [Phase06：SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42584868583)
- [Phase05：先行実Minecraft受入SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42583237775)。最新版への差分はPhase07試験環境・実受入の修正が中心で、従来筐体の製品コードは維持。

## 完了条件に対する実証

1. **管理・履歴・上段3カウンター**：設定1〜6、設定履歴、手動/自動設定モード、日次リセットをPaperテストで検証。専用台帳はAT初当たり・上位CZ挑戦・上位AT新規/継続の3種をそれぞれ独立記録。実Paper 350G・差枚+1200のfixtureを利用し、観覧Fabric 2名と認証モバイルHTTPの`4/3/5`カウンターが一致。HYPER_DOGのBIG/REGを捏造せず、既存JUGGLERのBIG=1/REG=2を保持。
2. **遠隔同期と安全性**：実際の2つのMinecraftクライアントが同じワールドの2台を観覧し、プレイヤー/観覧の受信表示が一致。実サーバーの一時イベントから観覧側へ`lever`音の転送を確認。公開パケットとスマホに設定・RNGなどの内部情報は出さない。スマホは未認証アクセスを拒否、6桁ペアリングに成功し、未公開HYPER_DOGへの着席操作は拒否。
3. **保存/中断/二重精算**：実Paper 1.21の別JVM 2回起動で、BAR成立の2停止から第三停止を復元。15枚払い出しは一度だけ、累積収支は有料1G・差枚+12の台帳と一致。所有者ロック、復元RNG/停止履歴、上位初期5G最低500枚、AT初当たり/上位計上を検証。CZ中停止前および初期5G TOTAL表示直前の復帰と重複上乗せ排除は`HyperDogPhase07BoundaryTest`を含む回帰試験でPASS。
4. **周辺環境と既存保全**：既存機種とは独立して世界レンダラ・音ループ・スマホAPIを扱い、実Fabricのフレーム/メモリ統計とPNG証跡を取得。既存JUGGLER表示および旧Phase02〜06回帰がPASS。メインホールのSQLiteを使わず試験用データベースで実行。

## 保護条件

- 正本は`original-at-phases/reference/pirijuggler_new_machine_calibrated_v7.json`。Phase07では確率、配当、上乗せ、出玉目標を再調整しない。
- 新機種は**Phase08総合受入までは未公開**。未公開機種の生成/着席制限、既存機種・メダル・所持資産保全、PR Draft・未マージを継続。
- スクリーンショットの見栄えは後日の実プレイによる演出調整を妨げない。試験系の表示修正は別途実行記録として追跡する。
