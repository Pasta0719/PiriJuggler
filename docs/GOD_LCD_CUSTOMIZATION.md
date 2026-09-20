# GOD LCD customization

GODの液晶素材は、ジャグラーの user-audio と同じ考え方で、リソースパックではなくリポジトリ直下の `user-god-lcd` からビルド時にJARへ取り込みます。

## フォルダ

```text
PiriJuggler_Codex_Workflow_v4_AUDITED/
  user-god-lcd/
    scenes.json
    textures/
      normal.png
      gg.png
      god.png
      sgg.png
      z_zone.png
      好きな名前.png
```

PNGは何枚でも追加できます。

変更後はいつもの:

```powershell
.\build-jars.bat
```

だけで反映されます。ChatGPTやCodexにコード変更を頼む必要はありません。

## scenes.json

```json
{
  "scenes": {
    "NORMAL": {
      "background": "#10213A",
      "accent": "#FFD15A",
      "title": "OLYMPUS",
      "subtitle": "GOD SYSTEM",
      "layers": [
        {
          "texture": "piri:textures/god_lcd/normal.png",
          "x": 0,
          "y": 0,
          "w": 1260,
          "h": 650,
          "textureWidth": 1260,
          "textureHeight": 650,
          "alpha": 1.0
        }
      ]
    }
  }
}
```

液晶キャンバスは1260x650。layersは上から順に描画されるため、背景・エフェクト・文字素材などを自由に重ねられます。

使える基本シーン:

`NORMAL`, `GG`, `G_ZONE`, `SGG`, `SGG_COMEBACK`, `Z_ZONE`, `Z_GAME`

イベント専用:

`EVENT_GOD`, `EVENT_GOD_IN_GG`, `EVENT_RED7_SGG`, `EVENT_CEILING_Z`, `EVENT_GAIA_Z`

scenes.jsonに書いていないシーンは内蔵デフォルト表示を使います。ゲーム確率・設定・ストック等の内部状態には一切影響しません。
