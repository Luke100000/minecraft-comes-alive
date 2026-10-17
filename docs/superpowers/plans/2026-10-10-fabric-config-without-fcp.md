# MCA 1.21.1 Optional-FCP Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> or superpowers:executing-plans to implement this plan task by task. Each step uses
> a checkbox for tracking. Do not implement before the user reviews this plan.

**Goal:** Finish the already-merged MCA 1.21.1 optional-FCP configuration:
without FCP, Fabric uses FCP-compatible TOML with no config GUI; with FCP,
**FCP owns native configuration behavior** and provides the original searchable
MCA GUI. Preserve existing user settings and NeoForge's native behavior.

**Architecture:** One shared typed schema feeds native NeoForge/FCP specs and
a standalone Fabric TOML backend. FCP installed means FCP owns load, save,
validation, correction, watchers, reload, GUI and native peer synchronization;
absent means MCA writes the **same FCP-canonical TOML** without GUI or watcher.
SERVER synchronization uses FCP when both peers have it and MCA's validated
bridge otherwise; only one authoritative path may apply per connection.
No alternate GUI, config-editing commands or config-changing C2S packets.

**Tech Stack:** Minecraft 1.21.1, Java 21, Fabric Loader 0.19.5, Loom 1.18.2,
Fabric API 0.116.17+1.21.1, NeoForge 21.1.256, JUnit, and a pinned,
packaged NightConfig TOML/core version selected after dependency inspection.
FCP 21.1.6 must become an **optional** Fabric dependency, not be bundled
or required. Verify compatibility with the installed version before enabling
its original GUI integration.

**Spec:** ../specs/2026-10-10-fabric-config-without-fcp-design.md

**Revision (2026-10-17):** The gameplay merge `de1a9e6d7` is complete.
Tasks 1–4 have an **uncommitted, partly validated** implementation in the
existing worktree, not a fully accepted deliverable. Execute the new
compatibility/migration and sync checks below against that implementation;
do not rerun Task 0 or treat existing passing builds as GUI/network proof.
See `../reports/2026-10-15-optional-fcp-implementation-status.md`.

**Code review follow-up (2026-10-17):** Incorporate all eight issues from the
post-merge `REQUEST_CHANGES` review below. These are findings to verify and
resolve, **not** authorization to alter Java code or a claim that every
reported impact was observed at runtime. Recheck original line locations
against the current uncommitted source when implementing.

## Global constraints

- Existing worktree only: C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-config-system-port,
  branch dev/feature/1.21.1-config-system; `de1a9e6d7` is the completed
  gameplay merge. No new checkout or worktree. Current config edits and
  staged report are user work; inspect status, preserve them, and never
  reset, overwrite, or stage unrelated changes.
- Do **not** repeat the historical upstream merge. Preserve the audited
  gameplay hunks and existing NeoForge behavior while remediating the
  uncommitted optional-FCP implementation.
- **Fabric without FCP:** manual TOML editing only; no configuration GUI,
  config entrypoint, config-editing commands/C2S, watcher or reload command.
  **Fabric with FCP:** the **original searchable config GUI**, not a new screen,
  is the sole in-game config editor. FCP owns its normal watcher/reload and
  native saving; MCA-specific config commands, C2S editing and reload
  commands remain disabled. Preserve unrelated gameplay UI and SavedData.
- Declare FCP under Fabric `suggests`, not `depends`; use compile-only or
  equivalent optional integration dependencies. All FCP native references,
  GUI registration and classloading are guarded behind mod-presence checks.
  No second Fabric config writer or divergent SERVER authority is permitted.
- Keep exactly one setting definition per current scope; preserve typed
  Config.COMMON/SERVER/CLIENT field access, immutable product defaults, names,
  paths, validators, **section and entry** comments/translations, range/default
  comment format, and restart markers.
- FCP is the only active Fabric config backend when installed. Without FCP,
  the standalone writer must produce files accepted by FCP's native spec
  without an unnecessary corrective rewrite. For legacy noncanonical files,
  back up and migrate known values before FCP performs correction.
- COMMON credentials/endpoints/prompts and CLIENT TTS/API secrets must never
  enter SERVER sync. SERVER is server/world-owned, COMMON is installation-local,
  CLIENT is physical-client-only.
- The legacy import source is only dev/1.21.1 config/mca.json version 2.
  Never alter/delete it, never overwrite an existing TOML/world override,
  and never turn migrated values into product defaults.
- NeoForge keeps native registration, reload behavior, SERVER sync, and the
  existing searchable configuration screen. Reuse that GUI on Fabric when
  FCP is installed; no shared runtime signatures may require native classes
  on Fabric without FCP.
- MCA's fallback S2C protocol `mca:server_config_snapshot`, version 1,
  maximum 1,048,576 UTF-8 bytes, transports full SERVER settings when either
  peer lacks FCP; FCP native sync owns FCP-to-FCP. Explicitly select one
  applied sync path using reliable peer capability detection; no C2S config
  mutation (a transport capability signal is allowed).
- Files are config/mca-{common,client,server}.toml plus optional
  <world>/serverconfig/mca-server.toml; physical-client I/O only for CLIENT.
