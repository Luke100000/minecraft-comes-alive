# MCA 1.21.1 Fabric File-Only Configuration Without FCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> or superpowers:executing-plans to implement this plan task by task. Each step uses
> a checkbox for tracking. Do not implement before the user reviews this plan.

**Goal:** Run MCA on Fabric without FCP using only on-disk TOML config files;
retain native NeoForge configs and its searchable UI.

**Architecture:** One shared typed schema feeds a native NeoForge adapter and
a standalone Fabric TOML backend. Fabric sends validated SERVER snapshots at
join and exposes no in-game config screen, Mod Menu config integration,
config-editing commands, or config-editing client packets.

**Tech Stack:** Minecraft 1.21.1, Java 21, Fabric Loader 0.19.5,
Fabric API 0.116.17+1.21.1, NeoForge 21.1.252, JUnit, and a pinned,
packaged NightConfig TOML/core version selected after dependency inspection.
FCP 21.1.6 is an existing dependency to remove, **not** a target runtime.

**Spec:** ../specs/2026-10-10-fabric-config-without-fcp-design.md

## Global constraints

- Existing worktree only: C:/Users/mikol/IdeaProjects/minecraft-comes-alive-1.21.1-config-system,
  branch dev/feature/1.21.1-config-system, observed HEAD 190e0fcef. No new
  checkout or worktree. Review Git status before each change; preserve dirty
  ServerConfig.java, VillagerEntityMCA.java, and MixinLivingEntity.java.
- File-only on Fabric means **no configuration UI of any kind, Mod Menu
  config entrypoint, config editing via commands/C2S, watcher, or runtime reload
  command**. Preserve unrelated gameplay UI and persisted world/player data.
- Keep exactly one setting definition per current scope; preserve typed
  Config.COMMON/SERVER/CLIENT field access, immutable product defaults, names,
  paths, validators, comments, translation metadata, and restart markers.
- COMMON credentials/endpoints/prompts and CLIENT TTS/API secrets must never
  enter SERVER sync. SERVER is server/world-owned, COMMON is installation-local,
  CLIENT is physical-client-only.
- The legacy import source is only dev/1.21.1 config/mca.json version 2.
  Never alter/delete it, never overwrite an existing TOML/world override,
  and never turn migrated values into product defaults.
- NeoForge keeps native registration, reload behavior, SERVER sync, and the
  existing searchable configuration screen. No shared runtime signatures may
  require native classes on Fabric.
- One Fabric S2C protocol mca:server_config_snapshot, version 1, maximum
  1,048,576 UTF-8 bytes, full SERVER settings only; no C2S config mutation.
- Files are config/mca-{common,client,server}.toml plus optional
  <world>/serverconfig/mca-server.toml; physical-client I/O only for CLIENT.
- On Fabric, file edits take effect on the next relevant startup/world start;
  no live TOML monitoring or interactive save/broadcast is required.
- Use .\gradlew.bat from the repository root. Pass --console=plain and inspect
  real test execution and exit codes. A build is not runtime proof.

## Review focus

1. Two remote servers joined successively: each receives its own complete
   SERVER values and no stale overlay survives reconnect (Task 3 test).
2. Broken or unwritable TOML/legacy import: original file bytes survive and
   migration is not falsely declared complete (Task 2 test).
3. Fabric AI/TTS config-editing commands and global-system-prompt packet:
   unavailable without changing config, while nonconfig SavedData edits still
   work (Task 4 test).
4. Existing world override followed by another world in one JVM: no previous
   world's values leak; no override created when absent (Task 2 test).
5. Packaged Fabric jar without FCP/NeoForge/Mod Menu: no native classloading,
   config screen, metadata dependency, or missing TOML library (Task 5 test).

---

## Task 1 — Freeze the source schema and isolate native NeoForge config

**Files**

- Create: common/src/main/java/net/conczin/mca/config/{ConfigScope,ConfigValue,ConfigSchema,ConfigBackend}.java
- Modify: common/src/main/java/net/conczin/mca/{Config,CommonConfig,ServerConfig,ClientConfig}.java
- Create: neoforge/src/main/java/net/conczin/mca/neoforge/config/NeoForgeConfigBackend.java
- Modify: neoforge/src/main/java/net/conczin/mca/neoforge/{CommonNeoForge,NeoForgeClientConfigHelper}.java
- Move: common/src/main/java/net/conczin/mca/client/gui/ConfigScreenSearch.java
  to neoforge/src/main/java/net/conczin/mca/neoforge/client/gui/ConfigScreenSearch.java
- Tests: common/src/test/java/net/conczin/mca/config/ConfigSchemaParityTest.java;
  adapt ConfigLayoutTest, ConfigEarlyAccessTest, ConfigDecodedMapCacheTest,
  ConfigLegacyMigrationTest and native ConfigScreenSearchTest (move that native
  test to a supported NeoForge test lane, inspecting build configuration first).
- Resources: common/src/test/resources/config/schema-1.21.1-config-system.json

**Interfaces**

