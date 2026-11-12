# Dialogue reply placeholders and asynchronous parchment results

Status: **P4 approved design**. The generic, addon-registerable, datapack-configured async node resolver is the approved architecture. Minecraft **1.21.1**, **Java 21**, shared Fabric and NeoForge. This document reconciliation is documentation-only; implementation is coordinated separately with the P2 UI and P4 backend owners.

This design supersedes the rumour-specific `mca:rumor_location` action proposed in `docs/superpowers/specs/2026-11-09-inline-location-rumors-design.md` and `docs/superpowers/plans/2026-11-09-inline-location-rumors.md`. Those earlier documents are historical references, not parallel implementation instructions.

## Intent

The player should always understand why reply choices have not appeared yet. Keep the existing character-by-character villager speech (40 ms per Unicode grapheme), with a single disabled, greyed-out choice-shaped row containing three subtly hopping square dots until the full response is ready to interact with. For real asynchronous work such as location rumours, also show an **indeterminate loading bar within the parchment/chat speech box**; deliver the result and any coordinates there, rather than sending vanilla chat messages.

The player selected mockup **Character reveal** (variant 2) and **Rumours search** (variant 3). The previous **Instant reply** variant is not the chosen design. Preserve the parchment placement toggle, scrolling, category styles, footer controls and HUD clearance.

## Approaches

1. **Client-only placeholder**: cheap for typewriter delay; cannot know whether the server is still searching, cannot show accurate async completion, and cannot bring location results inline.
2. **Hardcoded location-rumour packets**: unblocks today's rumour but ties effects and indicator to one event ID; datapacks and addon operations could not reuse it.
3. **Server-owned, typed asynchronous node resolver** (**approved**): client reuses one generic pending state; datapacks declare a namespaced resolver and addon mods can register new resolver types. Requires a small protocol/session extension, but preserves server authority and is extensible.

These are implementation alternatives, **not** alternate visual designs. User approved visual variants 2 and 3 and the generic resolver in approach 3.

## User-visible behavior

1. Sending/selecting a prompt immediately replaces its selectable rows with **one grey disabled row** containing three bouncing pixel squares. There is no clickable stale choice, spinner per reply, fake percent or minimum animation time.
2. If the server sends a **regular resolved line**, the 40 ms/grapheme text reveal begins. The grey row remains until the line finishes; then it is replaced in one update by **all** actual choices, or by Continue/Finish, or disappears when no reply exists. The dot row does not block reading or Previous/Next history; history review must not enable live replies.
3. If the server instead reports a **genuine pending async node**, the parchment shows a short localized pending line and an **indeterminate progress bar** beneath it, while the choice panel keeps the same grey dot row. There is no fabricated percentage. The bar moves gently only while pending; reduced-motion mode uses a static filled strip.
4. Upon successful async completion, the bar disappears; the **actual result text appears inline in parchment**, including coordinates if applicable, and reveals at the existing 40 ms/grapheme rate. The grey row remains through this reveal. The choices/Finish appear together afterward, not one at a time.
5. On search miss, provider failure or timeout, the bar disappears and a localized fallback/failure line reveals normally. The menu must not get permanently stuck. Changing Topics, closing, changing villager, reloading data or disconnecting removes any old pending UI.

Illustrative states (not precise game pixels):

```text
PARCHMENT: "Searching for a nearby ruin..."          CHOICES: [ ▪  ▪  ▪ ] (disabled)
           [ ◀══════────────▶ ] (indeterminate)

PARCHMENT: "I heard of a ruin at 348, 70, -126."     CHOICES: [ ▪  ▪  ▪ ] (during text reveal)

PARCHMENT: "I heard of a ruin at 348, 70, -126."     CHOICES: [ Tell me more ]
                                                             [ Thanks! ]
```

No normal network wait is deliberately prolonged to display animation. If a request finishes in the same client tick, the placeholder may never be perceptible.

## Data-driven async contract (approved)

Add an **optional `resolve` object** to a visible event node, rather than inventing a special rumour event type. Visible node requirements remain the same (required `line`, one of `choices`/`next`/`complete`/`end`). For v1, a node with `resolve` must use **one `line`** rather than `lines`, and must not contain an automatic `outcomes` route. Existing datapacks without `resolve` behave exactly as before.

```json
{
  "location": {
    "line": "dialogue_event.example.ruin.found",
    "resolve": {
      "type": "mca:structure_location",
      "pool": "configured_rumors",
      "pending_line": "dialogue_event.example.ruin.searching",
      "failure_line": "dialogue.location.forgot"
    },
    "complete": true
  }
}
```

For this built-in example, `line` is required by the existing visible-node schema but success uses the resolver's dynamic `dialogue.location.<structurePath>` override. The explicit `failure_line` selects the existing localized forgotten-location variants. A generic addon provider may instead use its authored `line` on success.

