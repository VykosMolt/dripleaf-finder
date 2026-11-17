# Search performance verification

Baseline: `72a4e22`. Seed: `6608149111735331168`, centre `(0, 0)`.
Both builds use the same game jars, Fabric Loader 0.20.1 and Java 25.
Each server uses `-Xmx(1024 + radius / 3)M` and `-XX:+ExitOnOutOfMemoryError`.
All launches use the shared `slot.sh` limit. Times exclude waiting for a slot.

The optimized build caches the five Y-independent climate parameters per quart
column, selects a fixed floor chunk on exact ties, uses LZ4 region compression,
and retains `.fabric` only while every launch jar has the same content hash.
There is no world-generation threading or report-prefetch change.

## Climate equivalence

The noise settings and referenced density functions were read from each version's
own server jar, for 1.21.3, 1.21.11 and 26.2. Temperature, vegetation, continents,
erosion and ridges resolve through `flat_cache`/`cache_2d` and `shifted_noise`
with `y_scale: 0` and zero `shift_y`; the shift functions also ignore Y.
The sampler's temperature, humidity, continentalness, erosion and weirdness can
therefore be computed once at Y=0. Depth depends on Y and is still evaluated at
every sampled position. Float conversion and climate quantization are unchanged.

## Measurements

The 26.2 runs use radius 2500, in A/B/A/B order. Both decoration orders run
together. Ascending runs include the floor pass; descending runs omit it.
The older-version runs use radius 1000. Phase times come from status output,
sampled every 0.2 seconds, and include JVM startup in the total.

Shared machine load varied between runs. These measurements show observed
wall times; they do not isolate a clean speedup caused by these changes.

| Version / radius / order | Run | Load mean / max | Startup | Lush | Scan/floors | Decorate | Report | Check | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 26.2 / 2500 / asc | base1 | 23.5 / 34.6 | 9.1 | 33.3 | 114.4 | 133.0 | 38.7 | 8.4 | 336.9 |
| 26.2 / 2500 / desc | base1 | 21.8 / 31.9 | 5.9 | 32.0 | 0.4 | 217.1 | 35.7 | 4.6 | 295.8 |
| 26.2 / 2500 / asc | optimized1 | 14.1 / 25.2 | 7.9 | 27.5 | 92.3 | 114.5 | 22.0 | 8.2 | 272.5 |
| 26.2 / 2500 / desc | optimized1 | 14.6 / 25.2 | 7.9 | 26.7 | 0.2 | 193.5 | 21.2 | 4.2 | 253.7 |
| 26.2 / 2500 / asc | base2 | 18.8 / 34.4 | 7.7 | 42.9 | 97.6 | 116.3 | 30.8 | 0.4 | 295.7 |
| 26.2 / 2500 / desc | base2 | 19.3 / 34.4 | 7.5 | 40.3 | 0.4 | 202.1 | 29.8 | 4.4 | 284.5 |
| 26.2 / 2500 / asc | optimized2 | 18.5 / 38.3 | 7.9 | 34.5 | 85.1 | 112.3 | 21.8 | 8.4 | 270.1 |
| 26.2 / 2500 / desc | optimized2 | 19.1 / 38.3 | 7.9 | 34.5 | 0.4 | 183.0 | 20.0 | 4.6 | 250.4 |
| 1.21.11 / 1000 / asc | base | 10.9 / 13.2 | 9.7 | 7.6 | 11.8 | 17.4 | 3.8 | 0.4 | 50.8 |
| 1.21.11 / 1000 / desc | base | 11.0 / 13.2 | 9.5 | 7.0 | 0.4 | 27.7 | 3.0 | 0.4 | 48.0 |
| 1.21.11 / 1000 / asc | optimized | 13.9 / 16.1 | 9.7 | 5.0 | 11.8 | 17.4 | 2.8 | 0.4 | 47.2 |
| 1.21.11 / 1000 / desc | optimized | 14.0 / 16.1 | 9.5 | 4.8 | 0.4 | 27.1 | 2.2 | 0.2 | 44.2 |
| 1.21.3 / 1000 / floors | base | 11.7 / 12.4 | 9.7 | 4.2 | 10.6 | — | — | — | 24.6 |
| 1.21.3 / 1000 / floors | optimized | 11.9 / 13.2 | 6.9 | 2.8 | 10.8 | — | — | — | 20.6 |

The scan/floors column includes the startup-chunk scan and, for ascending runs,
the floor pass. Checking measures the actual interval spent advancing the
required 200 game ticks; an overloaded server can catch up faster than real time.

## Output parity

- 26.2 radius 2500, both full orders in both A/B pairs: hit lines match byte for
  byte. All 419 ascending lush tiles have identical floor totals. Their positions
  match except for the two proved maximum-count ties listed below. The two
  optimized runs have identical sorted lush output.
