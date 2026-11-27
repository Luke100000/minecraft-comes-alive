# MCA 1.21.1 optional-FCP configuration: implementation checkpoint

Date: 2026-10-15. Branch: `dev/feature/1.21.1-config-system`.
The gameplay merge `de1a9e6d7` is already committed and must be preserved.
The configuration changes below are **uncommitted** and require final review.

## Implemented

- Fabric declares `forgeconfigapiport` in `suggests`, not `depends`, and uses
  a compile-only FCP dependency. The built Fabric jar bundles NightConfig
  TOML/core 3.8.0 but no FCP classes.
- FCP absent: the shared schema is bound to a standalone TOML backend, with
  installation-level COMMON/CLIENT/SERVER values and optional existing
  per-world SERVER override. No config GUI is registered, and Fabric config
  editing commands/global AI prompt updates are disabled.
- FCP present: the shared schema is bound to the native-compatible config
  backend, and the **original** `ConfigScreenSearch`/`ConfigurationScreen`
  is registered through FCP. Client-only registration resides in its own
  client-only class, avoiding dedicated-server classloading.
- Fabric has a validated, bounded SERVER snapshot protocol on join. Only SERVER
  schema entries are transmitted; malformed/unknown/duplicate values reject
  atomically; remote state is cleared on disconnect.
- NeoForge retains native specs and the original searchable screen. A small
  native adapter registration change is necessary because schema definitions
  were made loader-independent; no FCP installation is needed on NeoForge.
- The README describes the optional FCP install matrix, TOML paths, file-edit
  restart behavior, and world override semantics.

## Evidence completed

- `:common:test :fabric:test :fabric:build :neoforge:build` — **BUILD SUCCESSFUL**.
- Focused Fabric TOML and snapshot tests — **BUILD SUCCESSFUL**, covering
  creation, scope isolation, value persistence, invalid/malformed TOML,
  world overrides, comments/unknown keys, protocol rejection and reconnect.
- Baseline vs refactored holder scan: CommonConfig 10/10, ServerConfig
  137/137, ClientConfig 19/19 definition names preserved in the same order.
- Packaged Fabric metadata: FCP appears only in `suggests`; no FCP classes
  packaged; NightConfig core/toml jars present.
- `:fabric:runServer` **without** FCP: loader listed no FCP, Minecraft reached
  `Done`, `stop` shut it down successfully.
- `:fabric:runServer` **with** FCP 21.1.6: after isolating client GUI code,
  Minecraft reached `Done`, `stop` shut it down successfully. An earlier run
  before isolation failed on dedicated-server client classloading; fixed.
- `:fabric:runClient` with FCP 21.1.6 and without FCP: both reached Minecraft
  render/resource initialization; sessions were terminated manually.
- `:neoforge:runGameTestServer -PmcaGameTest=GuardEnemiesSensorGameTests`:
  **all 5 required tests passed** and the task succeeded.

## Remaining acceptance checks / caveats

- The **original Fabric GUI** registered and its client started with FCP, but
  a human has **not yet opened the screen, changed a value, saved, reset,
  searched, or verified remote SERVER read-only** in a client. Do not claim
  the GUI's interactions were runtime tested.
- Real two-client multiplayer, integrated-server reconnect, and mixed FCP
  install/no-install peers were **not** exercised. Unit tests cover snapshot
  validation and simulated A-then-B remote overlay only.
- Fabric uses the native FCP backend when FCP is installed and the standalone
  TOML backend when absent. They target the same named config files but
  their actual write/lifecycle semantics differ. FCP corrected existing
  config files during an FCP-installed smoke run. Validate mode-switch
  preservation and SERVER networking authority under live multiplayer before
  calling the design fully production-ready.
- No release-ready runtime or network compatibility claim is made without
  the above acceptance runs. The current uncommitted changes must be reviewed
  before commit or publication.
