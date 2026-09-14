# 音源を置くフォルダ

使いたい音だけ、このREADMEと同じ場所へ置いてください。音源はすべてユーザーが用意します。空のままでもビルドできます。

| 名前 | 用途 |
|---|---|
| notice.ogg | 通常告知 |
| notice_strong.ogg | 強告知 |
| tenpai.ogg | テンパイ |
| bet.ogg | BET受付 |
| lever.ogg | LEVER受付 |
| stop.ogg | リール停止 |
| payout.ogg | 払出 |
| error.ogg | エラー |
| bonus_start.ogg | ボーナス開始 |
| bonus_end.ogg | ボーナス終了 |
| big_bgm.ogg | BIG消化中BGM（ループ） |
| reg_bgm.ogg | REG消化中BGM（ループ） |

ファイル形式はMinecraftが再生できるOGGです。MP3等の拡張子だけを変えてもOGGにはなりません。音の内容・長さは自由です。

1. 用意したOGGを上記の名前で配置します。12個すべてを揃える必要はありません。
2. リポジトリ直下の `build-jars.bat` をダブルクリックします。
3. 成功すると `dist` フォルダへPaper用とFabric用のJARが出ます。
4. 音源を変えたら同じ手順で再ビルドします。Javaコードの編集は不要です。

このフォルダの指定名OGGはFabric JAR内の `assets/piri/sounds/` に入ります。同名の `fabric/src/main/resources/assets/piri/sounds/` よりこのフォルダを優先します。未配置の音は無音として扱います。削除して再ビルドした音は新しいJARに残りません。

`big_bgm.ogg` / `reg_bgm.ogg` はPhase06で追加したユーザー指定のBGMです。BIG/REG中にクライアント側でループし、ボーナス終了または画面/session resetで停止します。

準備・導入先・コマンドでのビルドは `../BUILD_JARS.md` を参照してください。
