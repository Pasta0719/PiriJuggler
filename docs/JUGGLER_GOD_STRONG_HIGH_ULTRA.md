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

## Context-led initial bonus distribution (2026-10-10 revision)

The main ordinary BIG/REG initial-hit route now comes from visibly recognizable small roles and the HIGH/ULTRA state they can cause. There are no new sound assets, no new screen elements and no modification of Piri Chance semantics.

- Consecutive grape 2: next-lever HIGH chance per setting; consecutive replay 2: same
- Consecutive grape/replay 3: existing 15% next-lever HIGH draw
- Consecutive grape/replay 4: next-lever 20% bonus; if missed, chance to move up
- Consecutive grape/replay 5: bonus on the next lever (unless the existing GOD-priority draw wins)
- Single cherry (non-bonus result): next-lever HIGH chance increased from 5% to 8%
- Cherry two/three, bell and piero retain the existing small-role bonus/upgrading chance table
- HIGH 20G 40% and ULTRA 15G 70% base-bonus windows are unchanged

Main paths are small-role triggered bonus (approx 12–16%), HIGH base bonus (approx 41–43%) and ULTRA base bonus (approx 8–11%), adding to **65% cause-linked first hits** for every setting/profile. Direct ordinary-base mystery hits account for the remaining **35%**. GOD, heaven, GOD chain, and bonus-game stock are *excluded* from these initial-hit route percentages.

To preserve target RTP 97.5%, 99%, 101.5%, 105%, 109.5%, 115%, the normal-mode base odds are lowered as mode chances rise. New `precursor_two_high_ppm` values are deliberately per-profile/per-setting to reach approximately 65/35 without making one setting's payout disproportionately higher. New config is loaded on clean installs; on upgrades, stock (unaltered) rows from older versions are migrated automatically while customized profile rows remain untouched.

| Setting | GOD base ppm | GOD two-streak HIGH ppm | EXTREME base ppm | EXTREME two-streak HIGH ppm |
|---|---:|---:|---:|---:|
| 1 | 297986 | 94884 | 224070 | 72001 |
| 2 | 296030 | 105041 | 221440 | 78911 |
| 3 | 298549 | 114326 | 225491 | 92247 |
| 4 | 303386 | 130995 | 230039 | 110987 |
| 5 | 306204 | 139760 | 235721 | 127811 |
| 6 | 289197 | 153160 | 223878 | 139688 |

The 65% share and modeled RTP results come from an independent stationary Markov reward model, **not** a production Paper server run. Both bonus-family and heaven/GOD economy were included with the previous approximation of interrupted GOD bonuses. Verify with production-path sampling and full gameplay runtime acceptance before deploying.


## Machine-history generation (/piri sim)

`/piri sim <machineId|all> <games>` changes actual period data: BIG/REG bonus history, graph, current/total game counts and difference. In GOD/EXTREME GOD this uses `JugglerGodMachineDataSimulator`, **not** the separate obsolete `/piri godsim` economy probe.

The machine-history simulation now reproduces the production normal-game small-role streak, 2-streak promotion by setting, high/ultra 40/70-percent base drawing, countdown, reset on BIG/REG/GOD, and saved SIM_CURSOR continuity between calls. Real plays supersede and clear an existing synthetic SIM_CURSOR. This simulator remains an approximation for visuals and bonus-chain accounting, not a replacement for the production game-state engine; verify end-to-end on an actual Minecraft server.

