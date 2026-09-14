# Phase02 — Database, machines and session foundation

Product values come from SPEC.md v4. Existing Phase01 sources and tests are retained.

## Production implementation

- `paper/database/PiriDatabase`: one SQLite connection confined to the dedicated DB executor; WAL, foreign keys, FULL synchronization and 5000 ms busy timeout. The 13 table definitions and partial location index are copied exactly from chapter 94 into `schema-v4.sql`. Old schema versions fail startup with backup/removal guidance; data is never automatically deleted.
- `paper/database/StartupProfile`: date/profile resolution and initial setting allocation run inside the business-period transaction. JVM start time identifies a real process restart; plugin reload reuses the existing period. New period stats and graph origin rows preserve earlier periods. Allocation is implemented here because chapter 71 requires it at period creation; Phase10 management commands and UI remain outside this phase.
- `paper/machine/MachineService`: main-thread command/event entry, player and machine reservations during DB operations, read-only committed snapshots, and callback publication after commit. `create`, `redefine`, `remove`, `list`, `info` and `key give` enforce OP permissions. Creation/redefinition use a real Button raytrace within 5 blocks. Removal is logical and never changes world blocks.
- `paper/session/Session`: immutable copy of all 28 snapshot columns. One player has at most one row. ACTIVE/GRACE rows own the machine lock; SAFE rows do not. Resuming GRACE retains the game snapshot; resuming SAFE assigns the current business period. Assets stay in the session.
- Close deletes only an empty SEATED_READY session without unfinished cashout. Funded ready sessions become SAFE; other game states retain their snapshot in GRACE. Disconnect is deferred behind in-flight work. Expiry settles ready sessions into SAFE. Normal shutdown drains DB work, persists active sessions as GRACE, checkpoints, and closes the connection.
- `paper/session/AdminSessions`: memory-only UUID, owner/machine binding, sequence checks and 300-second inactivity expiry. OP plus a versioned machine key (`piri:item_type=machine_key`, `piri:item_version=1`) opens read-only ADMIN_STATE even on occupied or disabled machines. Admin mutations and the admin screen belong to Phase10.
- `fabric/network/ClientSession`: connection-local OPEN_MACHINE, PUBLIC_STATE and ADMIN_STATE reception and matching session-end handling. No screen is opened in this phase; the actual slot screen and input mapping are Phase03.

`LOCATION_ALREADY_REGISTERED` and `RECOVERY_REQUIRED` are chapter 63/66 chat/command results. They do not add values to the fixed chapter 115 network ErrorCode list.

## Later-phase integration boundaries

Phase02 has no BET, LEVER, spinning, bonus or monetary gameplay action, so every session it creates is SEATED_READY. The full unresolved-game Force Settlement engine depends on the reel/game/bonus implementations in Phases04–06 and is integrated and tested in Phase11. This foundation preserves all unresolved snapshot columns and refuses to release their lock or start a new period if data from a later game build is encountered. It never silently erases replay/bonus rights. Tests verify exact snapshot resume and all chapter 127 close branches using database fixtures; they do not claim runtime spinning/settlement coverage.

The full slot screen, admin screen close/input wiring, Vault/cashout, idle game settlement and admin mutations retain their assigned later phases. The admin entry object already supports explicit close/invalidation for that UI wiring.

## Verification

Run from the repository root with Java 21:

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat -PruntimeAcceptance=true build --console=plain
python runtime-test-support/run_phase02.py
python docs/verify_phase01_artifacts.py
```

The actual Paper 1.21-130 download is reused from the verified Phase01 runtime cache. The helper modules are opt-in and are excluded from both production jars. The runner creates a fresh, localhost-only world and loads the built production plugin and mod. Real clients `PiriRuntimeTest` and `PiriRuntimeTest2` perform raytraces, chat commands, block interaction packets and CLOSE_REQUEST. Test control files only select those actual inputs; server state is observed from the production service after DB commit.

The run records build hashes, commands, logs, snapshots, screenshots, process exits, SQLite integrity and individual assertions under `runtime-evidence/PHASE_02/`. Each attempt is retained separately. The first server process exits before the second starts against the same DB/world, so the persistence test is a real process restart.
