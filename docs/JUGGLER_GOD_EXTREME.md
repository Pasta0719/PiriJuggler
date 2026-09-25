# JUGGLER_GOD_EXTREME

JUGGLER_GOD_EXTREME is a high-volatility probability profile that reuses the existing JUGGLER_GOD gameplay, presentation, runtime state, assets, sounds, history, heaven flow, stock flow and GOD presentation.

## Locked economy

Target payout percentages remain identical to JUGGLER_GOD:

- setting 1: 97.5%
- setting 2: 99.0%
- setting 3: 101.5%
- setting 4: 105.0%
- setting 5: 109.5%
- setting 6: 115.0%

Profile values:

- GOD: 1/16384
- BIG gross payout: 420 medals
- REG gross payout: 168 medals
- small-role scale: 700000 ppm for settings 1-6
- normal BIG -> heaven: 60000 ppm (6%)
- normal REG -> heaven: 30000 ppm (3%)
- heaven -> heaven: 700000 ppm (70%)
- GOD guaranteed BIGs: 8 total
- GOD-in-GOD: +10 BIG stock
- post-guarantee GOD continuation: 75 / 78 / 80 / 82 / 85 / 90%

RTP-fit bonus scales, including 1/16384 GOD, 8 guaranteed BIGs, +10 GOD-in-GOD, 420/168 payouts, 70% heaven loop and 70% small-role scale:

- setting 1: 566259 ppm
- setting 2: 557664 ppm
- setting 3: 563702 ppm
- setting 4: 569053 ppm
- setting 5: 577396 ppm
- setting 6: 545547 ppm

## Commands

Dedicated EXTREME commands:

- `/piri jgextreme create`
- `/piri jgextreme setting <id> <1-6>`
- `/piri jgextreme role <id> <role|clear>`
- `/piri jgextreme info <id>`

Alias: `/piri extreme ...`

Generic machine commands also accept `JUGGLER_GOD_EXTREME`:

- `/piri machine create JUGGLER_GOD_EXTREME`
- `/piri machine type <id> JUGGLER_GOD_EXTREME`
- `/piri machine setting <id> <1-6>`

## Preservation

The ordinary JUGGLER_GOD profile remains unchanged: 1/8192 GOD, 280/112 bonus payouts, five guaranteed GOD BIGs and +7 BIG GOD-in-GOD stock.
