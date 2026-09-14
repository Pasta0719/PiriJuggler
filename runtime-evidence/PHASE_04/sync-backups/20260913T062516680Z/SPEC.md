# Piri Juggler — Codex実装指示書 v4（全件横断監査済み・単体完結版）

この文書だけを唯一の仕様書として扱ってください。
過去の会話、以前の仕様書、外部サイト、既存パチスロ実機の追加調査を前提にしないでください。
この文書に書かれた数値・配列・状態遷移・UI・通信・保存・コマンドをそのまま実装してください。
「configで変更可能」と明記された値だけ運用時に変更可能です。初期値は必ず本書記載値です。

実装対象は、Minecraft Java Edition 1.21上で動作するオリジナル3リールスロット「Piri Juggler」です。
Minecraft内のチェストGUIではなく、登録済みの筐体を右クリックすると専用2Dスロット画面が開き、普通のパチスロ実機に近い操作感で遊べるものを作ります。

名称・ランプ・筐体UI・音・図柄画像はオリジナルです。
ゲーム性はジャグラー系ですが、実装上の正はこの文書です。

---

# 1. 技術構成

Minecraft:
- Java Edition 1.21

Java:
- Java 21

Server:
- Paper 1.21

Client:
- Fabric 1.21
- Fabric Loader 0.16.14
- Fabric API 0.102.0+1.21
- Yarn 1.21+build.9

専用ResourcePack:
- 使用しない

Economy:
- Vault API

保存:
- SQLite
- WAL mode
- foreign_keys=ON

Build:
- Gradle Kotlin DSL

Repository:

```text
piri-juggler/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ common/
├─ paper/
├─ fabric/
└─ docs/
```

commonに置くもの:
- Packet DTO
- 公開enum
- protocol constants
- codec

paperにのみ置くもの:
- 抽選ロジック
- 内部役
- 設定
- 当選状態
- リール停止候補計算
- Vault処理
- DB
- 台管理
- セッション
- 金銭処理

fabricに置くもの:
- Slot Screen
- Admin Screen
- リール描画
- 入力
- 音
- Piri Chance
- データランプ
- inventory上の仮想メダル枚数描画

---

# 2. サーバー権威

Paper Pluginを唯一の正とする。

Fabric MODから受け取る情報は「プレイヤー操作要求」だけとする。

クライアントから指定させない:
- 内部役
- BIG/REG
- 設定
- payout
- CREDIT
- 持ちメダル
- メダル枚数
- 景品枚数
- Vault増減
- 最終stopIndex
- 滑り数
- ボーナス終了
- 当日統計

クライアント改造を前提として全操作をPaper側で検証する。

---

# 3. 図柄enum

固定:

```text
SEVEN
BAR
GRAPE
CHERRY
BELL
PIERO
REPLAY
```

Fabric側のオリジナル見た目:

```text
SEVEN  = 赤7 + 金アクセント
BAR    = 黒カプセル + "PIRI BAR"
GRAPE  = 紫ブドウ
CHERRY = 赤い2粒チェリー
BELL   = 金ベル
PIERO  = 唐辛子モチーフの道化師
REPLAY = オレンジ色のキツネ型マスコット
```

各図柄:
- 256x256 PNG
- transparent
- Fabric jarへ同梱
- 既存パチスロ画像をコピーしない

---

# 4. リール配列

3リール。
各21コマ。
indexは0..20。
下記配列を固定使用する。

## LEFT_REEL

```text
0  GRAPE
1  REPLAY
2  GRAPE
3  SEVEN
4  PIERO
5  GRAPE
6  REPLAY
7  GRAPE
8  CHERRY
9  BAR
10 GRAPE
11 REPLAY
12 GRAPE
13 BELL
14 SEVEN
15 REPLAY
16 GRAPE
17 REPLAY
18 GRAPE
19 BAR
20 CHERRY
```

```java
LEFT_REEL = [
  GRAPE, REPLAY, GRAPE, SEVEN, PIERO, GRAPE, REPLAY,
  GRAPE, CHERRY, BAR, GRAPE, REPLAY, GRAPE, BELL,
  SEVEN, REPLAY, GRAPE, REPLAY, GRAPE, BAR, CHERRY
];
```

## CENTER_REEL

```text
0  CHERRY
1  PIERO
2  REPLAY
3  SEVEN
4  GRAPE
5  CHERRY
6  REPLAY
7  BELL
8  GRAPE
9  CHERRY
10 REPLAY
11 BAR
12 GRAPE
13 CHERRY
14 REPLAY
15 BELL
16 GRAPE
17 CHERRY
18 REPLAY
19 BAR
20 GRAPE
```

```java
CENTER_REEL = [
  CHERRY, PIERO, REPLAY, SEVEN, GRAPE, CHERRY, REPLAY,
  BELL, GRAPE, CHERRY, REPLAY, BAR, GRAPE, CHERRY,
  REPLAY, BELL, GRAPE, CHERRY, REPLAY, BAR, GRAPE
];
```

## RIGHT_REEL

```text
0  BELL
1  REPLAY
2  GRAPE
3  SEVEN
4  BAR
5  BELL
6  REPLAY
7  GRAPE
8  PIERO
9  BELL
10 REPLAY
11 GRAPE
12 PIERO
13 BELL
14 REPLAY
15 GRAPE
16 PIERO
17 BELL
18 REPLAY
19 GRAPE
20 PIERO
```

```java
RIGHT_REEL = [
  BELL, REPLAY, GRAPE, SEVEN, BAR, BELL, REPLAY,
  GRAPE, PIERO, BELL, REPLAY, GRAPE, PIERO, BELL,
  REPLAY, GRAPE, PIERO, BELL, REPLAY, GRAPE, PIERO
];
```

リール配列はconfig化しない。
v1.0ではコード定数とし、運用中変更不可。

---

# 5. 表示段・リール位相

各リールは常に3図柄表示する。

`stopIndex`は中段図柄index。

```text
TOP    = reel[(stopIndex + 20) % 21]
MIDDLE = reel[stopIndex]
BOTTOM = reel[(stopIndex + 1) % 21]
```

通常回転方向はindex増加方向。

各リールはserver側に`phase`を持つ。`phase`は0以上21未満のdoubleで、整数部分が現在中段を通過中のindex。

STOP要求をserverが受信した時、132章の遅延補正式で`effectiveElapsedSec`を求め、132章のmotion functionから`phaseAtPress`を計算する。

```text
pressedIndex = floor(phaseAtPress) mod 21
slip = (targetStopIndex - pressedIndex + 21) mod 21
```

slipは0..20。

クライアントは`pressedIndex`、`phase`、`targetStopIndex`を送信しない。STOP要求はリール名だけを送る。

---

# 6. 有効ライン

固定5LINE。

```text
L1_CENTER = [MIDDLE, MIDDLE, MIDDLE]
L2_TOP    = [TOP, TOP, TOP]
L3_BOTTOM = [BOTTOM, BOTTOM, BOTTOM]
L4_DOWN   = [TOP, MIDDLE, BOTTOM]
L5_UP     = [BOTTOM, MIDDLE, TOP]
```

ライン優先順位:

```text
L1_CENTER
> L2_TOP
> L3_BOTTOM
> L4_DOWN
> L5_UP
```

---

# 7. 通常役と払い出し

通常時内部役:

```text
MISS
REPLAY
GRAPE
CHERRY
BELL
PIERO
BIG
REG
CHERRY_BIG
CHERRY_REG
PIERO_BIG
PIERO_REG
```

払い出し:

```text
GRAPE  = 8
CHERRY = 2
BELL   = 14
PIERO  = 14
REPLAY = 0 + 次ゲーム無料
MISS   = 0
```

---

# 8. 入賞形

GRAPE:

```text
GRAPE-GRAPE-GRAPE
```

5LINEの1本で成立。

BELL:

```text
BELL-BELL-BELL
```

5LINEの1本で成立。

PIERO:

```text
PIERO-PIERO-PIERO
```

5LINEの1本で成立。

REPLAY:

```text
REPLAY-REPLAY-REPLAY
```

5LINEの1本で成立。

CHERRY:

左リールのみで判定。

通常CHERRY:

```text
LEFT.TOP == CHERRY
XOR
LEFT.BOTTOM == CHERRY
```

左右両方同時にCHERRYになるstopIndexは通常CHERRY候補から除外。
MIDDLE CHERRYは通常CHERRY候補から除外。

中・右リールは問わない。

CHERRY payoutは1ゲームにつき1回だけ2枚。

---

# 9. ボーナス入賞形

BIG:

```text
SEVEN-SEVEN-SEVEN
```

5LINEの1本。

REG:

```text
SEVEN-SEVEN-BAR
```

5LINEの1本。

BIG成立時はBIG入賞形以外をボーナス開始条件にしない。
REG成立時はREG入賞形以外をボーナス開始条件にしない。

---

# 10. 目押し仕様

小役の取りこぼしは存在しない。

停止入力時刻は以下へ影響:
- stopIndex
- slip
- 最終出目

停止入力時刻は以下へ影響しない:
- 成立役の獲得可否
- payout

通常優先slip:
```text
0,1,2,3,4
```

0..4で成立役を維持できない場合:
```text
5,6,...,20
```
の順で探索。

必ず成立役を最終的に入賞させる。

---

# 11. 9261停止形事前計算

起動時に:

```text
21 * 21 * 21 = 9261
```

全triplet:

```text
(leftStop, centerStop, rightStop)
```

を列挙して各表示3段・5LINEを評価する。

各tripletに以下を記録:

```text
winningGrapeLines
winningBellLines
winningPieroLines
winningReplayLines
leftTopCherry
leftMiddleCherry
leftBottomCherry
winningBigLines
winningRegLines
```

---

# 12. 有効な最終triplet

通常GRAPE候補:
- GRAPE lineがちょうど1本
- BELL / PIERO / REPLAY / BIG / REG が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

BELL候補:
- BELL lineちょうど1本
- GRAPE / PIERO / REPLAY / BIG / REG が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

PIERO候補:
- PIERO lineちょうど1本
- GRAPE / BELL / REPLAY / BIG / REG が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

REPLAY候補:
- REPLAY lineちょうど1本
- GRAPE / BELL / PIERO / BIG / REG が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

CHERRY候補:
- `leftTopCherry XOR leftBottomCherry == true`
- leftMiddleCherry=false
- GRAPE / BELL / PIERO / REPLAY / BIG / REG が0本

MISS候補:
- GRAPE / BELL / PIERO / REPLAY / BIG / REG が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

BIG entry候補:
- BIG lineちょうど1本
- REG 0本
- GRAPE / BELL / PIERO / REPLAY が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

REG entry候補:
- REG lineちょうど1本
- BIG 0本
- GRAPE / BELL / PIERO / REPLAY が0本
- leftTopCherry=false
- leftMiddleCherry=false
- leftBottomCherry=false

プレミアB候補:
- leftMiddleCherry=true
- leftTopCherry=false
- leftBottomCherry=false
- GRAPE / BELL / PIERO / REPLAY / BIG / REG が0本

起動時assertで最低件数を確認する。
本仕様の固定配列で期待する最低件数:

```text
GRAPE >= 750
BELL >= 50
PIERO >= 20
REPLAY >= 525
CHERRY >= 1514
MISS >= 5250
BIG_ENTRY >= 10
REG_ENTRY >= 10
PREMIUM_B >= 1
```

1カテゴリでも0件ならgameplay disabled + SEVERE。

---

# 13. 停止候補tie-break

各STOP入力時:
- 既停止リールは`stoppedMask`と表示stopIndexで固定
- 内部役に対応する最終tripletだけを候補にする
- 未停止リールは最終成立可能性が残るtripletだけを維持

候補tripletごとに`targetRank`を定義する。

```text
L1_CENTER = 0
L2_TOP = 1
L3_BOTTOM = 2
L4_DOWN = 3
L5_UP = 4
CHERRY_TOP = 1
CHERRY_BOTTOM = 2
PREMIUM_B_MIDDLE_CHERRY = 0
MISS = 5
```

今回停止するリールについて、以下の比較順で最小の候補tripletを採用する。

1. `slip`の数値が小さい
2. `targetRank`が小さい
3. `leftStop`が小さい
4. `centerStop`が小さい
5. `rightStop`が小さい

slip 0..4を特別に別探索する必要はない。この比較規則では0..4が存在すれば必ず5..20より先に選ばれる。

乱数tie-breakは禁止。
同じ固定条件・同じpressedIndexなら必ず同じstopIndexになる。

---

# 14. 重複役停止

CHERRY_BIG / CHERRY_REG:
- CHERRY候補と同じ見た目
- 2枚払い出し
- ボーナス成立

PIERO_BIG / PIERO_REG:
- PIERO候補と同じ見た目
- 14枚払い出し
- ボーナス成立

BIG/REG単独:
- 通常ゲームではMISS候補の停止形を使う
- 当選ゲームで777/77BARを出さない
- ペカ後の1BETゲームでボーナス図柄を揃える

---

# 15. 1ゲームに複数払い出し禁止

通常ゲームでは、選択された内部役に対応する払い出し1種類だけを発生させる。

停止候補は12章の条件により他払い出し役を視覚的にも排除する。

起動時の全停止候補検証で、各役が全停止順・全pressedIndex状態から最終的に到達可能かテストする。

到達不能ケースが1件でもあれば:
- Pluginを遊技不可状態
- 該当内部役/停止順/pressedIndexをSEVEREログ
- 遊技開始を拒否

「他役も揃っているが内部役だけ払う」フォールバックは禁止。

---

# 16. BET

通常:
```text
3枚
```

ボーナス成立後の入賞ゲーム:
```text
1枚
```

BIG/REG中:
```text
2枚
```

CREDIT不足時:
- BET拒否
- 自動Vault貸出しない
- 自動メダル投入しない

自動BET:
- なし

---

# 17. REPLAY

REPLAYゲーム終了後:
- payout=0
- 次通常ゲームは3BET消費なし
- state=REPLAY_READY

