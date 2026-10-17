# MCA 1.21.1 Optional-FCP Configuration — Design

Date: 2026-10-11 (revised 2026-10-17)
Status: gameplay merge committed as `de1a9e6d7`; optional-FCP configuration
implementation is in progress. JVM/build and both Fabric startup modes have
been exercised; interactive GUI, canonical TOML compatibility, and mixed
multiplayer validation remain open. The 2026-10-17 revision records the
agreed **FCP-as-authority** design; it is not a claim that the current code
already implements that design.
See `../reports/2026-10-15-optional-fcp-implementation-status.md`.

## Decision and intent

MCA must launch and run on Fabric **with or without** Forge Config API Port
(FCP) installed. FCP is an **optional, opt-in integration**, not a requirement:

- **FCP absent:** no MCA configuration integration or GUI of any kind. Users
  edit TOML files manually outside Minecraft and restart the relevant
  client/server to apply changes. No Mod Menu config entrypoint or alternative
  built-in editor is supplied.
- **FCP installed:** expose the **same existing searchable MCA configuration GUI**
  through FCP, with its existing sections, search, validation, reset, restart
  indications and remote SERVER read-only behavior. GUI changes must persist
  to the canonical configuration, not a second disconnected copy. Do not
  replace the existing GUI with a newly designed screen.

In both modes, Fabric has no MCA config-editing command, config-changing C2S
packet, or MCA-specific runtime reload command. **Without FCP** there is no
file watcher and manual edits need a restart. **With FCP**, use FCP's own
native loading, saving, correction, reload events, and normal file watcher;
do not suppress these native behaviors or add a second MCA watcher. Ordinary
MCA gameplay screens and world/player data editors remain out of scope.

NeoForge keeps native configuration, its searchable native configuration screen,
and its native server synchronization. Preserve the existing settings, defaults,
ownership, migration, validation, and multiplayer authority on both loaders.

This makes the Fabric dependency optional and adapts its UI and synchronization parts of
[the previous native config design](2026-09-26-1.21.1-config-system-port-design.md).
It incorporates and narrows the documentation proposal from
https://github.com/tuxedomik/minecraft-comes-alive-backup/tree/docs/fabric-config-without-fcp-2026-10-10.
Neither documentation branch is an implementation base.

## Target, evidence, and scope

- Implementation checkout: existing worktree
  C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-config-system-port,
  branch dev/feature/1.21.1-config-system, observed HEAD d198d6ac50019fcd9f4fabaaed251b6fad9504d1
  on 2026-10-15. Check status and HEAD again before execution.
- Observed checkout metadata: Minecraft 1.21.1; Java 21; Fabric Loader 0.19.5;
  Fabric API 0.116.17+1.21.1; Loom 1.18.2; NeoForge 21.1.256; current FCP
  21.1.6 (to make optional). Recheck versions when implementation starts.
- Worktree was clean at the earlier review time, but the optional-FCP work
  is now uncommitted and the worktree is dirty. Preserve those edits. Freeze
  the setting manifest from
  the reviewed, post-merge source before transforming config declarations.
- The post-merge holders expose 166 typed setting definitions (COMMON 10,
  SERVER 137, CLIENT 19). The earlier architecture used three native specs.
  Fabric uses FCP registry/events and its screen factory; ConfigScreenSearch
  directly uses NeoForge UI types. Removing the Fabric dependency declaration
  alone cannot work.

## Upstream dev/1.21.1 comparison and merge decision

Use `origin/dev/1.21.1` as the behavioral authority for upstream gameplay
changes, and the current feature branch as the authority for the already-ported
native configuration design. The reviewed refs (2026-10-15) are feature
`d198d6ac5`, upstream `735babac0`, merge base `706c0090b`; branches diverge by
14 feature-side and six upstream commits. Refresh refs and repeat the comparison
immediately before the actual merge; these are evidence snapshots, not a claim
about future HEADs.

