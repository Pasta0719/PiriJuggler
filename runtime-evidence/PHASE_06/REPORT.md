# Phase 06 Runtime Report

Phase 06 (Piri Chance / Premium / Audio / BIG / REG) was implemented and manually exercised in Minecraft by the user before starting Phase 07.

## Runtime result

- Forced BIG / REG and Premium A-F scenarios were exercised with the test-only helper.
- BIG / REG bonus entry and end flow were confirmed working after the `bonusType` protocol-field fix.
- User reported the Phase 06 runtime scenarios as having no remaining problem after rebuilding.
- User also confirmed the replacement audio sources were acceptable in runtime.
- Final asset/audio follow-up build completed successfully via `build-jars.bat` on 2026-09-14.

## Follow-up audio behavior

The user requested and accepted these Phase 06 follow-up rules before moving on:

- REG: do not play `bonus_start`.
- REG: do not play `bonus_end`.
- BIG: delay the first `big_bgm` start by 4.5 seconds.
- BIG: the 4.5-second delay applies only to initial start and must not affect subsequent loop repetition.

These are client-side Fabric behaviors; Paper game progression was not changed for this follow-up.

## Evidence note

This report records the interactive runtime acceptance reported by the user in the development conversation. Raw Minecraft logs/screenshots for this final interactive pass were not separately archived into this repository.
