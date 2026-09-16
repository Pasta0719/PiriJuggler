# PHASE 10 Runtime Evidence

Status: IN_PROGRESS — implementation/build verified; manual runtime acceptance completed except direct stale-AdminSession rejection observation.

## Implemented

- OP + machine key opens server-issued memory-only AdminSession and Fabric Admin Screen.
- Admin Screen displays machine id, setting, current-period totals/BIG/REG/current games/difference/max difference, active profile, autoSetting, enabled, latest 30 setting history, and busy state.
- Admin mutations: setting 1..6, autoSetting, enabled, current-period daily reset.
- Busy machine stays viewable but all mutating admin actions reject with MACHINE_OCCUPIED.
- Approved protocol amendment `ADMIN_CLOSE=16` invalidates the AdminSession immediately when the Admin Screen closes.
- `/piri setting <machineId> <1-6>` uses history reason MANUAL, does not reset stats, and same-value setting is a successful no-op without history.
- `/piri reset daily <machineId>` and `/piri reset daily all`; all is atomic with respect to busy precondition.
- `/piri event status`, `/piri event next <profile>`, `/piri event next clear`.
- Existing startup allocation verified against Phase10 requirements: eligible list, pattern first, guarantees after pattern/base allocation, profile priority, setting history reasons, and transactional next-start override consumption.
- Unit/integration coverage added for admin store, AdminSession lookup/expiry/close invalidation, admin client sequence, and startup override commit/rollback behavior.

## Approved specification amendment

See `docs/spec-amendments.json` and `phases/PHASE_10_ADMIN_EVENTS.md`.

`ADMIN_CLOSE=16` payload:

```json
{
  "adminSessionId": "uuid",
  "machineId": 1,
  "adminSequence": 1
}
```

## Build gate

PASS in the user's Windows environment via `build-jars.bat` after the Phase10 compile fix. Reported output included:

- main Gradle build: `BUILD SUCCESSFUL in 28s` (`27 actionable tasks`)
- runtime test helper build: `BUILD SUCCESSFUL in 3s` (`8 actionable tasks`)
- distribution jars produced successfully

The user subsequently rebuilt/redeployed the current Paper jar and exercised the Phase10 runtime scenarios below successfully.

## Runtime Acceptance

- [x] OP + machine key Admin Screen actual open — user confirmed Admin Screen opened.
- [x] busy machine mutation reject — while the machine was occupied, `/piri setting 1 6` returned `MACHINE_OCCUPIED`; after leaving, Admin Screen confirmed the setting had not changed.
- [x] setting change history — Admin Screen showed the changed setting and a `MANUAL` history row.
- [x] true server restart -> new startup allocation path — after full stop/start, Admin Screen/history showed a fresh `SERVER_START` allocation entry. Direct UUID display of `businessPeriodId` is not exposed in the Admin UI, so the observed runtime evidence is the new startup allocation/history path rather than visual inspection of the UUID itself.
- [x] special date scenario with test config — `events.special_dates` was configured for 2026-09-16; after correcting YAML mapping syntax and restarting, the configured special-date profile became active. Test config was restored afterward.
- [x] manual next override scenario with test config — `event next` profile overrode the special-date profile on the next true restart, then was consumed exactly once; a subsequent restart returned to the special-date profile.
- [ ] Admin Screen close immediately invalidates AdminSession — production path is implemented (`AdminScreen` sends `ADMIN_CLOSE=16`; Paper routes it through admin validation and closes the memory-only session). `AdminSessionsTest.newEntryCloseAndRestartInvalidateOldId` verifies that after `close`, the old id is no longer current and any action using it is rejected. Normal UI does not expose the adminSessionId, so this exact stale-id rejection has not yet been directly observed in live Minecraft.

## Current blocker

No further ordinary manual UI action is required from the user. To close the final runtime-only item without weakening the acceptance criterion, use test instrumentation/runtime helper capable of retaining the old adminSessionId, closing the screen, replaying an admin mutation with that stale id, and observing rejection.
