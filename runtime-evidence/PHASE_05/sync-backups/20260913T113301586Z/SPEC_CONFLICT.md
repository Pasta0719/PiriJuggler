# SPEC_CONFLICT — 解消済み

現在の単語間隔は第120章・第138章とも20px（4列×5px）。下記は修正前の指摘履歴であり、解消内容は末尾に記録する。

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
