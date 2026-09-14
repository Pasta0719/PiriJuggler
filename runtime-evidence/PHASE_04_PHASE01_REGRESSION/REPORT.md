# Phase04 JARによるPhase01実Minecraft回帰 — PASS

実行日時（UTC）: 2026-09-13T06:32:52.544455+00:00 ～ 2026-09-13T06:35:10.412542+00:00

Phase04と同一SHAのcommon / Paper / Fabric JARを使用。隔離した実Paper/Fabricで、正常protocol1のHELLO/ACKとprotocol不一致による利用拒否を確認した。詳細な判定、終了コード、画面とログは同フォルダresult.jsonおよびnormal/mismatchを参照。

再現: 環境変数 `PIRI_PHASE01_EVIDENCE=PHASE_04_PHASE01_REGRESSION` を設定して `python runtime-test-support/run_phase01.py`。過去Phase01の証跡を上書きしない。
