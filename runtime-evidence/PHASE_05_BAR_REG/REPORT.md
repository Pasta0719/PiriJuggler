# BAR確定目・REG当選の実Minecraft確認 — PASS

UTC: 2026-09-14T00:59:19.484106+00:00 ～ 2026-09-14T01:00:42.791240+00:00

9項目PASS。全server/client exit0、未処理例外0。

配布対象外configを単独REG当選100%とし、通常のBET/LEVERで本番RNGを実行。実clientで右・左・中STOPキーを表示位相に合わせて操作し、停止位置(10,12,5)の上段BAR-BAR-BARを確認した。位相/pressedIndex/stopIndexはclientから送っていない。

払出し0、Piri Chance点灯、PUBLIC_STATE=BONUS_PENDING、内部BONUS_PENDING_REG、BONUS_START/PAYOUTなし、元資産802枚からBET3枚だけ消費して799枚保持。全client packetでBIG/REG種類を非公開、ESC離席後も権利を保持。スクリーンショットとraw packetsをresult.json/attempts/screenshotsへ保存。

再現: PIRI_BAR_BONUS=REGを設定し python runtime-test-support/run_phase05_bar.py。
