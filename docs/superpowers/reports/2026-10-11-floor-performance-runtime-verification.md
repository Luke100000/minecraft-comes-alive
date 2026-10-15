# Floor performance runtime verification — 1.21.1

## Outcome

The earlier POI/materialization changes are verified more broadly, and a further
measured floor-indexing bottleneck is fixed. The original 7.2-second registration
pause is **not yet closed**: the full Builder construction rerun stopped during
access restoration, before registration. Large-floor registration also still
causes a noticeable synchronous server-thread pause.

Navigation and other agents' changes were preserved. No changes were committed,
and no cross-version ports or dependency-pin changes were made.

## Confirmed root cause and fix

Spark attributed substantial registration time to `ImmutableCollections.MapN.probe`
inside `FloorGeometry.indexColumns`, reached through `Map.copyOf`. Packed X/Z
coordinates have collision-prone boxed `Long` hashes for rectangular floors.
This affected immutable-map construction and subsequent column lookups.

`FloorGeometry` now uses the existing fastutil primitive-long open hash map for
column indexing, wrapped read-only. The locally owned position map is exposed
only through an unmodifiable wrapper instead of being copied into `MapN`.
Column lists remain immutable; no world cache, asynchronous world reads, new
dependency, or persistent state was introduced.

Two regressions cover a 192-by-192 floor and read-only snapshots. The rectangular
test exercises construction plus three exact column-lookup sweeps. Against the
old implementation its timed body took about 39.85 seconds and failed the
two-second bound. With the final implementation its JUnit test duration was
176 ms. These are a regression result, not an isolated microbenchmark or a
promise about end-to-end gameplay latency.

## Runtime results

| Scenario | Observed result | Interpretation |
| --- | --- | --- |
| Standalone vanilla Taiga Temple | Cold analysis 127.757 ms; repeat analysis 9.0035 ms; commit 12.6222 ms; 31 selected-room cells | Real template registration passes. Repeat timing includes warmed JVM/world state, not a new persistent cache. |
| Builder completed-Taiga registration, previous optimized jar | 1,752.9 ms, 1,471.3 ms, and 1,609.5 ms in separate runs | Baseline before the additional geometry-index fix. |
| Builder completed-Taiga registration, final jar | 1,178.6 ms; required GameTest passed | Still a noticeable large-floor server-thread stall. Single-run timing is not a statistical benchmark. |
| Builder full five-builder Tall Taiga construction | 435/435 operations completed; access restoration stopped at 28/86; registration never reached | Cannot verify the original full-lifecycle pause from this run. |

The Builder acceptance fixture is not an isolated 31-cell natural house. Its
`prepareFlatCourse` encloses an 81-by-81 course with walls and a roof. Read-only
inspection of the stopped test world's `mca_villages.dat` found 6,236 persisted
selected-room cells in the latest profiled run (6,231 in the baseline). MCA is
scanning the large enclosed arena as well as the placed structure. Treat this as
large-floor integration evidence; add a natural-placement geometry acceptance
case before interpreting it as ordinary Taiga-house latency. Do not remove the
shared enclosure without reviewing the construction tests that depend on it.

## Spark comparison and remaining costs

Both registration captures used execution sampling on the server thread with
the Java sampler at a 4 ms interval. Inclusive samples describe attribution,
not exact wall-clock timers; nested ancestors must not be summed.

- Baseline capture: 1.935 seconds overall; 1,560 ms server-thread sampled weight.
  Two separate `MapN.probe` paths under column indexing accounted for at least
  276 ms inclusive in total.
- Updated capture: 1.632 seconds overall; 1,404 ms server-thread sampled weight.
  Column indexing accounted for 32 ms across separate paths, and the old `MapN`
  hotspot disappeared from the leading self-time entries.
- Updated selected-floor analysis still accounted for 1,164 ms inclusive.
  `StructureConnector.verticalInteractionConnector` accounted for 300 ms
  inclusive; separate `reachesExterior` paths accounted for 224 ms inclusive.
  POI candidate iteration accounted for 80 ms inclusive.

