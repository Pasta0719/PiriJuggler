# JUGGLER_GOD / EXTREME — High and Ultra-high 2026-10-10

This proposal keeps existing cabinet, reels, Piri Chance bonus-only lamp and bonus-only notice sounds. HIGH / ULTRA are invisible internal states; use small-role streaks and resulting bonus wins to infer them. No visual asset or sound effects are introduced.

## Bonus targets and persistence
- HIGH: 20 normal lever games, per-game underlying bonus chance `1-0.60^(1/20)`; 40% in a full uninterrupted 20G window.
- ULTRA: 15 normal lever games, per-game underlying bonus chance `1-0.30^(1/15)`; 70% in a full uninterrupted 15G window.
- A separate next-lever small-role-trigger win can add to those probabilities. Bonus or GOD resets HIGH/ULTRA. Heaven and GOD_CHAIN retain priority.
- Setting-specific BIG/REG role family mix preserved, including existing CHERRY/PIERO overlap.
- The runtime persists mode name, remaining HOT games and streak code in the existing JSON to permit server restart/reconnect.

## Trigger table (on next normal lever)
| Role | Bonus draw | Mode promotion after failed bonus |
|---|---:|---|
| grape 3 | 0% | HIGH 15% |
| grape 4 | 20% | HIGH 20%, ULTRA 3% |
| grape 5 | 100% | - |
| replay 3 | 0% | HIGH 15% |
| replay 4 | 20% | HIGH 18%, ULTRA 5% |
| replay 5 | 100% | - |
| cherry 1 (standalone) | preserve overlap | HIGH 5% |
| cherry 2 | 40% | HIGH 20%, ULTRA 20% |
| cherry 3 | 100% | - |
| bell | 35% | HIGH 15%, ULTRA 10% |
| piero (standalone) | existing overlap + 15% | HIGH 20%, ULTRA 5% |

HIGH promotion from HIGH -> ULTRA; promotion from ULTRA refreshes its 15G timer. ULTRA promotion directly selects ULTRA 15G.

## Rebalanced base bonus scales (ppm)
| Setting | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---:|---:|---:|---:|---:|---:|
| GOD | 511253 | 515456 | 524724 | 541234 | 547868 | 521596 |
| EXTREME GOD | 373441 | 374111 | 389811 | 407855 | 424958 | 406753 |

Targets: 97.5%, 99.0%, 101.5%, 105.0%, 109.5%, 115.0%. An **independent simulation**, not the production Java engine, processed two 500m-game RNG series per profile/setting (12 billion normal levers total). Its maximum observed gap from a target was **0.091 percentage points**. Its GOD-in-bonus and recovery behavior are approximations; production integration tests and real-client runtime checks remain required before merging.

The 40%/70% windows describe direct basic bonus probabilities, not a guarantee that every promotion results in a bonus. Existing GOD, heaven and bonus-stock priority rules are unchanged.
