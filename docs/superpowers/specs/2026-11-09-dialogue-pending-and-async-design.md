# Dialogue reply placeholders and asynchronous parchment results

Status: **proposed design for review; implementation not authorized**. Minecraft **1.21.1**, **Java 21**, shared Fabric and NeoForge.

## Intent

The player should always understand why reply choices have not appeared yet. Keep the existing character-by-character villager speech (40 ms per Unicode grapheme), with a single disabled, greyed-out choice-shaped row containing three subtly hopping square dots until the full response is ready to interact with. For real asynchronous work such as location rumours, also show an **indeterminate loading bar within the parchment/chat speech box**; deliver the result and any coordinates there, rather than sending vanilla chat messages.

The player selected mockup **Character reveal** (variant 2) and **Rumours search** (variant 3). The previous **Instant reply** variant is not the chosen design. Preserve the parchment placement toggle, scrolling, category styles, footer controls and HUD clearance.

## Approaches

1. **Client-only placeholder**: cheap for typewriter delay; cannot know whether the server is still searching, cannot show accurate async completion, and cannot bring location results inline.
2. **Hardcoded location-rumour packets**: unblocks today's rumour but ties effects and indicator to one event ID; datapacks and addon operations could not reuse it.
3. **Server-owned, typed asynchronous node resolver** (**recommended proposal**): client reuses one generic pending state; datapacks declare a namespaced resolver and addon mods can register new resolver types. Requires a small protocol/session extension, but preserves server authority and is extensible.

These are implementation alternatives, **not** alternate visual designs. User approved visual variants 2 and 3; approach 3 is a proposed architectural decision awaiting review with this document.

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

## Data-driven async contract (proposed)

Add an **optional `resolve` object** to a visible event node, rather than inventing a special rumour event type. Visible node requirements remain the same (required `line`, one of `choices`/`next`/`complete`/`end`). For v1, a node with `resolve` must use **one `line`** rather than `lines`, and must not contain an automatic `outcomes` route. Existing datapacks without `resolve` behave exactly as before.

```json
{
  "location": {
    "line": "dialogue_event.example.ruin.found",
    "resolve": {
      "type": "mca:structure_location",
      "pool": "configured_rumors",
      "pending_line": "dialogue_event.example.ruin.searching",
      "failure_line": "dialogue_event.example.ruin.not_found"
    },
    "complete": true
  }
}
```

For the built-in `mca:structure_location`, `pool:"configured_rumors"` reuses the existing `structuresInRumors` configuration, world registries and `AsyncStructureLocator`. Optionally allow `structure:"<namespace>:<path>"` instead of `pool` to let a datapack select a specific registered structure; exactly one selector is required. Preserve bounds, eligibility and registry checks; no client-supplied structure IDs. Success resolves the node's normal `line` translation with **`%1$s` = "x, y, z"** (three coordinates, no fabricated position). Failure/timeout uses `failure_line` and no arguments. `pending_line` defaults to `gui.dialogue.searching` when absent; its value and `failure_line` are translation keys supplied by the associated client resource pack. An empty configured structure pool is an immediate handled failure.

An asynchronous type is a registered server-side **resolver**, not a privileged command or arbitrary code string. Proposed shared API:

- `DialogueAsyncResolver.register(ResourceLocation id, Codec<T> codec, Resolver<T> resolver)` during common initialization, before datapack reload.
- `Resolver<T>.resolve(T parameters, DialogueContext context)` returns `CompletionStage<DialogueAsyncResolver.Result>`; `Result` contains a success/failure flag and a bounded list of `Component` arguments for the node's `line`. Failure uses the authored `failure_line`. Resolve and deliver on the server thread; any worker result is marshalled back before touching entities, sessions, registries or network.
- A datapack may select **built-in or addon-registered** resolver IDs and provide only validated codec parameters. Datapacks alone **cannot add executable async provider Java code** or arbitrary command execution. Unknown IDs/parameters, blank or invalidly formatted key fields, and unsupported combinations are rejected on reload with clear errors. The server cannot verify that a corresponding client-side translation actually exists; missing resource-pack translations follow the game's normal missing-key behavior. Both loaders use the same common registry and codec.

This is a reusable **pending-result protocol**: another asynchronous feature declares its registered resolver in a node and automatically receives the same dots, parchment progress bar, timeout/error handling and inline result. It does **not** turn all existing `mca:command` side effects into async operations.

## Server state, completion and safety