The updated capture's temporary asynchronous Spark-save wait timed out, so that
instrumented GameTest is **not a passing test**, even though registration
returned and the local profile was saved. Temporary capture/timing code was
removed from Builder, and the normal acceptance test subsequently passed.
Rolling Spark maximum-tick statistics are not registration-local timings.

Next performance work should investigate vertical-connector discovery and
direction-sensitive exterior traversal on large floors with behavior-preserving
regressions. No aggressive exterior cache was added without invalidation and
semantic evidence.

## Fresh verification

- Common JVM suite: 577 tests, zero failures, errors, or skips.
- Fabric and NeoForge builds: passed.
- `FloorScannerGameTests`: 88 required tests passed.
- `TaigaFloorPerformanceGameTests`: one required test passed.
- Builder `completedTaigaTempleRegistersWithMca`, using the rebuilt local MCA
  jar: one required test passed.
- Whitespace diff checks passed. Fabric in-game acceptance and other-version
  verification remain separate outstanding work.

The rebuilt NeoForge jar is `neoforge/build/libs/mca-neoforge-1.21.1-SNAPSHOT.jar`.
SHA-256: `10DBC2198F39D99F098D8FC839800CDC95AD27A5E39B8FF776C4F8FDDF7F542D`.

## Evidence files

Paths below are local diagnostic output, not source artifacts to commit.

MCA checkout:

- `build/floor-index-verification-20261011.log`
- `build/floor-scanner-runtime-20261011.log`
- `build/taiga-floor-runtime-final-20261011.log`

Builder checkout (`C:/Users/mikol/IdeaProjects/builder-addon`):

- `versions/1.21.1-neoforge/build/agent-verification/taiga-rebuilt-mca-spark-20261011.log`
  — full construction/access-restoration failure before registration.
- `versions/1.21.1-neoforge/build/agent-verification/taiga-rebuilt-mca-registration-20261011.log`
- `versions/1.21.1-neoforge/build/agent-verification/taiga-rebuilt-mca-registration-repeat-20261011.log`
- `versions/1.21.1-neoforge/build/agent-verification/taiga-rebuilt-mca-registration-spark-20261011.log`
- `versions/1.21.1-neoforge/build/agent-verification/taiga-registration-20261011.sparkprofile`
  — baseline registration profile.
- `versions/1.21.1-neoforge/build/agent-verification/taiga-primitive-index-registration-spark-20261011.log`
  — instrumented run with the save-wait timeout.
- `versions/1.21.1-neoforge/run-natural-gametest/config/spark/profile-2026-10-11_05.44.44.sparkprofile`
  — updated registration profile.
- `versions/1.21.1-neoforge/build/agent-verification/taiga-primitive-index-registration-final-20261011.log`
  — passing final normal registration acceptance.
- `versions/1.21.1-neoforge/run-natural-gametest/world/data/mca_villages.dat`
  — stopped-world room geometry inspected read-only; later test runs can replace it.

## Connector-discovery follow-up

The measurements above describe the geometry-index snapshot, before this
additional optimization. Its Spark capture attributed 300 ms inclusive to
`StructureConnector.verticalInteractionConnector` on the large enclosed floor.
Tracing the caller found that connector collection repeated a downward search
at every height in each scanned column, although lower positions had already
been examined.

`SelectedFloorScanner` now retains the original short-gap search at the bottom
of each column and classifies higher blocks directly. It reuses a local mutable
position, freezes retained connector positions, and avoids revalidating an
already collected connector. The result is the same union of physical
connectors: any lower connector within the band was encountered earlier, and
only the bottom probe can discover a connector below the band. No persistent
cache or off-thread world access was added. Exterior traversal is unchanged.

A full-scan regression on a tall 16-by-16 room failed before the fix: an empty
upper layer incurred 4,032 block reads. After the fix it incurs 1,600 reads
(60.3% fewer), with all 256 floor cells retained and no false connectors.
Additional full-scan checks retain high ladder/trapdoor columns, reject
standalone decorative trapdoors, and retain a short ladder gap below the
support layer. The mutable cursor must not escape into stored geometry.

Fresh follow-up checks:

