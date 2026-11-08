# HYPER_DOG — Phase 02 v2 最終実機受入（2026-11-08）

**STATUS: COMPLETE / Phase 03: NOT_STARTED**

## 変更の承認・検証対象

2026-11-08のユーザー承認により、v1配列を以下のv2規則へ変更した。現行配列の正本は [REEL_DESIGN_V2.md](REEL_DESIGN_V2.md) / [REEL_STRIPS_V2.json](REEL_STRIPS_V2.json)。旧v1の成功証跡 [PHASE_02_RUNTIME_ACCEPTANCE.md](PHASE_02_RUNTIME_ACCEPTANCE.md) は履歴として保持。

- 特殊図柄 RED7 / BAR / RAINBOW7 は各リール1つずつで、0・7・14番へ7コマ間隔で配置。
- 未成立の特殊図柄3連は、異なる図柄の混合を含め5ライン全てで禁止。正規のBAR・AT赤7・CZ虹7・CHANCE・STRONG_CHANCEの1ラインのみ例外。
- 左リールのチェリーは、BAR当選時以外中段停止禁止。弱/強チェリー成立時は上段・下段、弱＝左中の横2連、強＝5ライン上の3連。
- BAR当選時のみ左チェリー中段停止許可。BARは全5ラインで揃い、(6,6,6)では左チェリー中段＋下段BAR揃い。
- MISS/REPLAY/GRAPEは4コマ以内で全6押し順・全21³押下履歴を完走。レア・確定役は最大20コマ。

## 自動検証・実機証跡

- [GitHub Actions HYPER_DOG Phase 02 実機テスト #42464236837](https://github.com/Pasta0719/PiriJuggler/actions/runs/42464236837) — **SUCCESS**。コミット `968d4083abce77762591532d2171dab64b6f44b9`。
- [証跡ZIP：GitHub Actions Artifact #13610992343](https://github.com/Pasta0719/PiriJuggler/actions/runs/42464236837/artifacts/13610992343)。Run ID：`20261108T084626Z`（UTC）。
- [独立Javaビルド #42463961866](https://github.com/Pasta0719/PiriJuggler/actions/runs/42463961866) — **SUCCESS**（改訂v2のソース/仕様書）。
- Python `original-at-phases/validate_reels.py` は本番Java停止制御に依存せず v2 JSON から全9,261停止形、通常役4コマの全押し順到達性を検証 — **PASS**。
- Java `HyperDogReelAcceptance` は12プロファイル × 6押し順 × 21³押下位置＝**666,792**通りを網羅テスト — **PASS**。
- プロジェクトJUnit XML合算：**540 tests / 0 failures / 0 errors / 1 skipped**。
- 実Paper 1.21 + Fabric 1.21：**57ラウンド / 171停止**（手動143、自動28） — **PASS**。
- 実ネットワークでPaper停止座標とFabric表示位置の**57/57完全一致**、7つの256×256 RGBA図柄の実リソース読込、3×3描画、途中保存→復帰、スクリーンショット証跡 — **PASS**。
- 実機表示の全12役が検査対象。**18/18 assertions PASS**、拒否された合法停止0、クライアント側プローブエラー0。
- 追加の表示検証：**5ラインで特殊図柄が3つ並ぶ場合、成立した正規演出1ラインのみ**、**BAR以外で左チェリー中段0件**、**弱/強チェリーの左上/下段停止** — 全て**PASS**。

## 新設計の停止候補数と偽リーチ目

21×21×21＝**9,261**の理論停止三つ組から、全5ラインの成立・非成立を厳密分類。特殊3図柄だけが1ラインに現れる位置組合せは135通り、そのうち**混合の120通り**は原則拒否し、正式なCHANCE・STRONG_CHANCEの中段停止形2通りだけ許可。他の混合**118通り**を禁止。単独同図柄3連の非成立時表示も禁止。`legalFinal`と`choose`が成立役別に最終停止形を制限し、手詰まりを防ぐ。

## 重要な境界

本ランは実Minecraft Paper/Fabricを用いた**Phase 02専用テストフィクスチャ**であり、製品のホールにHYPER_DOGを登録・経済抽選処理を実装した証明ではない。機種登録・モード抽選はPhase 03、AT/CZ経済はPhase 04、完成筐体はPhase 05で実装する。v5抽選確率・機械割基準、他機種の停止制御・設定履歴・資産は変更しない。

## 再現

GitHub workflow `.github/workflows/hyperdog-phase02-runtime.yml` をプッシュで実行。独立配列検証、全Gradle回帰、実Paper/Fabric起動、証跡アップロードを行う。実機ランナーは `runtime-test-support/run_hyperdog_phase02.py`。証跡は `runtime-evidence/HYPER_DOG_PHASE_02/` のActions Artifactに保存。