The upstream `dev/1.21.1` implementation stores a version-2 JSON object in
`config/mca.json`: `Config extends CommonConfig`, `loadOrCreate()` reads and
rewrites JSON, `configDefaultsVersion` migrates map defaults, and
`getServerConfig()` selects an in-memory server overlay. Its old
`ConfigRequest` C2S / `ConfigResponse` S2C pair serializes `CommonConfig` with
Gson. This is **migration and behavior evidence**, not the replacement backend:
do not restore those old packets, whole-object server sync, mutable config
model, or unconditional JSON rewrite. Map legacy keys into the appropriate
COMMON/SERVER/CLIENT schema scopes instead.

**Merge upstream before shared-schema implementation**, as a separate,
explicitly authorized integration step. Nonmutating `git merge-tree` preview
found four content conflicts: `CommonConfig.java`, `VillagerEntityMCA.java`,
`GuardEnemiesSensor.java`, and `GuardEnemiesSensorGameTests.java`. In particular,
upstream adds `enableVillagerCollisions = true` to legacy `CommonConfig`;
the feature branch already defines `Config.SERVER.enableVillagerCollisions`
with default `true`, and its collision check reads that SERVER value. Keep
the native SERVER owner and port any upstream collision behavior against it;
do **not** introduce a duplicate legacy COMMON setting.

The six new upstream commits principally change navigation, guard/archer AI,
building scans, and their tests. Audit **every source-side changed hunk** into
`ported`, `adapted`, `already present`, or `not portable`, with exact files,
methods, and verification. Any proposed omission marked `not portable` needs
the user's explicit approval. Never treat `ours`/`theirs`, conflict-free merge,
or a successful compile as gameplay parity evidence. Preserve the existing
native config API, UI, migrations, scopes, and updated documentation through
the merge; verify no old JSON/config networking or FCP assumptions return.

The gameplay merge described here was subsequently completed in commit
`de1a9e6d7`. This document does not claim that the optional-FCP configuration
implementation has been completed or verified.

## Architecture choice

Use **one explicit shared schema** with two mutually exclusive Fabric runtime
implementations: FCP's native `ModConfigSpec`/registry when FCP is installed,
and a standalone FCP-compatible TOML backend otherwise. NeoForge continues
using its own native implementation. **FCP is the source of truth when
installed**, for values, validation, correction, persistence, GUI saves,
reloads, and native SERVER sync wherever both peers support it. MCA must not
maintain a competing in-memory/file authority in that mode. The standalone
backend reproduces the same canonical paths, types, constraints, defaults,
section metadata, comments, and TOML representation without loading FCP.
Shared holders remain loader-independent so the no-FCP startup path cannot
require NeoForge/FCP classes.

Fabric should declare FCP in `suggests`, not `depends`; compile-time access
may be provided via `modCompileOnly` or an isolated optional source set, but
FCP must not be bundled. Detect it with `FabricLoader.isModLoaded` before
loading or initializing any FCP-specific integration class. Reuse the
existing `ConfigScreenSearch` implementation with the FCP screen factory,
not a different Fabric UI. Verify the actual FCP screen/registry API and
its save semantics before committing to an adapter: GUI edits must save via
FCP's registered spec to the same canonical files, and the no-FCP path must
never load native GUI classes.

Alternatives rejected:

1. Requiring FCP or offering a replacement GUI when it is absent: defeats
   optional installation and manual-file-only fallback.
2. Bundling the FCP mod: avoids manual installation but still carries an FCP
   runtime, contrary to the selected goal.
3. Duplicate Fabric setting declarations or homemade TOML parser: creates
   schema drift and avoidable parser correctness/security problems.
4. Two active Fabric config backends writing the same TOML independently:
   risks overwrites, stale UI values and conflicting multiplayer authority.

No reflection-based schema extraction, copied NeoForge packages or auxiliary
live config object. An optional native/FCP classloading path is permitted
**only** behind the FCP-present integration boundary.

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
  default, type, constraints, **section-level** as well as entry comments
  and translations, and restart flags. Native builder metadata applied
  *before* `push(section)` must survive the shared-schema adaptation.
  One builder definition per setting; support the actual holder declarations
  (push/pop, comment, translation, gameRestart/worldRestart, define,
  defineInRange, defineListAllowEmpty, etc.) after source inspection.
