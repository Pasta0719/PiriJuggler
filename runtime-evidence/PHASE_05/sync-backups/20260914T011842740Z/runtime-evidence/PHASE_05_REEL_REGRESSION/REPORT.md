# Phase05 回転方向変更後の実Minecraftリール回帰 — PASS

UTC: 2026-09-13T12:20:00.599463+00:00 ～ 2026-09-13T12:24:43.803935+00:00

95 assertions PASS、72回転/216 STOP、通常9役とF3役×全6停止順。全server/client exit0、未処理例外0。

本番StopSolver/ReelRoundへテスト専用controllerが役とprofileを渡し、実クライアントでキー操作した。全3 profileの表示位相を毎tick観測し、通常/RESUMEは負方向、REVERSE_500MSは最初正方向で0.8秒以降負方向と確認。固定リール配列は維持した。

Phase05の本番JARと同一SHA。result.jsonの各assertionに生の位相trace、STOP packet、client最終停止位置を記録。12 PNGを保存。Phase06のプレミア抽選・実台ボーナス消化は未実装。

再現: PIRI_PHASE04_EVIDENCE=PHASE_05_REEL_REGRESSIONを設定し、python runtime-test-support/run_phase04.pyを実行する。
