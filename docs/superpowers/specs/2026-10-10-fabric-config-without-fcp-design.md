# MCA 1.21.1 Fabric File-Only Configuration Without FCP — Design

Date: 2026-10-11
Status: design prepared for review; implementation not approved or started.

## Decision and intent

MCA must launch and run on Fabric without Forge Config API Port (FCP) installed.
**Fabric configuration is files only.** Users edit TOML outside Minecraft and
restart the relevant client/server to apply changes. There is **no Fabric
configuration screen, Mod Menu configuration entrypoint, in-game settings
editor, configuration command, config-changing C2S packet, runtime reload
command, or file watcher**. Ordinary MCA gameplay screens and world/player data
editors are not configuration editors and remain out of scope.

NeoForge keeps native configuration, its searchable native configuration screen,
and its native server synchronization. Preserve the existing settings, defaults,
ownership, migration, validation, and multiplayer authority on both loaders.

This replaces the Fabric dependency, UI, and synchronization parts of
[the previous native config design](2026-09-26-1.21.1-config-system-port-design.md).
It incorporates and narrows the documentation proposal from
https://github.com/tuxedomik/minecraft-comes-alive-backup/tree/docs/fabric-config-without-fcp-2026-10-10.
Neither documentation branch is an implementation base.

## Target, evidence, and scope

- Implementation checkout: existing worktree
  C:/Users/mikol/IdeaProjects/minecraft-comes-alive-1.21.1-config-system,
  branch dev/feature/1.21.1-config-system, observed HEAD 190e0fcef788484ec2a996e8fcb4dea79c3afce9.
- Observed checkout metadata: Minecraft 1.21.1; Java 21; Fabric Loader 0.19.5;
  Fabric API 0.116.17+1.21.1; NeoForge 21.1.252; current FCP 21.1.6
  (to remove). Recheck versions when implementation starts.
- The checkout has unrelated dirty work in ServerConfig.java, VillagerEntityMCA.java,
  and MixinLivingEntity.java. Preserve those edits; do not overwrite, reset, or
  assume the committed HEAD contains their behavior. The setting manifest must
  be recorded from the chosen reviewed source state before transformation.
- Existing holders expose 160 typed setting fields across CommonConfig,
  ServerConfig, and ClientConfig. Config.java constructs three native specs.
  Fabric uses FCP registry/events and its screen factory; ConfigScreenSearch
  directly uses NeoForge UI types. Removing the Fabric dependency declaration
  alone cannot work.

## Architecture choice

Use **one explicit shared schema**, a native NeoForge adapter, and a **single
standalone Fabric TOML backend**. This is necessary because shared holders
currently expose native ConfigValue types and the Fabric jar must have no
runtime reference to NeoForge/FCP types.

Alternatives rejected:

1. Optional FCP backend or optional FCP screen: retains two Fabric lifecycle
   paths and conflicts with the file-only requirement.
2. Bundling the FCP mod: avoids manual installation but still carries an FCP
   runtime, contrary to the selected goal.
3. Duplicate Fabric setting declarations or homemade TOML parser: creates
   schema drift and avoidable parser correctness/security problems.

No reflection-based schema extraction, copied NeoForge packages, auxiliary
live config object, or optional native classloading path on Fabric.

## Shared configuration contract

Keep net.conczin.mca.Config.COMMON, SERVER, CLIENT and existing field names so
gameplay call sites continue to use Config.SERVER.someSetting.get().
Create package net.conczin.mca.config with:

- ConfigScope: COMMON, SERVER, CLIENT.
- ConfigValue<T>: get(), set(T), getDefault(), getPath(); typed value handle
  with an immutable product default and validated backend binding.
  Early get() yields a defensive copy of the original default; collection
  data must not expose mutable backing state.
- ConfigSchema: ordered sections/entries containing scope, full path,
  default, type, constraints, comments, translation, and restart flags.
  One builder definition per setting; support the actual holder declarations
  (push/pop, comment, translation, gameRestart/worldRestart, define,
  defineInRange, defineListAllowEmpty, etc.) after source inspection.
- ConfigBackend: binding and persistence/lifecycle operations implemented by
  the loader. The shared Config class chooses it once before any registry or
  attribute consumer reads config. Loader-specific specs never appear in
  shared public signatures.

Preserve map/list encoding, registry-aware validators, and decoded-map caches.
Build a manifest of **all** settings from the current reviewed worktree:
scope, path, type, actual default, range/list validator cases, translation,
comment, restart metadata. Compare every shared entry to this frozen manifest.
Do not silently reuse the older document's counts, values, or versions.

## NeoForge remains native

NeoForgeConfigBackend converts the shared schema to real ModConfigSpec values,
registers COMMON/SERVER/CLIENT using the existing ModContainer lifecycle,
and continues native correction, save, reload, and SERVER sync. Retain
NeoForgeClientConfigHelper and searchable ConfigScreenSearch behavior,
including search, list validation, reset defaults, and remote SERVER read-only
rules. Move native GUI classes and native-only tests out of the sources
packaged for Fabric; keep pure shared helpers only when demonstrably reused.

Native-config-specific migrations observe real loaded config paths and skip
pathless default events. This migration never changes ModConfigSpec defaults.
The old MCA ConfigRequest/ConfigResponse packets remain removed.

## Fabric files and lifecycle

Use a small TOML library rather than FCP; the preferred candidate is the
NightConfig core/TOML version resolved against this checkout's current
NeoForge dependency. Verify exact version, license, Loom packaging, and
runtime relocation before choosing it. Bundle ordinary library classes
(relocated if necessary) inside the Fabric artifact; do not ship an FCP mod.

