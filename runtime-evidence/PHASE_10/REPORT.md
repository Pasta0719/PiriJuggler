# PHASE 10 Runtime Evidence

Status: IN_PROGRESS — implementation complete, build/runtime acceptance pending.

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
- Unit/integration coverage added for admin store, AdminSession lookup/expiry, admin client sequence, and startup override commit/rollback behavior.

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

Not yet executed on the user's Windows build environment for this HEAD.

Required:

```powershell
git pull
.\build-jars.bat
```

Do not mark Phase10 COMPLETE until the build gate and all Runtime Acceptance items below PASS.

## Runtime Acceptance pending

- [ ] OP + machine key Admin Screen actual open
- [ ] busy machine mutation reject
- [ ] setting change history
- [ ] true server restart -> new businessPeriodId / setting allocation
- [ ] special date scenario with test config
- [ ] manual next override scenario with test config
- [ ] Admin Screen close immediately invalidates AdminSession