- ConfigBackend: binding and persistence/lifecycle operations implemented by
  the loader. The shared Config class chooses it once before any registry or
  attribute consumer reads config. Loader-specific specs never appear in
  shared public signatures.

Preserve map/list encoding, registry-aware validators, and decoded-map caches.
The native and standalone adapters must agree on generated range/default
comments and section nesting. A FCP-valid standalone file should pass FCP's
`ModConfigSpec.isCorrect` without a corrective write; metadata differences
are not an excuse for rewriting an otherwise canonical new file.
Build a manifest of **all** settings from the current reviewed worktree:
scope, path, type, actual default, range/list validator cases, translation,
comment, restart metadata. Compare every shared entry to this frozen manifest.
Do not silently reuse the older document's counts, values, or versions.
The post-merge manifest must also reconcile new upstream config fields, even
if they already exist in a different native scope; compare behavior and owner,
not just matching field names or values.

## NeoForge remains native

NeoForgeConfigBackend converts the shared schema to real ModConfigSpec values,
registers COMMON/SERVER/CLIENT using the existing ModContainer lifecycle,
and continues native correction, save, reload, and SERVER sync. Retain
NeoForgeClientConfigHelper and searchable ConfigScreenSearch behavior,
including search, list validation, reset defaults, and remote SERVER read-only
rules. Preserve the existing searchable GUI for **both NeoForge and Fabric
with FCP installed**. Isolate its native-dependent classes/tests so they are
never loaded on Fabric without FCP; do not move them exclusively into a
NeoForge-only source set if doing so breaks reuse by the optional Fabric path.
Keep pure shared helpers only when demonstrably reused.

Native-config-specific migrations observe real loaded config paths and skip
pathless default events. This migration never changes ModConfigSpec defaults.
The old MCA ConfigRequest/ConfigResponse packets remain removed.

## Fabric files and lifecycle

Use a small TOML library for the no-FCP path; the preferred candidate is the
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
use the negotiated authoritative SERVER state (FCP native or MCA bridge),
not its own SERVER file, while connected. No client
config file I/O or client-only classloading occurs on a dedicated server.

If a global TOML is missing, create it with the original product defaults
or migrated values. **Without FCP**, read each existing file into a validated
temporary snapshot and publish the completed state atomically. For a valid
TOML with individual invalid entries, report affected paths and use validated
defaults without silently replacing the file. Preserve unrelated valid
values, unknown keys, comments, Unicode, and map/list text on standalone
saves. For malformed TOML, leave bytes unchanged, log the file path (never a
secret value), use defaults at process startup or the global validated SERVER
state when a *different world's* override fails (never inherit the previous
world's values), and do not overwrite the broken file. Writes use a sibling
temporary file and safe replacement; failures must not mark migration complete.

**With FCP**, defer to FCP's actual native correction rules, including
its treatment of unknown keys and comments; do not promise byte-identical
preservation when the input is not native-canonical. Back up any legacy
file before a one-time path/ownership migration or other potentially lossy
normalization. Never silently discard a valid, recognizable user value.
An already canonical file must not be rewritten merely because it was
created without FCP.

**Without FCP there is no in-game edit or reload path on Fabric.** Manual
file edits require restarting the relevant game/server (or beginning the next
world/server session for a world override). **With FCP**, the existing
searchable GUI is the sole *in-game* config editor; FCP controls native validation,
save/restart semantics, watcher and reload events. The GUI may not bypass
SERVER authority or create a second persistent config copy. Do not expose
a separate generic interactive config API or MCA reload command.

## Canonical format and observed rewrites (2026-10-15)

FCP 21.1.6 logged `Configuration file ... is not correct. Correcting` for
`mca-common.toml` and `mca-server.toml` during the FCP-present smoke. Its
`ConfigTracker` calls native `ModConfigSpec.isCorrect`/`correct`, which checks
paths, values, expected comments, and extra/missing keys. It may rewrite a
parseable TOML file; this is not evidence that both MCA backends wrote it at
the same time. FCP's file watcher is enabled by default.

