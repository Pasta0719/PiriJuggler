# Successor machine phased development

Status: **ACTIVE WORKFLOW — DESIGN NOT YET LOCKED**

このフローは、2026-09-23に打ち切った Piri GOD の代わりに作る新しい方式専用。

## 絶対ルール

1. GOD完成を前提にしない。
2. GOD Phase 02 runtime acceptanceを通すことを開始条件にしない。
3. GODの仕様・確率・状態遷移・20コマ制御・G-ZONE/GG/SGG等を自動継承しない。
4. 既存のPaper/Fabric基盤、台登録、通信、画面、リール描画、音声読み込み等の汎用部品は再利用候補にしてよいが、Phase 01で採否を明記する。
5. 新しいゲーム仕様は Phase 01 で固定する。未決定事項をコードから逆算して「仕様」にしない。
6. 一つのPhaseだけ実行し、完了後に次Phaseへ自動で進まない。
7. 実装Phaseは test/build/runtime evidence が揃うまで COMPLETE にしない。

## Phase order

- NEXT Phase 01: Successor Concept / Game Loop / Presentation Lock
- NEXT Phase 02: Core Gameplay / Reel / State Implementation
- NEXT Phase 03: Economy / Probability / Long-run Simulation
- NEXT Phase 04: Presentation / LCD / Audio / Replaceable Assets
- NEXT Phase 05: Recovery / Runtime Acceptance / Final Audit

現在は **NEXT Phase 01 NOT_STARTED**。

新方式の中身がまだ明示されていないため、Phase 01では「何を作るか」を確定するところまで。GODの代わりだからという理由だけでAT機、パチンコ、ジャグラー派生などを勝手に選択してはならない。
