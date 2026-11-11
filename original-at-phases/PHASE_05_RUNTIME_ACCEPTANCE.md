# HYPER_DOG Phase05 — Fabric専用筐体・入力UI・実Minecraft受入

**2026-11-09 JST / COMPLETE**  
受入対象実装コミット: [`f0b052ea105bd6f9b32c28d73bc07dc499d835ce`](https://github.com/Pasta0719/PiriJuggler/commit/f0b052ea105bd6f9b32c28d73bc07dc499d835ce)。Phase06–08は未着手。PRはDraft・未マージ。

## 実Minecraft検証: SUCCESS

- [HYPER_DOG Phase05専用Paper/Fabric実機CI SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42550229897) — Paper 1.21 build 130 + Fabric 1.21の実ネットワーク。全ワークフローステップ成功、`HYPER_DOG_PHASE05_RUNTIME=PASS`。
- [実機スクリーンショット・双方ログ・JSON証跡 Artifact #13644638539](https://github.com/Pasta0719/PiriJuggler/actions/runs/42550229897/artifacts/13644638539)。94ゲーム、282停止（270手動・12自動）、FabricとPaperのspinIdと3停止の組合せ全一致、操作要求とサーバー受理数一致、拒否パケット0。実機Clientで数千フレーム描画。
- [同一コミットの製品全体ビルド・JUnit回帰 SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42550233810)。
- [同一コミットのPhase02実Paper/Fabricリール回帰 SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42550229899)。
- [前コミット `f9c3b7d5` のPhase03通常時実機回帰 SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42549933036) / [Phase04 AT/CZ実機回帰 SUCCESS](https://github.com/Pasta0719/PiriJuggler/actions/runs/42549932970)。Phase05最終差分は新機種専用UIとデータランプ履歴スクロール処理。

## 完了条件と実装の対応

1. **既存配置・資産導線**: `HyperDogCabinetScreen` は`SlotLayout`の1920×1080論理座標と、既存のLEVER→BET→停止左中右、CREDIT/BET/PAY/MEDALS、貸出/投入/精算、データランプ位置/クリック矩形を再利用。1920座標の共有`SlotLayout.CONTROLS`でクリック判定。Fabric端でviewport投影/逆変換を実装し、unit testで16:9、4:3、2:1など6画面サイズの全操作矩形中心を検証。
2. **独立画面・既存4機種保護**: `MachineScreenFactory.createHyperDog` から`HyperDogCabinetScreen`に限定して新機種を開く。`JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` の従来画面生成経路、`SlotScreen` / `GodScreen` / `SlotLayout`自身は変更なし。従来`SlotScreen`の実フレーム撮影と既存unit/integrationテストを実施。従来四機種の**全てを個別にPaper/Fabric GUIで起動した証跡ではない**ため、その範囲は最終Phase08の総合回帰でも確認する。
3. **筐体画面・数字・演出表示の土台**: `HyperDogCabinetState` はPaperから受信した公開状態のみを投影。左右告知灯、下位/上位/CZの縦3状態灯、32有料G引戻しのダブルパルス、右3期待度灯、リール左の黒基調LCD、従来データランプ（履歴10件スクロール）を表示。64bitの大きなCREDIT・残差枚もテキスト幅に応じた縮小表示をunit testで検証。ランプ色は当該獲得枚数の下限条件を満たす時のみ点灯し、0枚では右演出なし。
4. **第三停止の`TOTAL`・`+N`表示**: サーバー公開`displayEvents`の固有IDとFIFOを使い、下位初期1Gの`TOTAL`（1400ms）、上位初期1〜5Gの`+N`（1200ms）、上位6Gの初期5G`TOTAL`→6Gで別途獲得した`+N`→現在残差枚を順に表示し、再表示を重複しない。演出表示中の次BET・レバーをFabricでロック。サーバーの当選や枚数をクライアント側では決定しない。unit testで再送冪等性と新セッション時のイベント再生を検証。
5. **実入力・表示・再着席**: 実FabricのGUIインスタンスの`mouseClicked`と`keyPressed`を使い、押し順を切り替え、AT/CZの上位/下位・当否両方・ランプ点灯/消灯・FIFO表示を観測。CLOSE→SESSION_SUSPENDED→再着席後の専用画面再生成も実機PASS。Paperは固定されたPhase04サーバー権威エンジンに接続し、実機受入用fixtureから正規公開パケットを送る（本番一般開放ではない）。

## 実フレームの画像と再現

画像はCI Artifact `hyperdog-phase05-real-fabric-cabinet` 内に収録され、PNGシグネチャと実画像の画面比を自動検証済み。

- `hyperdog-phase05-16x9.png` — 専用筐体、16:9。
- `hyperdog-phase05-4x3.png` — 画面リサイズ後の4:3。
- `hyperdog-phase05-2x1.png` — 画面リサイズ後の横長2:1。
- `hyperdog-phase05-existing-slot-screen-comparison.png` — 旧`SlotScreen`を実際に描画した比較画像。
- `hyperdog-phase05-after-reseat.png` — CLOSE/再着席後の新筐体。

再現用: `.github/workflows/hyperdog-phase05-runtime.yml`、`runtime-test-support/run_hyperdog_phase05.py`、`runtime-test-support/client/src/main/java/jp/pirijuggler/runtime/client/HyperDogPhase05Probe.java`、`runtime-test-support/paper/src/main/java/jp/pirijuggler/runtime/paper/HyperDogPhase05Fixture.java`、`fabric/src/test/java/jp/pirijuggler/fabric/ui/HyperDogCabinetStateTest.java`。

## 適用範囲と未着手範囲

- **Phase05完了は専用筐体・操作UI・データ表示の基本実装とその実Paper/Fabric受入の完了を意味する。** 上乗せ最終色・1→2→3灯の停止段階・第三停止フラッシュ、CZ虹7、赤7の最終演出仕様は**Phase06**で実装・受入。データランプのHYPER_DOG専用分類・管理画面・観覧/遠隔・音声は**Phase07**。公開と最終横断回帰は**Phase08**。
- 実機試験は**test-only Paper fixture**を用いる。`MachineService`側の`MACHINE_NOT_RELEASED`ガードは維持し、既存店の台とデータを変更しない。
- `PHASE_MAP.json`のPhase06–08は**NOT_STARTED**。`one_phase_per_run=true`に従い、この受入記録で次Phaseには着手しない。
