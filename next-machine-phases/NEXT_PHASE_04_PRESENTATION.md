# NEXT PHASE 04 — Presentation / LCD / Audio / Replaceable Assets

Status: **IN_PROGRESS — UNBLOCKED AFTER PHASE 03 ACCEPTANCE**

Scope comes from the locked successor specification:
- final machine UI
- LCD/static/limited-animation presentation
- reel/symbol assets
- lamps/navigation/status presentation
- audio hooks
- user-replaceable asset folders and packaging

Presentation consumes authoritative server results and must not decide gameplay.

If the design intentionally simplifies LCD/animation, this phase must spend the saved complexity on the locked alternative feedback/gameplay axis rather than silently reducing the experience.

## Preservation rules

- Existing ordinary JUGGLER presentation and probability behavior must remain unchanged.
- `JUGGLER_GOD` presentation must remain independently replaceable through its dedicated asset namespace.
- Ordinary JUGGLER assets may be used as fallback only; adding/replacing a `JUGGLER_GOD` asset must not require editing the ordinary asset.
- Client presentation may react to authoritative GOD/heaven/bonus state and events, but must not decide outcomes or economy.

## Phase 04 work now unblocked

NEXT Phase 03 completed with 90,000,000 total deterministic simulated lever games and recorded confidence evidence. Phase 04 can now audit the actual successor client render path, dedicated `JugglerGodAssets` resolution, LCD/event presentation and audio hooks before runtime acceptance.