- Resolve when entering the designated visible node, including after automatic routing. Store at most **one** pending request per player session, owned by `DialogueEngine` and associated with player UUID, villager UUID, session UUID, resource generation, offer token, node ID and line index. No futures or entity objects in persisted `DialogueSession` / history.
- Selection/advance yields either a **pending view** (session/token/pending line) or a **ready node view**; the server explicitly reports pending status before beginning any expensive operation. Use a dedicated bounded S2C pending payload or an equivalent explicit state, then send the existing authoritative active node response on completion. The client must reject out-of-order pending/ready messages, duplicate completions and responses from older sessions/tokens.
- Never offer/accept choices or completion while a resolver is pending. A duplicate inbound choice/advance must not launch another lookup or apply effects. The existing completion acknowledgement alone commits ordinary actions/rewards/history **once**, after the resolved line is displayed, not at request start.
- Set **30 seconds** as the proposed default hard deadline; an uncompleted job becomes the localized failure result. Owner may cancel underlying locator work when safe; even if cancellation fails, a stale callback is discarded after close/leave/pause, logout, death, player-villager change, reload, server stop, token replacement or timeout. A paused pending node may **restart an idempotent read-only resolver on explicit Resume** with a fresh token, never replay a reward/command. Only idempotent lookup providers are supported by this node-resolution API.
- Track pending operation lifecycle on the existing server tick, bounded per-player memory and packet sizes. Use an **operation-unique locator owner** so cancelling one dialogue's search never cancels a legacy search under the villager UUID. Async exceptions and cancellation must be handled, never propagate into the network tick. Avoid vanilla-chat output from this path.
- When the same event has an unrelated legacy `mca:command`, preserve its normal deferred commit semantics. Do not use command-return flags as proof that a future has completed.

## Built-in rumours migration

Today `social/rumors.json` attaches `mca:command location` on an automatic route; the effect starts in `DialogueEngine.commit()` when the player finishes the rumour. `VillagerCommandHandler` starts `AsyncStructureLocator.locate` and later sends the result to **vanilla chat**. Replace only the *dialogue location path*: the route selects the `location` node, the node's `resolve` performs one search immediately, and the server publishes success/failure as a **resolved inline line** before Finish becomes available. Remove the location branch's deferred command effect to prevent duplicate searches/chat output. Keep any **non-dialogue legacy uses** of `location` unchanged. Keep lore and failed rumour branches unchanged. Preserve the default structure pool, location translations/localization coverage, and existing repeat/history policy, except that the successful or failed search now settles before a player can acknowledge completion.

## Non-goals and constraints

- No fake linear percentage, indefinite animation, automatic reward on discovery, new worktree, new dependencies, or unrelated interface restyling.
- No arbitrary scripting in datapacks. Addon-provided async behavior requires installed Java code and registered codecs on the server; matching localized assets on clients.
- Keep server-controlled outcomes, session/token integrity, Fabric/NeoForge parity, Unicode reveal, existing menu navigation, and dedicated-server safety.

## Documentation and acceptance

The implementation **must update all three existing guides**: `docs/dialogue-authoring.md` (new `resolve` schema, keys, validation and limitations); `docs/dialogue-datapack-guide.md` (copyable node example with datapack JSON and resource-pack translations, test/reload/troubleshooting); `docs/dialogue-system-guide.md` (provider API, request/response lifecycle, cancellation, threading, history/command semantics, addon integration and loader parity). Document distinction between client-only 3 dots during typing and actual server-side async bar; add language keys/examples in the appropriate resources and tests.

Acceptance scenarios: ordinary immediate and typewriter dialogue, happy/missing/timeout location lookup, no vanilla chat duplication, addon/pack resolver, malformed/unknown datapack decoder, reload, close/pause/resume, outdated token, cross-villager callback, server shutdown, zero choices, reduced motion, HUD/small GUI scales and Fabric/NeoForge dedicated server. Automated tests should pin phase changes, serialization, validity and stale results; actual UI appearance needs an in-game screenshot/interaction check.

## Decisions for review

User-selected: visual mockups 2 (character reveal) and 3 (rumours search); grey row before choices, parchment loading indicator for true async; datapack compatibility and updated documentation.

Proposed builder decisions (not yet approved): generic namespaced typed `resolve` node instead of special-case rumours; 30-second timeout; single-line v1 async nodes; `%1$s` coordinate argument; only idempotent providers; fallback line after failure, and recommence on explicit Resume. All may be corrected during review before code.
