# Interaction command cleanup and addon API — design (draft)

**Status:** Revised for review; documentation only. Implementation is not authorized merely by this document.
**Target:** Existing `dev/1.21.1-dialogue-system-b` Windows worktree; Minecraft 1.21.1, Java 21, Fabric Loader 0.20.1 and NeoForge 21.1.265.
**Revision (2026-11-12):** User chose to keep command strings and `mca:command`, reuse the existing protocol and simplify the implementation beneath them. This replaces the earlier typed-effect/menu-packet proposal.

## Intent and existing work

Keep the useful command system, remove duplicated dispatch and ambiguous outcomes, and allow addons to add commands without patching MCA internals. Existing menu buttons and authored dialogue should continue to work without a format migration. `DialogueEngine` remains the sole Talk/session/history owner; `EntityCommandHandler` remains the sole Interact owner. Gameplay stays in existing relationship, brain, inventory and residency owners.

Reuse the [deferred Work authorization finding](../reviews/2026-11-11-deferred-work-command-authorization.md), [earlier authorization design](2026-11-10-interaction-command-authorization-design.md) and [plan](../plans/2026-11-10-interaction-command-authorization.md). Keep `assets/mca/api/gui/work.json`, its existing navigation and `Chore` implementation; do not invent a second Work dialogue.

## Current source and original addon contracts

- `InteractionVillagerMessage(String command, UUID villagerUUID, UUID interactionId)` already checks living target, reach, interaction owner/UUID and `InteractionMenuCommandAccess`. Retain its payload identity and field layout.
- Work buttons are visible for `(family && !baby) || (!family && mercenary)`, but the server currently allows the five Work commands without that predicate. This is a source finding, not a demonstrated runtime exploit.
- `DialogueAction.Command` implements the existing `{"type":"mca:command","command":"apologize"}` format. Nine bundled occurrences in seven event files remain valid.
- `DialogueEngine.RELIABLE_DIALOGUE_COMMANDS`, `VillagerCommandHandler.handleDialogue` and `handle` overlap. The boolean from `handle` means close-screen, not success. There is already a reusable `VillagerCommandHandler.DialogueCommandResult(boolean accepted, boolean closeScreen)` with `accepted(boolean)` / `rejected(boolean)` factories; no new outcome record is needed.
- The [unified dialogue design](2026-10-12-unified-dialogue-flow-design.md) preserves datapack extension points. The [overhaul master design](2026-11-09-dialogue-overhaul-master-design.md) and [approved async design](2026-11-09-dialogue-pending-and-async-design.md) preserve namespaced addon registration and shared loader behavior.
- Reuse `DialogueAction.register(ResourceLocation, Codec<T>, BiConsumer<T, DialogueContext>)`, `DialogueCondition.register(...)` and `DialogueAsyncResolver.register(...)` unchanged. Keep `DialogueAction.Registered` and its completion semantics. Typed parameterized effects and asynchronous lookups already have APIs; don't recreate them for commands.
- `MCAScreens` loads resource-pack button JSON. The present server allowlist rejects unknown commands: dynamic buttons alone do not provide an addon execution API. Keep dynamic layouts and translation identifiers.

## Approaches and chosen direction

1. Replace commands with dedicated effect classes, a closed enum and a new packet: rejected by the user as unnecessarily restrictive and complex.
2. Retain separate menu/dialogue string dispatchers indefinitely: preserves duplication and forces addons to patch multiple places.
3. **Chosen:** keep strings, JSON and packet; add one small shared command registration/dispatch class, reuse the existing result type, then delete redundant internal routing. No second action registry or protocol migration.

Here, a command means an MCA gameplay operation such as `harvesting` or `myaddon:inspect`. It does not implicitly mean arbitrary Minecraft `/commands`. Adding a generic slash-command bridge, its execution identity or elevated permissions is outside this revision; no installed addon behavior is prevented from using Minecraft's normal APIs under its own explicit policy.

## Contracts

### 1. Finish Work authorization

Apply `(Constraint.FAMILY && Constraint.NOT_BABY) || (Constraint.NOT_FAMILY && Constraint.MERCENARY)` to `fishing`, `harvesting`, `hunting`, `chopping` and `stopworking` in the existing server policy. Denial leaves job, resources and ownership unchanged. Keep `sethome`, `setworkplace` and `gohome` under their separate Locations rules. No client-derived permission cache or new owner state.

### 2. One small command API

Create `InteractionCommands` in `net.conczin.mca.entity.interaction`. Reuse the existing `DialogueCommandResult` type. Its public registration surface is:

```java
public static void register(ResourceLocation id,
        Predicate<Context> allowed,
        Function<Context, VillagerCommandHandler.DialogueCommandResult> executor);
```

Nested `Context` is `record Context(ServerPlayer player, VillagerLike<?> villager, UUID interactionId, Source source, String command)`; nested `Source { MENU, DIALOGUE }` identifies the trusted server entry point, not a closed list of command names. Addon `allowed` predicates may accept either or both sources. This keeps one registration per command and prevents a dialogue-only operation becoming callable by a forged menu packet.

Common dispatch methods are `canExecute(Context context): boolean` and `execute(Context context): DialogueCommandResult`. Both resolve the same entry from `context.command()`; callbacks can read the original command when preserving an existing builtin argument form. `execute` rechecks all guards immediately before mutation. Guards require the server thread, live player/target in the same current world, existing reach and interaction-owner/UUID checks, known registration and its read-only permission predicate. The packet always constructs `Source.MENU`; only the validated engine completion path constructs `Source.DIALOGUE`. Clients never choose the source. Dialogue still requires its existing event/choice/session/acknowledgement checks before dispatch.

