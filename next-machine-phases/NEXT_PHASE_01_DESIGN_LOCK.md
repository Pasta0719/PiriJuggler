# NEXT PHASE 01 — Successor Concept / Game Loop / Presentation Lock

Status: **NOT_STARTED**

## Goal

GODを前提にせず、新方式のゲームとして何を作るかを一つの仕様に固定する。

## Must lock before implementation

- machine / game type
- one-play core loop
- reel count and reel-stop/input rules
- normal-state objective
- hit/bonus/AT/ST/etc. state structure if any
- payout / replay / credit semantics
- player information and hidden information
- LCD/presentation scope
- animation level and what can be static
- audio role
- user-replaceable asset structure
- Paper-authoritative vs Fabric presentation responsibilities
- persistence/recovery requirements
- multiplayer/spectator behavior
- which existing Juggler/GOD components are reused, rewritten, or discarded
- what makes the machine interesting when presentation is simplified

## Forbidden in this phase

- guessing missing game mechanics
- treating old GOD behavior as inherited
- production gameplay implementation before the design is locked
- inventing probabilities just to make implementation progress

## Exit gate

A single successor master specification exists, every required item above is classified as LOCKED / intentionally configurable / explicitly unknown, and the user-selected direction is unambiguous.

When complete, stop. Do not begin NEXT Phase 02 automatically.
