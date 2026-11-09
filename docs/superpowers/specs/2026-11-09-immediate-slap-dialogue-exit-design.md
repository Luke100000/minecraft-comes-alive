# Immediate dialogue exit on slap

## Intent

A villager slapping the player is a physical interruption. The player should see the hit in the world immediately after selecting the provoking dialogue response, without having to acknowledge a final line or remain in the Talk interface.

## Approaches

- Finish the last line in the UI, then slap, close, and print a chat message. Rejected because the delay makes the hit feel disconnected from the selected response.
- **Chosen:** the accepted provoking response immediately triggers the slap, closes the interaction UI, and delivers the villager's final authored reaction as a localized chat message.
- Close the UI after the existing delayed slap without a message. Rejected because the player would lose the final authored reaction.

## Behavior and constraints

- Apply the slap on the server exactly once after the response is accepted, with the existing villager-attributed damage and empty-hand swing behavior.
- Close the entire interaction, returning the player to normal gameplay. Deliver the final authored reaction in chat, using its existing localization, rather than requiring a final Continue or Finish click.
- Treat a dialogue-triggered slap as an interruption of that dialogue path. Preserve normal completion/history and any other authorized effects from that path exactly once, even though the final UI acknowledgement is skipped.
- Reuse the established interaction identity, server-to-client close mechanism, and dialogue state lifecycle. Avoid a client-only health check or a second independent interaction state machine. A close must target the correct villager and interaction, without closing a newer conversation.
- Keep ordinary non-slap dialogue progression and voluntarily closing/resuming a conversation as they currently work. Keep the legacy slap command's damage amount and swing behavior.

## Validation

Add one focused behavior regression covering the chosen response, immediate single slap, successful completion, and interaction closure. Check final reaction localization/chat delivery and ensure stale repeated response packets do not slap twice. Verify the visible swing and closed UI in-game if a client is available.

## Builder's remaining choices

Choose the narrowest existing server-owned transition and client message plumbing that can deliver the final line and close the interaction. Retain existing tokens and ownership checks.
