# Secure Interact-menu commands without a second dialogue authority

**Decision:** Implement the explicitly approved P0 hardening. A player prefers removing legacy paths; remove *unnecessary exposed actions*, but do not break non-Talk menu gameplay still using command dispatch. `DialogueEngine` remains the only Talk/event/history authority, while the existing `EntityCommandHandler` interaction ID owns the entire right-click Interact screen.

## Existing problem

The old `InteractionVillagerMessage(String command, UUID villagerUUID)` blindly invokes the matching entity's legacy command handler; it does not identify the active screen owner, check range, or check whether the command was offered. Its Boolean result may call unqualified `stopInteracting()`. This is source evidence of missing enforcement, not proof of exploitability. The same packet is still used by gifting and non-Talk buttons: work, movement, trades, locations, inventory, professions, etc. DialogueEvent actions follow a separate server-owned `DialogueEngine.commit()` path and must not be authorized by this GUI packet.

## Alternatives

1. **Delete the legacy packet and all handlers now:** breaks functional non-Talk pages and some zombie-villager interactions; rejected.
2. **Require current player only:** blocks foreign players but allows a stale packet from an earlier screen and forged privileged command strings; rejected.
3. **Chosen: interaction-ID-scoped, server-allowlisted commands:** retain the one existing non-Talk command execution seam, but bind every GUI command to the owner, active screen and server-side permission predicates; reject dialogue-only effects from it. Remove the unsafe unqualified-close path and migrate the client send sites together.

## Server contract

- A GUI command includes `(command, villager UUID, interaction UUID)` in its network payload. A same-player stale ID, another player's ID, unknown villager, dead actor or excessive range is rejected with no effect or close. Only a genuinely active owner may execute it. An accepted command requiring closure uses `stopInteracting(player, interactionId)`; a delayed old packet cannot close a newer screen.
- The server enforces the existing built-in GUI command set and its relevant constraints **independently** of client resources: trade requires adult/trader; inventory family or mercenary; pick-up requires non-adult/non-teen; execute/pardon require mayor and the appropriate outlaw state; profession changes require adult/monarch; move/follow/stay honor current state. Work/ride/armor/location/home and gift remain usable when their own conditions allow. Unsupported or malformed command/argument forms fail closed, including direct `slap`, `location`, `procreate`, `divorcePapers`, `adopt`, and other DialogueEvent-only commands.
- The approved menu authorization is not permission to use an arbitrary dialogue action. `DialogueEngine` remains server-authoritative for authored node progression, effects, cooldowns and final acknowledgement. Resource-pack UI additions cannot introduce new privileged server commands without an explicit server-side rule.
- Zombie-villager gift preserves its existing behavior if the correct player is interacting and close by. Ordinary gift inputs preserve the existing inventory/relationship logic.
- Prevent unnecessary read requests from bypassing owner/range checks; retain same-player Interact-screen data response when legitimate. Do not add parallel owner maps or custom game tick state.

## Tests and migration

Use NeoForge real-server GameTests and packet-handler calls for forged player, reused/stale interaction UUID, distance, rejected `slap`, mayor/profession restriction and legitimate `armor`/work/gift. First capture a behavioral RED with the old exposed packet, then introduce the token and allowlist and prove GREEN. Verify both loaders compile, `:common:test` remains green and a client can still use non-Talk commands. Follow the existing `InteractionCloseRequest` ID/callback conventions. This feature does not redesign Talk UI or the generic async resolver.
