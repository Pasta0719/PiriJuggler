# HYPER_DOG Phase04 — 下位/上位AT・BAR・CZ・32G引戻し・SQLite経済・実Minecraft受入

**2026-11-09 JST / COMPLETE**

受入対象のゲームコード・実機テスト: [`1bb4aa8c9275c43929b0619a247449dd08d26551`](https://github.com/Pasta0719/PiriJuggler/commit/1bb4aa8c9275c43929b0619a247449dd08d26551)。ユーザー承認のv5数値・既存仕様を変更せず、新機種専用のゲームエンジン、DB状態保存、実Paper/Fabricテスト補助に実装。

## 実装と完了条件の照合

1. **下位/上位ATと無料特殊区間**: `HyperDogAtGame` に下位初期1G（150枚以上）、上位初期5G（各100枚以上・計500枚以上）、下位/上位の差枚数、REPLAY後の0BET、無料区間0BET/0払出、上位通常上乗せ下位の厳密5倍、上位MISSの1/32書換と+2,000例外を実装。第三停止で一度だけ確定し、保存復帰と64bitオーバーフロー例外でも途中精算しない。
2. **CZ・BAR・ストックと終端**: 下位の1権利＝挑戦、2権利＝成功保証、3権利以降＝上位初期レア役枠。上位CZは最大5無料G・通常合計50%・早期成功。下位経由CZ失敗は過去の差枚を引きずらず下位初期から再開、上位経由CZ失敗は完全終了。下位中BARは残差枚・権利を引き継ぎ上位へ直行。上位BARは次CZ成功と初期レア役保証を累積して維持。役分布と各移行を専用JUnitで検証。
3. **32有料G引戻しと通常モード**: `HyperDogFullGame` はAT終了時に保存入賞モードからの通常モード移行を行い、32有料ゲーム中は通常抽選に独立引戻し抽選を加える。REPLAYによる無料Gは32Gを消費しない。BET/LEVER/第三停止間の状態は秘密の抽選状態と共にサーバー側でのみ保持。
4. **SQLite・権利・復帰**: `HyperDogStateStore` は既存schema-v4の`player_sessions`でCREDIT 0–50と持ちメダルを原子的に保存。クレジット/IN/OUTの保存前後差分検証、重複・古いシーケンス拒否、セッション所有権、物理停止位置・役・台ID・セッションIDの整合性を強制。再起動、離席、期限切れ、明示CLOSE、シミュレーション強制解放を通じて未完了AT/CZ/32G引戻しを保全。既存機種には既存動作を維持。
5. **固定v5と既存回帰**: 数値JSON SHA256=`af1ae6f88e6593116692f987534c406673fbbfa73cb25f52615a7f1674c20ab3`。実Paperログの役別払出、差枚、CREDIT保存則、強チャンス目の抽選テーブルを**独立Python受入スクリプト**で照合しすべてPASS。全体Gradleビルド/JUnit、Phase02 v2リール実機、Phase03通常時実機の回帰も同一コミットでSUCCESS。

## 最終実Minecraft証跡 — すべてSUCCESS

- [Phase04 Paper 1.21 + Fabric 1.21 AT/CZ 実ネットワーク・v5照合・別JVM SQLite復帰 — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42545976374) / [Artifact #13643901123（JSON/ログ/スクリーンショット/SQLite）](https://github.com/Pasta0719/PiriJuggler/actions/runs/42545976374/artifacts/13643901123)。
  - 実クライアントからのBET/LEVER/STOPをPaperの判定に接続し、70G以上・各3停止、全7図柄ロード・3×3描画、停止ID/停止位置完全一致、拒否パケット0、BET/LEVER要求と受理回数一致を確認。
  - 下位/上位AT、CZ成功/失敗、保証CZ、3個以上CZ権利、無料特殊区間、30枚GRAPE、有料3BET、差枚・財布台帳、v5照合、途中停止からの復元をすべてPASS。
  - **実Paper JVMを一度終了し、同じ隔離SQLiteを使う別のPaper JVMで再起動**。BAR成立中のリール2停止済みから第三停止を続行し、BAR 15枚を一度だけ払い出し。台・プレイヤー所有権と内部RNG/停止履歴を維持し、上位初期5G計500枚以上へ継続、別プレイヤーの占有を拒否。ログに `HYPER_DOG_PHASE04_REAL_TWO_JVM_RESTART=PASS`。
- [全体ビルド・単体/統合・既存機種回帰 — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42545980276)。
- [Phase02 v2 実Paper/Fabricリール回帰 — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42545976353)。
- [Phase03 実Paper/Fabric通常時回帰 — SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42545976403)。

## 再現方法・成果物

- `.github/workflows/hyperdog-phase04-runtime.yml`: Paper/Fabric/JUnit・本物の2 JVM再起動を必須チェックとし、失敗時もログと証跡をArtifactへ保存。
- `runtime-test-support/run_hyperdog_phase04.py`: Fabricを使ったAT/CZ実機試験と独立v5モデル照合。
- `runtime-test-support/run_hyperdog_phase04_restart.py`: Paperプロセスを2回起動し、隔離SQLite/未完了物理リール/権利/経済を再開・検証。
- `paper/src/test/java/jp/pirijuggler/paper/game/hyperdog/HyperDogAtGameTest.java`、`HyperDogFullGameTest.java`、`HyperDogStateStoreTest.java`: 無料/有料区間、CZ権利、BAR、+2000、32G、復元と境界値・所有者拒否の回帰。

## 完了判定の境界（非公開・非マージ）

- Phase04 COMPLETEは**実装済みの独立ゲーム経済エンジンとテスト専用Paper/Fabric受入**について。新機種を一般プレイヤーが現在の本番筐体画面から選択できる状態ではない。既存`MachineService`の`MACHINE_NOT_RELEASED`ガードを維持し、製品の新筐体UI、演出、周辺システム、最終総合受入はPhase05～08で別途実装。
- `PHASE_MAP.json` のPhase05は **NOT_STARTED**。Phase規約`one_phase_per_run=true`に従い、この完了記録で次Phaseには着手しない。
- PR #24はDraft・未マージ、`main`は変更しない。仕様固定値、既存機種の権利、Vault、保存データを保持する。