- Without FCP, edits apply at startup/world start, with no watcher. With FCP,
  native GUI/file reload may occur; verify whether live SERVER changes reach
  connected peers and document/restrict changes requiring rejoin or restart.
- Use .\gradlew.bat from the repository root. Pass --console=plain and inspect
  real test execution and exit codes. A build is not runtime proof.
- Freeze the config schema **after** the reviewed upstream merge, never from
  an older ref while upstream gameplay/config ownership is still changing.

## Review focus

1. No-FCP generated TOML loaded with FCP: `ModConfigSpec.isCorrect` succeeds
   with no unnecessary rewrite; metadata, lists, values and section paths
   match native specs (Task 2 and 2A tests).
2. Pre-migration old flat/relocated keys and moved COMMON ownership: protect
   a custom value from native correction; back up and migrate without
   promoting COMMON/CLIENT secrets into SERVER (Task 2A test).
3. Every Fabric peer pairing (FCP/FCP, FCP/no FCP, no FCP/FCP, no FCP/no FCP):
   exactly one applied sync path; join A -> B does not retain stale settings
   (Task 3 test).
4. Broken or unwritable TOML/legacy import: original file bytes survive and
   migration is not falsely declared complete (Task 2 test).
5. Fabric AI/TTS config-editing commands and global-system-prompt packet:
   unavailable without changing config, while nonconfig SavedData edits still
   work (Task 4 test).
6. Existing world override followed by malformed override in another world:
   no previous world's values leak; restore validated global SERVER data and
   do not create missing overrides (Task 2 test).
7. Packaged Fabric jar without FCP/NeoForge/Mod Menu: no native classloading,
   config screen, required FCP metadata or missing TOML library. With FCP,
   verify the original GUI displays/edits the registered FCP spec, saves via
   FCP, and respects multiplayer SERVER read-only state (Task 5 test).
8. Upstream collision, pathfinding and guard/archer AI hunks after merge:
   same runtime behavior with no duplicate `enableVillagerCollisions` config
   entry or lost native SERVER owner (Task 0 audit and regression runs).

## Post-merge code review resolution checklist

All eight review findings fit as **targeted work within the existing tasks**.
Resolve P1 items before packaged release testing; complete the P2 regression
coverage and performance decision before considering the review closed.
The two backend *modes* do not constitute two simultaneous file writers:
only one is active at a time. The real concern in finding 1 is authority
handoff, canonical file compatibility, and duplicate SERVER sync.

| Finding | Priority | Required disposition | Owner |
| --- | --- | --- | --- |
| **R1** Two Fabric config authorities | P1 | Retain mutually exclusive backends; FCP owns its own files, watcher, GUI and native sync when installed. No-FCP follows FCP's canonical format. Negotiate **one** SERVER sync path per peer, never two overlays; preserve mixed-install compatibility. Do not disable FCP's native watcher or replace its sync indiscriminately. | Tasks 2, 2A, 3 |
| **R2** Lost native section metadata | P1 | Preserve section comment/translation before `push()` in `ConfigSchema` and native adapter; verify generated native section headers and search context on NeoForge and FCP. | Tasks 1, 2A |
| **R3** Local SERVER values while awaiting sync | P1 | Represent pending authoritative SERVER state for both native FCP and MCA fallback negotiation; block/defer client-side policy-dependent reads until the selected sync is applied. Clear/reestablish state on disconnect and reconnect, including integrated server. | Task 3 |
| **R4** Malformed world override retains previous world | P1 | On world B parse failure restore validated **global** SERVER config, not world A's last snapshot. Leave B's invalid TOML untouched and cover consecutive worlds in one JVM. | Task 2 |
| **R5** Existing TOML misses default-map upgrade | P1 | Run eligible `upgradeDefaultMaps()` migration on valid **existing** TOML as well as newly created files; keep user overrides/deletions and persist migration completion only after successful save. | Tasks 2, 2A |
| **R6** Live GUI SERVER edits do not reach peers | P1 | Determine FCP's actual SERVER save/reload and network behavior. Keep FCP's native channel for FCP/FCP; propagate new authoritative values to **mixed** clients using the MCA bridge if live changes are supported. Otherwise make rejoin/restart explicit and prevent silently stale connected clients. Do **not** broadcast an MCA overlay to FCP/FCP in parallel. | Tasks 3, 5 |
| **R7** Native reload/pathless tests weakened | P2 | Restore real loader-backed `ModConfigSpec` load/reload/unload and pathless SERVER-default tests alongside isolated handle/cache unit tests. Assert map-cache invalidation and migration skip behavior on actual callbacks. | Tasks 1, 2A, 5 |
| **R8** Redundant per-read list copies | P2 | Profile or demonstrate copy costs in repeated gameplay reads; copy/validate mutable inputs **once at a safe backend/snapshot boundary**, expose unmodifiable lists, and avoid repeated copies on hot `get()` paths where proven safe. Never expose a native mutable collection or regress default/remote isolation. | Tasks 1, 2, 3 |

