# NEXT PHASE 01 — Juggler + GOD Design Lock

Status: **IN_PROGRESS — CORE DESIGN LOCKED; PAYOUT TARGETS / HEAVEN DETAILS PENDING**

## Core concept

The successor machine keeps the current Juggler-style BIG / REG game as the basic form, and adds a GOD-style heaven loop plus a 1/8192 premium GOD event.

This is not a recreation of the retired Piri GOD implementation. The existing PiriJuggler game is the base.

## Locked normal game

- BIG and REG remain the normal bonus types.
- Normal reel behavior remains almost the same as current PiriJuggler.
- After a normal BIG or REG ends, the machine may enter a hidden heaven state.
- When heaven is active, another BIG or REG is guaranteed/selected to occur within 32 games according to the final locked draw model.
- After a bonus won from heaven, the following 32-game period has a higher chance of remaining/returning to heaven than the 32-game period after a bonus won from normal.
- Exact heaven transition/continuation probabilities are not locked yet and must be fitted against machine payout.

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

- GOD itself is written to history as **GOD**.
- During the guaranteed GOD stock portion, game count does **not** advance between chained BIGs.
- The 1G BIG chain therefore remains grouped under the GOD event rather than appearing as ordinary independent game-count progression.
- When the guaranteed GOD stock has ended, exact history/count behavior for continuation-probability BIGs must preserve the same GOD-chain grouping unless later explicitly changed.
- After the full GOD chain ends, normal game counting resumes from the resulting heaven state.

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
- whether BIG and REG differ in heaven transition probability;
- final setting-by-setting payout targets;
- any small numerical fine-tuning of GOD continuation rates required by whole-machine simulation;
- any additional GOD-chain weighting required to hit payout targets without changing the locked 5-BIG guarantee or the increasing-by-setting continuation structure.

Do not guess these during core implementation.

## Remaining design items before Phase 01 COMPLETE

The core game design above is locked.

Still to lock before Phase 01 can be COMPLETE:

- exact definition of how a bonus is selected/fired inside the 32-game heaven window;
- whether heaven guarantees a hit inside 32G or instead applies a special high-probability draw table that can theoretically miss;
- exact history UI representation for GOD-chain BIGs;
- whether GOD-chain BIGs can contain REG substitution (currently assumed **NO**, BIG only);
- exact freeze/audio asset IDs and replaceable asset filenames.

Until those points are explicitly fixed, do not begin NEXT Phase 02 production implementation.

When Phase 01 becomes COMPLETE, stop. Do not begin NEXT Phase 02 automatically.
