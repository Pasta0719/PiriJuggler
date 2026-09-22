# NEXT PHASE 01 — Juggler + GOD Design Lock

Status: **COMPLETE — DESIGN LOCKED; ECONOMY TUNING DEFERRED TO NEXT PHASE 03**

## Core concept

The successor machine keeps the current Juggler-style BIG / REG game as the basic form, and adds a GOD-style heaven loop plus a 1/8192 premium GOD event.

This is not a recreation of the retired Piri GOD implementation. The existing PiriJuggler game is the base.

## Locked normal game

- BIG and REG remain the normal bonus types.
- Normal reel behavior remains almost the same as current PiriJuggler.
- After a normal BIG or REG ends, the machine may enter a hidden heaven state.
- When heaven is active, another BIG or REG is **guaranteed to occur within 32 games**.
- On heaven entry, the server selects and persists one target hit game in the range **1..32**. The initial implementation uses a uniform draw across 1..32; NEXT Phase 03 may alter only this within-window distribution if needed for feel, but may not remove the <=32G guarantee.
- The bonus type at the target game is drawn from the setting's normal BIG/REG balance unless a later locked economy table explicitly separates heaven BIG/REG weights.
- After a bonus won from heaven, the following 32-game period has a higher chance of remaining/returning to heaven than the 32-game period after a bonus won from normal.
- Exact normal->heaven and heaven->heaven probabilities are intentionally left for NEXT Phase 03 payout fitting.

## GOD trigger

- GOD occurs at probability **1 / 8192**.
- GOD is represented by **BAR-BAR-BAR on the middle line**.
- BAR-BAR-BAR is a **15-medal role**.
- The old current-PiriJuggler premium BIG presentation that permits BAR-BAR-BAR is removed.
- Outside a GOD-confirmed game, aiming for BAR must never produce a BAR-BAR-BAR line.
- The existing normal BAR-alignment denial logic is the base rule for all non-GOD games.

## GOD freeze / reel presentation

On the lever-on of a game where GOD/BAR-BAR-BAR is confirmed:

1. play the dedicated freeze/lever presentation;
2. fully black out the reel area in a GOD-style "puchun" presentation;
3. when each reel is stopped, force BAR to the middle row for that reel;
4. only the stopped reel's middle BAR area becomes illuminated/visible while the rest remains blacked out;
5. after the third stop confirms middle-line BAR-BAR-BAR, light Piri Chance **silently**;
6. immediately transition into the first BIG of the GOD chain.

The middle-line BAR result is authoritative gameplay state, not cosmetic-only rendering.

## GOD BIG chain

GOD starts a sequence of BIG bonuses with 1-game chaining behavior.

Locked structure:

- GOD grants an initial stock of **5 BIGs** on every setting.
- After the 5 guaranteed BIGs are exhausted, additional BIGs use a setting-dependent continuation probability.
- Each successful continuation adds another 1G BIG and then rolls continuation again.
- Initial continuation targets are locked as: Setting 1 = **25%**, Setting 2 = **30%**, Setting 3 = **35%**, Setting 4 = **45%**, Setting 5 = **55%**, Setting 6 = **70%**.
- These are design targets and may only be numerically fine-tuned in NEXT Phase 03 if whole-machine simulation cannot meet the final payout targets without breaking the locked behavior.
- GOD itself is recorded separately from ordinary BIG/REG history.

After the GOD continuation ends, the machine enters heaven.

## Game-count / history semantics

- GOD itself is written to history as **GOD** at the BAR-BAR-BAR result.
- GOD-chain bonuses are **BIG only**. REG substitution is not allowed.
- The first **5 guaranteed BIGs** belong to the guaranteed GOD stock section and do **not** advance the displayed/history game count between them; their history distance is recorded as **0G** under the active GOD chain.
- After the five guaranteed BIGs are exhausted, every continuation-success BIG is a true **1G BIG**: one game is advanced and that BIG is recorded as **1G** while remaining linked to the same GOD chain.
- The continuation roll is performed after each continuation BIG until failure.
- After the full GOD chain ends, the chain closes and normal game counting resumes from the resulting heaven state.
- Data-lamp/history presentation must distinguish the parent **GOD** event from ordinary BIG/REG hits and must not miscount guaranteed-stock BIGs as ordinary normal games.

## Reuse from current PiriJuggler

Reuse unless implementation proves a direct conflict with this locked design:

- current BIG / REG base game;
- current reel arrays and normal stop behavior;
- current BAR-denial logic;
- current Piri Chance lamp system;
- current Paper-authoritative game-state design;
- current Fabric reel/UI rendering infrastructure;
- current bonus/history persistence infrastructure where compatible.

Explicitly remove/replace:

- current premium BIG behavior that can produce BAR-BAR-BAR;
- any rule that treats BAR-BAR-BAR as a BIG presentation;
- any GOD-era retired workflow dependency.

## Economy values intentionally pending

These values must be determined in NEXT Phase 03 against the target payout:

- normal bonus -> heaven entry probability;
- heaven bonus -> heaven continuation/re-entry probability;
- final normal/heaven BIG/REG weights if the common setting balance cannot meet the targets;
- any small numerical fine-tuning of GOD continuation rates required by whole-machine simulation;
- any additional GOD-chain weighting required to hit payout targets without changing the locked 5-BIG guarantee or the increasing-by-setting continuation structure.

NEXT Phase 02 must expose these as explicit authoritative configuration/tuning values rather than burying them in presentation code.

## Locked GOD presentation/audio asset contract

The implementation must register a dedicated replaceable sound for the GOD freeze:

- file: `god_freeze.ogg`
- SoundEvent ID: `piri:god_freeze`
- use: played at GOD-confirmed lever-on together with the blackout/freeze transition
- missing file behavior: silent fallback; gameplay continues and GOD remains authoritative

Existing normal `lever.ogg` remains the ordinary lever sound. The GOD-confirmed lever event uses the dedicated GOD freeze sound path so the user can replace it without changing code.

No extra automatic voice/music asset is required for Phase 02. Later presentation work may add optional GOD-chain BGM without changing gameplay semantics.

## Phase 01 completion

All gameplay-critical successor design items are now classified.

The only intentionally tunable items are the explicit economy values assigned to NEXT Phase 03. They are not design blockers.

Status may be set to COMPLETE. Stop after Phase 01; do not begin NEXT Phase 02 automatically.


## Locked payout targets

Whole-machine target payout including normal BIG/REG, heaven behavior, GOD, GOD continuation, and all ordinary-role economics:

- Setting 1: **97.5%**
- Setting 2: **99.0%**
- Setting 3: **101.5%**
- Setting 4: **105.0%**
- Setting 5: **109.5%**
- Setting 6: **115.0%**

These are the final target payout values for tuning. NEXT Phase 03 must fit normal BIG/REG probabilities and heaven transition/continuation behavior to these targets while preserving the locked GOD probability, 5-BIG guarantee, and setting-dependent continuation structure unless a later explicit user-approved specification change overrides them.


## Dedicated replaceable JUGGLER_GOD asset namespace

JUGGLER_GOD must not bind its long-term presentation directly to the ordinary JUGGLER asset IDs.

For every reused visual/audio asset needed by JUGGLER_GOD:
- define a JUGGLER_GOD-specific logical resource ID/path;
- initially render/play the same current JUGGLER asset as a fallback so no new artwork is required now;
- allow a resource pack or later committed asset at the dedicated JUGGLER_GOD path to override that fallback without modifying gameplay code or the ordinary JUGGLER assets;
- ordinary JUGGLER continues using its original resource IDs.

Initial dedicated visual namespace:
- `piri:textures/juggler_god/symbols/*`
- `piri:textures/juggler_god/lamp/*`
- `piri:textures/juggler_god/ui/*`

Initial dedicated audio namespace/IDs:
- normal reused sounds may have JUGGLER_GOD-specific aliases where presentation needs independent replacement;
- `piri:god_freeze` remains dedicated to GOD freeze and never aliases ordinary lever behavior semantically.

The current appearance may therefore be identical to JUGGLER while remaining independently replaceable later.


## User-approved gameplay amendment — visible bonus-in-bonus stock

Approved after the original Phase 01 lock and before final Phase 03 payout fitting.

- During JUGGLER_GOD bonus games, BIG / REG / GOD are also eligible to be drawn as additional bonus-in-bonus hits.
- A newly drawn bonus is visibly confirmed: Piri Chance may light and the corresponding bonus symbol alignment is shown rather than silently adding an invisible stock.
- Additional bonus stock is preserved; confirming an added bonus must not discard the unfinished payout remainder of the currently active bonus.
- A dedicated STOCK indicator exposes only a boolean state:
  - off when there is no **bonus-in-bonus additional stock**;
  - on when one or more such additional stocks exist;
  - exact stock count is never displayed.
- The initial five BIGs granted by the parent GOD do **not** light the STOCK indicator.
- BIGs produced by the parent GOD's ordinary post-guarantee continuation do **not** light the STOCK indicator.
- GOD in GOD is considered an additional bonus-in-bonus acquisition and therefore may light the STOCK indicator.
- GOD in GOD does not restart or replace the outer GOD chain and does not add another redundant end-of-chain heaven grant.
- GOD in GOD grants **seven additional BIG stocks** to the existing GOD chain.
- The outer GOD chain's existing continuation process and final heaven transition remain intact.