REPLAY_READYでSpace:
- 直接LEVER
- CREDIT減少なし
- BET表示は3点灯
- REPLAYラベル表示

次ゲームがREPLAYでも再度REPLAY_READY。

---

# 18. GameStateとSessionLifecycle

`GameState`は以下で固定する。

```text
SEATED_READY
NORMAL_BETTED
NORMAL_SPINNING
REPLAY_READY
BONUS_PENDING_BIG
BONUS_PENDING_REG
BONUS_ENTRY_BETTED_BIG
BONUS_ENTRY_BETTED_REG
BONUS_ENTRY_SPINNING_BIG
BONUS_ENTRY_SPINNING_REG
BIG_READY
BIG_BETTED
BIG_SPINNING
REG_READY
REG_BETTED
REG_SPINNING
```

`SUSPENDED`をGameStateへ入れてはいけない。中断すると元GameStateが失われるためである。

sessionの接続状態は別enum `SessionLifecycle`で管理する。

```text
ACTIVE
SUSPENDED_GRACE
SUSPENDED_SAFE
```

意味:
- ACTIVE: 画面を開いて遊技中。machine lockあり。
- SUSPENDED_GRACE: exact resume用の猶予中。machine lockあり。
- SUSPENDED_SAFE: 未完ゲームを持たない安全状態。machine lockなし。

CASHOUTはtransaction状態でありGameStateではない。

---

# 19. 抽選方式とRNG分離

通常ゲームのLEVER ON時に1回だけ内部役を決定する。STOP時再抽選禁止。

RNG denominator:
```text
1_000_000_000
```

production master seed:
```text
java.security.SecureRandom().nextLong()
```

master seedから用途別に`SplittableRandom`を分離する。

```text
gameplay RNG: machineIdごとに専用stream
event allocation RNG: gameplayと別stream
runtime simulator RNG: gameplay/eventと別stream
```

event抽選によって実ゲームの乱数列を進めてはいけない。

seedをclientへ送らない。通常/DEBUGログへ出さない。
Unit TestとSimulator testだけ固定seed注入を許可する。

---

# 20. 設定1〜6抽選weight

下記は完全固定の初期値。
各設定合計1,000,000,000。

## Setting 1

```text
REPLAY        137023842
GRAPE         166599213
BELL             915525
CHERRY         26532274
PIERO             686643
BIG              2794577
REG              1459850
CHERRY_BIG        849314
CHERRY_REG        694893
PIERO_BIG         109863
PIERO_REG         119018
MISS           662214988
TOTAL        1000000000
```

## Setting 2

```text
REPLAY        137023842
GRAPE         166783634
BELL             915525
CHERRY         26476121
PIERO             686643
BIG              2898373
REG              1589789
CHERRY_BIG        848190
CHERRY_REG        752169
PIERO_BIG         114441
PIERO_REG         114441
MISS           661796832
TOTAL        1000000000
```

## Setting 3

```text
REPLAY        137023842
GRAPE         167751292
BELL             915525
CHERRY         26391892
PIERO             686643
BIG              2928092
REG              1796183
CHERRY_BIG        859140
CHERRY_REG        825449
PIERO_BIG         119018
PIERO_REG         109863
MISS           660593061
TOTAL        1000000000
```

## Setting 4

```text
REPLAY        137023842
GRAPE         168647497
BELL             915525
CHERRY         26307662
PIERO             686643
BIG              3040213
REG              2072823
CHERRY_BIG        849033
CHERRY_REG        919785
PIERO_BIG         123596
PIERO_REG         105285
MISS           659308096
TOTAL        1000000000
```

## Setting 5

```text
REPLAY        137023842
GRAPE         170413045
BELL             915525
CHERRY         26223433
PIERO             686643
BIG              3202886
REG              2221245
CHERRY_BIG        833871
CHERRY_REG       1019176
PIERO_BIG         128173
PIERO_REG         100708
MISS           657231453
TOTAL        1000000000
```

## Setting 6

```text
REPLAY        137023842
GRAPE         172701646
BELL             915525
CHERRY         26111127
PIERO             686643
BIG              3584744
REG              2583880
CHERRY_BIG        825449
CHERRY_REG       1139905
PIERO_BIG         137329
PIERO_REG          91552
MISS           654198358
TOTAL        1000000000
```

---

# 21. 設定確率の意味

全設定:

```text
REPLAY ≈ 1/7.298
BELL   ≈ 1/1092.27
PIERO全体 ≈ 1/1092.27
CHERRY全体 ≈ 1/35.617
```

PIERO成立時:
- 75% PIEROのみ
- 25% PIERO+BONUS
- 25%重複は20章weightへ組み込み済み
- 別抽選禁止

PIERO重複BIG割合:

```text
S1 48%
S2 50%
S3 52%
S4 54%
S5 56%
S6 60%
```

残りREG。

CHERRY重複率:

```text
S1 5.5%
S2 5.7%
S3 6.0%
S4 6.3%
S5 6.6%
S6 7.0%
```

CHERRY重複時BIG割合:

```text
S1 55%
S2 53%
S3 51%
S4 48%
S5 45%
S6 42%
```

残りREG。

総BIG:

```text
S1 1/266.4
S2 1/259.0
S3 1/256.0
S4 1/249.2
S5 1/240.1
S6 1/219.9
```

総REG:

```text
S1 1/439.8
S2 1/407.1
S3 1/366.1
S4 1/322.8
S5 1/299.3
S6 1/262.1
```

---

# 22. 機械割定義

```text
機械割 = 総払い出し枚数 / 総BET消費枚数 * 100
```

含む:
- 通常3BET
- REPLAY次ゲームBET0
- ボーナス入賞ゲーム1BET
- BIG中2BET
- REG中2BET
- 通常小役payout
- 重複小役payout
- BIG 280
- REG 112

目標:

```text
S1 97.8%
S2 99.4%
S3 101.0%
S4 103.4%
S5 106.0%
S6 111.4%
```

20章weight理論値:

```text
S1 97.800003%
S2 99.400002%
S3 101.000003%
S4 103.400006%
S5 105.999988%
S6 111.400001%
```

運用中weightを変更しない限り、Codexが再調整しない。

---

# 23. 通常告知

告知ランプ名称:

```text
Piri Chance
```

デザイン:
- 唐辛子
- 赤基調
- 消灯: 暗赤
- 点灯: 鮮赤 + 白縁
- 文字: Piri Chance

プレミア非選択のボーナス当選時:

```text
先ペカ25%
後ペカ75%
```

整数weight:
```text
FIRST = 250000
AFTER = 750000
DENOM = 1000000
```

通常先ペカ:
- LEVER ONと同時
- 無音

通常後ペカ:
- 第3停止完了
- 通常告知音1回
- Piri Chance点灯

---

# 24. プレミア抽選

BIG系内部役 `BIG / CHERRY_BIG / PIERO_BIG` の各当選ゲームで独立して:

```text
50,000 / 1,000,000 = 5%
```

でプレミア抽選を行う。

REG / CHERRY_REG / PIERO_REGではプレミア確率0。

プレミア当選時は通常25/75告知抽選を行わない。

候補:
```text
CHERRY_BIG: A,B,C,D,E,F
BIG:        A,C,D,E,F
PIERO_BIG:  A,C,D,E,F
```

BはCHERRY_BIG専用。

configのA-F weightを使用し、対象内部役でeligibleな演出だけを取り出してweight再正規化する。
デフォルトA-Fは全て1。

したがってデフォルト時:
- CHERRY_BIGではA-F各1/6
- BIG/PIERO_BIGではA,C,D,E,F各1/5

Bを単独BIGまたはPIERO_BIGへ適用しない。これにより通常抽選で決まった小役payoutをプレミアが変更しない。

---

# 25. プレミアA 逆回転

LEVER ON:
1. 3リールを逆方向12 symbols/secで500ms
2. 500ms経過時に通常方向へ切替
3. 切替瞬間Piri Chance点灯
4. 告知音なし
5. 300msで18 symbols/secまで加速
6. LEVER ONから800ms後にSTOP入力解禁

AだけstopEnableAfterMs=800。

---

# 26. プレミアB 中段チェリー

CHERRY_BIG当選ゲーム専用。
単独BIG・PIERO_BIGでは選択しない。

左リールSTOP時:
- LEFT.MIDDLE=CHERRYのプレミアB候補を使用
- 左STOP完了直後に通常告知音1回

第3STOP完了後:
- 通常告知音1回
- Piri Chance後ペカ

payout:
```text
2枚
```

通常CHERRY / 通常CHERRY_BIG / CHERRY_REG / MISSではLEFT.MIDDLE=CHERRYを表示しない。
CHERRY_BIGでプレミアBが選ばれた時だけ中段CHERRYを許可する。

---

# 27. プレミアC 音付き先ペカ

LEVER ON:
- Piri Chance点灯
- 同時に通常告知音1回

---

# 28. プレミアD 強後ペカ

第3STOP:
- 強告知音1回
- Piri Chance後ペカ

---

# 29. プレミアE 5連+高速点滅

LEVER ON:
- Piri Chance先ペカ
- noticeを100ms間隔で5回
- lampは0..999msの間、100ms ON / 100ms OFF
- 1000msで常時ONへ
- STOP入力は通常400msで解禁

---

# 30. プレミアF 非テンパイテンパイ音

BIG系内部役 `BIG / CHERRY_BIG / PIERO_BIG` でFが選択されたゲーム専用。

base表示役候補は内部役に従う:
```text
BIG        -> MISS候補
CHERRY_BIG -> CHERRY候補
PIERO_BIG  -> PIERO候補
```

1本目STOP:
- 13章の通常tie-breakをそのまま使う

2本目STOP:
1. 1本目stopIndexで固定したbase候補を取得
2. 「現在停止済みになる2リールについて、5LINEのどの投影でもSEVEN-SEVENにならない」候補だけへfilter
3. filter後候補に対して13章の同じtie-breakを適用
4. 2本目を停止
5. 実SEVENテンパイが0本であることをserver assert
6. その直後にテンパイ音1回

3本目STOP:
- 1・2本目stopIndexを固定した残存base候補へ13章tie-breakを適用
- base内部役を通常どおり入賞させる
- 通常告知音1回
- Piri Chance後ペカ

固定リール配列について、`BIG/MISS`, `CHERRY_BIG/CHERRY`, `PIERO_BIG/PIERO`の3 base候補すべてで、全6停止順・1本目pressedIndex 0..20・2本目pressedIndex 0..20に対して2本目filter後候補が1件以上存在することを起動時self-testとunit testで検証する。1件でも失敗した場合gameplay disabled + SEVERE。

---

# 31. 通常テンパイ音

2本目のリールが停止した瞬間に判定。

5LINEについて、停止済み2リールが同じライン上で両方SEVENならテンパイ。

1ライン以上あれば:
- テンパイ音1回

複数テンパイでも1回。

内部役不問。

プレミアFだけ実テンパイ0本でも鳴らす。

---

# 32. 音仕様

実音源はユーザーが別途提供する。実装側は所定ファイル名のOGGを受け入れて再生する。
Codexは効果音・BGM・告知音等の音声内容を生成・推測・代替生成してはいけない。

既存SoundEvent IDと用途を維持する:

| SoundEvent ID | OGGファイル名 | 用途 |
|---|---|---|
| piri:notice | notice.ogg | 通常告知 |
| piri:notice_strong | notice_strong.ogg | 強告知 |
| piri:tenpai | tenpai.ogg | テンパイ音 |
| piri:bet | bet.ogg | BET受付 |
| piri:lever | lever.ogg | LEVER受付 |
| piri:stop | stop.ogg | リール停止 |
| piri:payout | payout.ogg | 払出 |
| piri:error | error.ogg | エラー |
| piri:bonus_start | bonus_start.ogg | ボーナス開始 |
| piri:bonus_end | bonus_end.ogg | ボーナス終了 |

ファイル配置・未配置時の動作は第129章に従う。
告知の発火条件や複数回再生のタイミングは既存のゲーム仕様に従う。

---

# 33. 回転開始・STOP受付

通常LEVER受付時:
1. serverがspinIdを生成
2. serverが現在の3リール表示stopIndexを`startPhase`とする
3. internalRoleとpremiumを確定
4. serverが`SPIN_START`送信時刻を`motionStartNanos=System.nanoTime()`として保持
5. clientはSPIN_START受信直後から視覚回転開始

通常motion:
- client受信後150msは表示開始待ち
- 150msから350msかけて線形加速 0→18 symbols/sec
- STOP解禁はclient受信から400ms

serverはSTOP packet受信時に132章のping補正式を使い、clientで見えていた位相を近似してpressedIndexを求める。

プレミアAだけmotion profileとSTOP解禁が25章どおり異なる。

早すぎるSTOPは`STOP_TOO_EARLY`。
拒否されたSTOPでclientSequenceは消費済みとし、playerは次sequenceで再操作する。

---

# 34. STOP animation

serverが送る:

```text
stopIndex
slip
durationMs
```

duration:

```text
durationMs = min(1200, 80 + slip * 50)
```

Fabricは指定stopIndexへアニメーションして停止。

---

# 35. BIG成立後

当選ゲーム終了後:

```text
BONUS_PENDING_BIG
```

Piri Chanceは点灯維持。

次:
1. 1BET
2. LEVER
3. 3STOP
4. 777を1LINEに揃える
5. BIG開始

ボーナス入賞ゲームは:
- totalGamesに含めない
- currentGamesに含めない
- todayDifferenceにはBET -1を含める

---

# 36. REG成立後

当選ゲーム終了後:

```text
BONUS_PENDING_REG
```

次:
1. 1BET
2. LEVER
3. 3STOP
4. 77BARを1LINEに揃える
5. REG開始

統計扱いはBIGと同じ。

---

# 37. BIG

BIG:
- 2BET
- 20ゲーム
- 毎ゲーム14 payout
- 毎ゲーム手動LEVER/STOP
- 自動消化なし

COUNT:

```text
0
14
28
...
266
280
```

