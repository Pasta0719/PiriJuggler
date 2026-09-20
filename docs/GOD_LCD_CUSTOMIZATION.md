# GOD LCD customization

The GOD LCD is intentionally data-driven so that images and scene presentation can be changed without rebuilding the mod.

## Easiest workflow

Create a normal Minecraft resource pack, for example:

```text
.minecraft/resourcepacks/PiriGodCustom/
  pack.mcmeta
  assets/
    piri/
      god_lcd/
        scenes.json
      textures/
        god_lcd/
          normal.png
          gg.png
          god.png
          anything_you_want.png
```

Enable the resource pack in Minecraft. After changing files, use Minecraft's normal resource reload (F3+T). No Java/Gradle build is required.

## scenes.json

The pack can override `assets/piri/god_lcd/scenes.json`. A scene can change colors/text and can contain any number of PNG layers:

```json
{
  "scenes": {
    "NORMAL": {
      "background": "#10213A",
      "accent": "#FFD15A",
      "title": "MY NORMAL SCREEN",
      "subtitle": "CUSTOM",
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

Coordinates are relative to the LCD's 1260x650 canvas. Layer order is array order; later entries draw over earlier entries.

Supported base scene keys:

`NORMAL`, `GG`, `G_ZONE`, `SGG`, `SGG_COMEBACK`, `Z_ZONE`, `Z_GAME`.

Event scenes override the phase scene when present. Current event keys include:

`EVENT_GOD`, `EVENT_GOD_IN_GG`, `EVENT_RED7_SGG`, `EVENT_CEILING_Z`, `EVENT_GAIA_Z`.

You may omit any scene: built-in defaults remain active. You may add PNGs freely and reference them from any scene. Invalid/missing custom resources fall back safely instead of changing gameplay.

This system is client presentation only. Probabilities, stocks, settings and other hidden machine state stay server-owned.
