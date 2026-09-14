# Phase 01 foundation — v4

製品仕様は `SPEC.md`。移行経緯は `docs/V4_MIGRATION.md`、実Minecraft受入結果は
`runtime-evidence/PHASE_01/REPORT.md` を参照する。

## Build

Java 21 / Gradle Wrapper 8.14.3 / Fabric Loom 1.10.5。
Minecraft/Paper 1.21、Loader 0.16.14、Fabric API 0.102.0+1.21、Yarn 1.21+build.9。

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
```

本番jarは各moduleの `build/libs/piri-juggler-{common|paper|fabric}-1.0.0.jar`。
Paper/Fabricにはcommonを同梱。Gson 2.10.1はplatform提供、SnakeYAML 2.2はPaper提供。
テスト専用moduleは `-PruntimeAcceptance=true` 指定時だけ有効。本番jarには含まれない。

## Protocol / configuration

- `piri:main` payload本体はPIRI envelopeそのもの。追加配列長prefixなし。
- packet種別はenvelopeのみ。HELLO JSONは `protocol` と `modVersion`、ACKは `protocol` と `serverVersion`。
- HELLO成功前は利用不可。不一致はERROR/PROTOCOL_MISMATCH。切断で互換状態を消去。
- 古い/同値sequenceは無副作用でACTION_REJECTED/SEQUENCE_OLD。BUSY中はqueueせず拒否。
- STOP照合IDはserver側の値。clientへspinIdを要求しない。
- configは第124章全文と第20章全weights。実ファイルの欠損をデフォルト補完で隠さず検証する。
- v4のpremium eligible合計・可変denominator・idle>=30・grace>=0・音量0..2を検証。
- invalid configはSEVEREで列挙し、pluginをloadしたまま `canUseSlot(UUID)` が利用を拒否する。
- spec-lockはv4の37章とschemaVersion=4を記録。Phase 01ではschema操作を行わない。

## Executors / actions

`MainThread` で状態アクセスを検査する。`TaskExecutors` は専用single-thread DB executorと
別のfixed-size-1 simulator executorを所有し、成功・失敗のcallbackをmain threadへ戻す。
`ActionGate` はsessionごとに保存済みlastClientSequenceで初期化する。
金銭transactionまたはSTOP開始時にBUSY leaseを取得し、main-thread callbackのfinallyで閉じる。

## Runtime

```powershell
.\gradlew.bat -PruntimeAcceptance=true :runtime-test-client:remapJar :runtime-test-paper:jar --console=plain
python runtime-test-support/run_phase01.py
```

事前に公式Paper 1.21-130 jarを `runtime-evidence/PHASE_01/work/downloads/` へ配置し、
SHA-256を照合する。取得元・hash・実行条件は `docs/V4_MIGRATION.md` に記載。
ランナーは実Paperと実Fabricをlocalhostだけで起動し、正常接続とprotocol不一致を別プロセスで検証する。
結果・バージョン・build hash・実コマンド・ログ・画像をevidenceに保持する。

台登録、DB、UI、リール、抽選、Vault、景品は後続Phaseの範囲。
