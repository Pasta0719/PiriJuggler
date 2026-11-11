# Deployed PiriJuggler pair — authoritative reference (2026-11-11)

The owner supplied the **actual Paper and Fabric JARs** used on the Minecraft server/client. These files are the behavioral and asset reference for future source changes. This record is **not** a claim that current GitHub source builds them byte-identically.

## Exact supplied binaries

| Side | Runtime filename | SHA-256 | ZIP entries |
| --- | --- | --- | ---: |
| Paper | `piri-juggler-paper-1.0.0.jar` | `fa6fbc0caa12949ee6bd0f7a47177dd75f8ab116c78fb5082c2de43d5d887344` | 1039 |
| Fabric | `piri-juggler-fabric-1.0.0.jar` | `c2622815fb92d2f4ffacc4dbe4c3e5c57e9017536a3290c527db10c530dc8bff` | 213 |

Validated with Python zipfile CRC, per-entry SHA-256, and the byte-level protected-entry fingerprint defined in `release-policies/hyperdog-20261111.json`. Paper's 1024 protected entries match `3c61134c0005f2c35c5ce5426808ad3e8e16fa29ad1bed8b4c7e240d4d8825cc`; Fabric's 209 protected entries match `baa239220046031fe679e7a05b8d44722a885bf2b7e1ba8e5bab7896b90b599c`.

The files were also byte-identical to the previously supplied 2026-11-11 Paper/Fabric JAR copies, despite different upload filenames.

## Rebuilt-source comparison (2026-11-11)

The Java 21 GitHub Actions audit built `:paper:jar :fabric:remapJar` from the HYPER_DOG-integrated `main` source (`19acc1a3626c07044f4cb700bdd00e22d849729b`; audit commits changed only scripts/workflow). It produced **different artifacts**:

| Metric | Paper | Fabric |
| --- | ---: | ---: |
| Entries matching deployed JAR by name and decompressed content | 969 | 155 |
| Existing entries with different bytes | 60 | 34 |
| Entries present ONLY in deployed JAR | 10 | 24 |
| Entries present ONLY in source-built JAR | 0 | 0 |
| Source-built ZIP entry count | 1029 | 189 |
| Deployed ZIP entry count | 1039 | 213 |

### Paper: classes absent from source-built JAR

- `jp/pirijuggler/paper/data/HyperDogMachineDataSimulator$Cursor.class`
- `jp/pirijuggler/paper/data/HyperDogMachineDataSimulator$State.class`
- `jp/pirijuggler/paper/data/HyperDogMachineDataSimulator.class`
- `jp/pirijuggler/paper/database/HyperDogSettlement.class`
- `jp/pirijuggler/paper/game/hyperdog/HyperDogShotSupport$Shot.class`
- `jp/pirijuggler/paper/game/hyperdog/HyperDogShotSupport.class`
- `jp/pirijuggler/paper/game/hyperdog/HyperDogStopPersistence.class`
- `jp/pirijuggler/paper/game/hyperdog/HyperDogStopSync.class`
- `jp/pirijuggler/paper/machine/HyperDogRemoteStats$Counts.class`
- `jp/pirijuggler/paper/machine/HyperDogRemoteStats.class`

The corresponding six Java source files are absent in current `main` (the nested classes come from them). These are not optional when reconstructing the deployed runtime.

### Fabric: assets/classes absent from source-built JAR

- 16 OGG sounds, including previously working standard sounds and HYPER_DOG sounds (e.g. `bet.ogg`, `big_bgm.ogg`, `hyper_dog_cz_success.ogg`, `hyper_dog_strong_add.ogg`)
- `assets/piri/textures/gui/original_at/lamp/chance_off.png`
- `assets/piri/textures/gui/original_at/lamp/chance_on.png`
- `jp/pirijuggler/fabric/network/HyperDogWorldStats.class`
- `jp/pirijuggler/fabric/ui/HyperDogAudioOpen.class`
- `jp/pirijuggler/fabric/ui/HyperDogClientReels$StopMotion.class`
- `jp/pirijuggler/fabric/ui/HyperDogErrorFormatter.class`
- `jp/pirijuggler/fabric/ui/HyperDogIdleReelPhase.class`
- `jp/pirijuggler/fabric/ui/HyperDogResumeClock.class`

The corresponding Java source files are also absent in current `main` (nested `StopMotion` requires inspection of its containing class). `user-audio` is normally user-provided and may not be tracked in the repository; do not silently replace missing sounds with silence or generated substitutes.

### Existing entries that differ

60 Paper entries differ, including HYPER_DOG game/stop/recovery/accounting classes, machine and data simulation classes, `config.yml`, `schema-v4.sql`, `hyperdog/v7.json`, and several symbols. 34 Fabric entries differ, including HYPER_DOG UI/audio, cabinet rendering, protocol state, symbol textures, and resource JSON. These are **content differences**, not merely ZIP timestamp or compression differences. See `scripts/audit_deployed_binary_identity.py` for the verification format and comparison logic.

## Immutable safety requirements

1. **Do not overwrite the installed JARs.** The supplied pair is the reference and remains unchanged.
2. **Do not weaken or regenerate** the approved JAR allowlist, integrity fingerprints, or unrelated existing machine/audio behavior to make CI pass.
3. Restore and reconcile absent source files, modified classes, exact supplied textures, and legally shareable/user-authorized sound assets before claiming that a fresh build is equivalent.
4. Existing unit tests passing is insufficient. Compare rebuilt ZIP entry names, decompressed content, image/audio assets, runtime behavior, and byte-level release safety policy against the supplied reference.
5. A bare successful merge into `main` does not certify parity with the deployed pair.

## Diagnostic

`python scripts/audit_deployed_binary_identity.py <paper.jar> <fabric.jar> <report-dir>`

For the supplied binaries, all four expected hash/count checks per JAR must report `match=True`. This script is diagnostic only; it never bypasses the release gate.