払い出し後:
```text
if count > 266 => END
```

最終:
```text
280
```

BIG開始時:
- bigCount +1
- bonus history追加

BIG終了時:
- currentGames=0
- Piri Chance消灯
- bonusCount=0
- SEATED_READYへ

---

# 38. REG

REG:
- 2BET
- 8ゲーム
- 毎ゲーム14 payout

COUNT:

```text
0
14
28
42
56
70
84
98
112
```

払い出し後:
```text
if count > 98 => END
```

最終112。

REG開始時regCount+1。
終了時currentGames=0。

---

# 39. ボーナス中表示役

各BIG/REGゲームでserverが表示役を1つ選ぶ。

1,000,000 denominator:

```text
BELL   916
PIERO  916
GRAPE  848443
CHERRY 149725
TOTAL  1000000
```

全表示役payout:
```text
14
```

BELL/PIERO:
通常と同じ3図柄揃い。

GRAPE:
3GRAPE揃いだがBONUSルールで14。

CHERRY:
左角CHERRYだがBONUSルールで14。

REPLAY/MISS:
ボーナス中は存在しない。

中段CHERRY:
ボーナス中には存在しない。

---

# 40. CREDIT

範囲:
```text
0..50
```

payout:

```text
toCredit = min(payout, 50-credit)
credit += toCredit
heldMedals += payout-toCredit
```

CREDIT>50はassert violation。

---

# 41. 持ちメダル

`heldMedals`:
- long
- >=0
- 上限Long.MAX_VALUE
- 画面常時表示
- 遊技中仮想残高
- itemではない

---

# 42. CREDIT/heldMedalsのsingle source of truth

CREDITとheldMedalsは、物理メダルitemへ精算されるまで`player_sessions`だけが正である。

`player_wallet`へCREDIT/heldMedalsを保存してはいけない。
`player_wallet`が保持するのはinventoryへまだ渡せていない`pending_medals`だけ。

新規着席session:
```text
credit=0
heldMedals=0
```

以前のSUSPENDED sessionが存在するplayerは、新しい別sessionを作成できない。
以下のどちらかを先に行う:
- 元machineへresume
- `/piri recover cashout`でsession資産を物理Piri Medalへ精算

ACTIVE / SUSPENDED_GRACE / SUSPENDED_SAFEのいずれでも、sessionが存在する間はcredit/heldMedalsをsession内に保持する。

SUSPEND時にwalletへ移動しない。
server restart時にもwalletへ移動しない。

sessionをDBから削除できる条件:
```text
GameState == SEATED_READY
credit == 0
heldMedals == 0
未完cashout transactionなし
```

pending_medalsはsessionとは独立してplayer_walletに残り、`/piri recover cashout`で再配送する。

---

# 43. Vault貸出

config:
```yaml
economy:
  vault_per_medal: 20
```

貸出操作は画面の貸出ボタン左クリックとLキーの双方で同じLOAN action。

LOAN allowed GameState:
```text
SEATED_READY
REPLAY_READY
BONUS_PENDING_BIG
BONUS_PENDING_REG
BIG_READY
REG_READY
```

BETTED/SPINNING中は`INVALID_STATE`。

CREDITを50まで満たす。

```text
need = 50-credit
affordable = floor(vaultBalance/vault_per_medal)
borrow = min(need, affordable)
```

borrow>=1なら134章のVault transaction journalを使用してwithdrawする。
成功確定後credit += borrow。

Spaceで自動Vault貸出しない。

---

# 44. 物理メダル投入

投入操作は画面の投入ボタン左クリックとIキーの双方で同じINSERT_MEDALS action。

allowed GameStateは43章のLOANと同じ。

CREDIT空き分までinventory内の有効なPiri Medal tokenを消費する。

探索順:
```text
hotbar slot 0..8
main inventory slot 9..35
```

```text
need = 50-credit
insert=min(need,totalValidInventoryMedals)
```

serverは136章のtoken ledgerを検証してから消費する。
一部だけ使用するtokenは136章の規則でremainder tokenへ置換する。

heldMedalsはこのI操作では消費しない。BET時のheldMedals→CREDIT補充は131章で別に定義する。

---

# 45. 精算

精算操作は画面の精算ボタン左クリックとRキーの双方で同じCASH_OUT action。

active screenでCASH_OUTを受理するGameState:
```text
SEATED_READY
REPLAY_READY
BONUS_PENDING_BIG
BONUS_PENDING_REG
BIG_READY
REG_READY
```

BETTED/SPINNING中は`INVALID_STATE`。

精算対象:
```text
credit + heldMedals
```

精算は現在のゲーム権利を消さない。
例: BONUS_PENDINGで精算してもBONUS_PENDINGは維持。

transaction:
1. UUID `cashoutTransactionId`生成
2. SQLite `BEGIN IMMEDIATE`
3. sessionのcredit+heldMedalsをcashout_transactions(PENDING)へ記録
4. session credit=0, heldMedals=0
5. 必要な136章medal tokenを`PENDING_DELIVERY`で作成
6. COMMIT
7. token itemをinventoryへ付与
8. 実際に付与できたtokenをACTIVEへ
9. inventory容量不足分の総枚数をplayer_wallet.pending_medalsへ加算し、その未配送tokenはRETIREDへ
10. cashout transactionをCOMPLETED

地面へ自動dropしない。

同じcashoutTransactionIdの再実行は二重発行せず、未配送分だけrecoveryする。

---

# 46. 仮想500枚Piri Medal token

base item:
```text
minecraft:iron_nugget
```

ItemStack count:
```text
常に1
```

item component `minecraft:max_stack_size=1` を設定する。

display name:
```text
Piri Medal
```

PDC/custom data:
```text
piri:item_type="medal"
piri:bundle_id=<UUID>
piri:medal_amount=1..500
piri:item_version=1
```

serverはPDCだけでなく136章`medal_tokens` ledgerも検証する。

662枚精算:
```text
bundle A amount=500
bundle B amount=162
```

Fabricは通常inventory画面でも右下に`medal_amount`を描画する。
Minecraft標準stack count文字はcount=1なので非表示扱いにし、Piri表示を優先する。

---

# 47. Piri Medal inventory操作

1token最大500。ItemStack countは常に1。

左クリックでtoken同士をmerge:
```text
total = target.amount + cursor.amount
newTarget = min(500,total)
newCursor = total-newTarget
```

右クリック、cursor empty:
- `amount==1`ならno-op。token/ledger/itemを変更しない
- `amount>=2`なら以下でsplit
```text
cursor=floor(amount/2)
slot=ceil(amount/2)
```

例:
```text
1   -> no-op
2   -> slot1 / cursor1
499 -> slot250 / cursor249
```

Shift-click:
1. 移動先inventoryの有効tokenをslot index昇順で500まで埋める
2. 残りを空slotへ500以下の新tokenとして作る

以下はbundle全体をそのまま移すので許可:
- Q/drop
- number-key swap
- chest/player inventory間通常move
- hopperによる1 ItemStack単位move

以下はcancel:
- vanilla double-click collect
- drag distribute
- crafting ingredient
- anvil
- smithing
- grindstone
- furnace/smoker/blast-furnace fuel/input
- brewing ingredient/fuel

全merge/split/partial consumeは136章ledger transactionで旧bundle IDをRETIREし、新bundle IDを発行する。

`ItemStack#getAmount()!=1`のPiri MedalはINVALID_ITEMとして利用拒否。
vanilla iron_nuggetはPiri Medalではない。

---

# 48. 景品

メダル→景品は`/piri prizes` GUI。

default:

```text
小: 50枚
中: 200枚
大: 450枚
```

item:

```text
小 minecraft:gold_nugget
中 minecraft:gold_ingot
大 minecraft:gold_block
```

PDC:
```text
piri:item_type=prize_small
piri:item_type=prize_medium
piri:item_type=prize_large
piri:item_version=1
```

---

# 49. 景品GUI

景品交換GUIはBukkit/Paper 54slot Inventory GUI。
スロット本体ScreenにはInventory GUIを使用しない。

slot:
```text
20 小
22 中
24 大
31 最大交換
49 閉じる
```

通常click:
- 対象景品1個

Shift-click:
- 所持メダルで可能な対象景品の最大個数

「最大交換」:
1. 所持有効Piri Medal総数以下で、消費メダル合計が最大になる組合せ
2. 同一消費枚数なら大景品個数最大
3. 次に中景品個数最大
4. 次に小景品個数最大

DPで厳密決定。

inventory capacityは「空slot数」ではなく、既存の同一PDC景品stackの空き容量 + 空slotの64stack容量を計算する。
交換結果の全景品が収まらない場合は1個も交換しない。

メダルtoken消費は136章ledgerでatomicに行う。

---

# 50. 景品→Vault

default:
```text
small 1000
medium 4000
large 9000
```

config変更可能。

commands:
```text
/piri exchange small [count]
/piri exchange medium [count]
/piri exchange large [count]
/piri exchange all
```

count省略=1。countは1以上の整数。

`all`のinventory走査/削除順:
```text
small -> medium -> large
```

処理は134章economy transaction journalを使用する。
正常API failureなら景品を完全復元する。
hard process crash during external Vault callは134章の`REVIEW_REQUIRED`規則に従い、根拠なく再depositしない。

---

# 51. Space操作・BET資金

Spaceは現在GameStateにおける次の標準操作。

BET actionでは、先にCREDIT不足分だけheldMedalsからCREDITへ内部移動する。

```text
shortage=max(0, betCost-credit)
move=min(shortage,heldMedals)
credit += move
heldMedals -= move
```

この内部移動はtodayDifferenceへ影響しない。
移動後もcredit<betCostなら`NOT_ENOUGH_CREDIT`。
物理メダル投入/Vault貸出は自動実行しない。

通常:
```text
SEATED_READY --Space/BET3--> NORMAL_BETTED
NORMAL_BETTED --Space/LEVER--> NORMAL_SPINNING
NORMAL_SPINNING --Space--> 未停止リールをLEFT,CENTER,RIGHT優先でSTOP
```

REPLAY_READY:
```text
Space -> 無料LEVER -> NORMAL_SPINNING
```

BONUS_PENDING_BIG/REG:
```text
Space -> BET1
次Space -> LEVER
以後Space -> 未停止LEFT,CENTER,RIGHT優先STOP
```

BIG_READY/REG_READY:
```text
Space -> BET2
次Space -> LEVER
以後Space -> 未停止LEFT,CENTER,RIGHT優先STOP
```

個別STOP後にSpaceを押した場合も、未停止リールのLEFT>CENTER>RIGHT優先。

key repeat禁止。keydown edge1回=action1回。

---

# 52. 個別停止キー

default Fabric KeyBinding:

```text
LEFT  = GLFW_KEY_LEFT
CENTER= GLFW_KEY_DOWN
RIGHT = GLFW_KEY_RIGHT
```

これら3キーはFabric KeyBindingへ登録し、Minecraftのキー設定画面から変更できる。

画面停止ボタンclickも同一action。

停止済みリール操作はserver reject。

---

# 53. その他キー

```text
SPACE 標準操作
L     Vault貸出
I     メダル投入
R     精算
ESC   離席要求
```

---

# 54. UI canvas

基準:
```text
1920x1080
```

uniform scale。
aspect維持。
余白黒。

データランプ:
```text
x330 y20 w1260 h185
```

筐体:
```text
x290 y205 w1340 h835
```

Piri Chance:
```text
x350 y390 w300 h170
```

リール窓:
```text
x670 y300 w900 h390
```

reel:
```text
w270
gap45
rowHeight130
```

status:
```text
x670 y710 w900 h95
```

BET:
```text
x420 y860 w150 h100
```

LEVER:
```text
x250 y780 w130 h260
```

LEFT STOP:
```text
x720 y865 w180 h110
```

CENTER:
```text
x990 y865 w180 h110
```

RIGHT:
```text
x1260 y865 w180 h110
```

CASH OUT:
```text
x1490 y870 w100 h90
```

LOAN:
```text
x1490 y740 w100 h55
```

INSERT:
```text
x1490 y805 w100 h55
```

---

# 55. UI見た目

スロットScreen:
- Minecraft inventory slot見た目禁止
- 背景 dark red
- metallic frame
- reel背景 white
- Piri Chance左側
- controls下側
- data lamp上部

ラベル文字:
- Minecraft TextRendererを使用
- データランプ数字だけ自作7segment rendererを使用
- 外部font依存なし

図柄texture:
- linear filtering
- UI pixel snappingを避ける

---

# 56. HUDとESC

Slot Screen中非表示:
- hotbar
- crosshair
- health
- hunger
- armor
- XP
- scoreboard

chatはTでchat screenを開いた時だけ表示。

ESC:
- CLOSE_REQUESTを1回送信
- 127章のserver判定を待つ
- SESSION_ENDまたはSESSION_SUSPENDED受信後にSlot Screen close
- 2000ms返答が無い場合client画面だけcloseするが、clientは資産/状態を変更しない
- 再接続/再open時はserver PUBLIC_STATEを正とする

---

# 57. status表示

通常:

```text
CREDIT 0..50
BET 0/1/2/3
PAY 0..14
MEDALS heldMedals
```

bonus:
```text
COUNT
```

PAY:
- 次BET受付時0へ
- REPLAY時0

BET:
- game完了時0
- REPLAY_READYだけ3点灯 + REPLAY表示

---

# 58. データランプ

表示対象は「現在のbusiness period」だけ。
同じ暦日でもserver process restart後は別periodなので前periodの本日データを混ぜない。

表示:
- machine No.
- totalGames
- BIG
- REG
- currentGames
- todayMaxDifference
- bonusHistory current period直近10
- todayDifference graph
- ピリ連チャレンジ表示

7segment:
- 数字0..9を122章の矩形7本で描画

BIG label: red
REG label: blue
G: white

整数表示は符号を含む10進表記。割当矩形に収まらない時は文字/segment全体を等比縮小し、最小scale=0.45。0.45でも収まらない場合は左側からclipせず、右寄せのまま末尾桁を全て表示するためさらにscaleを下げる。省略記号/K/M表記は使用しない。

