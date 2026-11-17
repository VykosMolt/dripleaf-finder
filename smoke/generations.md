Generation support in 1.3.0

The additional review and 1.3.1 detector fix are recorded in [lush-neighborhood.md](lush-neighborhood.md).

The 1.3.2 client update sends finder chat through AUTISM's native messaging API, matching its prefix, theme colors, and body styling. Message text and seed redaction are preserved. The planted in-game check verifies both consecutive prediction lines remain in chat. All 198 server-engine class payloads match 1.3.1; the bundled search engines retain their 1.3.1 version.

One AUTISM 5.3 addon runs on Minecraft 26.2 and carries 32 small search engines for the other exact stable releases from 1.17 through 26.1.2. Each engine uses its own matching Minecraft server jar. Historical API compatibility ranges are not used as generation aliases. Minecraft server jars are downloaded and hash-checked when needed.

Ordinary 1.17 and 1.17.1 worlds do not generate natural lush caves. Selecting those versions explains this without launching a search. Special presets, custom generators, and server-specific placement changes are outside this standard-Overworld support.

Auto uses the version reported by the current server connection. It refreshes on reconnect and withholds cached Auto results until that connection's query completes. Standard chunk packets do not preserve the original generation version, and disk DataVersion changes when chunks are saved. Old chunks can also receive newer underground generation. Accurate automatic switching across historical generation boundaries requires known region history or server-provided provenance; this release does not infer it from terrain or biomes. Select World Version for the area being searched.

Prediction and lush files are stored separately for each server, seed hash, and exact generation version. Switching versions or seeds restores the matching saved results. A running search retains its original destination even if Auto changes or the player leaves. Old flat prediction files remain preserved; their generation is not guessed, so run a search once to populate the new folders. The typed seed retains authority over SeedcrackerX.

SeedcrackerX remains an optional, separate seed-cracking mod. This addon receives its results; its native generation engines search with an already known seed. The old standalone setup used separate SeedcrackerX jars matched to each client. The current 26.2 SeedcrackerX release is 2.16.1, whose bundled version selector has exact entries only through 1.21.3. Client-version compatibility, available model names, and successful seed cracking are distinct claims. This release does not claim seed-cracking verification for all 33 generation versions. SeedcrackerX's own seed-in-chat behavior is outside the addon's output handling.

Verification:

- All 33 exact releases build and pass their existing SelfCheck; the 26.2 client checks also pass.
- All 33 engines completed real ascending-order searches on Java 25 in 331 seconds. Every post-1.17 run produced lush-floor data; five 1.20 releases retained a naturally spliced half after ticking. [Full matrix and limits](all-engines.md).
- The final packaged artifact passes the existing six modern engine/order fixtures, with exact hit, plant, and normalized lush comparisons: `ENGINES PASS in 59 s`.
- The 26.2 client launched its restored 1.18 engine and reported the known descending-order half with six lush tiles: `SMOKE PASS`, 57 seconds.
- All 198 server-engine class payloads in the final package match the artifact used for the 33-version runtime matrix.
- The UI test physically selected and checked all 34 dropdown entries, including Auto, and saved the old 1.18 choice correctly. Seed edits, settings, Search/Stop, offline Stop, Reset, server isolation, and layout passed. The full suite still exits with failure at the effective activation check because AUTISM does not authorize the offline test identity; no assertion was skipped or weakened.

Two fresh adversarial reviewers covered the complete client, module, storage, process, engine, compatibility, packaging, rendering, and test paths. Their findings and the additional callback check were fixed:

- Queued SeedcrackerX results are discarded after the client connection changes, and ignored without AUTISM.
- IPv6 and international names use Minecraft's native status-address resolver.
- Auto queries belong to individual connection sessions; delayed earlier replies cannot replace current results.
- Initial and reconnecting Auto sessions cannot briefly load a different generation's cache before their query starts.
- Result files use temporary writes and replacement. A size-limited write failure preserved all 3,090 original bytes and left no temporary file.
- Hits saved while offline are announced once on returning to the correct server and namespace, including after the search has finished.
- Copied non-ASCII jar names use content hashes to prevent filename collisions.
- Inherited classpath jars and directories receive ASCII relative/copied paths too. Launches containing directory inputs conservatively skip remapping-cache reuse; jar-only launches keep content-based caching.

The fixes were independently checked with actual-source harnesses, controlled local status endpoints, fault injection, and preserved negative controls. Both reviews concluded with no remaining blocking or should-fix finding. Native Windows game execution and every seed-cracking strategy on every historical release were not covered by these checks.
