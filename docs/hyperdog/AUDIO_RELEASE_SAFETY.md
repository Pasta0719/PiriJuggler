# HYPER_DOG sound and release safety policy (2026-11-11)

- HYPER_DOG owner channel plays the seated player's audio. World positional audio
  MUST NOT play sound effects or BGM from that same machine to the same player.
- Other machines, spectators, and other machine families continue to use hall audio.
- Paper marks reconstructed reel packets with `resumeSnapshot=true`.
  Fabric MUST update reel and stop-history state but MUST NOT emit lever, stop,
  win, or other one-shot SFX from those reconstruction packets.
- Future packets after returning to the machine MUST still be audible.
- Do not restore an arbitrary old JAR as the new baseline; identify the
  user-verified release and compare it entry by entry against the output.
- For every release run:
  `python scripts/verify_hyperdog_audio_release_delta.py --baseline-fabric old-fabric.jar --candidate-fabric new-fabric.jar --baseline-paper old-paper.jar --candidate-paper new-paper.jar`
- Audio-only corrections may change ONLY the four allowlisted Fabric classes.
  ALL Paper changes, texture/audio assets, class deletions, and any unrelated
  class modification must fail the release.
- Required tests: owner/spectator audio separation, no repeated BET/lever/stop,
  no replay sounds on reconnect, BGM loop count, new action sounds still occur,
  all unrelated machine tests, and binary file inventory comparison.
- Real acceptance additionally requires two Minecraft clients connected to a
  Paper server; Java-only tests and bytecode inspection never establish actual
  Minecraft audio/display behavior.

Do not declare a release ready or ask users to replace installed JARs until the
checks relevant to the release have succeeded. If real Minecraft acceptance is
missing, label that limitation explicitly instead of calling it a real PASS.
