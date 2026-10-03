# SKILL STOP Phase05 — チャレンジ表示・成功音・残G
Status: COMPLETE — BUILD_CI_PASS / REAL_PAPER_FABRIC_RUNTIME_PASS

レバー確定時の公開SPIN_STARTへ残Gと固定チャレンジ対象を追加。操作画面・観覧用SPIN/snapshotへ同じ情報を送り、CHANCE下（論理410,580/180×140）へ既存BAR/ベル/ピエロPNGを実寸比率で収める。第三停止の完了PUBLIC_STATEでAUTOへ戻し消灯。ボーナス中は従来COUNTに代えて「残り nG」を表示。通常機種の表示は変更しない。

成功時のみ第三停止完了のdelayでNOTICE（既存notice音）を1イベント送る。クライアントはspinIdで重複を抑止。観覧には成功専用REMOTE_MACHINE_SOUNDを送り、同じspinIdを重複再生せず台位置から再生する。通常機種の観覧音や既存A〜Fの音・タイミングは変更しない。

観覧registryの機種allow-listにSKILL_STOPを追加。未公開の役・種別・stopHintsを観覧へ送らない。既知のボーナス残Gとレバー固定済みの指定図柄だけを追加。

共通の公開状態と重複音gate、Paperの3図柄成功/指定外失敗/再送、Fabricの固定対象・復帰・消灯・重複音のテストを追加。実機シナリオはPhase03回帰に、第二Fabric観覧クライアント、3指定図柄の成功、指定外失敗、ESC外観/復帰、成功音の実SoundManager境界と再送の確認を追加。

音源は既存のuser-audio/notice.oggまたは同じresource-packパスを使用。新しい音源は生成・追加しない。リポジトリに通常notice.oggが無い状態でもゲームは進む既存仕様を維持。実機の音接続検証では、既存ユーザー提供OGGをnotice.oggとして参照するテスト専用resource packを2クライアントに配置する。このfixtureは配布JARへ含めない。

実行: run-skill-stop-phase05-runtime.bat、または .github/workflows/skill-stop-phase05-runtime.yml。
Phase06の最終総合受入は別run。


## 完了記録 — 2026-10-03
- 通常build CI: PASS — Actions run `37127664141`
- Phase05実Paper/Fabric runtime: PASS — Actions run `37127643241`
- 検証source: `b74bb5180b7d663c0e2d7bb5885cbd6a440131b3`
- PR: #5
- Phase06最終総合受入は未着手