- Three focused connector regressions passed.
- Forced common JVM execution (`:common:test --rerun`): 580 tests passed,
  no failures, errors, or skips.
- Both loader build tasks passed on retry (up-to-date artifacts).
- The already-started floor GameTest suite passed all 88 required tests.
- The single Taiga runtime check passed: 31 selected-room cells, cold analysis
  93.2286 ms, repeat analysis 6.9973 ms, commit 5.2661 ms. Different runs have
  JVM/world warmup variation; do not infer a precise percentage improvement
  from this one comparison.

The follow-up NeoForge jar SHA-256 is
`F57CCEB577B3A0635B14DB07DCF89A7E4B58398FB4C022EEFCD09EB5DE7AE4FD`.

The first post-fix focused attempt ended in a Gradle `EOFException`; the
focused rerun passed. An overlapping loader build failed because the Fabric
input jar was missing during remapping. Another agent was building the same
checkout; the subsequent build check passed. These failed attempts are not
behavioral verification and are retained in the logs.

Follow-up logs are `build/floor-connector-red-20261011.log`,
`build/floor-connector-green-20261011.log`,
`build/floor-connector-focused-20261011.log`,
`build/floor-connector-suite-20261011.log`,
`build/floor-connector-verification-20261011.log`,
`build/floor-connector-loader-builds-20261011.log`, and
`build/floor-connector-runtime-20261011.log`. The Taiga runtime log is
`build/taiga-connector-runtime-20261011.log`.

The read-count reduction is deterministic evidence of less scan work, not a
60.3% end-to-end latency claim. The original full-construction registration
pause remains unverified. Navigation, exterior-search caching, other versions,
and Fabric in-game acceptance were not changed by this follow-up.

## Exterior landing-discovery follow-up

Exterior traversal previously called `findLandings` twice for each ordinary
neighbour: first to detect unsupported openings, then through `floorSteps` to
traverse supported floor. It now shares that evidence within each direction
and uses the existing horizontal-connector boundary rules without allocating
an intermediate `FloorStep` list. The shared connector probe also remains the
owner used by ordinary floor-step discovery.

This is local reuse, not a new exterior-result cache. Positive results still
apply only to the search start, negative results still apply to exhausted
visited cells, and open-passage queue priority and directional step rules are
unchanged. No off-thread world access or navigation changes were introduced.

A full-scan regression on the closed 256-cell tall room reproduced 18,369
feet-level block reads before the change and 14,401 afterwards: 3,968 fewer
reads (21.6%). Its read budget accounts for the other work in the full scan;
the old implementation fails it. Exact floor size and empty connector markers
are also asserted. This measures reduced work, not end-to-end latency.

Only the necessary verification was run for this change:

- Eight focused common tests passed across `SelectedFloorScannerConnectorTest`,
  `SelectedFloorScannerBoundaryTest`, and `SelectedFloorScannerCachingTest`,
  with no failures, errors, or skips.
- The single NeoForge GameTest
  `FloorScannerGameTests#highSourceStillFindsExteriorThroughUnevenDescent`
  passed, preserving exterior detection from a high source through an uneven
  descent.
- Scoped diff whitespace checks passed. No full suite or loader artifact
  build matrix was repeated for this phase.

Logs: `build/floor-exterior-red-20261011.log`,
`build/floor-exterior-green-20261011.log`, and
`build/floor-exterior-runtime-20261011.log`.

Repeated searches from different starts remain a possible cost; this change
only removes duplicate work inside each search step. The original 7.2-second
full-construction registration pause is still not verified as resolved.

## Current Taiga timing after connector and exterior fixes

Two focused runtime checks were run with the latest floor changes. Only the
NeoForge jar task was needed for the Builder dependency; no full JVM suite or
loader build matrix was repeated.

| Fixture | Timing | Geometry | Result |
| --- | --- | --- | --- |
| Standalone vanilla Taiga Temple | Cold analysis 119.8782 ms; repeat analysis 6.0101 ms; commit 8.5289 ms | 31 selected-room cells | One required GameTest passed |
| Physically completed Builder Taiga blueprint in the existing enclosed course | `CompletedBuildingRegistrar.register` took 1,849.05 ms | All 435 operations materialized; newly registered room had 6,231 cells | One required GameTest passed |

