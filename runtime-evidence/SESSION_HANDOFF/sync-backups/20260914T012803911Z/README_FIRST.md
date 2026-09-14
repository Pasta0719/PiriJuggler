# PiriJuggler Codex Workflow v4 AUDITED

音源を差し替えてJARを作成する場合は **`BUILD_JARS.md`** を読んでください。
`user-audio` に必要なOGGだけを置き、`build-jars.bat` を実行すると `dist` に配布JARができます。音源未配置でもビルドできます。

既存v3 repo: 最初に`MIGRATION_FROM_V3.md`を読む。
新規repo: `IMPLEMENTATION_STATUS_TEMPLATE.md`を`IMPLEMENTATION_STATUS.md`へコピー。

Codexへ毎回送る文:
```text
このリポジトリの CODEX_START.md を読んで、その指示に従ってください。
```

Unit test/buildだけでなく、全Phaseで実Minecraft Runtime Acceptanceが必須。
