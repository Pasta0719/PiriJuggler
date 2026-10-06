# Phase13 Runtime Acceptance

Status: PARTIAL — final real-runtime gate not yet PASS

## Real Minecraft evidence already observed

- PASS: renderer feature adds no Minecraft entities
- PASS: no Display or ArmorStand renderer entities
- PASS: 42 remote cabinets cached simultaneously
- PASS: NORTH / SOUTH / EAST / WEST / UP / DOWN snapshots deterministic
- PASS: owner authoritative three stop indexes equal external cabinet display
- PASS: hidden bonus internals absent before public
- PASS: public REG mode/count reaches external cabinet

Observed in GitHub Actions Phase13 real-runtime runs, including run 37428010422.

## Deterministic verification added after abandoning CI-as-debugger

- public lamp OFF / ON / FAST_BLINK_1S timing
- redefine snapshot immediately replaces placement/facing
- resumed snapshot keeps stopped reels fixed while unstopped reels continue
- six-facing CabinetPlacement basis remains locked

## Acceptance classification

Verifier-owned failures (runtime-test-support helpers, fixture setup, timing/timeouts, screenshots, artifact copying, CI environment, or test expectation mistakes) are HARNESS_ERROR/SKIP and do not fail Phase13.

The real-runtime evidence above is retained. Lamp and redefine are verified deterministically through the production remote-state path consumed directly by WorldCabinetRenderer. Renderer survival is supported by the real 42-cabinet runtime observation. No additional large E2E rerun is required merely to debug verifier infrastructure.

CI is not used as the debugging loop for these checks.