The completed-house timer excludes fixture preparation and block placement;
it includes the real registration entry point, scanning and commit. It is not
an isolated `SelectedFloorScanner` timer. Room size was read from the newly
registered structure, excluding the fixture's pre-existing structures.
Temporary timing code was removed after measurement without changing the
test's behavioral assertions or other agents' edits.

The 1.85-second large-floor pause remains noticeable and is not evidence of a
latency improvement over the earlier 1.18-second single observation. Different
runs have startup, JVM and world-state variation; the deterministic read-count
regressions establish reduced work, not an end-to-end speedup guarantee. The
fixture encloses the entire 81-by-81 course, so this is not ordinary 31-cell
Taiga-room scan latency. House-only repeat analysis is about 6 ms in this run;
cold analysis is about 120 ms and should not be described as zero hitch.

This bypasses construction to measure the completed blueprint directly. It
does not close the original five-Builder construction/restoration acceptance
gap, and the original 7.2-second full-lifecycle pause remains unverified.

Evidence:

- MCA: `build/taiga-latest-floor-runtime-20261011.log`.
- Builder: `versions/1.21.1-neoforge/build/agent-verification/taiga-latest-completed-registration-20261011.log`.
- Rebuilt MCA jar explicitly selected with `-PmcaLocalNeoForgeJar`:
  `neoforge/build/libs/mca-neoforge-1.21.1-SNAPSHOT.jar`, SHA-256
  `D1A1BDAD215F0441DF12CACF6B608DDAC17FFE0763D7FAC8E6FCF80A4A6A0314`.

## Follow-up: synchronous large-floor registration diagnosis

The completed Builder Taiga fixture was replayed using the current MCA jar,
without any async registration or off-thread world access. It still constructs
all 435 operations, and the selected geometry spans roughly 6,230 floor cells
because the acceptance arena itself has a floor and ceiling across 81 by 81.
The runtime acceptance test passed each time.

| Run | Initial registration | Fresh-manager repeats (ms) | Notes |
| --- | ---: | --- | --- |
| After neighbor-index hash fix | 1,059 ms | 522, 565, 420 | 6,236 cells |
| Instrumented phase diagnosis | 1,027 ms | 496, 337, 254 | 6,231 cells |
| Single-map region discovery | 976 ms | 690, 625, 570 | 6,236 cells |

The measurements are noisy and floor geometry varies slightly between fixture
runs. They do not prove a repeatable end-to-end speedup for the new single-map
region walk. The change eliminates one redundant visited set: map insertion
now marks cells when queued; the previous `cells.size() + queue.size()` bound
is preserved as `cells.size()`.

Temporary method timings localized the remaining work on a typical warm pass:
the preliminary region flood was approximately 128-176 ms, region anchor
analysis around 38-53 ms, selected traversal about 51-79 ms, exterior check
88-101 ms, structural materialization about 46-89 ms, with additional vertical
connector/marker scans and room POI indexing. The large arena consequently
still pauses the synchronous server thread for hundreds of milliseconds.
These are diagnostic timings rather than additive independent wall-clock
benchmarks across different runs.

Temporary diagnostic logging was removed. Focused JVM topology/boundary tests
and the one required Builder completed-Taiga registration GameTest passed with
the single-map change. The original 7.2-second full Builder construction
acceptance event has not been reproduced with current sources, and eliminating
the large-floor synchronous stall remains unproven.

Logs: `build/taiga-region-map-green-20261011.log`,
`versions/1.21.1-neoforge/build/agent-verification/taiga-synchronous-hash-runtime-20261011.log`,
`versions/1.21.1-neoforge/build/agent-verification/taiga-region-diagnostic-runtime-20261011.log`,
`versions/1.21.1-neoforge/build/agent-verification/taiga-region-map-green-runtime-20261011.log`.

## Follow-up: why the Taiga completed-house arena takes longer

The completed 435-operation Taiga Temple was compared under several Builder
test-world geometries using the same rebuilt local MCA jar. The timings measure
the synchronous `CompletedBuildingRegistrar.register` call, not all construction.

