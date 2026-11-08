# HYPER_DOG — リール配列 v2（2026-11-08 ユーザー承認変更）

**Current authoritative 21×3 strip design.** [旧v1は履歴として保存](REEL_DESIGN_V1.md)。既存機種の停止制御は変更しない。仕様正本の詳細： [REEL_STRIPS_V2.json](REEL_STRIPS_V2.json)。

## ユーザー指定の優先事項

1. RED7（水色）・BAR（紫）・RAINBOW7（虹）は**各リール1個ずつ**、**7コマずつ均等**に0/7/14配置。各21コマの特殊図柄は計3個。
2. **5ライン上の特殊図柄3連（混合含む）を、成立していない役では全面拒否。** 例：RED7/BAR/RAINBOW7、BAR/RED7/BAR、RED7/RED7/BARもリーチ目として禁止。正規成立役の出目**1ラインのみ**を例外とする。
3. 左リールのチェリー**中段停止禁止**。弱・強チェリーとも左チェリーは上段または下段。弱チェリー＝上段または下段の左・中横2連（右は非チェリー）、強チェリー＝5ラインのいずれかでチェリー3個、左だけは上段か下段。
4. **BAR成立時だけ**左チェリー中段停止を許可。BARの3つ揃いは中段固定をやめ、5ラインどこでも可。たとえば中段停止位置 (6,6,6) は左チェリー中段・BAR下段揃い。
5. 通常役は0～4コマ、レア・BAR・AT/CZの確定引込みは0～20コマ。AT赤7告知とCZ虹7成功は中段3連。CHANCEとSTRONG_CHANCEは中段指定形を維持。

## 新物理配列

| コマ | 左 | 中 | 右 |
| ---: | --- | --- | --- |
| 00 | RED7 | RED7 | RED7 |
| 01 | GRAPE | REPLAY | GRAPE |
| 02 | REPLAY | GRAPE | REPLAY |
| 03 | WATERMELON | WATERMELON | WATERMELON |
| 04 | GRAPE | GRAPE | REPLAY |
| 05 | REPLAY | REPLAY | GRAPE |
| 06 | CHERRY | WATERMELON | WATERMELON |
| 07 | BAR | BAR | BAR |
| 08 | GRAPE | REPLAY | GRAPE |
| 09 | REPLAY | GRAPE | REPLAY |
| 10 | CHERRY | WATERMELON | WATERMELON |
| 11 | GRAPE | GRAPE | REPLAY |
| 12 | REPLAY | REPLAY | GRAPE |
| 13 | WATERMELON | CHERRY | CHERRY |
| 14 | RAINBOW7 | RAINBOW7 | RAINBOW7 |
| 15 | GRAPE | GRAPE | REPLAY |
| 16 | REPLAY | REPLAY | GRAPE |
| 17 | CHERRY | CHERRY | CHERRY |
| 18 | GRAPE | GRAPE | REPLAY |
| 19 | REPLAY | REPLAY | GRAPE |
| 20 | WATERMELON | CHERRY | CHERRY |

各リール：RED7×1 / BAR×1 / RAINBOW7×1 / GRAPE×6 / REPLAY×6 / CHERRY×3 / WATERMELON×3。

## 代表停止形（中段インデックス）

| 役 | 停止位置 | 表示 |
| --- | --- | --- |
| CHANCE | (0,7,0) | 中段 RED7/BAR/RED7 |
| STRONG_CHANCE | (0,7,14) | 中段 RED7/BAR/RAINBOW7 |
| BAR | (7,7,7) | 中段 BAR/BAR/BAR |
| BAR 左チェリー例外 | (6,6,6) | 下段 BAR/BAR/BAR＋左中段チェリー |
| AT_ENTRY_RED7 | (0,0,0) | 中段 RED7/RED7/RED7 |
| CZ_WIN | (14,14,14) | 中段 RAINBOW7/RAINBOW7/RAINBOW7 |

BAR下段の例外は、別の役を加算する意味ではない。サーバーの抽選役がBARのときのみ停止可能。

## 全9,261最終停止形の合法候補数

| 内部停止役 | 合法停止形 |
| --- | ---: |
| MISS | 5,716 |
| REPLAY | 706 |
| GRAPE | 780 |
| WATERMELON | 119 |
| WEAK_CHERRY | 195 |
| STRONG_CHERRY | 99 |
| CHANCE | 1 |
| STRONG_CHANCE | 1 |
| BAR | 5 |
| AT_ENTRY_RED7 | 1 |
| CZ_WIN | 1 |
| CZ_LOSE | 18 |

数値は**抽選確率ではない**。コード側は最終候補を前計算し、押し順・押下位置が後からどのように来ても完成可能な停止のみ選ぶ。正常形・偽リーチ目・左チェリー中段禁止・BAR例外を9,261停止形と全666,792操作履歴の検証で照合する。

## Phase 02の改訂経緯

v1版の[実Paper/Fabric 57ラウンド受入](PHASE_02_RUNTIME_ACCEPTANCE.md)は**旧配列v1の実績**。v2はユーザー要求による承認変更であり、新配列の再試験は [実機受入 #42464236837](https://github.com/Pasta0719/PiriJuggler/actions/runs/42464236837) で18/18 PASS。過去の証跡を削除・書換えず、v2用の新ランを記録する。Phase03は未着手。