The optional cleanup suggestions are **not release blockers**: consider
inlining the one-method `ClientConfigHelper` only if it does not weaken the
FCP classloading boundary; remove `ConfigSchema.ValuePaths` only after
confirming it has no production consumers and replacing test-only callers.
Do not fold unrelated cleanup into security/authority fixes. After each
review finding, record the changed code, regression test, execution result,
and any still-unverified runtime scenario in the implementation status report.

---

## Task 0 — Merge upstream dev/1.21.1 with behavioral parity audit

**Status:** Historical execution task. The audited gameplay merge is already
committed at `de1a9e6d7`; see
`docs/superpowers/reports/2026-10-15-dev-1.21.1-merge-parity.md` for the
recorded review. **Do not rerun or undo this merge** when implementing the
optional-FCP design. The checklist below records the original review gates.

**Reference inputs:**

- Feature `dev/feature/1.21.1-config-system`, observed `d198d6ac5`.
- Upstream `origin/dev/1.21.1`, observed `735babac0`, merge base `706c0090b`;
  14 feature-only / 6 upstream-only commits at review time.
- Nonmutating merge preview yielded content conflicts in
  `common/src/main/java/net/conczin/mca/CommonConfig.java`,
  `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java`,
  `common/src/main/java/net/conczin/mca/entity/ai/brain/sensor/GuardEnemiesSensor.java`,
  and `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/sensor/GuardEnemiesSensorGameTests.java`.
  Re-run preview after fetching; the conflict list may change.

**Files:**

- Merge: files shown by updated `git diff --name-status` and `git merge-tree`
  (particularly collision, pathfinding, AI, building scanning and GameTests).
- Audit: `docs/superpowers/reports/2026-10-15-dev-1.21.1-merge-parity.md`
  (record refreshed refs and file/method outcome for each upstream hunk).
- Retain and adapt: `common/src/main/java/net/conczin/mca/ServerConfig.java`,
  `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java`,
  and relevant existing config/collision tests.

**Interfaces/ownership:**

- Source upstream gameplay behavior remains authoritative; preserve target
  version/loader APIs and feature-branch native-config architecture.
- In upstream, `CommonConfig.enableVillagerCollisions = true`; the feature
  branch already has `Config.SERVER.enableVillagerCollisions` default `true`.
  Keep a **single SERVER-owned** entry and its existing native `.get()`
  gameplay access while adapting the upstream change.
- Upstream `Config` uses `config/mca.json` v2, `loadOrCreate()`,
  `configDefaultsVersion`, `serverConfig`, and the old
  `ConfigRequest`/`ConfigResponse`. Use these as migration/sync behavior
  references; do not reintroduce a live JSON backend or old whole-object
  client sync. Native NeoForge config and the feature's migrations/UI stay.

- [ ] Verify branch, status, `gradle.properties`, and root repository guidance;
      preserve existing changes and ensure reviewed documents are safely
      committed or otherwise protected before the merge.
- [ ] Refresh `origin/dev/1.21.1`, record exact heads/merge-base, and inspect
      `git rev-list --left-right --count HEAD...origin/dev/1.21.1`,
      `git log --reverse HEAD..origin/dev/1.21.1`,
      `git diff --name-status HEAD...origin/dev/1.21.1`, and focused
      `git diff --histogram` by affected subsystem. Run nonmutating
      `git merge-tree --write-tree --name-only HEAD origin/dev/1.21.1`.
- [ ] Establish baseline focused config/collision/AI/navigation JVM tests and
      available GameTest lanes, recording executed counts and any pre-existing
      failures before merge. Do not claim a test executed if it did not.
- [ ] Perform the approved merge **without `ours`/`theirs` as a final
      resolution**. Reconcile every source-side hunk into `ported`, `adapted`,
      `already present`, or `not portable`, recording file/method evidence in
      the parity report. Stop for explicit approval before omitting any
      `not portable` hunk.
- [ ] Resolve `CommonConfig` without duplicating the native SERVER collision
      setting; preserve the latest villager crowding check alongside upstream
      AI/navigation, collision, and guard changes. Inspect all automatic
      resolutions as well as named conflicts for silent behavioral loss.
- [ ] Write/update focused regression tests for upstream collision setting
      parity (default `true`, `false` affects only MCA-villager crowding),
      guard target selection, archer movement, navigation recovery, and floor
      scans; retain upstream GameTests with version-appropriate assertions.
- [ ] Run focused JVM suites and relevant NeoForge GameTests/runtime smokes;
      inspect real outcomes, `git diff --check`, and full hunk-audit coverage.
      Commit only a reviewed and passing merge, or report blockers and leave
      the integration explicitly incomplete. Never mistake compilation for
      gameplay parity.
- [ ] With the **post-merge** branch stable, inventory native config scope,
      paths, defaults, validators, map-upgrade markers, and multiplayer policy;
      hand this source state to Task 1 for the frozen setting manifest.

---

## Task 1 — Freeze the source schema and isolate native NeoForge config

**Files**

- Adapt existing: common/src/main/java/net/conczin/mca/config/{ConfigScope,ConfigValue,ConfigSchema,ConfigBackend}.java
- Modify: common/src/main/java/net/conczin/mca/{Config,CommonConfig,ServerConfig,ClientConfig}.java
- Use existing: common/src/main/java/net/conczin/mca/config/nativeapi/NeoForgeConfigBackend.java
  (FCP-present Fabric also uses this guarded native adapter).
