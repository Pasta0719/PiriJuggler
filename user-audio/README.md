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
| god_freeze.ogg | GOD成立ゲームのレバー時フリーズ演出 |
| juggler_god_god_freeze.ogg | JUGGLER_GODでGOD成立時の「プチュン」音（god_freeze.oggより優先） |
| juggler_god_god_stop_1.ogg | GOD成立ゲームの第1停止音 |
| juggler_god_god_stop_2.ogg | GOD成立ゲームの第2停止音 |
| juggler_god_god_stop_3.ogg | GOD成立ゲームの第3停止音 |
| juggler_god_god_bonus_start.ogg | GOD揃い直後の最初のBIG開始効果音 |
| juggler_god_god_bonus_end.ogg | GOD揃い直後の最初のBIG終了効果音 |
| juggler_god_god_big_bgm.ogg | GOD揃い直後の最初のBIGだけで使うBGM（ループ） |

ファイル形式はMinecraftが再生できるOGGです。MP3等の拡張子だけを変えてもOGGにはなりません。音の内容・長さは自由です。

1. 用意したOGGを上記の名前で配置します。12個すべてを揃える必要はありません。
2. リポジトリ直下の `build-jars.bat` をダブルクリックします。
3. 成功すると `dist` フォルダへPaper用とFabric用のJARが出ます。
4. 音源を変えたら同じ手順で再ビルドします。Javaコードの編集は不要です。

このフォルダの指定名OGGはFabric JAR内の `assets/piri/sounds/` に入ります。同名の `fabric/src/main/resources/assets/piri/sounds/` よりこのフォルダを優先します。未配置の音は、全機種共通の再生処理でMinecraft標準の音へフォールバックします（無音にしません）。音源ファイルを配置した場合は元のOGGが必ず優先されます。削除して再ビルドしたOGGは新しいJARに残りませんが、標準音は鳴ります。

`big_bgm.ogg` / `reg_bgm.ogg` はPhase06で追加したユーザー指定のBGMです。BIG/REG中にクライアント側でループし、ボーナス終了または画面/session resetで停止します。

準備・導入先・コマンドでのビルドは `../BUILD_JARS.md` を参照してください。


`god_freeze.ogg` はJuggler + GOD後続仕様のGOD成立ゲーム専用音源です。未配置なら無音で続行します。


GOD成立時の専用音は、上記 `juggler_god_god_*.ogg` を置くと通常のJUGGLER_GOD音より優先されます。専用ファイルが無い場合は、停止音・BIG開始音・BIG終了音・BIG BGMは通常のJUGGLER_GOD音へフォールバックします。フリーズ音だけは `juggler_god_god_freeze.ogg` → `god_freeze.ogg` の順です。

専用BIG BGMはGOD揃いから入る最初のBIG 1回だけで、BIG終了時に通常どおり停止します。その次のBIGからは通常の `juggler_god_big_bgm.ogg`（未配置なら `big_bgm.ogg`）へ戻ります。

## 配布JARと音の優先順位（2026-11-10）

GitHub Actionsの配布物にユーザー提供の通常OGGが含まれない場合でも、BET・レバー・停止・告知・払出・ボーナス開始/終了は**Minecraft標準音**で鳴ります。BIG/REGのBGMもOGGが無い場合はMinecraft標準のレコード音源をフォールバックに使います。追加音源ではなくMinecraftが元から持っているものです。既存JUGGLER・SKILL_STOP・JUGGLER_GOD系・HYPER_DOGの全てで同じ動作です。

従来の音源そのものを使う場合は、このフォルダへ対象OGGを配置してFabric JARを再ビルドしてください。元ファイルがある場合は代替音を使用しません。音量・距離減衰・ホール外部観覧の制御は変更しません。
