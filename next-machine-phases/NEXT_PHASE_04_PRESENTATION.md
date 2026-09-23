# NEXT PHASE 04 — Presentation / LCD / Audio / Replaceable Assets

Status: **IN_PROGRESS — PRESENTATION ROUTING AUDITED; RUNTIME ACCEPTANCE PENDING**

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
- Hidden heaven state must not be exposed by client-only inference or a presentation-only counter.

## Phase 04 implementation/audit progress

- Confirmed the production successor uses the existing `SlotScreen`/world-cabinet render infrastructure rather than the retired Piri GOD screen.
- `SlotScreen` resolves reel/lamp textures through `JugglerGodAssets.texture(view.machineType(), path)`.
- `WorldCabinetRenderer` also resolves machine textures through `JugglerGodAssets`, so in-world and seated presentation share the same successor override contract.
- `JugglerGodAssets` checks `piri:textures/juggler_god/<ordinary path>` for `JUGGLER_GOD` and falls back to the ordinary JUGGLER texture only when no dedicated override exists.
- `PiriSounds.forMachine` checks the dedicated `juggler_god_*` sound first and falls back to the ordinary sound when the optional successor OGG is absent.
- `piri:god_freeze` remains a separate semantic event and is selected directly for authoritative GOD freeze spins; it is not an alias of the ordinary lever sound.
- `SlotScreen` already presents the authoritative bonus-in-bonus STOCK boolean for `JUGGLER_GOD`; it does not expose the stock count.
- GOD blackout and per-reel BAR reveal are driven from server-provided `godFreeze`/stop packets in `SlotViewState`; the client does not choose the GOD result.
- Added regression coverage that locks both seated and world texture routing, successor audio fallback routing, and dedicated GOD-freeze routing. This prevents later presentation work from silently rebinding JUGGLER_GOD to ordinary JUGGLER resource IDs.

## Remaining Phase 04 gate

Before Phase 04 can be marked COMPLETE:

1. run CI for the presentation-routing regression coverage;
2. add/execute a dedicated JUGGLER_GOD real-client runtime acceptance that proves the successor machine opens through the production screen and the GOD blackout/BAR reveal/STOCK presentation is visible from authoritative packets;
3. verify the runtime with successor-specific assets absent (ordinary fallback) and with at least a fixture successor override where practical;
4. record the runtime evidence without changing ordinary JUGGLER assets or economy.

NEXT Phase 03 completed with 90,000,000 total deterministic simulated lever games and recorded confidence evidence. Phase 04 remains presentation-only; Phase 03 economy is not reopened by this work.