- Modify: neoforge/src/main/java/net/conczin/mca/neoforge/{CommonNeoForge,NeoForgeClientConfigHelper}.java
- Isolate: common/src/main/java/net/conczin/mca/client/gui/ConfigScreenSearch.java
  in sources usable by NeoForge **and the optional Fabric FCP integration**,
  but never loaded by the Fabric no-FCP startup path; do not move the GUI
  exclusively to NeoForge if it prevents Fabric reuse.
- Tests: common/src/test/java/net/conczin/mca/config/ConfigSchemaParityTest.java;
  adapt ConfigLayoutTest, ConfigEarlyAccessTest, ConfigDecodedMapCacheTest,
  ConfigLegacyMigrationTest and native ConfigScreenSearchTest (move that native
  test to a supported NeoForge test lane, inspecting build configuration first).
- Resources: common/src/test/resources/config/schema-1.21.1-config-system.json

**Interfaces**

- ConfigScope = COMMON, SERVER, CLIENT.
- ConfigValue<T> exposes T get(), void set(T), T getDefault(), List<String> getPath().
- ConfigSchema.Builder implements the actual builder operations used by
  the three holder constructors; ConfigSchema exposes ordered entry **and
  section** metadata, including translation/comment attributes set before
  a native builder's `push()`.
- ConfigBackend binds ConfigScope/ConfigSchema and supports save operations.
- NeoForgeConfigBackend exposes `ModConfigSpec spec(ConfigScope)` only along
  native NeoForge and **FCP-installed Fabric** paths; no public shared
  signatures or no-FCP Fabric classloading require the native classes.

- [ ] Verify Git status and Task 0 hunk-audit sign-off before capturing
      post-merge metadata; do not reset or stage unrelated edits.
- [ ] Capture **all** current entries from native specs, including scope,
      full path, type/default, validator examples, per-entry **and per-section**
      comment/translation, range/default comments, gameRestart/worldRestart.
      Derive the count from the fixture; check at
      least showNameTags=true, useSquidwardModels=false,
      babyItemGrowUpTime=24000, and COMMON-only private tokens.
- [ ] Write a failing ConfigSchemaParityTest asserting exact manifest
      parity, early default access, defensive lists, validation, and map
      decoding. Run .\gradlew.bat :common:test --tests
      'net.conczin.mca.config.ConfigSchemaParityTest' --console=plain.
- [ ] **R2:** Add a failing native-spec test for section-level comments and
      translations declared *before* `push()` in the original holder builders.
      Assert NeoForge/FCP native headings and section-search metadata, not
      merely matching leaf values; preserve section metadata through push/pop.
- [ ] **R7:** Restore loader-backed reload coverage instead of relying on
      `ConfigValue.set()` as a substitute for config events. In a supported
      native test lane, actually load/reload/unload a real `ModConfigSpec`,
      verify updated decoded-map caches, and exercise a pathless remote
      SERVER config that must skip on-disk migration/saving. Retain pure
      `ConfigDecodedMapCacheTest` and `ConfigLegacyMigrationTest` unit cases.
- [ ] **R8:** Add immutable-list regression tests for product defaults,
      native-bound values, standalone TOML lists and applied remote SERVER
      snapshots: mutating a source list after `set`/publication must not
      change a config read, and mutating a returned list must fail. Inspect
      hot gameplay call sites and measure repeated `ConfigValue.get()` list
      allocation before changing the defensive-copy strategy.
- [ ] Implement one schema and typed handles without reflection or
      duplicate values. Preserve existing gameplay get() calls; move native
      spec construction, migration events and registration into the NeoForge
      adapter. Preserve native section metadata through push/pop and verify
      `getLevelTranslationKey`/`getLevelComment` for real section paths.
      Keep existing native SERVER synchronization.
- [ ] **R8:** If testing demonstrates the overhead is meaningful, normalize
      validated lists into immutable snapshots at safe set/bind/backend
      boundaries and reuse immutable instances on reads. Do not assume
      `ModConfigSpec.ConfigValue.get()` itself returns an immutable list;
      retain copying or snapshot checks where native state could mutate.
      Rerun isolation tests and a representative hot-path allocation check.
- [ ] Isolate native-only config GUI and its tests from the no-FCP classloading
      path **without removing the GUI from Fabric's optional FCP mode**.
      Reuse the same GUI code and preserve search/reset/validation. Check the
      checkout's NeoForge test task before choosing a test command; if it
      has no viable JVM lane, retain native UI assertions in an existing
      supported lane and verify UI behavior in runtime smoke.
- [ ] Run focused parity tests, the available native tests, then
      .\gradlew.bat :common:test :neoforge:build --console=plain.
      Do not claim Fabric compilation is ready until Task 2 replaces its
      former FCP config integration.
- [ ] Review the diff and commit only completed config-schema/native
      changes, without staging pre-existing unrelated work.

## Task 2 — Fabric TOML persistence, migration, and lifecycle

**Files**