| Scenario | Registration | Floor cells | Result |
| --- | ---: | ---: | --- |
| Existing sealed 81x81 underground arena | 710.4 ms | 6,236 | Passed |
| Artificial ceiling removed, original underground terrain left intact | 3,441.9 ms | 0 | `BLOCK_LIMIT`; failed |
| Roof removed and ground disconnected by a narrow trench | 192.9 ms | 313 | Passed |
| House moved higher, one exterior sky witness verified | 100.1 ms | 31 | Passed |
| House moved near maximum world height, one exterior sky witness verified | 75.6 ms | 26 | Registration succeeded; experimental exact-31 assertion failed |

The old `openSky` flag was misleading: the base course sits around Y=-57,
and deleting the artificial stone ceiling does not clear natural terrain above
it. Even the high-altitude experiments were not repeatable: later randomly
placed runs failed the `canSeeSky` fixture prerequisite before registration.
The difference between 26 and 31 selected cells is not yet explained and must
not be treated as evidence of identical fixture geometry. The temporary
open-sky and trench diagnostics were removed from the Builder test source;
their captured logs remain under `versions/1.21.1-neoforge/build/agent-verification/`.

The narrow conclusion is that the 6,236-cell scan measures the artificially
roofed course, not the ordinary Taiga Temple room. Successful sky-witnessed
runs registered small rooms in 76-100 ms. No separate floor-system correctness
bug or reproduction of the original 7.2-second pause was established.

The five-Builder `fiveBuildersCompleteTallTaigaTempleWithSemanticProgress`
rerun failed **before** room registration: 294 of 435 blueprint operations
had finished when the existing watchdog detected 82 actionable ticks without
semantic progress during access `OPENING` at action 47/51. This is a separate
Builder construction/access failure; it provides no measurement of floor
registration after all five Builders complete. The acceptance watchdog was not
relaxed and the navigation owner was not changed.

Logs: `taiga-sealed-arena-comparison-20261011.log`,
`taiga-isolated-ground-comparison-20261011.log`,
`taiga-open-sky-comparison-20261011.log`,
`taiga-real-sky-registration-20261011.log`,
`taiga-real-sky-top-of-world-final-20261011.log`, and
`taiga-five-builder-floor-rerun-20261011.log` under Builder's
`versions/1.21.1-neoforge/build/agent-verification/` directory.

## Follow-up: durable full-Taiga outdoor GameTest

Added `completedTaigaTempleRegistersItsOwnFloorUnderSky` to Builder's
`LargeHouseBuilderAcceptanceGameTests`. Unlike the original sealed
81x81 underground registration course, this test physically materializes all
435 operations of the actual `taiga_temple_1` blueprint on a 49x49 stone
platform near the world ceiling. It clears all space over the platform to the
world height to make actual sky visibility independent of generated terrain,
verifies exterior sky visibility before and after construction, and invokes
the unchanged synchronous `CompletedBuildingRegistrar.register`. It requires
MCA to publish a new structure and a house-sized 20-100-cell registered room,
not the thousands of cells belonging to the artificial course.

Two independent NeoForge 1.21.1 GameTest server runs passed (`1 required
tests passed` and Gradle exit 0 each):

| GameTest run | MCA registration time | Selected room floor cells |
| --- | ---: | ---: |
| Initial outdoor fixture | 149.0 ms | 26 |
| Repeat at a new random test location | 123.6 ms | 29 |

Both runs compiled the updated GameTest and used the current local MCA
NeoForge snapshot jar. The different 26/29 floor counts reinforce that an
unjustified exact-cell-count assertion would be flaky. The new bounded
house-size assertion passed twice, and `git diff --check` passed.
No MCA production floor-scanner code or Builder movement/access logic was
changed in this follow-up. These measurements do not reproduce the original
7.2-second pause, and do not fix the separate five-Builder construction stall.

New logs: `taiga-outdoor-floor-gametest-20261011.log` and
`taiga-outdoor-floor-repeat-20261011.log`, in Builder's
`versions/1.21.1-neoforge/build/agent-verification/`.
