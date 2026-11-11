# HYPER_DOG world reel snapshot and duplicate BET repair (2026-11-11)

Both distributable JARs were reconstructed from the previously distributed owner-view release. Only two class entries per side differ:

- Paper: `RemoteMachineSync.class` changed; `HyperDogRemoteSnapshot.class` added.
- Fabric: `HallAudio.class` changed; `HyperDogOwnerAudioFilter.class` added.

Paper now builds a HYPER_DOG world snapshot using its persisted physical reel state, because legacy session fields do not carry the HYPER_DOG spin ID. The three reel start phases, stopped indices, and stopped mask are retained. This prevents Fabric rejecting a spinning snapshot with missing required fields. No hidden role data is sent. Non-HYPER_DOG machines are unaffected.

Fabric keeps the direct owner BET sound from `HyperDogAudio`, suppressing only the duplicate positional BET emitted by `HallAudio` for that owner's active HYPER_DOG machine. Spectator audio, other machines and other sounds remain unchanged.

Automated checks: 15 HYPER_DOG snapshot assertions; 9 audio policy/runtime assertions; 11,700 full stop sequences on each JAR; ZIP integrity; exact entry-level binary diff.

**No running Minecraft Paper/Fabric integration test has been completed**. The checks validate the identified packet and sound paths, not a verified visible 3D cabinet or actual speaker output.
