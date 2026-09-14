# SPEC_CONFLICT — 解消済み

## 現在の解消状況 — 2026-09-14確認

既知の未解消CONFLICTは0件、承認待ちは0件。Phase01–05はCOMPLETE、Phase06–11はNOT_STARTED。以下の未適用patch・停止時の記述は履歴であり、現在のBLOCKED理由ではない。再開時の全体情報は [SESSION_HANDOFF.md](SESSION_HANDOFF.md) を参照する。

| 事項 | 現在の確定内容 | 反映・検証 |
|---|---|---|
| v3→v4 migration | 既存Phase01を維持してpreflight完了。`v4 migration applied` 記録済み | `docs/V4_MIGRATION.md`、`runtime-evidence/PHASE_01/REPORT.md` |
| Phase03 SPEC_CONFLICT_WORD_GAP | 解消済み。第120章の20pxを正とし、第138章を4列×5pxへ修正 | SPEC第120/138章、`docs/spec-lock.json`、Phase03 COMPLETE |
| ユーザー提供音源 | 生成要求を撤回。既存10 ID/ファイル名でユーザーOGGを受け入れる。未配置可 | SPEC第32/106/129章、`BUILD_JARS.md`、`docs/USER_AUDIO.md` |
| Phase05 BONUS_TYPE_PUBLIC_STATE | 解消済み。内部状態を維持し、入賞前3組を明示的に公開用状態へ変換 | SPEC第92/101章、`PublicGameState`、`Session.publicGameState()`、Phase05 COMPLETE |
| 回転方向変更 | 通常は下向き・index減少、逆回転の最初500msは上向き・index増加 | SPEC第5/25/33/34/90/132章、`PHASE_05_REEL_REGRESSION` |
| BAR揃い変更 | 単独BIG/REG共通の0枚確定目。点灯・種類秘匿。MISSの全5ラインで禁止。REGの77BAR維持 | SPEC第9–14/30/92章、`PHASE_05_BAR_BIG`、`PHASE_05_BAR_REG` |
| Piri Medal束上限 | 2026-09-14ユーザー指示で500枚上限・500+残り分割を廃止。物理Piri Medal 1個が任意枚数を保持し、清算は原則1トークン。既存複数トークンは合算可能 | `MedalToken`、`EconomyStore`、`MedalMergeCommand`。Phase07 runtime acceptanceで要確認 |

Phase03/05ともBLOCKED→IN_PROGRESSへの復帰と、その後の全完了条件PASSによるCOMPLETEまで実施済み。回転方向・BAR揃いは追加仕様変更として反映済みで、未解消CONFLICTではない。

Phase05のBONUS_TYPE_PUBLIC_STATEもユーザー承認で解消済み（末尾参照）。

単語間隔とユーザー提供音源方式の問題は解消済み。以下のPhase03記録は履歴として保持する。

## Phase03 preflight — 当時のBLOCKED記録

CODEX_START.md、IMPLEMENTATION_STATUS.md、PHASE_03_CLIENT_UI.md、指定SPEC章、RUNTIME_ACCEPTANCE.mdを確認した。Phase01/02はCOMPLETE、次の対象はPhase03。

```text
CONFLICT
Section A:
  SPEC.md 第120章、4094–4096行
  glyph logical height35px
  glyph scale5
  word gap20px
Section B:
  SPEC.md 第138章、4882行
  letter gap=1 logical column、word gap=3 logical columns。
Technical reason:
  第120章はPIRI CHANCEを第138章のbitmap glyphで描くことを指定している。
  scale5では3 logical columnsは15pxとなり、第120章の20pxと一致しない。
  同一画像の同一単語間隔を15pxと20pxに同時にはできない。
  第130章はUI数値・asset shapeの変更をCodexの裁量から除外している。
  優先する値を決める規定は見つからなかった。
Minimal change required:
  第120章の20pxを維持する場合、第138章のword gapだけを
  3 logical columnsから4 logical columnsへ変更する。
  4 × 5 = 20px。PIRIには単語間がないため、BARのPIRI文字には影響しない。
```

提案差分は `word-gap.patch` に保存した。SPEC.mdには適用していない。

停止根拠: CODEX_START.mdの「SPEC内の真正面の矛盾を発見した場合だけCONFLICT形式で停止する」、SPEC.md第118章の矛盾報告形式、および第130章のUI数値・asset shapeの裁量禁止。

Phase03の本番実装・アセット生成・test/build・Runtime Acceptanceは未実施。Phase01/02の実装、完了状態、既存証跡は保持した。Phase04には進んでいない。

この指摘はPhase03開始時に検出した矛盾の記録であり、残りの実装が完了したことを示すものではない。仕様の修正方針が確定した後、Phase03を再開する。

## 解消済み — ユーザー承認による修正

第120章の単語間隔20pxを正とするユーザー指示を受け、第138章のword gapを3 logical columnsから4 logical columnsへ修正した。4 columns × 5px = 20pxとなり、第120章と一致する。上記CONFLICTは解消済み。Phase03をIN_PROGRESSへ戻して再開し、次Phaseには進まない。
## 解消済み — ユーザー指定の音源提供方式へ変更

第32章・第129章の音源生成、波形・周波数・長さ、Vorbis encoder、生成済みOGG commitの要求を削除。実音源はユーザーが提供し、既存SoundEvent ID/ファイル名で読み込み・再生する。今回追加した生成コード・生成タスク・生成済み音源を削除し、音源未配置のbuild/test/runtimeを許容する。Phase03の画像SHAと実画面・入力の受入条件は維持する。


## Phase05 preflight — 当時のBLOCKED記録: BONUS_TYPE_PUBLIC_STATE

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
  BIG/REGの区別は入賞ゲーム第3STOPのBONUS_STARTから公開する。

これは公開状態の外部仕様が不足していることによる停止であり、内部クラス構成などの実装都合による質問ではない。
第18/92章が公開状態も内部GameStateと完全同一の値域とする意図なら第101章と両立しない。公開専用の値域を認める意図なら、その変換を仕様に追加すれば解消する。

`runtime-evidence/PHASE_05/bonus-public-state.patch` は提案のみ。SPEC.mdへは未適用。
Phase01–04の実装・完了状態・JAR・証跡は保持。Phase05の製品実装、Gradle test/build、実Minecraft受入は未実施。Phase06は未着手。


## 解消済み — Phase05 BONUS_TYPE_PUBLIC_STATE

ユーザーの明示的承認に基づき、第92章へ入賞前3種類の公開状態mappingを追記。内部GameState・DB値・第18/131章の遷移を変更せず、種類が判明する入賞第3STOP以降はBIG/REG状態を公開する。第101章を優先し、専用PublicGameStateで通信時だけ変換する。Phase05をIN_PROGRESSへ戻した。以前のBLOCKED記録と未適用提案は履歴として保存する。
