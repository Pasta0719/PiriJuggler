# PHASE 09 Runtime Evidence

Status: RUNTIME_ACCEPTANCE_PASS / FINAL_BUILD_PENDING
Date: 2026-09-16 (JST)

## Runtime acceptance confirmed

The following Phase09 behavior was confirmed in the live Minecraft environment during the interactive acceptance session:

- Data lamp UI displays current-period machine data.
- Graph displays and advances from current-period data.
- Recent bonus history displays correctly.
- Piri Chain behavior is correct.
- 100G continues the chain and 101G resets it.
- `/piri data` returns the public machine summary without seating/physical access.
- `/piri data <machineId>` returns machine detail including total/current G, BIG/REG/combined observed odds, difference, max difference, and recent bonus history.
- Public remote data does not expose the machine setting.
- Admin real-data simulation updates the same current-period data consumed by the data lamp/public data path.

## Final completion gate

Phase09 must not be marked COMPLETE until the latest repository HEAD passes both required Gradle gates:

- `./gradlew test` exit 0
- `./gradlew build` exit 0

At the time this report was created, the latest GitHub HEAD was `90d743d8b771625b9cc19210ea0af62ebb942aca` and no CI status was published for that commit. Therefore runtime acceptance is recorded as PASS, while final Phase09 COMPLETE remains pending build/test evidence for the latest HEAD.