- 1.21.11 radius 1000, both full orders: all hit lines and all 48 ascending lush
  rows match exactly after sorting. Descending runs skip floor output.
- 1.21.3 radius 1000, floors only (`splices=false`): all 47 tile totals match;
  the only representative difference is the proved tie below.
- The known-hit engine gate compares expected and actual lush rows using the
  same explicit `LC_ALL=C sort`. It retains duplicates and exact hit bytes/order
  and plant totals. The stored fixture had a different collation order; normalizing
  both sides removes that locale dependency. Only the two 1.21.3 fixture rows for
  the newly fixed representative were changed. Independent negative controls
  confirmed detection of missing/reordered/changed hits, changed plant counts
  and lost duplicate lush rows.

## Exact floor ties

Floor counts and all hit lines are unchanged. Two 26.2 lush-tile representatives and two 1.21.3 representatives
change because the old code chose whichever equally dense chunk it visited first.
The new code chooses the smaller chunk X, then Z. Instrumented floor output
confirms both alternatives have exactly the same maximum count:

| Tile | Old position | Fixed position | Maximum in either chunk | Tile floor count |
| --- | --- | --- | ---: | ---: |
| 26.2 `(13, -6)` | `(1688, -6, -696)` | `(1672, -2, -696)` | 253 | 3080 |
| 26.2 `(16, 5)` | `(2136, 8, 760)` | `(2120, 5, 760)` | 318 | 2069 |
| 1.21.3 `(-1, -1)` | `(-72, -8, -8)` | `(-88, -1, -8)` | 256 | 1516 |
| 1.21.3 fixture `(48, 38)` | `(6264, 60, 4984)` | `(6248, 54, 4984)` | 349 | 1262 |

Two earlier 26.2 floor-only runs and both optimized full runs choose the same
fixed positions. Temporary 1.21.3 probes confirmed both additional exact ties.
Those probes were removed and the ordinary engines were rebuilt before the final gate. This is an intentional deterministic tie-break, rather than a
byte-identical lush-position result against the old arbitrary choice.

## Remap cache

`SearchRun.clear` removes the previous world and other run files while retaining
`.fabric` only if the game, engine, mod, loader and mapping jar hashes match.
The existing SHA-1 helper supplies content fingerprints; timestamps and sizes
alone are insufficient. ClientCheck verifies retention on an unchanged launch,
world removal, and invalidation after changing each jar while keeping its size
and modification time identical.

The 1.3.0 directory-classpath fallback conservatively disables cache reuse for
launches with directory inputs. This avoids recursively fingerprinting a game
directory that can itself contain the search cache and previous search output.
The measured jar-only launches retain the content-hash policy above.

Cold and warm launches use fresh worlds and the same retained remap directory.
Both pairs have exactly matching sorted hit/lush/spawn output (ascending order,
0 hits and 12 lush rows on 1.21.11; 0 hits and 20 lush rows on 1.21.3).

| Version | Cache | Load mean / max | Startup | Total |
| --- | --- | ---: | ---: | ---: |
| 1.21.11 | cold (2 files) | 10.6 / 11.6 | 8.5 | 22.4 |
| 1.21.11 | warm (2 files) | 14.2 / 21.7 | 5.3 | 20.0 |
| 1.21.3 | cold (2 files) | 22.1 / 23.9 | 8.4 | 23.6 |
| 1.21.3 | warm (2 files) | 20.8 / 23.2 | 6.5 | 20.0 |

## Gates and limits

- `./build.sh 1.21.3 1.21.11 26.2`: PASS. SelfCheck passed on all three;
  ClientCheck passed on 26.2, including cache lifecycle checks.
- `SLOT=<slot.sh> smoke/engines.sh dist/dripleaf-finder-1.2.0+mc26.2.jar`:
  `ENGINES PASS in 47 s`, all six version/order cases, with LZ4 enabled.
- Radius-2500 A/B/A/B and older-engine comparisons: PASS after confirming the
  exact maximum-floor ties above; no changed hit bytes or floor totals.
- Real cold/warm `.fabric` cache runs: `CACHE PARITY PASS` on both older engines.
- After removing temporary probes, every main and bundled-engine class payload
  matched the previously benchmarked ordinary artifact byte for byte.
- `git diff --check`: PASS.

Asynchronous report preloading is omitted. The earlier profiler's eager preload
experiment ran out of memory in three radius-2500 runs; this change adds no
replacement prefetch scheme. No parallel generation experiment is included.

Raw measurements and the recovered benchmark scripts are in
`/home/moloch/dripleaf-perf-recovery/` on the development machine. The duplicated
first-baseline descending summary and initial shell harness errors in `bench.log`
do not represent additional measurements; completed result files were preserved
and the sequence resumed at the first optimized pair.