Comparing *older September backups* to the resulting October files showed
25 SERVER keys at different section paths and seven old COMMON keys absent
from the new COMMON scope; FCP generated native default/range comments and
reported several list corrections. Those older backups are **not** a direct
before/after diff of the October rewrite. The next FCP client startup did
not show repeated correction warnings. Verify exact changed values using
controlled fixtures, not the historical backup alone.

The shared schema/native adapter must reproduce section translations and
comments, range/default comment formatting, list validators, and complete
key layout. The standalone writer must emit FCP-acceptable TOML; test both
directions of mode switching (absent -> installed -> absent), with no
unnecessary correction or drift for canonical known entries. For genuinely
legacy, flat, or relocated keys, provide an explicit, idempotent migration
**before FCP registers/loads that scope**; FCP loads COMMON/CLIENT during
registration and SERVER later in its server lifecycle. Move known values to
the canonical scope/path, prefer an already-present canonical value in a
conflict, never transfer secrets into SERVER, preserve backups and report
collisions. Use no second live writer after FCP takes ownership. FCP may
subsequently normalize metadata/unknown keys according to its contract.

Apply the existing `defaultMapsVersion` upgrades to *valid existing TOML*
as well as new files. Preserve explicit user overrides and intentional
deletions; mark the version and imported legacy state only after a durable
save. Validate the global SERVER fallback and existing world overrides with
both backends before claiming parity.

## Legacy mca.json migration

Accept only config/mca.json with the current dev/1.21.1 version-2 schema.
Only import into absent native destinations: COMMON, CLIENT, and global
SERVER; an existing file or per-world override must not be overwritten.
Keep original product defaults immutable, preserve mca.json byte-for-byte,
reject invalid values using the same schema validation, and record success
only after saving a valid destination. Malformed/unsupported JSON leaves
the original file intact and falls back to defaults. Per-villager Inworld
character assignments remain per-world SavedData, not shared config.
Preserve its `configDefaultsVersion` map-default upgrade intent, including user
overrides and deletions, without treating upgrade metadata or migrated values
as product defaults. Use the actual reviewed v2 JSON field names and types,
including upstream additions, in migration fixture tests.

## Fabric multiplayer authority

The **server's selected backend is authoritative**. When FCP is present on
both peers, prefer FCP's own SERVER sync and its native client spec; do not
also apply an MCA snapshot as a competing second source. When FCP is absent
on either peer, an MCA S2C compatibility bridge must convey the same
server-owned values. The bridge is a transport/remote overlay, **not** an
independent server configuration store:

| Fabric server | Fabric client | SERVER configuration transport |
| --- | --- | --- |
| FCP installed | FCP installed | FCP native sync; one applied authority |
| FCP installed | FCP absent | MCA snapshot from the server's FCP values |
| FCP absent | FCP installed | MCA snapshot from standalone values; override any FCP-local SERVER defaults |
| FCP absent | FCP absent | MCA snapshot from standalone values |

Confirm from FCP 21.1.6 sources and live sessions when/how its packet is
registered, sent, applied, and cleared. Select the path using a reliable
peer-capability/handshake signal at the appropriate Fabric networking phase,
not only the server's local `isModLoaded` result or a race with the join
event. If the native path cannot be selected safely, stop and document the
blocker rather than sending two active remote overlays. A capability signal
is not permission to modify config via C2S.

The MCA bridge retains `mca:server_config_snapshot`, protocolVersion=1,
full SERVER paths only, and a 1,048,576-byte UTF-8 limit. Bound length
before decoding or allocating. A connected peer must not silently fall back
to local/server-default values while its authoritative sync is pending.
When FCP reloads a live SERVER setting, verify whether FCP propagates it to
already-connected peers. Use supported native synchronization where possible;
if it does not support updates, explicitly define/document the required
rejoin/restart behavior before shipping, and ensure mixed peers cannot remain
silently out of sync. The standalone backend still has restart-only edits.

