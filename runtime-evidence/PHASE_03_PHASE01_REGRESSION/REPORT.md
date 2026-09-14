# Phase01 regression on Phase03 artifacts — PASS

2026-09-13T01:35:07.184166+00:00 ～ 2026-09-13T01:37:16.658669+00:00（UTC）。実Paper1.21-130 / Fabric1.21 / Loader0.16.14 / API0.102.0+1.21。

- normal: PASS。server/client exit0、未処理例外0。protocol1のACK正常成立、mismatch時はERROR(PROTOCOL_MISMATCH)とgameplay拒否を確認。
- mismatch: PASS。server/client exit0、未処理例外0。protocol1のACK正常成立、mismatch時はERROR(PROTOCOL_MISMATCH)とgameplay拒否を確認。

詳細はresult.json、server.log、client.log、screenshots/。Phase03 REPORTの本番JAR hashと一致。