- Adapt existing: fabric/src/main/java/net/conczin/mca/fabric/config/{FabricTomlConfigBackend,FabricConfigBootstrap,FabricFcpConfigIntegration,FabricFcpConfigClient}.java
- Adapt existing: common/src/main/java/net/conczin/mca/Config.java
  (legacy migration); add a dedicated migration helper only if the source
  comparison demonstrates it is necessary.
- Modify: fabric/src/main/java/net/conczin/mca/fabric/{MCAFabric,MCAFabricClient}.java
- Adapt: fabric/src/main/java/net/conczin/mca/fabric/ClientConfigHelper.java
  to a safe optional-FCP entrypoint; place native FCP-specific references
  in a separately loaded implementation class.
- Modify: common/src/main/java/net/conczin/mca/Config.java; fabric/build.gradle;
  common/build.gradle; gradle.properties.
- Tests: fabric/src/test/java/net/conczin/mca/fabric/config/FabricTomlConfigBackendTest.java;
  adapt common ConfigLegacyMigrationTest and add lifecycle tests in the
  supported Fabric lane as needed.

**Interfaces**

- FabricTomlConfigBackend implements ConfigBackend; loadGlobal(boolean
  physicalClient), startWorld(Path worldServerConfigDirectory), stopWorld().
- Existing `Config.migrateLegacy` imports version-2 JSON for a specified
  scope and *missing* destination Path; mark success only after a durable save.
- A validated TOML snapshot is published as one unit; world overrides
  replace only server-owned values. FCP-installed Fabric binds to its native
  `ModConfigSpec` values, not the standalone map. The standalone writer
  produces a document passing FCP's corresponding `isCorrect()` check.

- [ ] Resolve and record the **actual** NightConfig core/TOML versions and
      licenses using .\gradlew.bat :common:dependencyInsight --dependency
      night-config --configuration testRuntimeClasspath --console=plain.
      Inspect the effective Loom version and jar packaging before choosing
      relocation/shading; do not hardcode the earlier proposal's versions.
- [ ] Add failing Fabric backend tests for defaults, valid config with
      wrong-range entries, comments/unknown keys, lists/maps with equals
      signs and Unicode, unreadable/malformed TOML, failed atomic writes,
      and CLIENT file isolation on a dedicated server. Verify malformed
      input remains byte-identical.
- [ ] Add failing canonical-format parity tests, using the real native spec
      as the oracle: standalone-generated COMMON/CLIENT/SERVER TOML must pass
      `ModConfigSpec.isCorrect()` without FCP correcting missing/extra keys,
      section comments, entry comments, default/range lines, or list types.
      Test an unmodified standalone file across FCP-on -> FCP-off and assert
      its known values and expected comments do not drift.
- [ ] Add failing world tests for global-server fallback, present world
      override precedence, no generated override when absent, world A then
      **malformed** world B in one process, and integrated-server stop/restart.
      Failed world B parse must select validated global SERVER settings,
      not retain A's values; the broken file stays byte-identical.
- [ ] **R4:** Also test switching world A -> malformed B -> world C with
      no override, including when A previously held non-default values.
      Confirm B and C each use the validated global SERVER snapshot and
      the failed load never publishes A's stale values.
- [ ] Add failing migration tests for legacy JSON v2 only, all original
      product defaults, missing destination precedence, read-only JSON
      preservation, no import into existing TOML/override, and write failure
      not marking migration successful.
- [ ] Build Fabric TOML backend using validated temporary snapshots;
      preserve existing files on parse/write failures, create missing global
      files as needed, and restore global SERVER state after world stop.
      Generate nested sections and native-compatible metadata in canonical
      order, including `Default`/`Range` comments. For valid **existing**
      TOML, run pending default-map upgrade once and persist/mark completion
      only after a durable write; preserve user overrides and deletions.
      Use startup-only file loading, no MCA watcher or exposed reload method.
- [ ] **R5:** Add a valid pre-existing TOML fixture with an old
      `defaultMapsVersion`, overridden values, and deliberately deleted
      defaults. Assert only eligible defaults are inserted and upgrades are
      idempotent; a failed write must not advance persisted version markers.
      **R8:** Normalize accepted standalone lists into immutable snapshots
      once per successful load, not once for every gameplay read.
- [ ] Replace FCP initialization events in MCAFabric with Fabric lifecycle
      integration **before** attribute registration and startup gameplay
      consumers; load CLIENT only from physical-client initialization.
      Bundle an ordinary TOML library in the Fabric jar without bundling FCP.
- [ ] Keep `ClientConfigHelper` as a guarded optional integration: when
      `FabricLoader.isModLoaded("forgeconfigapiport")` is false, never load
      FCP/NeoForge classes or register a config screen; when true, register
      **the same existing `ConfigScreenSearch`/`ConfigurationScreen` GUI**
      through FCP's `ConfigScreenFactoryRegistry`, not a replacement GUI.
      Bind the existing native-compatible spec directly to FCP's registry:
      FCP itself owns loading, validation, saving, watching, and reloading;
      no standalone backend is constructed or bound on that path. GUI edits
      save through FCP, not an MCA file writer. Fail tests rather than silently
      substituting a new UI or leaving edits unsaved.
