# Deferred: Work command authorization

**Status:** Deferred by request on 2026-11-11; not fixed in the dialogue ownership follow-up.

**Finding (Important, source-level):** The Work submenu is restricted by
`common/src/main/resources/assets/mca/api/gui/main.json`, but the server-side
`InteractionMenuCommandAccess` permits chore commands and
`VillagerCommandHandler.handle` can assign a chore without independently
checking family/mercenary eligibility. A player with a valid current
interaction might bypass the submenu restriction by sending the command packet
directly.

**Future scope:** Confirm the exact built-in Work constraint, then enforce it
server-side at the command boundary, retaining valid Work commands. Add a
negative authorization regression test and verify both Fabric and NeoForge.
Do not treat a hidden client button as authorization.

**Related work:** `docs/superpowers/plans/2026-11-10-interaction-command-authorization.md`.
