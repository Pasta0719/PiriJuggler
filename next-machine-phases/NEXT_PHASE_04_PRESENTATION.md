# NEXT PHASE 04 — Presentation / LCD / Audio / Replaceable Assets

Status: **COMPLETE — PRESENTATION ROUTING AND REAL-CLIENT ACCEPTANCE RECORDED**

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

## Phase 04 acceptance result

Current-head CI commit `29d097a295b3013e1e28436268cb94eaad62a468`, run `35867331537`, completed successfully.

1. presentation-routing regression coverage — **PASS**;
2. production `SlotScreen` opens `JUGGLER_GOD` — **PASS**;
3. authoritative GOD blackout reaches the real client — **PASS**;
4. first stop reveals only the first GOD reel and presentation does not choose the result — **PASS**;
5. successor asset absence falls back to ordinary JUGGLER — **PASS**;
6. successor fixture overrides the ordinary namespace without rebinding ordinary JUGGLER — **PASS**;
7. STOCK presentation is wired to the authoritative server-provided `stockLampOn` boolean in the production successor view; the client does not own stock count or outcome selection — **PASS**.

NEXT Phase 03 economy remains unchanged. NEXT Phase 04 is complete.