Files and ownership:

| File | Owner | When read |
| --- | --- | --- |
| config/mca-common.toml | installation-local, never synced | process startup |
| config/mca-client.toml | physical client only, never synced | client startup |
| config/mca-server.toml | local hosting/server defaults | startup before config consumers |
| <world>/serverconfig/mca-server.toml | world SERVER override if it exists | server/world start |

An existing world override wins. Otherwise use the global SERVER defaults:
do not create a world override merely because a world starts. Restore the
appropriate global SERVER state after a world stops; test consecutive worlds
and integrated-server lifecycle in one JVM. A remote multiplayer client must
use the server snapshot, not its own SERVER file, while connected. No client
config file I/O or client-only classloading occurs on a dedicated server.

If a global TOML is missing, create it with the original product defaults
or migrated values. Read each existing file into a validated temporary
snapshot and publish the completed state atomically. For a valid TOML with
individual invalid entries, report affected paths and use validated defaults
for those entries without silently replacing the user's file. Preserve
unrelated valid values, unknown keys, comments, Unicode, and map/list text.
For malformed TOML, leave the bytes unchanged, log the file path (never a
secret value), use the last valid snapshot on reload or defaults at startup,
and do not write over the broken file. A write uses a sibling temporary file,
then a safe replacement; failures must not falsely mark migration complete.

**No in-game edit or reload path on Fabric.** Normal file edits require
restarting the relevant game/server (or beginning the next world/server
session for a world override). Restart-only settings are never applied early
through an editor, and no post-join update packet is required for file edits.
Backend-internal set() remains for controlled tests and loader-owned state,
not as an exposed interactive config API.

## Legacy mca.json migration

Accept only config/mca.json with the current dev/1.21.1 version-2 schema.
Only import into absent native destinations: COMMON, CLIENT, and global
SERVER; an existing file or per-world override must not be overwritten.
Keep original product defaults immutable, preserve mca.json byte-for-byte,
reject invalid values using the same schema validation, and record success
only after saving a valid destination. Malformed/unsupported JSON leaves
the original file intact and falls back to defaults. Per-villager Inworld
character assignments remain per-world SavedData, not shared config.

## Fabric multiplayer authority

Fabric owns one S2C payload mca:server_config_snapshot using existing
network registration. The payload contains protocolVersion=1 and a UTF-8
JSON object keyed by full SERVER schema paths, limited to 1,048,576 UTF-8
bytes. Bound the raw length before decoding or allocating the body. Send
one complete validated snapshot on join; no Fabric GUI or external live
reload means there is no interactive setting-change broadcast.

Reject incompatible protocol versions, malformed JSON, duplicate/unknown/
missing keys, wrong-scope keys, invalid values, and oversized payloads as a
whole; disconnect incompatible peers with a clear message. Apply only on
the client game thread as an atomic, memory-only overlay. Never persist
remote settings. While awaiting the first snapshot, prevent client-side
policy-dependent features from using stale previous-server values. On
disconnect clear remote state, restoring local defaults/source without
overwriting an active integrated server's authoritative state. Test sequential
joins to different servers and integrated-server disconnection.

Only SERVER entries may be transmitted. Private COMMON AI tokens, endpoints,
prompts, and CLIENT TTS credentials/keys stay local. No C2S config writes,
no NeoForge registration of this Fabric payload. Fabric behavior is identical
whether FCP is absent, installed on one peer, or installed on both peers.
Cross-loader peer compatibility and older native-port clients are not
guaranteed; incompatible peers fail explicitly.

## No Fabric interactive config editing

Remove the Fabric ClientConfigHelper config-screen registration; do not
introduce FabricConfigScreen, FabricConfigListScreen, Mod Menu integration,
or any replacement GUI. Existing generic MCA gameplay screens remain.

The current shared Command.java contains AI/TTS commands that call
ConfigValue.set() and Config.save*(). ChatAIContextUpdateRequest also
persists a global AI system prompt. On Fabric, config-mutating commands,
their completion/help entries where appropriate, and the global-prompt
packet branch must be unavailable or return a clear file-edit/restart
message without changing state. NeoForge retains current functionality.
Commands and packets that edit per-player/per-villager/world SavedData
rather than configuration remain functional; classify each call site
instead of disabling unrelated gameplay. The only Fabric config write
paths are initial file creation and one-time legacy migration.

## Acceptance and remaining decisions

- Fabric packaged client and dedicated server start and play with no FCP,
  no NeoForge classes, and no Mod Menu installed; config loads before
  attributes/registries need it. Running with FCP installed does not
  change the backend or behavior.
- Fabric users have TOML files only: no config GUI/entrypoint, command
  editing, C2S global config editing, automatic watcher, or hot reload.
  Restart/rejoin semantics are documented.
- All settings match the frozen manifest; existing defaults, migration,
  world overrides, map/list validation/caching, and NeoForge native GUI
  behavior remain correct.
- S2C SERVER snapshot is complete and validated, never leaks COMMON/CLIENT
  secrets, and survives reconnects and integrated-server lifecycle.
- Damaged/unwritable configs never get silently overwritten. The produced
  Fabric jar and metadata contain no FCP requirement or native-runtime
  types; compile-classpath success alone does not satisfy this test.
- Verify packaged Fabric client/server, multiplayer clients, and NeoForge
  config/native screen at runtime. JVM/build success is separate evidence.

This document is design-only. No code, packaged jars, or runtime checks are
claimed complete. The implementation plan is
[2026-10-10-fabric-config-without-fcp.md](../plans/2026-10-10-fabric-config-without-fcp.md).
