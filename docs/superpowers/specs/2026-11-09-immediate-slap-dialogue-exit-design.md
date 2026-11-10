# Immediate dialogue exit on slap

## Intent

A villager slapping the player is a physical interruption. After selecting the provoking response, the player should return to the world, see the villager slap them, and then receive the villager's final reaction in chat. The player should not have to acknowledge a final line in the Talk interface.

## Approaches

- Finish the last line in the UI, then slap, close, and print a chat message. Rejected because the delay makes the hit feel disconnected from the selected response.
- **Chosen (revised):** the accepted provoking response closes the interaction UI first, then the villager slaps the player, then the final authored reaction appears in chat.
- Close the UI after the existing delayed slap without a message. Rejected because the player would lose the final authored reaction.

## Behavior and constraints

- Close the entire interaction first, returning the player to normal gameplay. Allow a short delay for the UI to disappear before applying the slap on the server exactly once with the existing villager-attributed damage and empty-hand swing behavior.
- Deliver the final authored reaction in chat **after** the slap, using its existing localization, rather than requiring a final Continue or Finish click.
- Treat a dialogue-triggered slap as an interruption of that dialogue path. Preserve normal completion/history and any other authorized effects from that path exactly once, even though the final UI acknowledgement is skipped.
- Reuse the established interaction identity, server-to-client close mechanism, and dialogue state lifecycle. Avoid a client-only health check or a second independent interaction state machine. A close must target the correct villager and interaction, without closing a newer conversation. Any delayed effect must not fire if the player or villager becomes unavailable and must not be scheduled twice by retransmitted responses.
- Keep ordinary non-slap dialogue progression and voluntarily closing/resuming a conversation as they currently work. Use the typed `mca:slap` action for slap outcomes; remove the unused legacy `mca:command/slap` and standalone text command on this development branch while retaining other gameplay commands.

## Validation

Add one focused behavior regression covering the chosen response, screen-close request before the single slap, successful completion, and ordered chat reaction. Check final reaction localization/chat delivery and ensure stale repeated response packets do not slap twice. Verify the visible swing and closed UI in-game if a client is available.

## Builder's remaining choices

Choose the narrowest existing server-owned transition and client message plumbing that can close the interaction, defer the slap briefly, and deliver the final line. Retain existing tokens and ownership checks. Exact render-frame ordering across a slow network may require an acknowledgement packet. Prefer a small delay unless testing shows it is inadequate.
