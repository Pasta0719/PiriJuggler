# PiriJuggler Codex Workflow v4 AUDITED

音源を差し替えてJARを作成する場合は **`BUILD_JARS.md`** を読んでください。
`user-audio` に必要なOGGだけを置き、`build-jars.bat` を実行すると `dist` に配布JARができます。音源未配置でもビルドできます。

このリポジトリの再開入口は [CODEX_START.md](CODEX_START.md)。続いて [SESSION_HANDOFF.md](SESSION_HANDOFF.md)、[docs/POST_PHASE05_HOTFIX.md](docs/POST_PHASE05_HOTFIX.md)、[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)、[SPEC_CONFLICT.md](SPEC_CONFLICT.md) を確認します。Phase01–05はCOMPLETE、Phase06–11はNOT_STARTEDです。2026-09-14のPhase05後hotfix（STOP表示方向/速度連続性、正式リーチ目）はsource/testへ反映済みですが、hotfix後のローカルtest/build/実Minecraft再検証はまだです。

このリポジトリには `v4 migration applied` が記録済みです。既存Phase01実装を引き継いで移行済みなので、移行やテンプレートの上書きを繰り返しません。未移行の別v3 repoに限り `MIGRATION_FROM_V3.md` を使用し、実装も状態ファイルもない新規repoに限り `IMPLEMENTATION_STATUS_TEMPLATE.md` をコピーします。

Codexへ毎回送る文:
```text
このリポジトリの CODEX_START.md を読んで、その指示に従ってください。
```

Unit test/buildだけでなく、全Phaseで実Minecraft Runtime Acceptanceが必須。