Register during common mod initialization before resource loading/interaction handling. Null arguments and duplicate IDs fail registration; public addon registration cannot overwrite builtins or register in MCA's `mca` namespace. Java registrations persist across resource reload, without another synchronization or discovery framework. No client handler registration is required for server callbacks. Each executor is synchronous and calls the existing gameplay owner. Unknown/denied commands return `rejected(false)` without side effects; callback exceptions are logged with the ID and reject without automatic closure. Addon handlers must validate before mutation; there is no rollback of arbitrary addon changes.

Builtin entries reuse `InteractionMenuCommandAccess` for menu eligibility and existing relationship/age/cost checks for dialogue eligibility. The registry entry replaces the separate reliable-dialogue allowlist; do not retain competing lists or apply the menu allowlist to every dialogue command. Menu-only commands reject the DIALOGUE source; dialogue-only commands reject MENU. A shared command can explicitly allow both when its domain rules support that behavior.

### 3. Preserve command names and JSON

Existing bare names remain exact builtin aliases: for example `apologize` -> `mca:apologize`, `divorceConfirm` -> `mca:divorce_confirm`, `hire_short` -> `mca:hire_short`, and `profession.guard` -> `mca:profession.guard`. Use an explicit alias table and resolve the full legacy name before considering any old argument syntax. Do not split namespaced addon IDs on dots, invent aliases for unknown bare names or silently substitute the Minecraft namespace. Preserve currently reachable builtin argument forms at their owner; do not broaden previously rejected forms as a side effect of cleanup.

Addons use their namespaced name directly in the same format:

```json
{"type":"mca:command","command":"myaddon:inspect"}
```

Keep `DialogueAction.Command`, its current codec and bundled command resources. No seven-effect family, new per-effect codecs, JSON rewrite, or unsupported-legacy-format migration. Existing typed `mca:slap`, registered typed actions and `mca:structure_location` remain unchanged.

### 4. Same packet and simple addon buttons

Keep `InteractionVillagerMessage` and its string field serialization with existing codec bounds. Its server handler calls the shared dispatcher instead of independently allowing/dispatching commands. No enum serialization, new packet ID, custom argument payload, mirrored client registry or additional command-length cap in this scope.

Existing buttons without a new field keep their current command derivation and local navigation/Gift behavior. Add an optional string `command` to `MCAButton` solely to decouple a button's translated identifier from its command; preserve the existing constructor with an overload if needed. An addon can put this in a supported existing layout:

```json
{"identifier":"gui.myaddon.inspect","notifyServer":true,
 "targetServer":false,"command":"myaddon:inspect"}
```

When present, send that command unchanged via the existing packet. Blank explicit commands reject the click without fallback. Absent uses the existing identifier-derived builtin name. Known local navigation and Gift mode keep precedence. The server still decides registration and permission: missing addons or resource-pack-only buttons grant no execution rights. Keep layout and translation assets on clients and the Java handler on the server. No new arbitrary screen navigation framework.

### 5. Explicit results and narrow cleanup

Return the existing `DialogueCommandResult` from each canonical builtin operation; never infer acceptance from screen closure or a list of non-closing strings. Preserve 5 emeralds/3 days and 10 emeralds/7 days hire terms, adoption/procreation refusal, inventory delivery, relationship checks and existing gameplay lines. Domain results may intentionally request closure on refusal (the existing `rejected(true)` cases); authorization denial and callback exceptions do not request closure.

For dialogue completion, keep the current maximum of one command. Validate and execute it before accompanying hearts/mood/registered callbacks/history. On refusal, none of those accompanying effects commit; a successful command executes once through the existing session lifecycle. Apply close requests only to the matching player and concrete interaction UUID. Preserve Trade/Inventory handoff and direct Villager Editor inventory access without a fake menu token. Keep native zombie curing.

After callers migrate, remove duplicate dispatch switches, the engine's hardcoded reliable-command list, acceptance heuristics and redundant wrappers. A public old handler signature may remain as a thin delegate if compatibility evidence warrants it, but it must not own a second implementation. Do not delete the retained command packet/schema merely because they are old. Remove `command`/`clothing` UI branches or button helpers only if the dynamic-layout/addon audit establishes they are unused; otherwise document them as intentional extension points.

## Acceptance and scope

- Windows existing worktree only; no WSL, new checkout, push, merge, reset, clean or unrelated edits. This revision changes the two planning Markdown files only.
- Behavioral checks: Work positive/negative cases; registered addon command via menu and dialogue; wrong-source/foreign/stale/distant/unknown rejection; real success/failure and single completion; duplicate registration; namespace separation; preserved builtin JSON/packet, constructors, typed extension APIs, Gift/Trade/Inventory/Locations/Professions/Editor and zombie cure.
- During implementation run focused common tests and NeoForge GameTests, both loader compiles, dedicated-server startup and focused client checks. Compilation is not gameplay evidence. Addon callback tests must prove execution/denial, not just decoding.
- Record production lines added/deleted and removed entry points separately from tests/docs. Aim for fewer routing layers; do not promise a net line reduction before measuring.

The [implementation plan](../plans/2026-11-11-interaction-legacy-retirement.md) follows this command-preserving design. The earlier typed-effect/new-packet tasks are superseded.
