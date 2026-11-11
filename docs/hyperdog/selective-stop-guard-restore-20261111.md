# HYPER_DOG: restore released Fabric/Paper JARs and keep only left-reel trigger guard (2026-11-11)

## Intended behavior
Only these changes were authorized:
1. In ordinary play, when CHERRY or WATERMELON is not the drawn role, do not visibly stop that icon in any of the three LEFT reel rows.
2. Prevent MISS/REPLAY/GRAPE arrangements mistaken for a weak-cherry result.
3. Keep the six-position max slip for ordinary roles where needed to enforce the visual restriction.

## Artifact restoration
Restore **all contents** from the last ChatGPT-distributed JARs, then replace **only**
`jp/pirijuggler/common/reel/HyperDogReelControl.class` with the independently
compiled Fabric and Paper versions from the Codex-produced JARs. No other
changed entry is retained.

Fabric baseline: `hyperdog_chance_A_release/piri-juggler-fabric-1.0.0.jar`.
Paper baseline: `piri-juggler-paper-1.0.0.jar` (the last distributed version).

Repaired SHA-256:
- Fabric: `55bdf1e65d6112f75668fe858129d3fd9ed24f7d8587323a8ededb501d33b717`
- Paper: `eb8db3334e05946a039c4d15583b888ba251e0fc9e99fd6bf3f219064a15d728`

Every other entry in both JARs is SHA-256-identical to its respective baseline:
audio, assets, CHANCE layout, client idle/resume, game state, credit and settlement,
persistence, simulations, remotes and all other classes.

## Evidence
- Each of Fabric and Paper: 13 roles × 6 reel-stop orders × 150 deterministic
  input sequences = 11,700 valid complete 3-reel stop sequences.
- For ordinary role MISS/REPLAY/GRAPE and the WATERMELON/WEAK_CHERRY/
  STRONG_CHERRY roles: no unauthorized LEFT reel CHERRY or WATERMELON in any
  legal 3-row window.
- Six of 21 LEFT press indices per ordinary role require >4 slips.
- JAR integrity and one-file-only content difference validated.

## Explicit scope limitation
The preserved guard checks ordinary roles only. Some premium/fixed-result
roles (e.g. CHANCE/STRONG_CHANCE/AT_ENTRY_RED7) have a forced stop with
a visible LEFT WATERMELON even when WATERMELON is not the role.
Fixing those requires separately redesigning fixed stop patterns and/or
physical reel strips, and was not silently included.

No real Minecraft Paper/Fabric server-client run has been performed.
The repository's source and built JARs still have unrelated historical
divergences; this document records the binary-selective restore,
not a claim of full build reproducibility.