For the built-in `mca:structure_location`, `pool:"configured_rumors"` reuses the existing `Config.structuresInRumors` entries, world structure registry and `AsyncStructureLocator`. Alternatively `structure:"<namespace>:<path>"` selects one registered structure; exactly one selector is required. The pool selector retains the existing random configured-structure choice, random origin offset (horizontal range `1024`, vertical `0`), lookup radius `64`, eligibility and registry checks. No client-supplied structure IDs. A missing/invalid structure, empty pool, unsuccessful lookup, exception or timeout resolves to the authored fallback rather than invented coordinates while the ticket remains valid. Cancellation on session invalidation discards the result without reopening the UI.

**Location-specific translation contract:** On success, the built-in resolver overrides the authored `line` key with the existing dynamic key `dialogue.location.<structurePath>` (path portion of the selected structure ID). The server produces the inline text through `villager.getTranslatable(player, key, coords)`, where `coords` is the exact found `x,y,z` string. `Messenger.getTranslatable` prepends the player's name at **`%1$s`**, making coordinates **`%2$s`** in existing location translations. On miss/failure/timeout the built-in uses `dialogue.location.forgot` via `villager.getTranslatable(player, key)`. Retain the messenger's personality/profession/gender/dialogue-type localization behavior and its existing numbered language variants. The node's required `line` remains part of the visible-node schema for ordinary providers, even when this built-in overrides it on success. `pending_line` defaults to `gui.dialogue.searching` when absent; its value and `failure_line` are translation keys supplied by the appropriate client assets/resource pack. The default pending key is bundled in `assets/mca/lang/en_us.json`; locales without an override display the English fallback. For the built-in, set `failure_line` to `dialogue.location.forgot`; do not invent a parallel generic success key.

An asynchronous type is a registered server-side **resolver**, not a privileged command or arbitrary code string. Shared API contract:

- `DialogueAsyncResolver` is the node's optional typed `resolve` value, decoded by its `CODEC` as `mca:structure_location` or a registered addon provider. `DialogueAsyncResolver.register(ResourceLocation id, Codec<T> codec, Provider<T> provider)` is called during common initialization before datapack reload.
- `DialogueAsyncResolver.resolve(DialogueContext context, UUID operationId)` returns `CompletionStage<Result>`; `DialogueAsyncResolver.cancel(UUID operationId)` cancels its owned operation on the server thread. Addon `Provider<T>.resolve(T parameters, DialogueContext context, UUID operationId)` and optional `Provider<T>.cancel(T parameters, UUID operationId)` use the same operation identity. The engine creates a fresh UUID per pending ticket, retains the resolver, and invokes `resolver.cancel(operationId)` after invalidating that ticket. Read-only addons without cancellable underlying work may use the provider's no-op cancel default; their late completions are still discarded. For `mca:structure_location`, the operation ID is exactly the `AsyncStructureLocator` owner passed to `locate` and `cancel`, ensuring timeout does not leave an active locator request or temporary chunk ticket.
- `DialogueAsyncResolver.Result(boolean found, Optional<String> lineOverride, List<String> arguments)` contains a bounded list of string arguments; `Result.withKey(key, args...)` selects an alternate translation key, `Result.found(args...)` keeps the authored success line, and `Result.missing()` selects the failure line. On success use `lineOverride` when present or the node's authored `line` key otherwise. On failure use the authored `failure_line` with no arguments. Validate any override key as nonblank. `mca:structure_location` supplies the dynamic `dialogue.location.<structurePath>` override. On the server thread the engine resolves the selected key with the villager's `getTranslatable(player, key, args...)` path, preserving MCA localization and the implicit player-name argument. Any worker result is marshalled back before touching entities, sessions, registries or network.
- A datapack may select **built-in or addon-registered** resolver IDs and provide only validated codec parameters. Datapacks alone **cannot add executable async provider Java code** or arbitrary command execution. Unknown IDs/parameters, blank or invalidly formatted key fields, and unsupported combinations are rejected on reload with clear errors. The server cannot verify that a corresponding client-side translation actually exists; missing resource-pack translations follow the game's normal missing-key behavior. Both loaders use the same common registry and codec.

This is a reusable **pending-result protocol**: another asynchronous feature declares its registered resolver in a node and automatically receives the same dots, parchment progress bar, timeout/error handling and inline result. It does **not** turn all existing `mca:command` side effects into async operations.

## Server state, completion and safety