---

# 59. G数

totalGames:
- 通常LEVER受付ごと+1
- REPLAY無料ゲームも+1
- ボーナス当選ゲームも+1
- ボーナス入賞1BETゲーム含めない
- BIG/REG消化ゲーム含めない

currentGames:
- ボーナス終了時0
- 通常LEVERごと+1
- 当選ゲームの値をhistory.gamesとして保存
- ボーナス開始までは値維持

---

# 60. ピリ連

過去に当日1回以上BONUS終了済みで:

```text
currentGames <=100
```

なら表示:

```text
ピリ連チャレンジ中
```

ボーナス終了直後currentGames=0で表示。
101G目LEVER受付後非表示。

---

# 61. 差枚

todayDifferenceは台単位、プレイヤー側差枚。

加算:
- BET: -bet
- payout: +payout

含めない:
- Vault貸出
- メダル投入
- 精算
- 景品
- Vault換金

todayMaxDifference:
- day start 0
- todayDifference更新ごとmax保存
- 負しかなくても0

---

# 62. 差枚graph

点:
```text
x=totalGames
y=todayDifference
```

business period開始時:
```text
(0,0)
```

通常ゲーム完了時1点追加。
bonus終了時、同じxで1点追加。
force settlementでbonusを完了した場合も同じ規則。

DB原本は全保存。

表示最大300点。
301点以上は123章LTTB threshold=300。

Y scale:
- current business period min/max
- minimum visible range=400 medals
- range<400ならmid=(min+max)/2を中心にmid±200
- range>=400ならmin..maxに上下5% padding
- 全点同値ならその値を中心に±200

---

# 63. 台登録

Minecraft内筐体blocksはPluginが生成しない。
登録対象はプレイヤーが見ているButton block。

```text
/piri machine create
```

条件:
- OP
- player
- ray trace max5.0 blocks
- target blockが`BlockTags.BUTTONS`
- 同一world UUID + block xyzが他のnon-deleted machineに未登録

重複登録なら`LOCATION_ALREADY_REGISTERED`。

初期値:
```text
setting=1
enabled=true
autoSetting=true
last visible reel stops = 0,0,0
```

machineId:
```text
COALESCE(MAX(machine_id),0)+1
```

deleted rowもMAX対象。再利用禁止。

登録button右click時のvanilla button/redstone activationをcancelする。

---

# 64. redefine

```text
/piri machine redefine <id>
```

条件:
- OP
- target machine exists and deleted=false
- 135章machine busy=false
- raytrace max5.0 blocksのButton
- target Buttonが別non-deleted machineに登録されていない

更新:
- world UUID/name
- xyz
- facing

維持:
- id
- setting
- 全stats/history
- enabled
- autoSetting
- last visible reel stops

同じmachine自身の現在座標を再指定した場合は成功no-opとしてupdated_atだけ更新する。

---

# 65. remove

```text
/piri machine remove <id>
```

135章machine busy=trueなら拒否。

安全なSUSPENDED_SAFE sessionがmachineIdを参照していても、未完ゲームを持たないためremove可能。そのsessionは以後resume不可で`/piri recover cashout`だけ可能。

Minecraft blockは変更しない。

DB:
```text
deleted=1
enabled=0
```

stats/historyは保存。machineId再利用禁止。

---

# 66. registered Button interaction

入口はPaper `PlayerInteractEvent`のRIGHT_CLICK_BLOCKだけ。clientからOPEN_REQUEST/ADMIN_OPEN packetを送らない。

処理順:
1. blockがregistered machineか確認
2. OP + 有効なmachine keyならenabled/occupiedに関係なくAdmin Screenをread可能で開く
3. normal player flowではmachine.deleted=falseかつenabled=true必須
4. playerに既存sessionがある場合を確認

既存sessionが同machineでSUSPENDED_GRACE:
- lock ownerが本人ならexact resume

既存sessionが同machineでSUSPENDED_SAFE:
- machineが空席ならsessionをACTIVEへし、source business periodを現在periodへ更新してresume

既存sessionが別machine、または同machineが他sessionに占有:
- `RECOVERY_REQUIRED`または`MACHINE_OCCUPIED`

既存sessionなし:
- machine空席ならcredit=0/held=0の新session作成
- lock取得成功後OPEN_MACHINE

Fabric handshake無し:
```text
Piri Juggler Client Mod 1.0.0 が必要です
```

normal playerがdisabled machineを触ると`MACHINE_DISABLED`。

---

# 67. machine key

base:
```text
tripwire_hook
```

name:
```text
Piri 台鍵
```

PDC:
```text
piri:item_type=machine_key
piri:item_version=1
```

give:
```text
/piri key give [player]
```

OPのみ。

管理操作は毎回player.isOp()検証。

---

# 68. Admin Screen / AdminSession

OP + machine key interaction時、serverはmemory上に`AdminSession`を作る。

```text
adminSessionId UUID
playerUuid
machineId
lastSequence=0
expiresAt=now+300s
```

ADMIN_STATE packetでadminSessionIdを送る。
AdminSessionはDB永続化しない。server restart/5分無操作/admin screen closeで失効。

表示:
- id
- setting
- current period totalGames/BIG/REG/currentGames
- todayDifference/todayMaxDifference
- active event profile
- autoSetting
- enabled
- latest30 setting history
- busy state

変更:
- setting 1..6
- autoSetting
- enabled
- current period daily reset

135章machine busy=true:
- screen閲覧可
- すべてのmutating admin actionは`MACHINE_OCCUPIED`

manual setting:
- 即時反映
- stats resetなし

全admin packetでadminSessionId, player.isOp(), machineId, sequenceを再検証する。

---

# 69. 設定変更履歴

DBには全履歴を保存。UI最新30件。

fields:
```text
machineId
businessPeriodId nullable
time
old
new
reason
actorUuid nullable
profileName nullable
```

reason:
```text
MANUAL
SERVER_START
EVENT
```

reason規則:
- `/piri setting`: MANUAL
- startup resolved profileが`normal`かつpattern=NONEかつsourceがdefault/weekday normal: SERVER_START
- それ以外のstartup profile/pattern/special/manual-next: EVENT

同じsettingへ設定するmanual commandは成功no-opとし、履歴を追加しない。

---

# 70. Business Period

「営業日」は暦日ではなくserver process単位の`business period`として管理する。

真の新process起動ごとに:
```text
business_period_id = UUID
local_date = Asia/Tokyo yyyy-MM-dd
started_at = epoch millis
```

同じ暦日に2回再起動してもbusiness_period_idは別。

同一JVM内Plugin reloadを新periodにしてはいけない。
JVM識別は`ManagementFactory.getRuntimeMXBean().getStartTime()`を使用する。
metadataに`current_jvm_start_ms`と`current_business_period_id`を保存する。

onEnable時:
- DBのcurrent_jvm_start_ms == 現JVM start time: 既存periodを再利用、rolloverしない
- 不一致: true process startupとして82章の旧session settlement後に新periodを作成

以後「本日」「daily」は現在business periodを意味する。

---

# 71. 新Business Period開始

true process startup時、82章の旧session force settlementを完了してから新periodを開始する。

新periodごとに全non-deleted machineへ新しい`machine_period_stats` rowをINSERTする。
過去rowをUPDATEして0へ戻してはいけない。

新row初期値:
```text
totalGames=0
bigCount=0
regCount=0
currentGames=0
todayDifference=0
todayMaxDifference=0
lastBonus=null
graph first point=(0,0)
```

setting allocation:
- enabled=true && autoSetting=true: current profileで再抽選
- autoSetting=false: setting維持
- enabled=false: setting維持

player session資産/pending_medalsは変更しない。

---

# 72. イベントprofile

## normal

```text
S1 55%
S2 25%
S3 12%
S4 5%
S5 2%
S6 1%
```

weighted machine rate:
```text
99.164%
```

理論house margin:
```text
0.836%
```

guarantee:
```text
minS6=0
minS5Plus=0
```

## light

```text
S1 35%
S2 25%
S3 18%
S4 12%
S5 7%
S6 3%
```

weighted:
```text
100.430%
```

guarantee:
```text
minS6=0
minS5Plus=1
```

## strong

```text
S1 15%
S2 20%
S3 20%
S4 20%
S5 15%
S6 10%
```

weighted:
```text
102.470%
```

guarantee:
```text
minS6=1
minS5Plus=2
```

すべてこの数値はconfigキーから変更できる。

---

# 73. event schedule

default:

```yaml
MONDAY: normal
TUESDAY: normal
WEDNESDAY: normal
THURSDAY: normal
FRIDAY: normal
SATURDAY: normal
SUNDAY: normal
```

special_dates:
```yaml
{}
```

priority:
```text
manual next-start override
> special date
> weekday
> normal
```

manual:

```text
/piri event next <profile>
/piri event next clear
/piri event status
```

next overrideは次startupで1回使った後削除。

---

# 74. startup setting allocation順序

eligible:
```text
deleted=false
enabled=true
autoSetting=true
```

1 profileにつきpatternは0個または1個。

手順:
1. machineId昇順eligible list作成
2. 75-77章pattern対象と割当を先に決定
3. pattern非対象をbase profile distributionで独立抽選
4. 全eligibleを対象に`minSetting6`不足数を補正
5. 4の結果を含めて`minSetting5OrHigher`不足数を再計算して補正
6. 1 transactionでmachines.setting更新 + history追加
7. transaction成功後にmanual next-start overrideを消費

保証補正はpattern割当を含む全eligibleを数える。
不足時はsettingを「upgrade」だけする。downgrade禁止。

補正候補:
- まだこの保証処理で補正されていないeligible
- 現settingが最小の群
- event RNGで1台選択

minSetting6 -> 6。
minSetting5OrHigher -> 5。ただし既に6はcountへ含む。

eligible台数不足時は可能数までclampしWARNING。

allocation transaction失敗時はsetting/history/next override消費を全rollback。

---

# 75. pattern ALL

config:

```yaml
pattern:
  type: ALL
  setting: 6
  machine_ids: []
```

machine_ids空:
- eligible全台

非空:
- 指定eligibleだけ固定

残りprofile weight。

---

# 76. pattern SUFFIX

machineId decimal last digit。

config例:

```yaml
pattern:
  type: SUFFIX
  suffixes: [3,7]
  distribution:
    1: 0
    2: 0
    3: 10
    4: 30
    5: 35
    6: 25
```

suffix対象だけ指定distribution。
残りprofile。

---

# 77. pattern RUN

eligible machineId昇順。

run_length=3なら、昇順リスト上の連続3要素を1runと定義。
machineId数値が連番である必要はない。

config:

```yaml
pattern:
  type: RUN
  run_length: 3
  run_count: 2
  distribution:
    1: 0
    2: 0
    3: 10
    4: 30
    5: 35
    6: 25
```

非重複runをmachine RNGで選ぶ。
候補不足時は最大数だけ選びWARNING。

---

# 78. session ownership / 必須snapshot

1 playerにつき未終了sessionは最大1。
1 machineにつきACTIVE/SUSPENDED_GRACE sessionは最大1。
SUSPENDED_SAFEはmachine lockを持たない。

session snapshot最低フィールド:
```text
sessionId
playerUuid
machineId
sourceBusinessPeriodId
gameState
lifecycle
credit
heldMedals
spinId nullable
internalRole nullable
premiumType nullable
noticeState
lampOn
bonusType nullable
bonusPayoutCount
currentBet
payDisplay
displayLeftStop
displayCenterStop
displayRightStop
stoppedMask
phaseLeft
phaseCenter
phaseRight
motionProfile nullable
lastClientSequence
lastActivity
lockExpiresAt nullable
```

`display*Stop`は現在画面に見える中段index。ゲーム終了後も保持。
`stoppedMask`だけが現在spinでどのリールが停止済みかを示す。

GameStateとlifecycleを1カラムへ混在させない。

---

# 79. disconnect grace

player disconnect時:
1. 現在実行中action transactionがあれば完了まで待つ
2. current phaseをsnapshot
3. lifecycle=SUSPENDED_GRACE
4. `lockExpiresAt = now + disconnect_grace_seconds`（default60秒）
5. machine lock維持
6. DB commit

期限内に同playerがjoinし、同machineを再interaction:
- exact GameState/internalRole/stops/lamp/bonusを復元
- unstopped reelはsnapshot phaseから132章`RESUME_NORMAL` motionで再開
- premium演出/告知音は既にdelivery済みなら再生しない

期限切れ時:
- 133章force settlement
- lifecycle=SUSPENDED_SAFE
- machine lock解除

GameStateがSEATED_READYでもdisconnectは60秒grace後にSAFE化する。

---

# 80. SUSPENDED_SAFE session

SUSPENDED_SAFEは未完ゲーム/REPLAY/BONUS権利を持たない。133章が必ずSEATED_READYまでsettleしてからSAFEへ移す。

資産credit/heldMedalsはsessionに保持。
machine lockは持たない。

playerの選択:
- 元machineが存在・enabled・空席なら同machineへresumeしACTIVE化
- `/piri recover cashout`でcredit/held/pendingをPiri Medalへ精算

別machineへcredit/heldを直接持ち越してresumeしてはいけない。
別machineで遊ぶには先にrecover cashoutして物理Piri Medal化し、新machineへ投入する。

元machineがremove/disabledの場合はrecover cashoutだけ可能。

---

# 81. unresolved sessionとmachine再利用

NORMAL_BETTED/SPINNING、REPLAY_READY、BONUS_PENDING/ENTRY、BIG/REG状態のsessionを未解決のままmachine lock解除してはいけない。

理由: 同一machineのcurrentGames/bonus timelineが分岐するため。

machine lockを他playerへ渡す前に必ず:
- playerがexact resumeしてゲームを完了する
または
- 133章force settlementでSEATED_READYまで完了する

これにより同じmachineに未完timelineが複数存在することを禁止する。

---

# 82. true server process restart

新JVM processのonEnable時、new business periodやsetting再抽選より先に以下を行う。

