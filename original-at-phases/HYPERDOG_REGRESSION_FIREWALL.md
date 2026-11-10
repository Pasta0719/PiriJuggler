# HYPER_DOG regression firewall — 2026-11-10

## Scope and rationale

This is a fail-closed safeguard for the failures reported during the HYPER_DOG cabinet work. It is **not** a claim that every physical client render has already been visually accepted, and it does not change gameplay, other cabinets, or sound assets.

The guard targets the dedicated development branch `plan/original-at-machine-20261107`. Do not merge this directly to `main` without the normal project review.

## Automatic checks

1. **Actual production geometry** (`HyperDogLayoutRegressionTest`): both full-size flowers must remain clear of the pre-existing top data lamp, existing 900px reel window, and data sidebars; left/right status lamps must not collide. Tests check the recessed light margins and 5-petal envelope against the *actual Rects returned by the production layout methods*. An intentionally overlapping fake sidebar is also detected.
2. **Outside cabinet width** (`CabinetPlacementTest`): all six facings must map the wide HYPER_DOG face to exactly one block, without scaling ordinary machines.
3. **Audio protection** (`HallAudioTest`, `HyperDogAudioTest`, and locked blobs): all original sound IDs must retain audible fallbacks; public routing, OGG priorities and the seven shipped user OGG files cannot change unnoticed.
4. **Original graph reuse** (`check_hyperdog_regression_contract.py`): HYPER_DOG must display the authoritative data-lamp graph using the same `SlotScreen.drawGraph` renderer, without creating a second graph. The existing `SlotScreen` source and shared `SlotLayout` are checksum-protected.
5. **Approved symbols** (`HyperDogSymbolAssetsTest`, `validate_symbol_assets.py`, and locked asset blobs): image dimensions, transparency, original image content, and 7/BAR larger render size are checked.
6. **Other machines**: full `./gradlew test` across common, Fabric and Paper is run after every push/PR to the development branch.

CI workflow: `.github/workflows/hyperdog-regression-firewall.yml`.

## How to prove it catches regressions

- The geometry suite checks a synthetic rectangle deliberately overlapping the right flower by one pixel; the overlap predicate must reject it.
- `python original-at-phases/check_hyperdog_regression_contract.py --self-test` verifies that substituting a changed asset hash produces a detectable failure, then checks all 19 real protected files against pinned Git blob hashes.
- A real change to a protected path fails CI until its reviewed checksum is consciously updated.
- CI uploads JUnit XML even on a failed job. A green badge alone is not a substitute for these artifacts.

## Known limits — never claim full acceptance from these tests alone

- Pixel-level visual acceptance still requires a captured real Fabric client and world-render screenshot at the intended resolution. Geometry tests can catch rectangles colliding, not typography or every drawn overlay.
- An audible fallback is not proof of real human-perceived volume or timing; check using the real client speakers.
- A green CI result is proof of the checks **listed above only**. Do not announce that HYPER_DOG's entire design and playtest is complete based on this workflow.
- Changes to the actual `drawFlower` petal radii must update the regression test's matching envelope together.

## Approval policy

Do not silently update protected hashes to make CI pass. If an existing machine's sound/graph/shared layout or an approved OGG/PNG must change, document precisely why, show the old/new hashes, and get explicit approval before changing the baseline.
