# GOD bell-chain benchmark comparison

This is a benchmark, not a locked gameplay parameter.

For the current high-pure-increase reference, the published Kamigami no Kiseki role table lists push-order yellow 7 at 1/1.7 during normal/AT play. If Piri GOD were to count that common yellow-7/bell stream directly for a visible streak, a short guaranteed streak reward would be extremely expensive.

Using:
- GG length = 50 games
- common qualifying bell benchmark = 1/1.7
- one V stock economic value = one plain GG set = +350 net medals
- every bell from the threshold onward is another qualifying opportunity

the exact finite-horizon model gives:

| threshold | expected qualifying opportunities / 50G | EV if every opportunity gives +1 stock |
|---:|---:|---:|
| 4 | 5.6273 | +1969.6 medals |
| 5 | 3.2398 | +1133.9 medals |
| 6 | 1.8643 | +652.5 medals |
| 7 | 1.0723 | +375.3 medals |
| 8 | 0.6164 | +215.7 medals |

So copying a Gaisen-like guaranteed reward at a short streak threshold into a 1/1.7 bell environment is economically impossible unless a very large share of the machine's total EV is assigned to bell-chain stocks.

This does NOT mean the mechanic must disappear.

It means the final design has three honest levers:
1. raise the visible streak threshold,
2. make threshold hits probabilistic rather than guaranteed,
3. define a narrower qualifying-bell class whose actual probability is lower.

We should not choose among those until the total GG-side EV budget and the one-stock continuation value are solved.

Historical comparison: Gaisen used consecutive yellow-7 history rewards, with 4 and 5 consecutive yellow 7s guaranteeing GG rewards. Kamigami no Kiseki removed ordinary AT yellow-7 streak stock draws because of its higher pure increase and moved the streak concept into Z-ZONE/Z-GAME instead.