1. 前processでACTIVEまたはSUSPENDED_GRACEだったsessionを列挙
2. 各sessionを133章force settlementし、旧`sourceBusinessPeriodId`へstatsを記録
3. lifecycle=SUSPENDED_SAFE
4. 全machine lockを解除
5. 資産はsession内保持
6. すべて成功してから70-71章のnew business periodを作成

したがってprocess restart後に未完spin/BONUSを対話的resumeすることはない。経済価値と旧period統計はforce settlementで確定済み。

既にSUSPENDED_SAFEのsessionはsettlement不要。

---

# 83. idle timeout

config default:
```text
180 seconds
```

ACTIVE sessionのlastActivityから180秒。

発火時:
1. 実行中action完了待ち
2. 133章force settlement
3. lifecycle=SUSPENDED_SAFE
4. machine lock即解除
5. SESSION_SUSPENDED(reason=IDLE_TIMEOUT)送信
6. client Screen close

idle timeout自体が十分な猶予なので追加60秒graceは付けない。

---

# 84. network envelope

channel:
```text
piri:main
```

binary envelope:
```text
magic: 4 bytes ASCII PIRI (50 49 52 49)
protocol: unsigned uint16 big-endian, value1
packetType: uint8
payloadLength: standard Minecraft non-negative VarInt
payload: UTF-8 JSON object
```

payload max32767 bytes。
`payloadLength`はJSON bytesだけの長さ。

packetTypeはbinary envelopeが唯一のpacket種別の正。JSON payloadへ`type`フィールドを重複格納しない。

---

# 85. handshake

client join後HELLO packet type1 payload:
```json
{"protocol":1,"modVersion":"1.0.0"}
```

server HELLO_ACK type101:
```json
{"protocol":1,"serverVersion":"1.0.0"}
```

判定:
- envelope protocol !=1 -> PROTOCOL_MISMATCH
- HELLO payload protocol !=1 -> PROTOCOL_MISMATCH
- modVersion文字列はログ用。protocol=1ならversion文字列が1.0.0と異なっても接続を拒否しない

HELLO成功前はslot/admin gameplay packetを無視してERROR(PROTOCOL_MISMATCH)。

---

# 86. client→server packet IDs / payload

fixed IDs:
```text
HELLO=1
RESERVED_2=2
SPACE_ACTION=3
STOP_LEFT=4
STOP_CENTER=5
STOP_RIGHT=6
LOAN=7
INSERT_MEDALS=8
CASH_OUT=9
CLOSE_REQUEST=10
RESERVED_11=11
ADMIN_SET_SETTING=12
ADMIN_SET_AUTO=13
ADMIN_SET_ENABLED=14
ADMIN_RESET_DAILY=15
```

RESERVED IDsは送信禁止。将来互換のため番号だけ予約。

Gameplay action payload共通:
```json
{
  "sessionId":"uuid",
  "machineId":1,
  "clientSequence":123
}
```

STOP packetにもstopIndex/phase/client timeを追加しない。

HELLOは85章payloadでsession fields無し。

Admin mutation payload:
```json
{
  "adminSessionId":"uuid",
  "machineId":1,
  "adminSequence":12,
  "value":6
}
```

`value`:
- SET_SETTING: int1..6
- SET_AUTO: boolean
- SET_ENABLED: boolean
- RESET_DAILY: value fieldを省略

Admin mutationにgame sessionId/clientSequenceを使用しない。

---

# 87. server→client packet IDs / payload概要

IDs:
```text
HELLO_ACK=101
OPEN_MACHINE=102
PUBLIC_STATE=103
ACTION_ACCEPTED=104
ACTION_REJECTED=105
SPIN_START=106
REEL_STOP=107
PAYOUT=108
NOTICE=109
TENPAI_SOUND=110
BONUS_START=111
BONUS_END=112
DATA_LAMP=113
CASHOUT_RESULT=114
SESSION_SUSPENDED=115
SESSION_END=116
ADMIN_STATE=117
ERROR=118
```

共通規則:
- internalRoleを送らない
- normal playerへsettingを送らない
- field欠落をclient推測で補わない

詳細payloadは89-93章および131章。

ACTION_ACCEPTED:
```json
{"clientSequence":123,"action":"SPACE_ACTION"}
```

ACTION_REJECTED:
```json
{"clientSequence":123,"errorCode":"INVALID_STATE"}
```

TENPAI_SOUND:
```json
{"spinId":"uuid"}
```

BONUS_START:
```json
{"bonusType":"BIG|REG","count":0}
```

BONUS_END:
```json
{"bonusType":"BIG|REG","finalCount":280}
```

SESSION_END:
```json
{"sessionId":"uuid"}
```

ERROR:
```json
{"errorCode":"DB_ERROR"}
```

---

# 88. sequence / idempotency

Gameplay session:
```text
lastClientSequence
```

受理条件:
```text
clientSequence > lastClientSequence
```

古い/同値:
- side effectなし
- ACTION_REJECTED(SEQUENCE_OLD)

OPEN_MACHINE/PUBLIC_STATEは:
```text
expectedNextClientSequence = lastClientSequence + 1
```
を送り、resume/new clientはこの値からsequenceを開始する。

AdminSessionは独立した`lastAdminSequence`をmemory保持する。

BET/spin/payout/cashout/economy exchangeにはserver UUID transaction idを使用。
STOPはcurrent spinIdがserver session内で一致していることをserver側で確認するが、client STOP payload自体へspinIdを要求しない。sessionのcurrent spinIdを正とする。

---

# 89. SPIN_START payload

```json
{
  "sessionId":"uuid",
  "machineId":1,
  "spinId":"uuid",
  "mode":"NORMAL|BONUS_ENTRY|BIG|REG",
  "animation":"NORMAL|REVERSE_500MS|RESUME_NORMAL",
  "startPhase":{"left":8.0,"center":3.0,"right":12.0},
  "stopEnableAfterMs":400
}
```

clientはpacket受信時刻をvisual elapsed=0とする。
serverはpacket send直前の`System.nanoTime()`をmotionStartNanosとしてmemory保持。

BIG/REG当選種類はPiri Chance点灯前に送らない。
BONUS_ENTRY modeでは既にPiri Chance点灯済みであるため、stop resultの図柄から種類が視覚的に判明する。

---

# 90. REEL_STOP payload

```json
{
  "spinId":"uuid",
  "reel":"LEFT|CENTER|RIGHT",
  "pressedIndex":8,
  "stopIndex":14,
  "slip":6,
  "durationMs":380
}
```

`pressedIndex`はserver計算値をclientへ表示同期用に返すだけ。clientからは送らない。
clientはserver stopIndexを最終表示の正とする。

---

# 91. NOTICE

```json
{
  "spinId":"...",
  "lamp":"ON",
  "pattern":"STEADY|FAST_BLINK_1S",
  "sound":"NONE|NOTICE|NOTICE_STRONG|NOTICE_X5"
}
```

---

# 92. OPEN_MACHINE / PUBLIC_STATE

OPEN_MACHINE:
```json
{
  "sessionId":"uuid",
  "machineId":12,
  "expectedNextClientSequence":1
}
```

直後にPUBLIC_STATEを送る。

PUBLIC_STATE:
```json
{
  "machineId":12,
  "sessionId":"uuid",
  "gameState":"SEATED_READY",
  "lifecycle":"ACTIVE",
  "expectedNextClientSequence":1,
  "credit":32,
  "heldMedals":442,
  "bet":0,
  "pay":0,
  "bonusCount":0,
  "lampOn":false,
  "displayStops":{"left":0,"center":0,"right":0},
  "stoppedMask":0
}
```

setting/internalRole/premium未告知情報を含めない。

---

# 93. DATA_LAMP

```json
{
  "machineId":12,
  "totalGames":3500,
  "bigCount":14,
  "regCount":11,
  "currentGames":72,
  "todayDifference":320,
  "todayMaxDifference":880,
  "piriChain":true,
  "history":[
    {"type":"BIG","games":72,"occurredAt":123}
  ],
  "graph":[
    {"game":0,"difference":0},
    {"game":1,"difference":-3}
  ]
}
```

history max10。
graph max300。

---

# 94. SQLite schema v4

DB:
```text
plugins/PiriJuggler/piri.db
```

SQLite PRAGMA:
```text
journal_mode=WAL
foreign_keys=ON
synchronous=FULL
busy_timeout=5000
```

metadata:
```sql
CREATE TABLE metadata (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
```

`metadata.schema_version`初期値`4`。

business periods:
```sql
CREATE TABLE business_periods (
  business_period_id TEXT PRIMARY KEY,
  local_date TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  jvm_start_ms INTEGER NOT NULL,
  profile_name TEXT NOT NULL,
  profile_source TEXT NOT NULL
);
```

machines:
```sql
CREATE TABLE machines (
  machine_id INTEGER PRIMARY KEY,
  world_uuid TEXT NOT NULL,
  world_name TEXT NOT NULL,
  x INTEGER NOT NULL,
  y INTEGER NOT NULL,
  z INTEGER NOT NULL,
  facing TEXT NOT NULL,
  setting INTEGER NOT NULL CHECK(setting BETWEEN 1 AND 6),
  enabled INTEGER NOT NULL DEFAULT 1,
  auto_setting INTEGER NOT NULL DEFAULT 1,
  deleted INTEGER NOT NULL DEFAULT 0,
  last_left_stop INTEGER NOT NULL DEFAULT 0 CHECK(last_left_stop BETWEEN 0 AND 20),
  last_center_stop INTEGER NOT NULL DEFAULT 0 CHECK(last_center_stop BETWEEN 0 AND 20),
  last_right_stop INTEGER NOT NULL DEFAULT 0 CHECK(last_right_stop BETWEEN 0 AND 20),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX ux_machine_location_active
ON machines(world_uuid,x,y,z) WHERE deleted=0;
```

period stats:
```sql
CREATE TABLE machine_period_stats (
  machine_id INTEGER NOT NULL,
  business_period_id TEXT NOT NULL,
  total_games INTEGER NOT NULL DEFAULT 0,
  big_count INTEGER NOT NULL DEFAULT 0,
  reg_count INTEGER NOT NULL DEFAULT 0,
  current_games INTEGER NOT NULL DEFAULT 0,
  today_difference INTEGER NOT NULL DEFAULT 0,
  today_max_difference INTEGER NOT NULL DEFAULT 0,
  last_bonus_type TEXT,
  last_bonus_at INTEGER,
  PRIMARY KEY(machine_id,business_period_id),
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(business_period_id) REFERENCES business_periods(business_period_id)
);
```

bonus history:
```sql
CREATE TABLE bonus_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  machine_id INTEGER NOT NULL,
  business_period_id TEXT NOT NULL,
  bonus_type TEXT NOT NULL CHECK(bonus_type IN ('BIG','REG')),
  games INTEGER NOT NULL,
  occurred_at INTEGER NOT NULL,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(business_period_id) REFERENCES business_periods(business_period_id)
);
```

graph:
```sql
CREATE TABLE graph_points (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  machine_id INTEGER NOT NULL,
  business_period_id TEXT NOT NULL,
  game INTEGER NOT NULL,
  difference INTEGER NOT NULL,
  occurred_at INTEGER NOT NULL,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(business_period_id) REFERENCES business_periods(business_period_id)
);
```

setting history:
```sql
CREATE TABLE setting_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  machine_id INTEGER NOT NULL,
  business_period_id TEXT,
  changed_at INTEGER NOT NULL,
  old_setting INTEGER NOT NULL,
  new_setting INTEGER NOT NULL,
  reason TEXT NOT NULL,
  actor_uuid TEXT,
  profile_name TEXT,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id)
);
```

player wallet only pending delivery:
```sql
CREATE TABLE player_wallet (
  player_uuid TEXT PRIMARY KEY,
  pending_medals INTEGER NOT NULL DEFAULT 0 CHECK(pending_medals >= 0),
  updated_at INTEGER NOT NULL
);
```

sessions:
```sql
CREATE TABLE player_sessions (
  session_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL UNIQUE,
  machine_id INTEGER NOT NULL,
  source_business_period_id TEXT NOT NULL,
  game_state TEXT NOT NULL,
  lifecycle TEXT NOT NULL,
  credit INTEGER NOT NULL CHECK(credit BETWEEN 0 AND 50),
  held_medals INTEGER NOT NULL CHECK(held_medals >= 0),
  spin_id TEXT,
  internal_role TEXT,
  premium_type TEXT,
  notice_state TEXT NOT NULL DEFAULT 'NONE',
  lamp_on INTEGER NOT NULL DEFAULT 0,
  bonus_type TEXT,
  bonus_payout_count INTEGER NOT NULL DEFAULT 0,
  current_bet INTEGER NOT NULL DEFAULT 0,
  pay_display INTEGER NOT NULL DEFAULT 0,
  display_left_stop INTEGER NOT NULL DEFAULT 0,
  display_center_stop INTEGER NOT NULL DEFAULT 0,
  display_right_stop INTEGER NOT NULL DEFAULT 0,
  stopped_mask INTEGER NOT NULL DEFAULT 0,
  phase_left REAL NOT NULL DEFAULT 0,
  phase_center REAL NOT NULL DEFAULT 0,
  phase_right REAL NOT NULL DEFAULT 0,
  motion_profile TEXT,
  last_client_sequence INTEGER NOT NULL DEFAULT 0,
  last_activity INTEGER NOT NULL,
  lock_expires_at INTEGER,
  FOREIGN KEY(machine_id) REFERENCES machines(machine_id),
  FOREIGN KEY(source_business_period_id) REFERENCES business_periods(business_period_id)
);
```

