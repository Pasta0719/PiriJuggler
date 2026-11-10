# HYPER_DOG dog symbols — JAR/source synchronization record

This branch tracks the source-side implementation of the mixed-size symbol renderer.
The latest user-delivered Fabric JAR was patched from an existing compiled JAR, not rebuilt from this branch. **Do not treat this branch as reproducible release source until the four PNG assets below and all preceding JAR-only changes are reconciled.**

## Exact 320x320 RGBA image assets required

| Relative path under `fabric/src/main/resources/assets/piri/textures/gui/original_at/symbols/` | SHA-256 | Provenance |
|---|---|---|
| `replay.png` | `8507303cbeeaf4017a132687aaffc221dd8d886468ad13a6578b1d7820816266` | User-supplied original `replay(2).png`; no changes |
| `red7.png` | `ccdc512b7300fd5812bbdb1e3d5fbeca21f6d47628324b185c21657b94a5fabf` | Pixel-only recolor of original |
| `rainbow7.png` | `0195c47805400a2e715267527f24e30fc788cf2a1d8ac0c1c8bc59c95e734ced` | Pixel-only recolor of original |
| `bar.png` | `a8e9d0fb31eaa6b8748fb7f3c3ba1d4dd833faf776be759c173d816ffa0d9ac0` | Pixel-only recolor of original |

The Fabric JAR supplied in the conversation after replay restoration has SHA-256
`c60bc1fa4d72050c3532b911173d12f69dfd8fa24455905a3beb9cc749076d9d`.

## Renderer changes committed on this branch

- HYPER_DOG cabinet and reel preview source regions resolve the actual texture dimensions rather than cropping at 256px.
- HYPER_DOG world rendering already uses `JugglerGodAssets.textureSize`, so the world geometry remains unchanged.
- No Paper changes required for the replay symbol replacement.
- The three other 256px symbols remain unchanged.

## Remaining source reconciliation

1. Add the four exact PNGs listed above to the resource tree.
2. Update tests that currently assert every HYPER_DOG symbol is 256px.
3. Reconcile additional JAR-only fixes from earlier ChatGPT sessions against the actual Git source before merging.
4. Build the Fabric artifact from source and run Minecraft runtime acceptance.
