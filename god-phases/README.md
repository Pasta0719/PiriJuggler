# Piri GOD phased development — RETIRED

Status: **RETIRED_BY_USER — 2026-09-23**

このディレクトリは、過去に Piri GOD を実装するために使用した専用Phaseと調査資料を保存するためのものです。

## 現在の扱い

- GOD Phase 01 は完了済みの仕様研究・資料として保持する。
- GOD Phase 02 は **未完了のまま打ち切り**。runtime acceptance を通すための修正を続行しない。
- GOD Phase 03–06 は **実行しない**。
- `run-god-phase02-runtime.bat` と `runtime-test-support/run_god_phase02.py` は履歴・調査用に残すが、後続開発のゲートにしない。
- GODの既存コード、テスト、仕様、画像・UI部品は削除しない。新方式で再利用価値がある場合のみ、後続Phaseで明示的に選んで使う。
- GOD Phase 02 が FAIL / PENDING / INCOMPLETE であっても、後続方式の開始をBLOCKしてはならない。
- 後続方式へGOD固有仕様を自動移植しない。

## 後続開発

GODの代わりに作る方式は、`../NEXT_MACHINE_PHASE_MAP.json` と `../next-machine-phases/README.md` を入口にする。

次の方式について未確定のゲーム仕様を、過去のGOD仕様や既存コードから勝手に補完しない。まず NEXT MACHINE Phase 01 で仕様を固定し、その後に実装する。

## GOD資料の位置づけ

Canonical GOD research/spec documents:

- `docs/GOD_MASTER_SPEC.md`
- `docs/GOD_ROLE_CONTRACT_V1.md`
- `docs/GOD_STATE_MACHINE_SPEC_V1.md`
- `god-phases/GOD_PHASE_01_SPEC_LOCK.md`

これらは **後続方式の正本ではない**。比較・再利用判断の資料としてのみ使用する。
