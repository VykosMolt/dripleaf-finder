# All-generation runtime verification

On 17 November 2026, all 33 exact stable releases from 1.17 through 26.2 completed
an ascending-order search using the single 26.2 AUTISM addon and its bundled
engines. The command exited 0:

```
ALL ENGINES PASS: 33 exact versions on Java 25 in 331 s
```

The tested addon was frozen before launch, with SHA-256
`158777edf852bb3610cde4f64e5a22e54c44ad0fc60477526d2e6dcb1677e6ae`.
The final addon can differ in client-only storage or UI code; server engine class
payloads must match this artifact before these results apply to another package.

Run the matrix with:

```
SLOT=<game-launch limiter> smoke/engines-all.sh <addon jar>
```

The harness checks every exact-release engine's Minecraft metadata and bundled
SHA-1, verifies each Mojang server jar and Fabric intermediary mapping jar, and
uses Fabric Loader 0.20.1 with the same Java 25 runtime as the client child-launch
path. At most four local server processes run through the shared limiter. Each
uses a fresh ordinary Overworld, the measured search heap formula, ascending
chunk order, floor measurement, and splice verification after ticking.

Every 1.18-and-later run completed finding, spawn scanning, floor measurement,
decoration, and checking, and produced positive lush-floor rows. No wider-area
fallback was needed. The 1.17 and 1.17.1 engines explicitly reported that ordinary
worlds have no naturally generated lush caves; their empty results are expected.

The seed was `6608149111735331168`. Search areas were `(2589, 665)` at radius 128
for 1.18 releases; `(3584, -3422)` at radius 128 for 1.19 and 1.20 releases;
`(6300, 5190)` at radius 128 for 1.21.3; `(1500, -160)` at radius 200 for 26.2;
and `(5988, 5144)` at radius 128 for the other post-1.20 releases. The two 1.17
runs used the origin and radius 64. Search seconds exclude startup and slot wait.

| Exact release | Retained halves | Lush floor tiles | Small-dripleaf blocks | Search seconds |
| --- | ---: | ---: | ---: | ---: |
| 1.17 | 0 | 0 | 0 | 0 |
| 1.17.1 | 0 | 0 | 0 | 0 |
| 1.18 | 0 | 6 | 118 | 14 |
| 1.18.1 | 0 | 6 | 118 | 14 |
| 1.18.2 | 0 | 6 | 114 | 16 |
| 1.19 | 0 | 9 | 754 | 16 |
| 1.19.1 | 0 | 9 | 754 | 15 |
| 1.19.2 | 0 | 9 | 754 | 15 |
| 1.19.3 | 0 | 9 | 768 | 13 |
| 1.19.4 | 0 | 9 | 760 | 14 |
| 1.20 | 0 | 9 | 760 | 14 |
| 1.20.1 | 0 | 9 | 760 | 14 |
| 1.20.2 | 1 | 9 | 753 | 15 |
| 1.20.3 | 1 | 9 | 753 | 14 |
| 1.20.4 | 1 | 9 | 753 | 13 |
| 1.20.5 | 1 | 9 | 753 | 15 |
| 1.20.6 | 1 | 9 | 753 | 14 |
| 1.21 | 0 | 6 | 126 | 13 |
| 1.21.1 | 0 | 6 | 126 | 13 |
| 1.21.2 | 0 | 6 | 140 | 12 |
| 1.21.3 | 0 | 9 | 422 | 14 |
| 1.21.4 | 0 | 6 | 110 | 13 |
| 1.21.5 | 0 | 6 | 108 | 14 |
| 1.21.6 | 0 | 6 | 108 | 13 |
| 1.21.7 | 0 | 6 | 108 | 14 |
| 1.21.8 | 0 | 6 | 108 | 14 |
| 1.21.9 | 0 | 6 | 108 | 14 |
| 1.21.10 | 0 | 6 | 108 | 14 |
| 1.21.11 | 0 | 6 | 108 | 14 |
| 26.1 | 0 | 6 | 114 | 14 |
| 26.1.1 | 0 | 6 | 114 | 14 |
| 26.1.2 | 0 | 6 | 114 | 13 |
| 26.2 | 0 | 16 | 832 | 17 |

Five releases, 1.20.2 through 1.20.6, retained the naturally generated upper half
at `(3586, 23, -3422)` after ticking. Its lower half was cut by smooth basalt;
the placement chunk was `(223, -215)` and the cutter chunk was `(223, -214)`.
The same area produced zero retained halves and 760 small-dripleaf blocks in
1.20/1.20.1, compared with one retained half and 753 blocks in 1.20.2–1.20.6.
Each release therefore ran its own game generator, rather than an API-range alias.

This matrix is a runtime and output-integrity check, not byte-parity proof for
all generators or a claim that every sampled area must contain a splice. The
existing six-case `smoke/engines.sh` regression remains the strict known-output
comparison for 1.21.3, 1.21.11, and 26.2 in both decoration orders. Fresh-world
searches also do not identify historical generation versions in multiplayer
chunks or reproduce mixed-version boundaries and custom generators.

The harness rejects missing/duplicate/inconsistent done records, engine errors,
malformed records or hit provenance, unknown engine versions, incorrect bundled
metadata/hashes, and missing generation phases. A post-1.17 run with no lush
floor rows retries a larger area and remains limited/failing if still empty.
Executable negative controls checked those failure paths before the matrix.

Raw logs, search properties, structured summaries, and result records remain in
`/home/moloch/dripleaf-all-generators-runtime/matrix-tROVkA/` on the development
machine. All matrix-owned generated worlds and Fabric/bootstrap caches were
removed after their servers exited.