Reject incompatible protocol versions, malformed JSON, duplicate/unknown/
missing keys, wrong-scope keys, invalid values, and oversized payloads as a
whole; disconnect incompatible peers with a clear message. Apply only on
the client game thread as an atomic, memory-only overlay. Never persist
remote settings. While awaiting whichever first authoritative sync path was
negotiated, prevent client-side policy-dependent features from using stale
previous-server values. On disconnect clear MCA overlays and FCP's remote
SERVER state as appropriate, restoring the correct local/default state
without overwriting an active integrated server's authoritative state. Test
sequential joins to different servers and integrated-server disconnection.

Only SERVER entries may be transmitted. Private COMMON AI tokens, endpoints,
prompts, and CLIENT TTS credentials/keys stay local. No C2S config writes,
no NeoForge registration of this Fabric payload. The effective SERVER values
must be identical for equivalent settings regardless of each peer's FCP mode.
Cross-loader peer compatibility and older native-port clients are not
guaranteed; incompatible peers fail explicitly.

## Optional Fabric GUI; no fallback GUI

Preserve the existing `ConfigScreenSearch` and `ConfigurationScreen`
integration behind an **FCP-installed-only** registration in Fabric's
`ClientConfigHelper` (or a dedicated optional equivalent). No FCP means
no `ConfigScreenFactoryRegistry` calls, no GUI entrypoint, no screen
classloading and no replacement `FabricConfigScreen`/`FabricConfigListScreen`.
With FCP installed the original config screen must be accessible and behave
as before. Existing generic MCA gameplay screens remain in both modes.

The current shared Command.java contains AI/TTS commands that call
ConfigValue.set() and Config.save*(). ChatAIContextUpdateRequest also
persists a global AI system prompt. On Fabric, config-mutating commands,
their completion/help entries where appropriate, and the global-prompt
packet branch must be unavailable or return a clear file-edit/restart
message without changing state; this restriction applies even when the FCP
GUI is available. NeoForge retains current functionality.
Commands and packets that edit per-player/per-villager/world SavedData
rather than configuration remain functional; classify each call site
instead of disabling unrelated gameplay. Without FCP, Fabric config writes
are limited to initial file creation and one-time legacy migration; with FCP,
the existing GUI may also save validated local configuration.

## Acceptance and remaining decisions

- Fabric packaged client and dedicated server start and play with no FCP,
  no NeoForge classes, and no Mod Menu installed; config loads before
  attributes/registries need it. With FCP installed, the **original** Fabric
  searchable config GUI is available and functional, including persistence.
- Fabric without FCP has TOML files only: no config GUI/entrypoint, command
  editing, C2S global config editing, watcher or hot reload. Fabric with FCP
  uses FCP's native watcher/reload and only the existing GUI, not an added or
  replacement editor. Restart/rejoin and GUI-save semantics are documented.
- All settings match the frozen manifest; existing defaults, migration,
  world overrides, map/list validation/caching, and NeoForge native GUI
  behavior remain correct.
- FCP-generated and standalone-generated files are native-spec compatible;
  canonical files do not incur gratuitous correction on mode switches, and
  changed legacy paths are explicitly migrated without losing user overrides.
- Each of the four Fabric FCP-presence combinations uses exactly one
  authoritative SERVER sync path, never leaks COMMON/CLIENT secrets, and
  survives reconnects and integrated-server lifecycle.
- Standalone mode never silently overwrites malformed/unwritable configs;
  FCP mode follows documented native backup/correction rules. The produced
  Fabric jar and metadata contain no **required** FCP dependency and no
  unconditional FCP/native-runtime classloading; compile-classpath success
  alone does not satisfy this test.
- Verify packaged Fabric client/server and multiplayer with FCP absent and
  present, plus Fabric's original FCP-backed GUI and NeoForge native screen
  at runtime. JVM/build success is separate evidence.
- Preserve the already-audited `dev/1.21.1` gameplay merge, single SERVER
  `enableVillagerCollisions` owner, and both loaders' gameplay behavior;
  never restore legacy JSON as the live backend.

This revision changes requirements only; prior code and smoke-test evidence
remain as recorded in the status report, not as proof of new acceptance.
The implementation plan is
[2026-10-10-fabric-config-without-fcp.md](../plans/2026-10-10-fabric-config-without-fcp.md).
