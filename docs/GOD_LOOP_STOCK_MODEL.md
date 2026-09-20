# Piri GOD — Kiseki loop-stock model

New research clarified an earlier uncertainty.

The exact **GOD-specific "strong loop stock"** is still not treated as a known numeric rate.
However, Kiseki's ordinary loop-stock system itself is published in much more detail than we had previously recorded.

Published loop classes:

| class | loop rate | expected extra GG stocks if one loop is held |
|---|---:|---:|
| A | 1% | 0.0101 |
| B | 25% | 0.3333 |
| C | 50% | 1.0000 |
| D | 80% | 4.0000 |

Expectation is geometric:

expected extra stocks = r / (1-r)

Published examples of loop-stock mix include:

- Heaven GG hit: 1%=25.0%, 25%=25.0%, 50%=46.9%, 80%=3.1%
- Super-Heaven GG hit: 50%=75.0%, 80%=25.0%
- blue-7 history hit, 3–4 chain: 1%=99.6%, 80%=0.4%
- blue-7 history hit, 5+ chain: 1%=50.0%, 25%=41.8%, 50%=7.8%, 80%=0.4%
- yellow-7 history GG hit: 1%=41.8%, 25%=33.2%, 50%=14.8%, 80%=10.2%

At Piri's current +350 net medals per plain GG set:

- Heaven mix contributes about +237.6 net medals of loop-stock EV per applicable GG hit.
- Super-Heaven mix contributes +612.5 net medals.

These values are now modeled exactly.

Important distinction:
- ordinary Kiseki loop-stock classes and many situational distributions: known
- GOD-specific "strong loop stock" exact numeric distribution/rate: still not assumed without a source

This lets the Piri economy model use authentic Kiseki loop math instead of a made-up aggregate loop value.
