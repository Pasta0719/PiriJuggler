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
| bonus_start.ogg | piri:bonus_start | BIG開始時のみ再生 |
| bonus_end.ogg | piri:bonus_end | BIG終了時のみ再生 |
| big_bgm.ogg | piri:big_bgm | BIG消化中BGM（ループ） |
| reg_bgm.ogg | piri:reg_bgm | REG消化中BGM（ループ） |
| god_freeze.ogg | piri:god_freeze | GOD成立ゲームのレバー時フリーズ演出 |

`big_bgm` / `reg_bgm` はPhase06でユーザー指定により追加したBGM SoundEventです。

追加仕様:
- REGでは `bonus_start.ogg` / `bonus_end.ogg` を再生しません。
- BIGでは `bonus_start.ogg` を再生したあと、`big_bgm.ogg` の初回開始だけ4.5秒待機します。
- 4.5秒待機はBIG BGMの初回開始にのみ適用し、BGM自体のループ間隔には入りません。
- REG BGMはREG開始時に待機なしで開始します。
- 途中でスロット画面を再度開いた場合は、進行中のBIG/REG BGMをその場から通常のループ再生として開始します。
- BIG/REG終了・session reset時にBGMを停止します。

未配置の場合は無音で処理を続行します。build/test/runtimeに実音源は必須ではありません。


Juggler + GOD後続仕様では `god_freeze.ogg` をGOD成立ゲーム専用として使用します。未配置でもGOD成立・BAR揃い・進行は変わらず、無音で演出を継続します。
