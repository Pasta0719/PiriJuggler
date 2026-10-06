# MOBILE REMOTE — Unified Phase

Status: BUILD_READY — full static parity audit applied / local build + final E2E acceptance pending

Updated: 2026-10-06

この文書をスマホ遠隔遊技・モバイル経済・景品交換・Minecraft同期・モバイル表示の唯一の正本とする。
過去の個別コミット名や断片的な実装記録は履歴として残すが、完了判定はこのPhaseだけで行う。

## 目的

Minecraft本体と同一のPaper側ゲーム状態・経済状態を使い、スマホから安全に遊技・離席・景品交換・同期を行えるようにする。
スマホ専用の別経済や別ゲーム状態を作らない。

## 実装範囲

### 接続 / セッション
- Minecraft内の /piri mobile pair でペアリングする
- 認証済みスマホから台一覧・台データを確認できる
- 着席 / 離席はPaperの同一セッションを使用する
- スマホとMinecraftで同一プレイヤー・同一台の権利状態を共有する
- 離席で遊技席を解放する

### 遊技
- BET / LEVER / LEFT / CENTER / RIGHT をスマホから操作できる
- 停止入力は実際のpressed indexをPaperへ送り、Fabricと同じ停止制御を使う
- 通常機 / SKILL_STOP / JUGGLER_GOD系の既存productionロジックを変更しない
- CREDIT / BET / PAY / MEDALS / MONEY を表示する
- LOAN / INSERT は遊技中に利用可能
- CASH OUT は台のCREDIT・持ちメダルを清算して持ち出す「台清算」であり、景品換金とは別物

### 景品交換 / 換金
- 景品交換と景品→Vault換金は遊技台画面に置かない
- 利用できるのは台から離席したロビーのみ
- ロビーに景品交換所を置く
- 小 / 中 / 大景品へ交換できる
- 所持景品をVaultへ換金できる
- 着席中の景品交換・景品換金APIは MUST_LEAVE_MACHINE で拒否する
- 遊技台画面に EXCHANGE ボタンを置かない

### Minecraft ↔ Mobile 資産同期
- Minecraftから離れる際、対象の持ち出しメダルをモバイルwalletへ安全に移せる
- Minecraftへログインした際、モバイルwalletのメダルをゲーム内へ戻す
- Minecraftの景品とモバイル景品を相互同期する
- ログイン時にモバイル景品をゲーム内inventoryへ復元する
- inventory不足などで戻せない分は失わずモバイル側へ残す
- DB処理は既存database executor / ledger / transaction経路を使用する

### モバイル表示
- 横画面 / 縦画面の両方で遊技可能
- 台データ、差枚グラフ、履歴、BIG/REG、確率表示を提供する
- SKILL_STOPでは通常機と異なる専用presentationを使う
- SKILL_STOP cabinet background は #111015
- SKILL_STOP challenge PNG、残G、成功音を既存production状態に同期する
- JUGGLER_GOD系の専用画像・リール背景・演出を壊さない

## 非対象
- 既存スロット各機種の抽選確率・停止制御・機械割の変更
- スマホ専用の別ゲームエンジン
- 着席中の景品交換・景品換金
- hidden role / setting / internal bonus state の公開

## 最終受入条件

COMPLETEにするには以下をすべて満たす。

- Paper production build PASS
- Fabric production build PASS
- mobile E2E runtime PASS
- ペアリング → 台一覧 → 着席 → BET/LEVER/STOP → 離席が実動作
- 実pressed indexで停止し、Paper判定と表示が一致
- LOAN / INSERT / 台CASH OUTが実経済で動作
- 着席中の景品交換・景品換金が拒否される
- 離席後ロビーで景品交換・景品換金が動作
- Minecraft→mobile→Minecraft のメダル往復で増減・消失がない
- Minecraft→mobile→Minecraft の景品往復で増減・消失がない
- inventory不足時も資産を失わない
- SKILL_STOP背景 #111015、チャレンジPNG、残Gが実ブラウザ表示で確認できる
- 遊技画面に EXCHANGE が存在しない
- 既存Paper/Fabric遊技への回帰がない
- evidenceを runtime-evidence/MOBILE_E2E/ に保存する

## 完了判定ルール

コードが存在するだけではCOMPLETEにしない。
古いPhaseのPASSや、別機種のruntime PASSをこのPhaseのPASSとして代用しない。
UIスクリーンショットだけでもCOMPLETEにしない。
上記E2Eを現行mainで通し、証跡を保存してからStatusをCOMPLETEへ変更する。

### Reel presentation parity
- Fabric/Minecraft の確定表示を正本とする
- 通常定速は 28 symbols/sec
- モバイルは Fabric と同じ加速・逆回転プロファイル、停止方向、停止速度上限、図柄サイズ比率を維持する
- モバイル独自の非等方スケーリングで図柄サイズ比率を崩してはならない


## 2026-10-06 full parity audit

Static cross-check completed against current Fabric/Paper production behavior.

- reel normal speed: 28 symbols/sec
- NORMAL acceleration: 150ms hold + 350ms linear acceleration to -28
- reverse premium: +12 symbols/sec for 500ms, then 300ms transition to -28
- RESUME_NORMAL: -28 symbols/sec
- stop interpolation cap: 28 symbols/sec
- mobile pressed-index stop path retained
- ordinary symbol boxes: seven 230x130, BAR 230x150, others 130x130
- GOD symbol boxes: seven/grape/replay/BAR 230x150, others 130x130
- portrait mobile reel viewport corrected to 96x139 and symbol scaling made uniform
- SKILL_STOP cabinet background #111015 retained
- machine label remains MACHINE <id> without machine type
- in-machine EXCHANGE button absent
- prize buy/cash restricted to lobby after leave
- machine CASH OUT remains separate from prize cash
- LOAN / INSERT / leave endpoints retained
- mobile E2E harness already covers pairing, seat, BET/LEVER/STOP, CASH OUT, leave, prize exchange/cash, medal/prize Minecraft↔mobile restoration

No COMPLETE claim is made until the current main is built locally and the E2E runtime is executed.