- [ ] Change Fabric metadata `forgeconfigapiport` from `depends` to `suggests`;
      replace mandatory `modImplementation` with `modCompileOnly` or an
      equivalent isolated optional classpath. Do **not** package FCP.
      Test classloading and TOML persistence in both modes before Task 2
      is green; compilation alone does not prove absence-safe startup.
- [ ] Test FCP absent: no GUI integration at all and manual TOML changes
      applied after restart. Test FCP present: original GUI appears and edits
      FCP's registered spec with validation, search, reset and restart flags.
      Verify FCP's default watcher works; MCA must not add another watcher.
- [ ] Run .\gradlew.bat :common:test :fabric:test :fabric:build
      --console=plain. Review file writes, classloading, and logs for
      secret-value disclosure; commit only scoped, verified changes.

## Task 2A — Repair legacy paths and eliminate avoidable FCP rewrites

**Status:** Required follow-up to the uncommitted Task 2 implementation,
before any packaged runtime acceptance. Historical September `.bak` files
cannot establish exactly what FCP changed on October 15; use controlled
fixtures, not assumptions drawn solely from that comparison.

**Files**

- Modify: common/src/main/java/net/conczin/mca/config/{ConfigSchema,ConfigValue}.java
- Modify: common/src/main/java/net/conczin/mca/config/nativeapi/NeoForgeConfigBackend.java
- Modify: fabric/src/main/java/net/conczin/mca/fabric/config/{FabricTomlConfigBackend,FabricFcpConfigIntegration,FabricConfigBootstrap}.java
- Adapt: common/src/main/java/net/conczin/mca/Config.java (legacy migration rules)
- Test: fabric/src/test/java/net/conczin/mca/fabric/config/FabricTomlConfigBackendTest.java
- Test: native-spec parity, legacy-path migration, and FCP present/absent
  packaged runtime fixtures in an existing runnable loader test lane.

**Interfaces/ownership**

- Before FCP registers each scope, a one-time **offline** migration may
  normalize recognized legacy paths/ownership with a backup. After FCP
  registration, only FCP may save/correct those files.
- Canonical path wins on duplicate old/new keys; preserve other recognized
  user overrides and never move credentials into a syncable SERVER scope.

- [ ] Reproduce FCP `ConfigTracker`'s correction decision against a fresh
      standalone-generated file with the actual FCP 21.1.6/native spec.
      Capture whether a rewrite stems from section/path drift, list values,
      missing/extra keys or comment/default/range metadata; compare values
      before/after by full scope/path, never by name alone.
- [ ] Add red tests for legacy flat SERVER fields, keys moved between
      sections, the seven former COMMON-owned fields, unknown keys, Unicode,
      and conflicting old/new entries. Assert a custom recognized old value
      survives, canonical values win conflicts, and backups preserve originals.
- [ ] Implement the smallest idempotent migration **before** FCP's immediate
      COMMON/CLIENT registration loads or SERVER load occurs. Avoid a second
      live FCP writer, data loss on failure, and repeated migration. Require
      durable save before marking migration complete.
- [ ] Confirm new standalone files pass native `isCorrect` and do not trigger
      corrective FCP writes on first launch or subsequent mode switches.
      For truly noncanonical files, verify FCP correction is intentional and
      backed up; do not promise preservation of unsupported unknown keys
      across native correction.
- [ ] Verify existing-file `defaultMapsVersion` upgrades, no old-world
      leakage on malformed overrides, global/world FCP precedence, and
      original GUI save/reset/reload against the **actual** FCP lifecycle.
- [ ] **R7:** Add a loader-backed FCP and NeoForge lifecycle test for real
      `loading`/`reloading` events. Verify native spec changes invalidate
      decoded maps, pathless SERVER remote defaults do **not** trigger disk
      migrations, and a real reload updates destination caches as intended.
      Direct setters or synthetic paths alone do not satisfy this test.
- [ ] Run focused native/Fabric tests and both-loader builds; compare
      controlled file hashes and full known-value snapshots before and after
      FCP installed -> absent -> installed. No gameplay/config Java changes
      are accepted on compilation evidence alone.

## Task 3 — Use FCP native sync or the MCA compatibility bridge, never both

**Files**

- Adapt existing: fabric/src/main/java/net/conczin/mca/fabric/config/FabricConfigSync.java
- Modify: fabric/src/main/java/net/conczin/mca/fabric/config/{FabricFcpConfigIntegration,FabricConfigBootstrap}.java
- Modify: fabric/src/main/java/net/conczin/mca/fabric/{MCAFabric,MCAFabricClient}.java
- Modify: common/src/main/java/net/conczin/mca/config/{ConfigValue,ConfigSchema}.java
- Tests: fabric/src/test/java/net/conczin/mca/fabric/config/FabricConfigSyncTest.java

**Interfaces**

- Existing `FabricConfigSync.Snapshot`: version 1 plus full SERVER paths,
  implemented with Fabric's payload registry; keep 1,048,576-byte limit.
- `FabricConfigSync`: emit snapshots from the **server's active backend**
  only for clients requiring the bridge. On FCP-to-FCP, native FCP transport
  and native spec are authoritative and MCA must not install a second overlay.
