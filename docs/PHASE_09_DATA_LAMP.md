# Phase09 — Data Lamp / Graph / Piri Chain

Status: **IN_PROGRESS**

## Preflight sources read

- `phases/PHASE_09_DATA_LAMP.md`
- `SPEC.md` §§58,59,60,61,62,70,71,93,99,100,106,111,122,123,133,137
- `RUNTIME_ACCEPTANCE.md` Phase09
- current `GameStore`, `MachineService`, `SlotViewState`, `SlotScreen`, schema v4 and current reel/game implementation

## Consistency audit

No blocking technical contradiction was found inside the Phase09 requirements.

Important boundaries:

- DATA_LAMP always reads **the current business period**, never calendar-day aggregation and never an older `sourceBusinessPeriodId` merely because a grace-resumed game is attributed there.
- Game/stat writes remain attributed by `Session.sourceBusinessPeriodId` as §99 requires. Display-period selection and write attribution are intentionally different concerns.
- Existing `GameStore` already updates `total_games`, `current_games`, BIG/REG counts, difference/max difference, history and raw graph points transactionally. Phase09 does not duplicate those writes.
- Piri Chain requires at least one **completed** bonus in the current period, not merely a started bonus. Schema v4 has no bonus-completed counter, so completion is derived from the raw graph invariant in §62: bonus end creates a second graph point at the same `totalGames` x. This avoids schema changes and does not mistake a currently-running first bonus for a completed bonus.
- Raw graph points remain untouched. LTTB is display-only and capped at 300 points.
- History is current-period only, newest to oldest, max 10.
- The 100/101 Piri Chain boundary is inclusive at 100 and off at 101.

## User-approved direct bonus entry compatibility

The current product intentionally allows a bonus-winning normal game to enter BIG/REG directly when the player actually stops the reels on the entry symbols. This is newer than the original SPEC §§35–36 flow and is therefore treated as an explicit product override.

Phase09 keeps its accounting coherent:

- the winning game is still one normal game and increments `totalGames/currentGames` once;
- direct entry has no separate 1-BET entry transaction;
- history stores the winning game's unchanged `currentGames` value;
- the normal-result graph point is written at the winning game x;
- bonus end later writes the vertical graph point at the same x and resets `currentGames` to 0;
- if direct entry is not achieved, the existing 1-BET pending/entry path still works and does not increment G counts.

## Runtime completion still required

Phase09 must not be marked COMPLETE until actual Paper + Fabric runtime verifies 100+ scripted games, G/BIG/REG/history/graph updates, Piri Chain ON at 100 and OFF at 101, and screenshot evidence is saved under `runtime-evidence/PHASE_09/`.