cashout:
```sql
CREATE TABLE cashout_transactions (
  transaction_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  amount INTEGER NOT NULL CHECK(amount >= 0),
  delivered_amount INTEGER NOT NULL DEFAULT 0,
  pending_amount INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK(status IN ('PENDING','COMPLETED')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

medal token ledger:
```sql
CREATE TABLE medal_tokens (
  bundle_id TEXT PRIMARY KEY,
  amount INTEGER NOT NULL CHECK(amount BETWEEN 1 AND 500),
  state TEXT NOT NULL CHECK(state IN ('PENDING_DELIVERY','ACTIVE','RETIRED')),
  source_transaction_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

medal inventory mutation journal:
```sql
CREATE TABLE medal_inventory_transactions (
  transaction_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  operation TEXT NOT NULL,
  before_bundle_json TEXT NOT NULL,
  after_bundle_json TEXT NOT NULL,
  container_snapshot_json TEXT,
  status TEXT NOT NULL CHECK(status IN ('PREPARED','LEDGER_COMMITTED','APPLIED','ROLLED_BACK','REVIEW_REQUIRED')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

economy journal:
```sql
CREATE TABLE economy_transactions (
  transaction_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  operation TEXT NOT NULL,
  vault_amount REAL NOT NULL,
  item_snapshot_json TEXT,
  balance_before REAL,
  status TEXT NOT NULL CHECK(status IN ('PREPARED','CALL_STARTED','APPLIED','ROLLED_BACK','REVIEW_REQUIRED')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

v3以前の開発DBをv4へ自動migrationする必要はない。正式リリース前の開発段階なのでschema_version!=4の既存DBは起動を拒否し、管理者へDBバックアップ後の削除を案内する。

---

# 95. DB transaction境界

SQLite書込はsingle dedicated DB executor。
ゲームstate mutationはPaper main threadで行うが、永続化成功前に次actionを受けない。

即時transaction:
- session create/resume/suspend/settle/end
- BET
- LEVER internal role snapshot
- each STOP snapshot
- payout
- bonus established/start/end
- medal insert/split/merge/cashout
- prize exchange
- setting/admin changes
- event allocation

Vault external callは134章journalを必須使用。

graph pointは対応ゲーム結果transactionと同時insert。
lastActivityだけ最大5秒batch update可能。

server normal shutdown:
- 新action受付停止
- in-flight DB transaction完了待ち
- lastActivity flush
- SQLite checkpoint
- shutdown

---

# 96. Vault unavailable

Vault provider無し:
- Plugin起動成功
- メダルitem投入遊技可
- loan disabled
- prize→Vault disabled
- UI Economy unavailable

---

# 97. commands

OP:

```text
/piri machine create
/piri machine redefine <id>
/piri machine remove <id>
/piri machine info <id>
/piri machine list
/piri key give [player]
/piri setting <machineId> <1-6>
/piri reset daily <machineId>
/piri reset daily all
/piri event status
/piri event next <profile>
/piri event next clear
/piri simulator <setting> <games>
```

player:

```text
/piri prizes
/piri exchange small [count]
/piri exchange medium [count]
/piri exchange large [count]
/piri exchange all
/piri recover status
/piri recover cashout
```

---

# 98. `/piri recover`

```text
/piri recover status
/piri recover cashout
```

status表示:
- session有無
- machineId
- lifecycle
- gameState
- session credit
- session heldMedals
- player_wallet.pending_medals
- resume可能か

cashout:
1. SUSPENDED_GRACEなら、先に133章force settlementしてSUSPENDED_SAFE化しlock解除
2. SUSPENDED_SAFEのsession credit+heldMedalsを45章cashout logicで物理token化
3. player_wallet.pending_medalsも配送可能分をtoken化
4. inventory不足分はpending_medalsのまま
5. sessionがSEATED_READYかつcredit=0/held=0ならsession削除

ACTIVE sessionに対するcommand recover cashoutは拒否。画面Rを使う。

未完ゲーム権利を残したまま資産だけ抜く方式は廃止する。force settlement後にのみrecover cashout可能。

---

# 99. stats attribution

ACTIVE/SUSPENDED_GRACE sessionは`sourceBusinessPeriodId`を持つ。

通常playは現在business periodをsourceにする。
Grace中exact resumeも同じsource period。

133章force settlementで発生する:
- normal spin completion
- auto replay spin
- bonus completion
- payouts/bets
はsession.sourceBusinessPeriodIdへ記録する。

force settlement完了後SUSPENDED_SAFEになったsessionには未完gameが無い。
そのsessionを元machineへresumeする時、sourceBusinessPeriodIdを現在business periodへ更新する。

これにより新business periodへ旧未完spinのstatsを混ぜない。

---

# 100. setting利用タイミング

通常LEVER ON受付時にmachine current settingを読み、その場で内部役を確定する。
NORMAL_BETTEDの時点ではまだ内部役を抽選しない。

135章machine busy中はadmin setting変更を拒否するため、BET後LEVER前にsettingが変わらない。

133章force settlementがNORMAL_BETTEDを処理する場合も、machine current settingを用いてLEVER相当抽選を行う。
true process restartでは82章settlementをsetting再抽選より先に実行するため、前営業periodのsettingを使用できる。

既にNORMAL_SPINNINGなら保存済internalRoleを使用し再抽選しない。

---

# 101. bonus告知後の種類秘匿

Piri Chance点灯時点ではFabricへBIG/REG種類を送らない。

ボーナス入賞ゲームのstop solverはserver内部bonusTypeに従う。

第3stopで実際に:
- 777ならclientはBIGだと視覚認識
- 77BARならREGと視覚認識

BONUS_START packetはその時点でtypeを送る。

---

# 102. Admin setting非公開

normal player packet:
- setting無し

client log:
- setting無し

debug modeでもFabricへ内部role/setting送信禁止。

server DEBUGログにはinternalRoleを出せる。

---

# 103. config変更可能項目

config変更可能:
- economy.vault_per_medal
- role weight 6設定分
- premium BIG chance
- premium A-F weights
- event profiles/schedule/special dates/guarantees/pattern
- prize medal_cost/vault_value
- idle_timeout_seconds
- disconnect_grace_seconds
- sound volumes

変更不可（protocol/code version更新が必要）:
- 21 reel arrays
- 5 paylines
- symbol enum
- CREDIT max50
- normal BET3
- bonus entry BET1
- bonus BET2
- BIG threshold266 / gross payout280
- REG threshold98 / gross payout112
- max virtual medal token500
- network protocol1
- packet IDs
- DB schema v4

---

# 104. config validation

各setting:
- 12 role weights
- sum exactly1,000,000,000
- each>=0

premium:
- denominator>=1
- 0<=big_chance_weight<=denominator
- A-F weight each>=0
- `(A+C+D+E+F) > 0` を必須（BIG/PIERO_BIG eligible set）
- `(A+B+C+D+E+F) > 0` を必須（CHERRY_BIG eligible set）

profile:
- setting1..6 weight each>=0
- sum exactly100
- guarantees>=0
- pattern schema/type validation

prize:
- medal_cost>=1
- vault_value>=0

economy.vault_per_medal>=1
idle_timeout_seconds>=30
disconnect_grace_seconds>=0
sound volume 0.0..2.0

invalid config:
- plugin自体はload
- gameplay disabled
- admin/info commandだけ利用可
- SEVEREに全validation errorを列挙

---

# 105. Simulator

async。

```text
/piri simulator <setting> <games>
```

games:
```text
1..100000000
```

fixed test seed:
```text
0x504952494A554747
```

command runtime seed:
- SecureRandom seed

出力:
- normal spins
- paid normal spins
- replay
- grape/cherry/bell/piero
- BIG
- REG
- total bet
- total payout
- payout %
- net

simulationは20章weightと実BET/bonusルールを使用。

---

# 106. 必須自動テスト

最低限:

1. 3 reel arrays exact21 + official fixed sequence lock
2. 5 paylines exact
3. all9261 precompute
4. strict candidate count minimums from12章
5. GRAPE/REPLAY candidateにcorner CHERRYが混在しない
6. every role candidate exists
7. every stop order + every pressedIndex sequence can complete target role
8. CHERRY/MISS tie-break deterministic
9. MISS no payout/bonus/cherry
10. middle CHERRY only premiumB
11. premiumF second stop actual tenpai=false for BIG/CHERRY_BIG/PIERO_BIG eligible visuals
12. normal payouts
13. REPLAY next BET0
14. heldMedals auto refill BET
15. overlap weights exact once
16. every setting sum1e9
17. BIG entry777 / REG entry77BAR
18. BIG20G gross280
19. REG8G gross112
20. premium REG impossible
21. premiumB CHERRY_BIG only +2
22. premium eligibility config validation
23. credit<=50 + overflow
24. loan/insert allowed-state validation
25. medal token 662=>500+162
26. token merge/split/partial consume ledger
27. duplicate token cannot double-spend
28. prize DP + inventory stack capacity
29. Vault normal failure rollback
30. economy journal hard-crash state becomes REVIEW_REQUIRED
31. duplicate cashout no duplicate token value
32. gameplay/admin sequences independent
33. resume sequence base
34. machine lock and duplicate location
35. redefine preserves data
36. deleted ID no reuse
37. lifecycle preserves gameState
38. disconnect grace exact resume
39. grace expiry force settlement before unlock
40. idle force settlement before unlock
41. process restart settlement before new period allocation
42. same-date two restarts create two businessPeriod IDs
43. no timeline branching per machine
44. safe session cannot enter different machine without cashout
45. event pattern+guarantee order
46. next override consumed only after successful allocation commit
47. setting reason rules
48. DATA_LAMP current period only
49. LTTB duplicate-x behavior
50. client packet contains no internalRole/setting
51. admin session validation
52. motion formulas + ping compensation bounds
53. deterministic audio noise seed/assets SHA
54. simulator fixed seed deterministic
55. 10M simulation payout within ±0.20 percentage point target
56. runtime acceptance evidence schema parser tests

Phaseごとの実Minecraft runtime acceptanceは137章と`RUNTIME_ACCEPTANCE.md`で別途必須。

---

# 107. logging

INFO:
- machine create/redefine/remove
- manual setting
- startup profile
- allocation summary
- seat/suspend/resume/end
- cashout
- prize exchange
- Vault exchange

DEBUG:
- spinId
- machineId
- internalRole
- stopIndex
- slip
- payout

seedはDEBUGにも出さない。

---

# 108. security

- custom PDCをserverだけで検証
- client amount無視
- client stopIndex無視
- admin毎回isOp
- sessionId/player/machine整合
- sequence整合
- action state整合
- Vault result検証
- SQLite transaction
- cashout idempotency

---

# 109. UI素材生成

生成対象:

symbol PNG（各256x256 ARGB transparent）:
```text
seven.png
bar.png
grape.png
cherry.png
bell.png
piero.png
replay.png
```

lamp PNG（各512x256 ARGB transparent）:
```text
piri_chance_off.png
piri_chance_on.png
```

生成方法:
- Java2D `AssetGenerator`のみ
- 外部画像/Python/Pillow/SVG禁止
- RenderingHints antialias/value quality
- external/system fontを使用しない
- 文字は138章の自作bitmap glyph rendererだけ使用
- 生成済みPNGをrepositoryへcommit
- test resourcesのSHA-256 manifestと一致必須

Minecraft block texture流用禁止。

---

# 110. 台世界側見た目

Pluginは筐体blocksを変更しない。

ユーザーが現在使っている:
- bookshelf系ブロック上部
- red block下部
- 前面stone button
の筐体をそのまま登録対象として使用する。

登録対象は前面Button座標のみ。

見た目変更後は`redefine`で再登録。

---

# 111. データランプ履歴

直近10。

表示順:
- 左から新しい→古い

各:
```text
B/R
当選G数
```

timestampはtooltipのみ。

---

# 112. mouse

Cursor visible。

hover:
- brightness +15%

left clickだけ受付。

同一buttonを100ms以内連続click:
- client側debounce
- server sequence/stateでも防止

---

# 113. server tick

ゲームlogicはmain threadでstate mutate。

SQLite I/O:
- dedicated single-thread DB executor
- transaction completionが必要な金銭操作はcallbackまで次action BUSY

Simulator:
- separate worker executor

Fabric packet send:
- main threadへmarshal

---

# 114. BUSY

金銭transaction処理中、またはSTOP処理中:
- session.actionBusy=true

追加操作:
```text
ACTION_REJECTED BUSY
```

queueしない。

処理終了時false。

---

# 115. Error codes

固定:

```text
BUSY
INVALID_STATE
NOT_ENOUGH_CREDIT
ECONOMY_UNAVAILABLE
NOT_ENOUGH_VAULT
NOT_ENOUGH_MEDALS
INVENTORY_FULL
MACHINE_OCCUPIED
MACHINE_DISABLED
SESSION_MISMATCH
SEQUENCE_OLD
SPIN_MISMATCH
STOP_TOO_EARLY
ALREADY_STOPPED
NOT_OP
INVALID_ITEM
TOKEN_REVIEW_REQUIRED
DB_ERROR
VAULT_ERROR
PROTOCOL_MISMATCH
```

Fabricは日本語メッセージへmapping。

---

# 116. 完成条件

以下をすべて実動:

1. Paper起動
2. Fabric client接続
3. handshake
4. machine create
5. button interaction
6. Slot Screen
7. Vault貸出
8. Medal投入
9. Space BET
10. Space LEVER
11. Space順押し
12. 個別逆押し/ハサミ
13. 固定リール出目
14. 必ず役取得
15. CREDIT50 overflow
16. heldMedals
17. REPLAY
18. Piri Chance
19. 6 premium
20. 1BET bonus entry
21. 777 BIG
22. 77BAR REG
23. BIG20G/280
24. REG8G/112
25. data lamp
26. graph
27. Piri chain
28. cashout
29. 500 bundle
30. merge/split
31. prize GUI
32. Vault exchange
33. admin key
34. event profiles
35. restart setting allocation
36. disconnect suspend
37. same machine resume
38. no asset loss
39. simulator
40. tests pass

---

# 117. 実装順

Phase 1:
- Gradle
- common protocol
- Paper/Fabric handshake

Phase 2:
- SQLite schema
- machine registration
- interaction/session

Phase 3:
- Fabric Slot Screen
- input
- packet state

Phase 4:
- reel constants
- 9261 precompute
- stop solver
- tests

Phase 5:
- role RNG
- normal payouts
- replay
- machine rate simulator

Phase 6:
- Piri Chance
- premium
- audio
- bonus entry
- BIG/REG

Phase 7:
- Vault
- CREDIT
- held medals
- virtual medal item

Phase 8:
- prizes
- exchange
- transaction hardening

Phase 9:
- data lamp
- graph
- piri chain

Phase 10:
- admin
- setting allocation
- events

Phase 11:
- suspend/restart recovery
- final tests

各Phase:
```text
./gradlew test
./gradlew build
```
を成功させてから次へ。

---

# 118. Codexへの最終命令

この仕様書には実装判断を残していません。

この文書の外からゲーム仕様を補完しないでください。
「ファンキージャグラー2では通常こうだから」という理由で本書を書き換えないでください。

不具合によって本書内の2項目が技術的に同時成立しないことを発見した場合だけ作業を止め、
以下の形式で報告してください。

```text
CONFLICT
Section A:
Section B:
Technical reason:
Minimal change required:
```

好みや実装都合で質問しないでください。
通常は本書どおりに実装を進め、116章の40条件をすべて満たして完成としてください。


---

# 119. UIカラー・形状の完全固定値

以下のARGB/RGB値を使用する。Codexの判断で別色へ変更しない。

```text
SCREEN_OUTSIDE        #000000
CABINET_BG            #5B1016
CABINET_BG_DARK       #31070B
CABINET_EDGE          #B68A42
CABINET_EDGE_LIGHT    #E4C174
REEL_BG               #F4F1E8
REEL_SEPARATOR        #8A8175
BUTTON_RED            #B61E29
BUTTON_RED_HOVER      #D72B39
BUTTON_RED_PRESSED    #7D1119
BUTTON_METAL          #B9BCC2
BUTTON_METAL_DARK     #666A72
DISPLAY_BG            #090B0E
DISPLAY_WHITE         #F2F2F2
DISPLAY_BIG           #FF4242
DISPLAY_REG           #3A78FF
DISPLAY_GREEN         #58E36A
GRAPH_LINE            #F4F4F4
GRAPH_ZERO            #777777
PIRI_OFF_BG           #3A090D
PIRI_OFF_CHILI        #6D141B
PIRI_ON_BG            #7D0810
PIRI_ON_CHILI         #FF2035
PIRI_ON_EDGE          #FFF2D1
TEXT_MAIN             #F6F1E7
TEXT_SHADOW           #190406
```

Panel border:
- outer radius 18px
- inner radius 12px
- border width 4px

Button:
- corner radius 18px
- border 4px `BUTTON_METAL_DARK`
- normal fill `BUTTON_RED`
- hover fill `BUTTON_RED_HOVER`
- pressed fill `BUTTON_RED_PRESSED`

Stop button center circles:
- diameter 88px
- center horizontally and vertically within each 180x110 hitbox
- outer ring 6px `BUTTON_METAL`
- inner fill `BUTTON_RED`

LEVER:
- stem width 22px
- stem fill `BUTTON_METAL_DARK`
- knob diameter 70px
- knob fill `BUTTON_RED`
- visual click animation: knob y +18px for 90ms then return in 90ms

---

# 120. Piri Chance画像の完全描画仕様

texture size:
```text
512x256
```

`piri_chance_off.png` / `piri_chance_on.png`。

共通:
- rounded rect x4 y4 w504 h248 radius32
- outer stroke8 `CABINET_EDGE`
- inner panel x16 y16 w480 h224 radius24

OFF:
- panel `PIRI_OFF_BG`
- chili `PIRI_OFF_CHILI`
- text #7B4045

ON:
- panel `PIRI_ON_BG`
- chili `PIRI_ON_CHILI`
- chili edge6 `PIRI_ON_EDGE`
- text `PIRI_ON_EDGE`

唐辛子pathはv3記載座標を維持:
- start(110,70)
- cubic(205,65),(236,123),(212,168)
- cubic(187,212),(121,207),(82,169)
- cubic(122,173),(157,158),(167,130)
- cubic(174,108),(153,87),(110,70)
- stem width15 #3BAA4A path(108,72)->(87,47)->(65,55)

textは138章bitmap glyphで`PIRI CHANCE`を描く。
- centered horizontally
- glyph logical height35px
- glyph scale5
- word gap20px
- baseline相当center y145

external/Minecraft fontをbuild-time texture生成に使用しない。

ON glowはFabric render-time:
- original behind copies alpha0.18
- offsets(-4,0),(4,0),(0,4)
- blur shader無し

---

# 121. 図柄PNG完全生成仕様

全symbol 256x256 transparent ARGB Java2D。

共通:
- outline width8
- subject x24..232,y24..232

SEVEN/GRAPE/CHERRY/BELL/PIERO/REPLAYの形状・座標・色はv3の同章に定義されていた値を`docs/spec-lock.json`へ完全転記し、その値を正とする。

BAR:
- rounded rect x38 y65 w180 h42
- x38 y112 w180 h42
- x38 y159 w180 h42
- radius18
- fill #171717
- stroke #070707 width6
- 各rect中央へ138章bitmap glyphで`PIRI`
- glyph color #F0444C
- external/system/Minecraft fontをAssetGeneratorで使用しない

注意: `docs/spec-lock.json`には以下のshape dataを必ず含める。
- SEVEN polygon/gold slash/highlight
- GRAPE 11 circle centers/leaf/stem
- CHERRY circles/stems/leaf
- BELL path/clapper
- PIERO face/hat/nose/eyes/mouth/collar
- REPLAY body/head/muzzle/eye/nose/ears/tail

これらshape dataはv3から値を変更しない。
AssetGenerator testはspec-lock shape dataから描画してSHA manifestと照合する。

---

# 122. 7segment描画仕様

各digit logical size:
```text
40x72
```

segment thickness:
```text
8
```

segments:
```text
A x=8  y=0  w=24 h=8
B x=32 y=8  w=8  h=24
C x=32 y=40 w=8  h=24
D x=8  y=64 w=24 h=8
E x=0  y=40 w=8  h=24
F x=0  y=8  w=8  h=24
G x=8  y=32 w=24 h=8
```

mapping:
```text
0 ABCDEF
1 BC
2 ABDEG
3 ABCDG
4 BCFG
5 ACDFG
6 ACDEFG
7 ABC
8 ABCDEFG
9 ABCDFG
```

inactive segments alpha=0.08 of active color。

---

# 123. Graph LTTB固定実装

300点以下:
- 全点そのまま

301点以上:
- Largest-Triangle-Three-Buckets
- threshold=300
- 最初の点と最後の点を必ず保持
- bucket calculationはSveinn Steinarsson LTTB standard formulaをそのままJava実装
- NaN禁止
- xが同一のbonus vertical pointsも別点として保持
- triangle area tieは元配列indexが小さい点を採用

外部LTTB libraryは追加しない。

---

# 124. full config.yml必須キー

既存v3 config内容を維持し、さらに以下を必須追加する。

```yaml
protocol_version: 1

economy:
  vault_per_medal: 20

game:
  idle_timeout_seconds: 180
  disconnect_grace_seconds: 60

sound:
  notice_volume: 1.0
  notice_strong_volume: 1.0
  tenpai_volume: 0.9
  bet_volume: 0.65
  lever_volume: 0.75
  stop_volume: 0.65
  payout_volume: 0.70
  error_volume: 0.70
  bonus_start_volume: 0.90
  bonus_end_volume: 0.90

premium:
  big_chance_weight: 50000
  denominator: 1000000
  weights:
    reverse: 1
    middle_cherry: 1
    sound_first_peka: 1
    strong_after_peka: 1
    five_notice_blink: 1
    fake_tenpai: 1

prizes:
  small: {medal_cost: 50, vault_value: 1000}
  medium: {medal_cost: 200, vault_value: 4000}
  large: {medal_cost: 450, vault_value: 9000}

events:
  timezone: "Asia/Tokyo"
  default_profile: normal
  weekday:
    MONDAY: normal
    TUESDAY: normal
    WEDNESDAY: normal
    THURSDAY: normal
    FRIDAY: normal
    SATURDAY: normal
    SUNDAY: normal
  special_dates: {}
  profiles:
    normal:
      distribution: {1: 55, 2: 25, 3: 12, 4: 5, 5: 2, 6: 1}
      min_setting_6: 0
      min_setting_5_plus: 0
      pattern: {type: NONE}
    light:
      distribution: {1: 35, 2: 25, 3: 18, 4: 12, 5: 7, 6: 3}
      min_setting_6: 0
      min_setting_5_plus: 1
      pattern: {type: NONE}
    strong:
      distribution: {1: 15, 2: 20, 3: 20, 4: 20, 5: 15, 6: 10}
      min_setting_6: 1
      min_setting_5_plus: 2
      pattern: {type: NONE}
```

20章全role weightを`probabilities.settings.1..6`へlower_snake_case keyで完全転記する。

---

# 125. 実装選択を禁止する事項

以下には複数案を作らない。

```text
RNG                  SplittableRandom + SecureRandom master seed
Graph downsampling   LTTB
Prize GUI            Bukkit 54slot Inventory
UI labels            Minecraft TextRenderer
数字                  custom 7segment renderer
Asset generation     Java2D AssetGenerator
Database             SQLite JDBC
DB executor          single dedicated thread
Simulator executor   fixed thread pool size 1
Network payload      custom payload + JSON envelope
Medal representation ItemStack count1 + PDC amount1..500
```

代替実装を同時に用意しない。

---

# 126. 曖昧性監査ルール

実装開始前にCodex自身で本書を機械的に読み、仕様値を`docs/spec-lock.json`へ抽出する。

最低限:
- reel arrays
- paylines
- payouts
- setting weights
- bonus thresholds
- UI coordinates
- packet ids
- DB schema version
- event distributions
- medal/prize values

`SpecLockTest`でコード定数とspec-lock.jsonが一致することを検証する。

本書の文章から「好みで選択する」実装箇所を新規作成しない。



# 127. CLOSE_REQUEST完全状態遷移

CLOSE_REQUEST時、実行中actionがあれば完了後に処理する。

A. `SEATED_READY`かつcredit=0かつheldMedals=0:
1. session delete
2. machine lock release
3. SESSION_END
4. screen close

B. `SEATED_READY`かつcredit>0またはheldMedals>0:
1. lifecycle=SUSPENDED_SAFE
2. 資産はsession内保持
3. machine lock即release
4. SESSION_SUSPENDED(reason=USER_CLOSE_SAFE)
5. screen close

C. それ以外のGameState:
1. current phase/state snapshot
2. lifecycle=SUSPENDED_GRACE
3. lockExpiresAt=now+disconnect_grace_seconds
4. machine lock維持
5. SESSION_SUSPENDED(reason=USER_CLOSE_GRACE)
6. screen close

BET済み料金は返金しない。
REPLAY/BONUS権利を消去しない。
Grace期限内resumeならそのまま続行。
期限切れは133章force settlement後にlock release。

---

# 128. プレミアと機械割

premiumはbase内部役payoutを変更しない。

```text
BIG base payout=0
CHERRY_BIG base payout=2
PIERO_BIG base payout=14
```

A/C/D/E/Fは全BIG系でbase payout維持。
BはCHERRY_BIG専用で2枚。

したがってpremium有無で22章理論機械割は変化しない。
Simulatorも同一規則。

「6種類weight=1」はeligibility内のweightであり、全BIG成立に対する実発生率が6種同一という意味ではない。

---

# 129. ユーザー提供OGGの読み込み・再生

実音源はユーザーが別途提供する。実装側は所定ファイル名のOGGを受け入れて再生する。

- 配置先: `fabric/src/main/resources/assets/piri/sounds/`。
- ユーザー向け配置先としてリポジトリ直下の `user-audio/` も用意する。同名のOGGはこちらを優先し、build時に上記リソースパスへ取り込む。
- `build-jars.bat` または `gradlew.bat test packagePiriJars` で自由に再ビルドでき、配布JARを `dist/` へ出力する。配置・ビルド手順を日本語で説明する。
- 第32章のファイル名とSoundEvent IDを一対一で対応させる。
- Fabric側は音声リソース読み込み、SoundEvent登録、再生処理を実装する。
- `assets/piri/sounds.json`で既存IDを対応するOGGへ解決する。
- ユーザー提供OGGを配置してbuildするか、同じリソースパスのresource packを読み込むと再生できる。
- 未配置の音は無音として扱い、他の操作・描画を継続する。
- 音源未配置の開発段階でもbuild/testが成功すること。
- Runtime Acceptanceは登録・読み込み・再生経路と未配置時の動作を確認し、音源そのものの品質・内容を完成条件にしない。
- 音声生成コード、波形合成、エンコーダによる音源生成、代替音源生成を実装しない。

---

# 130. 実装判断と仕様判断の境界

Codexが自由に決めてよいのは外部挙動を変えないprivate implementation detailだけ:
- class/package分割
- private helper名
- collection実装
- repository class分割
- test class名
- JSON library等、wire bytes/fieldsを変えない内部library選択

Codexが決めてはいけない:
- 数値/確率/payout
- reel/payline/stop rules
- GameState/SessionLifecycle transition
- asset/save/recovery semantics
- UI座標/色/asset shape
- input key
- packet IDs/JSON fields
- DB schema
- event allocation
- machine busy/admin rules
- runtime acceptance pass条件

本書にない外部仕様が不可欠な場合だけCONFLICT。

---

---

# 131. GameState完全遷移表

通常:
```text
SEATED_READY --BET3--> NORMAL_BETTED
NORMAL_BETTED --LEVER(draw role,+totalGames,+currentGames)--> NORMAL_SPINNING
NORMAL_SPINNING --3rd STOP/result MISS/GRAPE/CHERRY/BELL/PIERO--> SEATED_READY
NORMAL_SPINNING --3rd STOP/result REPLAY--> REPLAY_READY
NORMAL_SPINNING --3rd STOP/result BIG系--> BONUS_PENDING_BIG
NORMAL_SPINNING --3rd STOP/result REG系--> BONUS_PENDING_REG
REPLAY_READY --free LEVER(draw role,+totalGames,+currentGames)--> NORMAL_SPINNING
```

BONUS entry:
```text
BONUS_PENDING_BIG --BET1--> BONUS_ENTRY_BETTED_BIG
BONUS_ENTRY_BETTED_BIG --LEVER--> BONUS_ENTRY_SPINNING_BIG
BONUS_ENTRY_SPINNING_BIG --3rd STOP 777--> BIG_READY

BONUS_PENDING_REG --BET1--> BONUS_ENTRY_BETTED_REG
BONUS_ENTRY_BETTED_REG --LEVER--> BONUS_ENTRY_SPINNING_REG
BONUS_ENTRY_SPINNING_REG --3rd STOP 77BAR--> REG_READY
```

BIG:
```text
BIG_READY --BET2--> BIG_BETTED
BIG_BETTED --LEVER--> BIG_SPINNING
BIG_SPINNING --3rd STOP/payout14/count<=266--> BIG_READY
BIG_SPINNING --3rd STOP/payout14/count>266--> SEATED_READY
```

REG同様、threshold98。

BET成功時:
- currentBet=betCost
- payDisplay=0
- todayDifference-=betCost

3rd STOP payout transaction完了時:
- payDisplay=payout
- credit/held update
- todayDifference+=payout
- currentBet=0

BIG/REG entry 3rd STOP時:
- BIG/REG count/historyをこの時点で追加
- bonusPayoutCount=0
- Piri Chanceはbonus中点灯維持

BIG/REG終了時:
- Piri Chance OFF
- currentGames=0
- payDisplay=14を次BETまで維持
- 1500msだけclientにBONUS_END overlay (`BIG BONUS COUNT 280` / `REG BONUS COUNT 112`) を表示
- overlay中もserver GameStateはSEATED_READY。clientは1500ms中のBET inputを送らず無視する

GameState遷移とDB更新は1つのserver action transactionとして扱う。

---

# 132. server-authoritative reel motion / ping補正

clientはSPIN_START受信時をvisual elapsed=0とする。
serverはSPIN_START送信直前`motionStartNanos`を保持する。

STOP受信時:
```text
rawElapsedMs=(System.nanoTime()-motionStartNanos)/1_000_000.0
rttCompMs=clamp(Player.getPing(),0,250)
effectiveElapsedMs=max(0,rawElapsedMs-rttCompMs)
```

これは「server送信→client受信」の片道遅延と「client入力→server受信」の片道遅延の合計をRTTで近似してclientが見たelapsedへ戻すため。

NORMAL profile position delta(symbols):
```text
e = effectiveElapsedSec
if e <= 0.150: d=0
if 0.150 < e < 0.500:
  a=e-0.150
  d=0.5*(18/0.350)*a*a
if e >=0.500:
  d=3.15 + 18*(e-0.500)
phase=(startPhase+d) mod21
```

NORMAL STOP enabled client elapsed400ms。server validationは:
```text
effectiveElapsedMs >= 350
```
50ms tolerance。

REVERSE_500MS:
```text
0<=e<0.500: d=-12*e
0.500<=e<0.800:
  a=e-0.500
  d=-6 + 0.5*(18/0.300)*a*a
 e>=0.800:
  d=-3.3 + 18*(e-0.800)
```
STOP server threshold750ms(client spec800ms with50ms tolerance)。

RESUME_NORMAL:
- startPhase = suspend snapshot phase
- packet receive直後から18 symbols/sec定速
- client STOP enabled200ms
- server threshold150ms
- premium animation/soundを再実行しない

各unstopped reelは同じmotion elapsedを使う。stopped reelはdisplayStop固定。

clientが見たphaseとserver計算がnetwork jitterで1コマ以上ずれても、成立役取得可否は変わらない。slip/出目だけ変化する。

---

# 133. machine lock解放前Force Settlement

目的: 未完sessionを残したまま他playerへmachineを渡してtimelineを分岐させない。

適用:
- disconnect grace expiry
- idle timeout
- true process restart
- `/piri recover cashout`がSUSPENDED_GRACEへ実行された時

共通:
- visual animation/soundは実行しない
- server game logicだけでSEATED_READYまで完了
- original sourceBusinessPeriodIdへstats記録
- moneyは最終的なBET/payoutと同じnetになる

処理:

`SEATED_READY`: 何もしない。

`NORMAL_BETTED`:
- 既に3BET済み
- machine current settingでLEVER相当抽選
- totalGames/currentGames +1
- 下記NORMAL_SPINNING処理へ

`NORMAL_SPINNING`:
- 保存済internalRoleを固定リールsolverで自動complete
- 未払い小役payoutを適用
- nonbonus/nonreplayならSEATED_READY
- REPLAYならREPLAY_READY処理へ
- BIG/REG系ならtrigger payout後BONUS_PENDING処理へ

`REPLAY_READY`:
- 0BETでLEVER抽選、totalGames/currentGames +1
- REPLAYなら同処理を繰り返す
- それ以外はNORMAL_SPINNINGとしてsettle
- 乱数上連続REPLAY回数に人工上限を設けない

`BONUS_PENDING_BIG`:
- 未払いentry BET1 + BIG bonus BET40とgross payout280を一括net
- session assetへ`+239`
- todayDifference `+239`
- bigCount+1/history追加
- currentGames=0
- graph point追加
- SEATED_READY

`BONUS_PENDING_REG`:
- `+95`（112-1-16）
- REG count/history
- currentGames=0

`BONUS_ENTRY_BETTED_BIG` / `BONUS_ENTRY_SPINNING_BIG`:
- entry BET1は既に差枚/credit反映済み
- remaining bonus net `+240`（280-40）
- BIG count/history +1
- currentGames=0

REG同state:
- `+96`（112-16）

`BIG_READY`:
```text
remainingGames=(280-bonusPayoutCount)/14
settlement=remainingGames*12
```
BIGは既にcount/history済みなので再加算しない。

`BIG_BETTED` / `BIG_SPINNING`:
現在bonus gameの2BETは既に支払済み。
```text
remainingGames=(280-bonusPayoutCount)/14
settlement=remainingGames*14 - (remainingGames-1)*2
```

REGは280を112へ置換。

settlement正数を通常payout helperでCREDIT優先→heldMedalsへ加える。
force settlementでは将来BETをgross payoutからnetしているためCREDIT残高不足を理由に停止しない。

完了:
- currentBet=0
- payDisplay=0
- internalRole/premium/spinId/bonusType clear
- stoppedMask=0
- lampOff
- GameState=SEATED_READY
- lifecycle=SUSPENDED_SAFE
- lock解除

各session settlementはDB transactionでidempotentにする。transaction ID=`SETTLE:<sessionId>:<snapshotVersion>`。

---

# 134. Vault外部transactionの保証範囲

Vault Economy APIとSQLite/Minecraft inventoryは単一ACID transactionを共有できない。
したがって「OS/JVMがVault API callのまさに途中でhard crashしても数学的exactly-once」を要求しない。

保証するもの:
- client disconnect
- normal server stop/restart
- plugin exception before/after API resultが返ったケース
- Vault APIが明示failureを返したケース
- duplicate client packet
ではno double/no loss。

Vault operation:
1. economy_transactions PREPARED commit（amount/item snapshot/balanceBefore）
2. status CALL_STARTED commit
3. Vault withdraw/deposit callを同期実行
4. return failure -> compensating item/state restore -> ROLLED_BACK
5. return success -> local asset/state commit -> APPLIED

startupでCALL_STARTEDが残っている場合、Vaultは一般的transaction lookup APIを持たないため自動再実行禁止。
status=REVIEW_REQUIREDへ変え、対象playerの新しいVault/Piri economy operationを一時拒否し、SEVERE logへtransactionId/operation/expected amount/balanceBefore/current balanceを出す。

管理者が状態を確認するまで推測で再withdraw/depositしない。

この限定はVault cross-system crash windowだけ。Piri内部SQLite assetは失わない。

---

# 135. machine busy定義 / 管理操作

machine busy=true:
- ACTIVE sessionがlock owner
- SUSPENDED_GRACE sessionがlock owner
- action/force settlementが進行中

SUSPENDED_SAFEはbusy=false。

busy=true中は以下を全拒否:
- setting change
- autoSetting change
- enabled change
- redefine
- remove
- current period reset

`/piri reset daily all`は対象non-deleted machineにbusyが1台でもあればcommand全体を拒否し、1台もresetしない。

個別resetは現在business period rowだけを0へし、同period bonus_history/graph_pointsを削除してgraph(0,0)再作成。setting/history/player assetは変更しない。

---

# 136. Medal token ledger規則

有効Piri Medal item条件:
1. item_type=medal
2. item_version=1
3. ItemStack count=1
4. bundle_id UUID parse可
5. 1<=medal_amount<=500
6. medal_tokensに同bundle_id state=ACTIVEが存在
7. DB amount == item medal_amount

条件違反はINVALID_ITEM。自動的に価値を復元/推測しない。

発行:
- DBにPENDING_DELIVERY token
- item付与成功後ACTIVE

full consume:
- ACTIVE->RETIRED
- item remove

partial consume old amount A, use U:
- old token RETIRED
- remainder A-U>0なら新UUID ACTIVE tokenを作り、same slot itemを新ID/amountへ置換

merge/split:
- 関係旧token全部RETIRED
- 結果各bundleへ新UUID ACTIVE token
- 1 DB transactionでledger更新してからmain-thread inventory mutation

inventory mutationに失敗した場合は同action内でledgerをold token ACTIVEへrollbackして結果token RETIREDへする。

同bundle_idのduplicate itemが複数存在しても、最初に成功したconsume/transformでold tokenがRETIREDになるため以降のcopyは利用不能。これによりduplicate value spendを防ぐ。

bundle移動（player/chest/drop/hopper）だけではledger stateを変更しない。

## split/merge/partial consumeのprocess-crash対策

SQLite ledgerとMinecraft inventory/chest保存は同一ACID transactionにはできないため、値を黙って消失・複製させないよう`medal_inventory_transactions`を必ず使用する。

変形操作:
1. 対象旧bundle IDs、結果新bundle IDs、amount、player UUID、開いているcontainer identity/slot indexを`PREPARED`で保存
2. 同一SQLite transactionで旧tokenをRETIRED、結果tokenをACTIVEとして作成し、journal=`LEDGER_COMMITTED`
3. main threadでinventory/containerを結果itemへ置換
4. 置換成功後journal=`APPLIED`
5. 通常のmutation失敗ならinventoryをbefore snapshotへ戻し、ledgerも旧ACTIVE/新RETIREDへrollbackしjournal=`ROLLED_BACK`

hard process crash後、`LEDGER_COMMITTED`が残っていた場合:
- 関係playerが次回joinした時、または関係containerが次回開かれた時に、before/after bundle IDの存在を検査する
- before側だけ存在: ledgerをbefore ACTIVE / after RETIREDへrollbackし`ROLLED_BACK`
- after側だけ存在: after ACTIVE / before RETIREDを確認して`APPLIED`
- beforeとafterの双方が存在: **片方を推測で有効化しない**。総価値が1回分を超えないよう全関係tokenを一時利用不能にしjournal=`REVIEW_REQUIRED`、SEVEREログ
- どちらも存在しない: journal=`REVIEW_REQUIRED`。before/after snapshotを保持し、値を削除しない

`REVIEW_REQUIRED`対象bundleはINSERT/PRIZE交換/merge/split/consumeを拒否し`INVALID_ITEM`ではなく`TOKEN_REVIEW_REQUIRED`を返す。
管理者がjournalを確認できるconsole logへtransactionId/bundle IDs/amountを出す。自動的に価値を二重発行しない。

この規則により通常failure/clean shutdownではatomicにrollbackし、hard crashの判定不能windowでは二重支給・黙った消失の代わりに明示的REVIEW_REQUIREDへ停止する。これは134章のVault hard-crash方針と同じ境界である。

---

# 137. Phase Runtime Acceptance必須

Unit Test/Gradle buildだけでPhase COMPLETEにしてはいけない。
全Phaseはrepository rootの`RUNTIME_ACCEPTANCE.md`に定義された「実際のPaper server + 実際のFabric client」runtime testを成功させる。

Runtime evidence:
```text
runtime-evidence/PHASE_XX/
  server.log
  client.log
  result.json
  screenshots/   # UIを持つPhase
  REPORT.md
```

REPORT.mdに:
- exact commands
- server/client versions
- scenario steps
- PASS/FAIL
- log evidence path
を記録。

実Clientを環境上起動できない場合は`ゲーム上でのテストは未実施`のままCOMPLETEにしてはいけない。
対象PhaseをBLOCKEDにし`RUNTIME_ENVIRONMENT_UNAVAILABLE`を記録する。

Phase1をv3 workflowで既にCOMPLETEにしているrepoは`MIGRATION_FROM_V3.md`のPhase1 retrofit runtime acceptanceをPhase2再開前に実行する。

---

# 138. deterministic bitmap glyph renderer

AssetGenerator内文字はOS fontを使わない。
5x7 bitmap glyphを整数rectで描画する。

必要glyph:
```text
P
11110
10001
10001
11110
10000
10000
10000

I
11111
00100
00100
00100
00100
00100
11111

R
11110
10001
10001
11110
10100
10010
10001

C
01111
10000
10000
10000
10000
10000
01111

H
10001
10001
10001
11111
10001
10001
10001

A
01110
10001
10001
11111
10001
10001
10001

N
10001
11001
10101
10011
10001
10001
10001

E
11111
10000
10000
11110
10000
10000
11111
```

spaceは5列all0。

`PIRI`と`PIRI CHANCE`だけをこのglyph setで生成する。
1 bit=filled rectangle。antialiasを使わずpixel exact。
letter gap=1 logical column、word gap=4 logical columns。

これによりOS/JDK font差によるasset hash差を排除する。
