# v4 migration preflight

旧実装: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow`

移行先: `C:\Users\PC_user\Documents\PiriJuggler_Codex_Workflow_v4_AUDITED`

v4配布フォルダには仕様ファイルだけがあったため、旧実装のcommon・paper・fabric・Gradle・docsと
`IMPLEMENTATION_STATUS.md`を引き継いだ。テンプレートによる履歴上書きやPhase 01の再実装はしていない。
旧リポジトリは保存し、v4側で差分を修正した。

## Phase 01の変更

- HELLO/HELLO_ACK JSONから`type`を除去。packet種別はenvelopeのIDに一本化。
- RESERVED IDの送信を拒否。
- protocol不一致・handshake前の操作に`ERROR {errorCode: PROTOCOL_MISMATCH}`を返す。
- 古い／同値sequenceは状態を変更せず`ACTION_REJECTED(SEQUENCE_OLD)`。STOPの照合IDはserver側のものと明記。
- `TOKEN_REVIEW_REQUIRED`と日本語メッセージを追加。
- 第104章に従い、可変denominator、premium B以外のeligible合計、idle>=30、grace>=0、音量0..2を検証。
- 初期configへ`disconnect_grace_seconds: 60`を追加し、第124章から再抽出。
- spec-lockをv4の37章、全33 packet IDs、全20 ErrorCode、DB schema version 4へ更新。DB実装はこのrunで行わない。
- 旧テストの対象ケースを維持し、v4で明示的に変わった期待値を修正。新境界ケースを追加。

## 実Minecraft検証

`runtime-test-support`配下にPaper observerとFabric automation helperを分離した。
`-PruntimeAcceptance=true`指定時だけGradleに含まれ、本番jarへは入らない。
テストクライアントは本番ビルドのFabric jarをLoomのruntime dependencyとして読み込む。
テスト専用Mixinで2回目のHELLOだけprotocol 2に変更し、本番送信経路・受信経路・利用可否判定を検証する。

実行コマンド:

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat -PruntimeAcceptance=true :runtime-test-client:remapJar :runtime-test-paper:jar --console=plain
python runtime-test-support/run_phase01.py
```

Paper 1.21 build 130は公式配布APIから取得し、SHA-256
`ab9bb1afc3cea6978a0c03ce8448aa654fe8a9c4dddf341e7cbda1b0edaa73f5`を照合する。
実行にはデスクトップのOpenGL環境が必要。serverは127.0.0.1:25585だけにbindし、
`online-mode=false`、テストユーザー`PiriRuntimeTest`を使用する。通常のMinecraft profileを変更しない。
隔離したserverの初回起動にはMinecraft EULAの受諾設定を使用する。

最終結果は`runtime-evidence/PHASE_01/REPORT.md`、`result.json`、両側log、screenshotsを参照。
失敗した準備実行のログも`attempts/`へ残す。

移行完了はunit/integration・build・実runtimeすべてPASS後にのみ記録する。
このrunではPhase 02の実装を開始しない。
