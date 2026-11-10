# Inline location Rumours in Talk

**Status:** Proposed design for review. This document does not authorize implementation.

## Intent

When a player asks a villager for Rumours, a location rumour currently delivers the useful structure information and coordinates to Minecraft chat. Lore and failed rumours already appear inside Talk. Location rumours should also finish inside Talk, without stalling the server while the structure search runs.

## Proposed experience

1. The player chooses **Rumours**. The existing weighted route, hearts/trait conditions, five-second cooldown and history policy are unchanged.
2. **Lore/failed route:** Show the existing localized parchment line immediately, as today.
3. **Location route:** Start the existing asynchronous structure lookup. While the result is genuinely pending, the choice area shows **one disabled, greyed-out row with three animated squares**. Do not display a fake answer, offer clickable choices, or force a minimum animation time.
4. **Success:** Replace the pending row with the normal dialogue response and show the villager's existing localized `dialogue.location.<structurePath>` rumour, including coordinates formatted `x,y,z`, **in the parchment**, not chat.
5. **Not found / invalid configuration / lookup failure:** Show the existing `dialogue.location.forgot` line inline. The player can read it and return to topics normally.
6. The location node stays server-owned and uncompleted while the lookup is in flight. Its final acknowledgment commits history/cooldown **once**, as ordinary completed dialogue does. An interrupted search must not commit an event that was never presented.

The **Explore animated typing indicator** Codex task owns the loading row, its animation, the decision about previously visible parchment text, and general response/reveal timing. This feature only supplies a real pending state and a final server response, and must integrate with that task's final UI contract. Ordinary dialogue must not acquire a new artificial delay.

## Approaches considered

- **Keep the chat result and show an inline temporary line.** Low effort, but the useful rumour still lives outside Talk; rejected.
- **Recommended: defer the authoritative response until the lookup resolves.** The server holds a token-bound pending Rumours session and sends the usual active dialogue node when it has the actual line. The shared client loading state covers the wait. This avoids duplicate UI behavior and reuses current network semantics.
- **Send a placeholder active node and replace it later.** Adds replace-in-place/reveal rules, extra clicks and client/server coordination while another task is already changing the waiting UX; rejected.

## Technical design

### Specific Rumours action

Use a dedicated, parameterless `mca:rumor_location` dialogue action for both location outcomes in `common/src/main/resources/data/mca/dialogue_events/social/rumors.json`. Keep the outcome weights, route destinations, node ids and other branches unchanged. Unlike a regular terminal `mca:command` effect, this action marks the selected location route as a server-side asynchronous response that must resolve **before** the terminal node can be shown and acknowledged.

Keep the legacy `location` command's externally visible behavior unchanged for unrelated callers. Extract its configured-structure selection, random search origin, radius `64`, registry validation, lookup and translation formatting into one reusable server-side helper. The legacy command consumes its answer through chat; the dedicated Rumours action consumes the same answer as a dialogue `Component`, with **no Rumours chat side effect**.

### Authoritative pending / resolved flow

- The existing `DialogueEngine` owns the route and selected session. Selecting a location Rumour creates exactly one pending operation for the current `(player UUID, villager UUID, session UUID, offer token, resource generation)`; the client remains waiting for the answer instead of receiving the generic `dialogue_event.mca.social.rumors.location` text.
- Keep lookup work asynchronous. `AsyncStructureLocator` completes its futures on the Minecraft server thread; callbacks still explicitly enforce server-thread ownership before reading/mutating dialogue or world state.
- A completed result is applied **only** while the same token, session, player, villager, world, resource generation and interaction owner are still valid. Then send one existing `InteractionDialogueNodeResponse.active` containing the resolved line; normal dialogue display and final acknowledgment resume.
- A pending node cannot be advanced, committed, or completed by early/repeated clicks. Once resolved, the normal terminal advance commits history and any other permitted effects once, then returns to the topics menu.
- Keep pending/resolved line state transient and bounded; clear it on completion, server clear, timeout, invalidation, and session replacement. Do not create a second persistent conversation system.

### Errors, cancellation and lifecycle

- Empty configured structure pool, unknown structure, empty search result, failed worker/queue rejection: if the session is still valid, resolve inline to `dialogue.location.forgot` rather than leaving a spinner forever.
- Use a **proposed 600-server-tick (30-second) deadline** for a still-valid pending lookup; on deadline, show `dialogue.location.forgot` inline and ignore later results. This is a design default, not a user-selected value.
- Pausing or leaving, villager disappearance/death, player logout or world change, data reload, server shutdown, or selecting a new conversation invalidates the old operation. Late callbacks must send nothing and cannot close or modify a newer conversation.
- A paused/unresolved conversation that is later resumed must start a fresh search tied to its new offer token, not reuse an already-invalid callback. Avoid canceling unrelated locator users; check the locator's existing UUID ownership/cancellation rules when implementing.

## Boundaries

**In scope:** Rumours location search-to-parchment flow, async server state/packet handoff, localized success/failure, focused regression coverage, integration with the shared pending UI.

**Out of scope:** Redesigning the typing indicator, new animation assets, changing ordinary chat commands, changing Rumours probabilities/requirements, converting other conversations to async, unrelated interaction/slap or dialogue-row styling changes.

## Acceptance criteria

- Rumours success shows translated structure text **and exact found coordinates** in Talk with no chat duplicate; failure shows `dialogue.location.forgot` in Talk.
- The waiting row appears only for a genuinely delayed lookup and cannot be clicked; fast lookup is not artificially slowed. Lore/failed Rumours and unrelated conversations remain unchanged.
- A second click, stale token, pause, disconnect, unload/death, resource reload, timeout, or late result cannot duplicate a response or history commit, or leak a rumour into a newer session.
- Existing legacy `location` command behavior remains covered by its existing GameTest. Focused server GameTests, Fabric/NeoForge compilation and an in-game UI check verify the final change.

## Dependency / approval

The separately running **Explore animated typing indicator** thread is responsible for the single disabled three-square row. Review its final pending-selection semantics before coding and coordinate any shared client-file edits. The proposed timeout and dedicated action should be confirmed in the design review. No production code should be changed until this spec is approved.
