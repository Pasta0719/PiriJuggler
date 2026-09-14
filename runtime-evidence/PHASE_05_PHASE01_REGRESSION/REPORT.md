# Phase05 JARによるPhase01実Minecraft回帰 — PASS

UTC: 2026-09-14T01:12:11.249959+00:00 ～ 2026-09-14T01:14:14.351369+00:00

Phase05と同一SHAのJARで、実Paper/Fabricの正常HELLO/ACKとprotocol不一致時の利用拒否を確認した。全server/client exit0。詳細はresult.json、normal/、mismatch/を参照。

再現: PIRI_PHASE01_EVIDENCE=PHASE_05_PHASE01_REGRESSIONを設定し、python runtime-test-support/run_phase01.pyを実行する。