- Connection setup: select a single sync path using proven peer capabilities
  at the correct protocol phase; disconnect on incompatible/missing support.

| Server | Client | Expected applied SERVER path |
| --- | --- | --- |
| FCP | FCP | FCP native sync, no MCA overlay |
| FCP | No FCP | MCA snapshot sourced from FCP native values |
| No FCP | FCP | MCA snapshot overrides client FCP-local defaults |
| No FCP | No FCP | MCA snapshot sourced from standalone TOML |

- [ ] Write failing tests for protocol version 1, invalid versions, max
      1,048,576 bytes checked before buffer-body allocation, duplicate/
      unknown/missing keys, incorrect types/ranges, and wrong-scope keys.
      Invalid payloads must apply **zero** settings.
- [ ] Inspect FCP 21.1.6's `ConfigSync`, config registry, lifecycle and
      payload hooks, including FCP-present-server join against FCP-absent
      client. Record when FCP native sync occurs and whether it can update
      already-connected clients. No unverified assumption that FCP alone
      supports mixed installs.
- [ ] Add protocol/connection tests for all four server/client installation
      combinations. Negotiate capabilities before deciding which path applies
      (do not infer client capability from server `isModLoaded`). Assert
      exactly one remote authority and correct source values for each case.
- [ ] Seed distinct COMMON/CLIENT secret values and assert they are
      absent from MCA snapshots, FCP native SERVER payloads, and receiver
      state. No C2S config mutation is allowed; a capability marker is fine.
- [ ] Test join server A, disconnect, join B; also test integrated-server
      disconnect preserving authoritative state, FCP internal unload/default
      transitions, pending-authority behavior, and client-thread publication.
- [ ] **R3:** Add failing tests in which client-local SERVER differs from
      remote policy and gameplay reads occur **before** sync completion.
      Model joining/awaiting-native-FCP, joining/awaiting-MCA-bridge, sync
      rejection, disconnect and immediately joining another server. Assert
      no stale local or previous-server policy is accepted as authoritative;
      define a safe defer/deny behavior for policy-dependent consumers and
      confirm transition to the correct validated snapshot is atomic.
- [ ] Register/send the MCA snapshot via Fabric S2C hooks **only** when
      selected by capability negotiation. Validate before atomic memory-only
      application, with no duplicate FCP+MCA overlays. Reject incompatible
      or missing authority and audit client SERVER reads during join; in mixed
      mode, MCA overlay must win over FCP-local SERVER defaults and clear
      correctly on disconnect.
- [ ] **R8:** On the MCA snapshot path, validate and freeze lists before
      publishing a complete `ConfigSchema` remote map. Confirm subsequent
      reads cannot mutate or observe edits to the decoded packet collection;
      avoid repeated defensive allocations only when immutability is proven.
- [ ] Ensure the new packet is not registered on NeoForge and that no
      post-start **standalone manual file change** triggers a broadcast.
      If FCP reloads server settings, verify FCP native propagation and
      refresh the MCA bridge for mixed clients when supported; otherwise
      explicitly require rejoin/restart, with no silent indefinitely-stale
      connected clients. Document incompatibility with old config clients.
- [ ] **R6:** Test changing a SERVER option via the *actual* FCP GUI and
      via FCP's native file-reload path while clients remain connected:
      FCP/FCP must follow FCP's own behavior without duplicate MCA packets;
      FCP/no-FCP must receive an updated MCA bridge snapshot when live
      updates are supported. If FCP does not propagate live SERVER changes,
      make rejoin/restart a verified explicit contract and ensure no silent
      ongoing disagreement. Include remote SERVER read-only GUI assertions.
- [ ] Run .\gradlew.bat :fabric:test :fabric:build --console=plain
      and then a real two-client join/disconnect smoke for **all four**
      matrix rows. Record observed values, payload path and test limits.
      Review and commit scoped changes only after verification.

## Task 4 — Restrict Fabric configuration editing to the optional FCP GUI

**Files**

- Verify: fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java
  and FCP-guarded ClientConfigHelper.java from Task 2
- Modify: common/src/main/java/net/conczin/mca/server/command/Command.java
- Modify: common/src/main/java/net/conczin/mca/network/c2s/ChatAIContextUpdateRequest.java
- Modify: common/src/main/java/net/conczin/mca/Config.java and the existing
  platform-neutral capability seam only if required for loader-specific gating.
- Tests: add Fabric behavior/source-contract tests in an existing supported
  source set; retain NeoForge config-screen and command behavior tests.

**Interfaces**

- A single platform-owned edit-capability decision guards **command/packet**
  configuration changes (not gameplay/SavedData mutations). On Fabric it is
  false whether FCP is present or not. The optional FCP GUI has a separate,
  validated save path; NeoForge keeps its current editing capability.

- [ ] Inventory config-mutating call sites via
      rg -n 'Config\.save|Config\.(SERVER|COMMON|CLIENT)\..*\.set'
      common/src/main/java fabric/src/main/java and classify which are
      configuration versus world/player/other state.
