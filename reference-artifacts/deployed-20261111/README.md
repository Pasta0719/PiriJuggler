# Deployed PiriJuggler reference JARs (2026-11-11)

These are the exact owner-supplied Paper and Fabric JARs used as the current
behavioral and asset reference. They are tracked so that the deployed contents
are present on `main`, even while source-level parity work remains incomplete.

| Side | File | SHA-256 |
| --- | --- | --- |
| Fabric | `piri-juggler-fabric-1.0.0.jar` | `c2622815fb92d2f4ffacc4dbe4c3e5c57e9017536a3290c527db10c530dc8bff` |
| Paper | `piri-juggler-paper-1.0.0.jar` | `fa6fbc0caa12949ee6bd0f7a47177dd75f8ab116c78fb5082c2de43d5d887344` |

The detailed entry-level audit and known source gaps are documented in
[`docs/DEPLOYED_JAR_REFERENCE_20261111.md`](../../docs/DEPLOYED_JAR_REFERENCE_20261111.md).
Do not replace either JAR with a fresh source build unless the identity and
behavior checks described there have passed.
