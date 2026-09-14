# Phase05 preflight — BLOCKED

記録日時（UTC）: 2026-09-13T11:32:04.984991+00:00

## Phase05 preflight — 未解消: BONUS_TYPE_PUBLIC_STATE

CONFLICT
Section A:
  SPEC.md 第18章はGameStateを固定し、BONUS_PENDING_BIG/REGと
  BONUS_ENTRY_BETTED_BIG/REG、BONUS_ENTRY_SPINNING_BIG/REGを定義する。
  第131章は当選ゲーム完了からこれらへ遷移することを要求する。
  第92章のPUBLIC_STATEにはgameStateが必須フィールドとして存在する。
Section B:
  SPEC.md 第101章はPiri Chance点灯時点でFabricへBIG/REG種類を送ることを禁止し、
  入賞ゲームの第3停止でBONUS_STARTによって初めて種類を送る。
Technical reason:
  現行Session.publicState()はstate().name()を送信する。
  当選ゲームの次のPUBLIC_STATEにBONUS_PENDING_BIGまたはREGが入るため、
  internalRole/bonusTypeフィールドを除外してもBIG/REGを識別できる。
  入賞前のBETTED/SPINNINGでも同じ問題が起きる。
  公開用状態名と内部状態の対応規則はSPEC全体に定義がない。
  独自の状態名追加・値の置換・gameState省略は通信仕様の変更になるため、
  第118/130章に従って独断で実装しない。
Minimal change required:
  内部GameStateとDB保存値を維持したまま、第18/92/101章に公開状態の変換を明記する。
  BONUS_PENDING_BIG/REG -> BONUS_PENDING
  BONUS_ENTRY_BETTED_BIG/REG -> BONUS_ENTRY_BETTED
  BONUS_ENTRY_SPINNING_BIG/REG -> BONUS_ENTRY_SPINNING
  それ以外の公開状態は内部GameState名を使用する。
  BIG/REGの区別は入賞ゲーム第3停止のBONUS_STARTから公開する。

これは公開状態の外部仕様が不足していることによる停止であり、内部クラス構成などの実装都合による質問ではない。
第18/92章が公開状態も内部GameStateと完全同一の値域とする意図なら第101章と両立しない。公開専用の値域を認める意図なら、その変換を仕様に追加すれば解消する。

`runtime-evidence/PHASE_05/bonus-public-state.patch` は提案のみ。SPEC.mdへは未適用。
Phase01–04の実装・完了状態・JAR・証跡は保持。Phase05の製品実装、Gradle test/build、実Minecraft受入は未実施。Phase06は未着手。

## 確認済み

- CODEX_START / migration適用記録 / Phase05指示書 / 指定26章 / Runtime Acceptanceを確認。
- 関連章7/23/24/35–42/78/86/87/92/94/95/97/99/101/102/107/118/125–127/130/133/135を追加確認。
- SPEC全体をgameState / PUBLIC_STATE / BONUS_PENDING / 秘匿等で検索し、公開変換の定義がないことを確認。
- 現行Session.publicState()のstate().name()送信と6種類の状態名を照合。result.jsonの例は静的解析に基づく説明であり、実機実行結果ではない。
- 設定別理論機械割: {"1": 97.8000028096489, "2": 99.4000017757693, "3": 101.00000299085666, "4": 103.40000602191101, "5": 105.99998825417367, "6": 111.40000098688927}

## 停止根拠

SPEC第130章: 「本書にない外部仕様が不可欠な場合だけCONFLICT。」
SPEC第118章: CONFLICT形式で報告すること。CODEX_START: SPEC内の真正面の矛盾の場合に停止。

## 未実施

Phase05の製品実装、10M fixed-seed test、Gradle test/build、実Paper/Fabricの100通常ゲーム・Simulator受入は未実施。COMPLETEではない。

公開状態名と内部状態の対応が仕様に確定された後、Phase05のみ再開する。