- ConfigScope = COMMON, SERVER, CLIENT.
- ConfigValue<T> exposes T get(), void set(T), T getDefault(), List<String> getPath().
- ConfigSchema.Builder implements the actual builder operations used by
  the three holder constructors; ConfigSchema exposes ordered entry metadata.
- ConfigBackend binds ConfigScope/ConfigSchema and supports save operations.
- NeoForgeConfigBackend exposes ModConfigSpec spec(ConfigScope), never to
  Fabric or public shared code.

- [ ] Verify Git status and reconcile current unstaged ServerConfig.java
      comment before capturing metadata; do not reset or stage unrelated edits.
- [ ] Capture **all** current entries from native specs, including scope,
      full path, type/default, validator examples, comment, translation,
      gameRestart/worldRestart. Derive the count from the fixture; check at
      least showNameTags=true, useSquidwardModels=false,
      babyItemGrowUpTime=24000, and COMMON-only private tokens.
- [ ] Write a failing ConfigSchemaParityTest asserting exact manifest
      parity, early default access, defensive lists, validation, and map
      decoding. Run .\gradlew.bat :common:test --tests
      'net.conczin.mca.config.ConfigSchemaParityTest' --console=plain.
- [ ] Implement one schema and typed handles without reflection or
      duplicate values. Preserve existing gameplay get() calls; move native
      spec construction, migration events and registration into the NeoForge
      adapter. Keep existing native SERVER synchronization.
- [ ] Move native-only config GUI and its tests out of Fabric runtime
      sources, preserving search/reset/validation behavior. Check the
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

- Create: fabric/src/main/java/net/conczin/mca/fabric/config/{FabricTomlConfigBackend,FabricConfigLifecycle}.java
- Create: common/src/main/java/net/conczin/mca/config/LegacyConfigMigration.java
- Modify: fabric/src/main/java/net/conczin/mca/fabric/{MCAFabric,MCAFabricClient}.java
- Remove: fabric/src/main/java/net/conczin/mca/fabric/ClientConfigHelper.java
- Modify: common/src/main/java/net/conczin/mca/Config.java; fabric/build.gradle;
  common/build.gradle; gradle.properties.
- Tests: fabric/src/test/java/net/conczin/mca/fabric/config/{FabricTomlConfigBackendTest,FabricConfigLifecycleTest}.java;
  adapt common ConfigLegacyMigrationTest.

**Interfaces**

- FabricTomlConfigBackend implements ConfigBackend; loadGlobal(boolean
  physicalClient), startWorld(Path worldServerConfigDirectory), stopWorld().
- LegacyConfigMigration imports version-2 JSON for a specified scope and
  *missing* destination Path; only reports success after a durable save.
- A validated TOML snapshot is published as one unit; world overrides
  replace only server-owned values.

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
- [ ] Add failing world tests for global-server fallback, present world
      override precedence, no generated override when absent, world A then
      B in one process, and integrated-server stop/restart.
- [ ] Add failing migration tests for legacy JSON v2 only, all original
      product defaults, missing destination precedence, read-only JSON
      preservation, no import into existing TOML/override, and write failure
      not marking migration successful.
- [ ] Build Fabric TOML backend using validated temporary snapshots;
      preserve existing files on parse/write failures, create missing global
      files as needed, and restore global SERVER state after world stop.
      Use startup-only file loading, no watcher or exposed reload method.
- [ ] Replace FCP initialization events in MCAFabric with Fabric lifecycle
      integration **before** attribute registration and startup gameplay
      consumers; load CLIENT only from physical-client initialization.
      Bundle an ordinary TOML library in the Fabric jar without bundling FCP.
- [ ] Remove ClientConfigHelper, its invocation in MCAFabricClient, and
      all Fabric GUI/native-screen registration in this same integration
      slice: Fabric must compile without FCP before declaring Task 2 green.
- [ ] Remove FCP runtime classes/dependencies and the required
      forgeconfigapiport metadata; keep the change together with the
      standalone Fabric backend to restore compilability. Build without FCP
      installed as a runtime mod.
- [ ] Run .\gradlew.bat :common:test :fabric:test :fabric:build
      --console=plain. Review file writes, classloading, and logs for
      secret-value disclosure; commit only scoped, verified changes.

## Task 3 — Send server-owned Fabric settings at join

**Files**

- Create: fabric/src/main/java/net/conczin/mca/fabric/config/{ServerConfigSnapshot,FabricConfigSync}.java
- Modify: fabric/src/main/java/net/conczin/mca/fabric/{MCAFabric,MCAFabricClient}.java
- Modify: common/src/main/java/net/conczin/mca/config/{ConfigValue,ConfigSchema}.java
- Tests: fabric/src/test/java/net/conczin/mca/fabric/config/FabricConfigSyncTest.java

**Interfaces**

- ServerConfigSnapshot: protocolVersion + encoded full SERVER entries,
  implementing the Fabric-only CustomPacketPayload/HandleablePayload
  integration supported by Network.Registrar.
