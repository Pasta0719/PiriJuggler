# Piri GOD phased development

このディレクトリは Piri GOD 専用の開発Phaseです。
既存の全体 `PHASE_01..14` とは別管理とし、番号衝突を避けるため `GOD_PHASE_XX` を使用します。

## 最重要ルール

1. **仕様を先に確定する。**
2. 仕様が `LOCKED` になるまで production gameplay code を変更しない。
3. 実装は locked spec を唯一の要求元として行う。
4. コードの既存挙動を仕様の根拠にしない。
5. 公開情報が無い箇所は「実機仕様」と断定せず、`UNKNOWN` または `PIRI_SPECIFIC` とする。
6. 実装と仕様が食い違った場合、仕様を勝手にコードへ合わせない。まず差分を記録する。
7. 各Phaseは tests/build/runtime evidence が揃うまで COMPLETE にしない。
8. 一つのPhase完了後、次Phaseへ自動で進めない。

## Canonical spec

GOD仕様の入口は:

- `docs/GOD_MASTER_SPEC.md`
- `god-phases/GOD_PHASE_01_SPEC_LOCK.md`

既存の `docs/GOD_*.md` は研究ノート・根拠資料として残すが、矛盾時は `GOD_MASTER_SPEC.md` の明示的な分類を優先する。

## Phase order

- GOD Phase 01: Specification Consolidation / Lock
- GOD Phase 02: Role / Reel / Payout implementation
- GOD Phase 03: Gameplay state machine / benefits
- GOD Phase 04: Economy fitting / long-run simulation
- GOD Phase 05: Client presentation / replaceable assets
- GOD Phase 06: Recovery / runtime acceptance / final audit