- Resolve when entering the designated visible node, including after automatic routing and after choice/next transitions. Store at most **one** pending request per player session, owned by `DialogueEngine` and associated with player UUID, villager UUID, session UUID, resource generation, offer token, node ID and line index plus an operation identity. No futures or entity objects in persisted `DialogueSession` / history.
- Selection/advance yields either a **pending view** (session/token/pending line) or a **ready node view**; the server sends explicit pending status before allowing a result to be published. Start the resolver after installing the ticket, but do not deliver a synchronously completed result before the pending response is dispatched. Use a dedicated bounded S2C pending payload or an equivalent explicit state, then send the existing authoritative active node response on completion. No minimum visual delay is added. The client must reject out-of-order pending/ready messages, duplicate completions and responses from older sessions/tokens.
- Never offer/accept choices or completion while a resolver is pending. A duplicate inbound choice/advance must not launch another lookup or apply effects. The existing completion acknowledgement alone commits ordinary actions/rewards/history **once**, after the resolved line is displayed, not at request start.
- Set **30 seconds (600 server ticks)** as the v1 hard deadline; an uncompleted job becomes the localized failure result if the session is still valid. Discard the ticket and request cancellation on close/leave/pause, logout, death, player-villager change, reload, server stop, token replacement or timeout. A late or uncooperative callback must be ignored. A paused pending node may **restart an idempotent read-only resolver on explicit Resume** with a fresh token, never replay a reward/command. Only idempotent lookup providers are supported by this node-resolution API.
- Each `mca:structure_location` invocation uses the **new random operation-unique UUID** supplied by the engine as the `AsyncStructureLocator.locate(owner, ...)` owner. Keep that owner and the resolver instance in its pending ticket; `resolver.cancel(operationOwner)` calls `AsyncStructureLocator.cancel(operationOwner)` for exactly that operation. Never use the villager UUID for a dialogue lookup or cancel a legacy lookup owned by the villager UUID. The locator replaces requests for equal owners, completes results on the server thread, and owns temporary chunk tickets. Its cancellation/close paths release those tickets. Do not reuse owners across simultaneous players, retry, resume or distinct sessions.
- Track pending lifecycle on the existing server tick, bounded per-player memory and packet sizes. Marshal every completion through the Minecraft server executor, including already-completed/exceptional futures and addon callbacks, then re-check the *current* pending ticket identity, session/token/generation, live player/villager/world and interaction ownership **before** updating a view, applying effects or sending any packet. Invalidate the ticket before requesting underlying cancellation, so synchronous cancellation completions cannot race into an old session. Handle exceptions, queue rejection and cancellation as controlled failures while the ticket is valid. Never propagate them into a network tick. Avoid vanilla-chat output from this path.
- When the same event has an unrelated legacy `mca:command`, preserve its normal deferred commit semantics. Do not use command-return flags as proof that a future has completed.

## Built-in rumours migration

Today `social/rumors.json` attaches `mca:command location` to two automatic outcomes (weights `10` and `5`, the latter requiring `mca:hearts min 20`); the effect starts in `DialogueEngine.commit()` when the player finishes the rumour. `VillagerCommandHandler` starts `AsyncStructureLocator.locate(entity.getUUID(), ..., 64)` and later sends the result to **vanilla chat**. Replace only the *dialogue location path*: both location outcomes still route to the same `location` node with unchanged requirements/weights; remove their deferred command effects, and attach the generic `mca:structure_location` node `resolve` using `pool:"configured_rumors"`. Start exactly one search when entering that visible node, present the dynamic success/failure localization **inline**, and offer Finish only after the result is ready and revealed. Keep the unrelated `VillagerCommandHandler` `location` gameplay command and its chat behavior intact. Preserve lore and failed outcomes, all route weights, cooldown `5` seconds, history/repeat behavior and configuration structures.

## Non-goals and constraints

- No fake linear percentage, indefinite animation, automatic reward on discovery, new worktree, new dependencies, or unrelated interface restyling.
- No arbitrary scripting in datapacks. Addon-provided async behavior requires installed Java code and registered codecs on the server; matching localized assets on clients.
- Keep server-controlled outcomes, session/token integrity, Fabric/NeoForge parity, Unicode reveal, existing menu navigation, and dedicated-server safety.

## Documentation and acceptance

The implementation **must update all three existing guides**: `docs/dialogue-authoring.md` (new `resolve` schema, keys, validation and limitations); `docs/dialogue-datapack-guide.md` (copyable node example with datapack JSON and resource-pack translations, test/reload/troubleshooting); `docs/dialogue-system-guide.md` (provider API, request/response lifecycle, cancellation, threading, history/command semantics, addon integration and loader parity). Document distinction between client-only 3 dots during typing and actual server-side async bar; add language keys/examples in the appropriate resources and tests.

Acceptance scenarios: ordinary immediate and typewriter dialogue, happy/missing/timeout location lookup, no vanilla chat duplication, addon/pack resolver, malformed/unknown datapack decoder, reload, close/pause/resume, outdated token, cross-villager callback, server shutdown, zero choices, reduced motion, HUD/small GUI scales and Fabric/NeoForge dedicated server. Automated tests should pin phase changes, serialization, validity and stale results; actual UI appearance needs an in-game screenshot/interaction check.

## Approved decisions and implementation coordination

Approved: visual mockups 2 (character reveal) and 3 (rumours search), grey row before choices, parchment loading indicator for true async, and generic typed namespaced node `resolve` with addon Java registration and datapack parameters. The rumour-only action approach is superseded.

V1 contract: 30-second deadline, single-line async nodes, coordinates at `%2$s` through `getTranslatable`, idempotent read-only providers, localized failure, and fresh-ticket resolution on explicit Resume. The P4 backend owner integrates schema, engine, packets and structure provider; the P2 UI owner supplies the shared placeholder, reveal and parchment progress presentation. Coordinate their API seam before either owner changes a shared file. This reconciliation changes only this spec and its companion plan.