- FabricConfigSync: snapshot(), applyRemote(ServerConfigSnapshot),
  clearRemote(); on join send once; on disconnect clear client session
  overlay without mutating local files.

- [ ] Write failing tests for protocol version 1, invalid versions, max
      1,048,576 bytes checked before buffer-body allocation, duplicate/
      unknown/missing keys, incorrect types/ranges, and wrong-scope keys.
      Invalid payloads must apply **zero** settings.
- [ ] Seed distinct COMMON/CLIENT secret values and assert they are
      absent from the serialized SERVER snapshot and receiver state.
- [ ] Test join server A, disconnect, join B; also test integrated-server
      disconnect preserving authoritative state, pending-snapshot behavior,
      and correct client-thread publication.
- [ ] Register and send the snapshot using Fabric S2C hooks. Complete
      validation before atomic memory-only publication. Disconnect peers
      cleanly for invalid/incompatible data; audit client-side SERVER reads
      that must not consume stale values during join.
- [ ] Ensure the new packet is not registered on NeoForge and that no
      post-start file change triggers a broadcast. Document explicit
      protocol incompatibility with old config clients.
- [ ] Run .\gradlew.bat :fabric:test :fabric:build --console=plain
      and then a real two-client join/disconnect smoke. Review and commit.

## Task 4 — Enforce no in-game configuration editing on Fabric

**Files**

- Verify: fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java
  and removed ClientConfigHelper.java from Task 2
- Modify: common/src/main/java/net/conczin/mca/server/command/Command.java
- Modify: common/src/main/java/net/conczin/mca/network/c2s/ChatAIContextUpdateRequest.java
- Modify: common/src/main/java/net/conczin/mca/Config.java and the existing
  platform-neutral capability seam only if required for loader-specific gating.
- Tests: add Fabric behavior/source-contract tests in an existing supported
  source set; retain NeoForge config-screen and command behavior tests.

**Interfaces**

- A single platform-owned edit-capability decision guards interactive
  *configuration* changes (not gameplay/SavedData mutations). On Fabric
  it is false; NeoForge keeps its current interactive config capability.

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
- [ ] Verify the Task 2 removal left no Fabric
      ConfigScreenFactoryRegistry, ConfigScreenSearch or
      ConfigurationScreen references. Do **not** implement a Fabric
      screen, Mod Menu config entrypoint,
      config widget, runtime reload command, or file watcher.
- [ ] Assert Fabric metadata contains no config integration entrypoint
      and the distribution has no MCA config-screen registration.
      Test NeoForge native GUI search/reset/remote SERVER read-only in
      the existing runtime acceptance lane.
- [ ] Run .\gradlew.bat :common:test :fabric:test :fabric:build
      :neoforge:build --console=plain. Review and commit scoped changes.

## Task 5 — Packaged no-FCP release verification and documentation

**Files**

- Update: README/configuration instructions and earlier native-config
  design/plan status to point to the file-only design.
- Add: one CI-friendly packaged-jar dependency check under scripts/
  or an existing packaging validation mechanism, only if current tooling
  does not already provide equivalent assertions.
- Verify: fabric/build.gradle, fabric/src/main/resources/fabric.mod.json,
  gradle.properties, common+loader code and packaged output.

- [ ] Write an artifact assertion for the **built Fabric jar**: no FCP
      dependency declaration, FCP classes, NeoForge config/runtime types,
      native GUI classes or Mod Menu config entrypoint; bundled TOML
      parser classes and licenses are present. Development classpath
      contents are not distributed-jar proof.
- [ ] Run .\gradlew.bat :common:test :fabric:test :fabric:build
      :neoforge:build --console=plain; run extra configured NeoForge tests
      only if that test lane exists. Check git diff --check and inspect
      all scoped changes against the frozen setting manifest.
- [ ] Smoke-test the **packaged** Fabric client and dedicated server
      with FCP, NeoForge and Mod Menu absent. Confirm early config
      loading, editable TOML after restart, no Fabric config GUI/commands,
      legacy migration and world override.
- [ ] Smoke-test joining two Fabric servers, reconnecting, integrated
      singleplayer, and both peers with FCP present/absent independently.
      Verify SERVER sync and ensure COMMON/CLIENT secrets never appear
      in any payload/log.
- [ ] Smoke-test NeoForge native config load, screen/search/reset and
      SERVER sync on the same source commit. Record packaged jar hashes,
      environment and test execution results; report any unexecuted
      runtime scenario explicitly.
- [ ] Document TOML paths, restart-to-apply policy, safe recovery from
      malformed files, lack of Fabric config UI/commands, and network
      protocol compatibility. Review the final diff; commit only
      completed, verified scoped work when implementation is authorized.

## Execution gate

This plan is documentation, not authorization to implement. Review the
spec and plan together, then choose the implementation method. Because
the shared schema and native/Fabric backends have tightly coupled
interfaces, a single native execution sequence with an independent
final review is reasonable. No new worktree is required or permitted
without explicit consent.
