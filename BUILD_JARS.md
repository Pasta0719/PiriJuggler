# 音源の配置とJARの作成

必要なものはJava 21（JDK）です。初回ビルドはGradleと依存ライブラリの取得にインターネット接続が必要です。Gradle自体を別途インストールする必要はありません。

## Windowsで作成

1. `user-audio` フォルダを開き、READMEの表にある名前でユーザー提供OGGを置きます。空でも、一部の音だけでも構いません。
2. `build-jars.bat` をダブルクリックします。テスト後にJARを作成します。
3. `Build successful` が表示されたら `dist` フォルダを開きます。

| 完成ファイル | 配置先 |
|---|---|
| dist/piri-juggler-paper-1.0.1.jar | Paper 1.21サーバーのpluginsフォルダ |
| dist/piri-juggler-fabric-1.0.1.jar | Minecraft 1.21 + Fabricクライアントのmodsフォルダ |

現在の製品バージョンはルート `gradle.properties` の `piriVersion` を唯一の変更点として管理し、Paper/Fabric/テストhelperのGradle project versionとplugin/mod metadataへ展開します。

Fabric Loader 0.16.14とFabric API 0.102.0+1.21を使用します。音源はFabric用JARに同梱されます。差し替えたら再ビルドし、クライアントのJARを更新してください。ソースを自由に編集して同じ手順で再ビルドできます。

## ターミナルから作成

リポジトリ直下で実行します。

```powershell
.\build-jars.bat --no-pause
```

同じ処理をGradleで直接実行できます。

```powershell
.\gradlew.bat test packagePiriJars --console=plain
```

通常の `gradlew.bat build` も利用でき、JARは各moduleの `build/libs` に生成されます。配布用には `-sources.jar` や `-dev.jar` を使用しないでください。

## 音源をJAR外から差し替える

resource packの `assets/piri/sounds/` に同じ名前のOGGを置く方法にも対応します。Minecraftでresource packを有効にし、リソースを再読込すると新しい音源を使えます。ファイル名・SoundEvent ID・用途の一覧は `docs/USER_AUDIO.md` にあります。

音源未配置のbuild/testは成功する構成です。画像アセットのSHA検証は維持します。現在のゲーム実装はIMPLEMENTATION_STATUS.mdに記録したPhaseまでです。
