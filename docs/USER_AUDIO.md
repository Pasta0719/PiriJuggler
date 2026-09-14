# ユーザー提供音源

実音源はユーザーが別途提供します。実装には音声生成・代替音源を含めません。

推奨配置先はリポジトリ直下の `user-audio/` です。次のOGGを置いて `build-jars.bat` を実行すると、`dist/` にJARができます。`fabric/src/main/resources/assets/piri/sounds/` への直接配置も可能ですが、同名なら `user-audio/` を優先します。同じ `assets/piri/sounds/` のresource packによる配置とresource reloadにも対応します。

| ファイル名 | SoundEvent ID | 用途 |
|---|---|---|
| notice.ogg | piri:notice | 通常告知 |
| notice_strong.ogg | piri:notice_strong | 強告知 |
| tenpai.ogg | piri:tenpai | テンパイ |
| bet.ogg | piri:bet | BET受付 |
| lever.ogg | piri:lever | LEVER受付 |
| stop.ogg | piri:stop | 停止 |
| payout.ogg | piri:payout | 払出 |
| error.ogg | piri:error | エラー |
| bonus_start.ogg | piri:bonus_start | ボーナス開始 |
| bonus_end.ogg | piri:bonus_end | ボーナス終了 |
| big_bgm.ogg | piri:big_bgm | BIG消化中BGM（ループ） |
| reg_bgm.ogg | piri:reg_bgm | REG消化中BGM（ループ） |

`big_bgm` / `reg_bgm` はPhase06でユーザー指定により追加したBGM SoundEventです。BIG/REG開始後にクライアント側でループし、終了・session reset時に停止します。

未配置の場合は無音で処理を続行します。build/test/runtimeに実音源は必須ではありません。