- [ ] Add failing tests proving Fabric config-editing commands (AI model,
      token/endpoint, enable/disable, TTS toggles) cannot mutate or save
      TOML and that the global AI system-prompt branch in
      ChatAIContextUpdateRequest cannot write COMMON config.
- [ ] Gate or omit those command branches and packet edit routes only on
      Fabric; produce a clear edit-file-and-restart message where a command
      remains discoverable. Preserve per-villager/per-player/per-world
      SavedData paths and NeoForge behavior.
- [ ] Verify FCP-absent startup never loads or registers
      `ConfigScreenFactoryRegistry`, `ConfigScreenSearch` or
      `ConfigurationScreen`. FCP-present startup **must** register and use
      the existing searchable screen. Do **not** implement a replacement
      Fabric screen, independent Mod Menu config entrypoint, config widget,
      MCA runtime reload command or *MCA-specific* file watcher; FCP's own
      watcher and reload events remain enabled in FCP mode.
- [ ] Assert Fabric metadata has only an **optional** FCP suggestion and no
      unconditional config integration entrypoint. Test GUI visibility,
      search/reset, persistence and remote SERVER read-only on Fabric with
      FCP; test NeoForge's original screen independently.
- [ ] Run .\gradlew.bat :common:test :fabric:test :fabric:build
      :neoforge:build --console=plain. Review and commit scoped changes.

## Task 5 — Packaged optional-FCP release verification and documentation

**Files**

- Update: README/configuration instructions and earlier native-config
  design/plan status to point to the file-only design.
- Add: one CI-friendly packaged-jar dependency check under scripts/
  or an existing packaging validation mechanism, only if current tooling
  does not already provide equivalent assertions.
- Verify: fabric/build.gradle, fabric/src/main/resources/fabric.mod.json,
  gradle.properties, common+loader code and packaged output.

- [ ] Write an artifact assertion for the **built Fabric jar**: no **required**
      FCP dependency (optional `suggests` is allowed), no bundled FCP classes,
      no unguarded NeoForge/FCP classloading or replacement GUI/Mod Menu
      config entrypoint. The **original GUI's optional integration classes**
      may be present but cannot load without FCP. Bundled TOML parser classes
      and licenses are present. Compile classpath is not packaged-jar proof.
- [ ] Run .\gradlew.bat :common:test :fabric:test :fabric:build
      :neoforge:build --console=plain; run extra configured NeoForge tests
      only if that test lane exists. Check git diff --check and inspect
      all scoped changes against the frozen setting manifest.
- [ ] Smoke-test the **packaged** Fabric client and dedicated server
      with FCP, NeoForge and Mod Menu absent. Confirm early config
      loading, manually editable TOML after restart, no Fabric config GUI,
      config integration, config-editing commands or watcher,
      legacy migration and world override.
- [ ] Smoke-test the **same packaged Fabric jar with FCP installed**:
      verify the original searchable GUI is available (not replaced),
      its settings/reset/validation/read-only behavior is preserved and
      GUI saves persist via FCP's native spec, FCP watcher/reload works,
      and MCA never creates another writer. Compare canonical TOML from both
      modes with `ModConfigSpec.isCorrect` and inspect file hashes/values
      after repeated FCP-installed/absent switches.
- [ ] Smoke-test **all four** FCP presence combinations, joining server A
      then server B and using integrated singleplayer. Record whether FCP
      native or MCA bridge was selected, verify exactly one applied remote
      source, no stale values on disconnect, and no COMMON/CLIENT secrets
      in either transport/log.
- [ ] Reproduce legacy TOML correction with an explicit fixture and back up
      its old values. Verify relocated/flat recognized keys survive migration,
      new canonical files do not trigger correction merely from metadata,
      and user-created known values survive round-trips. Do not infer a
      data-loss regression solely from September backups.
- [ ] Smoke-test NeoForge native config load, screen/search/reset and
      SERVER sync on the same source commit. Record packaged jar hashes,
      environment and test execution results; report any unexecuted
      runtime scenario explicitly.
- [ ] **R7/R8:** Confirm native reload/pathless SERVER regressions actually
      executed in a loader-backed test lane, not only synthetic unit tests.
      Record list immutability/aliasing test results and, if copy optimization
      was justified, before/after allocation evidence on representative
      gameplay reads. Do not pursue unmeasured cleanup that risks semantics.
- [ ] Close **R1–R8** one by one in the status report with changed files,
      specific regression test names, observed pass/fail output, runtime
      coverage, and any remaining limitations. Prior passing builds and the
      five previously passing NeoForge GameTests are baseline evidence,
      **not** proof that these new review fixes have been verified.
- [ ] Document TOML paths, restart-to-apply policy when FCP is absent,
      native FCP watcher/GUI/save/reload when installed, FCP correction and
      backup/migration rules, safe recovery from malformed files, lack of
      Fabric config-editing commands, sync capability matrix, and network
      protocol compatibility. Review the final diff; commit only
      completed, verified scoped work when implementation is authorized.

## Execution gate

This plan is documentation, not authorization to implement. Review the
spec and plan together, then choose the implementation method. Because
the shared schema and native/Fabric backends have tightly coupled
interfaces, a single native execution sequence with an independent
final review is reasonable. No new worktree is required or permitted
without explicit consent.
