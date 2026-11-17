Recovery verification, 17 November 2026

The release is one AUTISM 5.3 addon for Minecraft 26.2, bundling the 1.21.11 and 1.21.3 search engines. Work was recovered from Claude session `f024d516-abe3-4df9-a259-fe92aec24786`.

The build and existing self/client checks passed on all three versions. Searches through the 26.2 client returned the expected results:

| Selected generation version | Halves | Lush tiles |
| --- | ---: | ---: |
| 26.2 | 1 | 25 |
| 1.21.11 | 1 | 12 |
| 1.21.3 | 2 | 20 |

A separate search confirmed that the typed seed wins over a different SeedcrackerX seed. Executable lifecycle checks confirmed that Stop saves unread hits, records a dropped queued search, retains its result after disconnecting, and reports exit code 3 as out of memory. Failure checks confirmed that unsuccessful result archiving or metadata replacement preserves the previous seed/version and prediction/lush data.

Planted detection and Folia server-type smokes passed with an explicit enabled-state fixture confined to the separate smoke mod. The real AUTISM UI test passed seed editing, version choices, server-type persistence, radius/memory controls, Search/Stop, offline Stop, Reset, server isolation, and layout checks across 33 screenshots. The full UI suite still reports failure for its effective enable-toggle check: AUTISM did not authorize the offline test identity. Its saved module setting remained enabled. A separate lifecycle harness verified recovery when licence readiness changes from pending to ready, without an unintended settings write. The AUTISM jar and original access files were not modified.

Auto uses the server's reported current version. It does not identify historical generation versions per region. Mixed-version terrain requires choosing the generation version for the area being searched; a search spanning a generation boundary can produce incorrect or missing predictions. Loaded-block detection still checks the actual terrain. Custom generators outside the bundled engines are not modelled.

Singleplayer storage now uses the full canonical save path, with a bounded readable prefix and a hash. Ambiguous legacy local-world folders remain intact but are not automatically assigned to a world; previously stored local seeds must be entered again. Existing ordinary multiplayer server keys remain stable.

The parallel-generation prototype remains excluded on branch `opt-tiles`, commit `939e87b`. Its corrected extra-dimension test differed on 1,520 of 6,291,456 blocks; multi-JVM parity was not qualified. Safe performance measurements and parity checks are recorded in [performance.md](performance.md).

Generated files from the old main checkout were preserved in the Git stash named `Preserve generated dist files from before the Claude recovery`.
