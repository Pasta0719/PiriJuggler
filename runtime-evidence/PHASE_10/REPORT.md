# PHASE 10 Runtime Evidence

Status: COMPLETE — implementation, build gate, and Phase10 Runtime Acceptance passed.

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

The user subsequently rebuilt/redeployed the Paper jar used for the Phase10 runtime scenarios.

## Runtime Acceptance

The authoritative Phase10 section of `RUNTIME_ACCEPTANCE.md` requires these five runtime items. All passed:

- [x] OP + machine key Admin Screen actual open — user confirmed Admin Screen opened.
- [x] busy machine mutation reject — while the machine was occupied, `/piri setting 1 6` returned `MACHINE_OCCUPIED`; after leaving, Admin Screen confirmed the setting had not changed.
- [x] setting change history — Admin Screen showed the changed setting and a `MANUAL` history row.
- [x] true server restart -> new businessPeriodId / setting allocation — after full stop/start, Admin Screen/history showed a fresh `SERVER_START` allocation entry. The UI does not display the UUID itself; the observed runtime evidence is the new startup allocation/history path.
- [x] special date/manual next override scenario with test config — special-date profile activation was observed; manual-next then overrode it on the next true restart and was consumed exactly once. Test config was restored afterward.

## AdminSession close verification

`Admin Screen close -> ADMIN_CLOSE -> AdminSession immediate invalidation` is an implementation/spec requirement, but it is **not an additional Phase10 runtime-acceptance bullet** in `RUNTIME_ACCEPTANCE.md`.

Production wiring is present, and `AdminSessionsTest.newEntryCloseAndRestartInvalidateOldId` verifies that after `close`, the old id is no longer current and further actions using it are rejected. Therefore this does not block Phase10 completion.
