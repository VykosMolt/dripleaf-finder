Lush-neighborhood verification in 1.3.1

An additional adversarial review found that the client sampled only 27 positions when checking for a nearby lush biome. It could skip an in-range biome cell, announce that an otherwise valid predicted upper half was not there, and discard that prediction for the session.

The check now covers every block in its existing inclusive neighborhood: eight blocks in either horizontal direction and four vertically. It tests the center first and reuses one mutable position for the remaining checks. The maximum is 2,602 lookups, including the repeated center; a lush center exits after one lookup. The search bounds and other plant-validation rules are unchanged.

Four-block sampling was also insufficient. An independent test using Minecraft's actual BiomeManager, seed zero, and a synthetic noise-biome source confined lush caves to quart cell `(-2,14,-2)`. The target was `(0,64,0)`. The in-range block `(-8,60,-5)` was lush, while all 75 four-block samples missed it. This is a controlled logic reproduction, not a claim that a natural seed-zero world contains that exact isolated biome pocket.

The regression lives in the existing ClientCheck file. It covers skipped cells, negative coordinates, diagonal and inclusive edges, outside-range negatives, a thin off-grid region, and the real BiomeManager case. An independent reviewer also tested every one of the 2,601 in-range positions, six immediately outside positions, mutable caller preservation, and the center fast path. The same frozen regression fails against the original 1.3.0 source and passes against commit `efa0ff3`.

All 33 exact-version builds and existing self-checks passed. The rebuilt package was tested again:

```
ALL ENGINES PASS: 33 exact versions on Java 25 in 283 s
ENGINES PASS in 230 s
SMOKE PASS 26.2 planted: positive reported immediately; outside-lush control rejected; 318 green pixels, box centred
```

The six strict modern fixtures retain exact hit, plant-count, and normalized lush comparisons. Slot contention is included in the longer regression duration; it is not a performance comparison.

Across the full 33-version matrix, every hit line and sorted lush row matches the earlier 1.3.0 matrix. Diagnostic plant totals changed in three older runs: 1.18 and 1.18.1 from 118 to 120, and 1.18.2 from 114 to 112. A repeat using the unchanged 1.3.0 artifact also produced 120 plants on 1.18, versus its earlier 118. The cause of that diagnostic variation was not established; candidates and retained hits remained zero in those cases. The changed neighborhood helper is used by client detection, not the server search predicates.

The new matrix output is in `smoke/out/all-engines-20261117T123055Z-0SVaNd/` on the development machine. Independent reproduction sources are in `/tmp/dripleaf-generation-review-efa0ff3/`. The reviewer accepted the fix and found no additional concrete adapter defect. No complete UI rerun was needed for this detector-only change; the 1.3.0 UI coverage and its offline-identity licence limitation remain documented in generations.md.

Released addon SHA256:

```
50f908ef790f27bc36440b94dc8b16cad032fcf88005d04029af3bd7399bd016
```
