# MCA DialogueEvent showcase (Minecraft 1.21.1)

An **opt-in development fixture**, not shipped with normal MCA. It makes multi-line, branching conversations available without rare prerequisites.

## Install in a disposable test world

1. Copy the entire **datapack** folder to the world's **datapacks/DialogueEvent Showcase/** folder; its pack.mcmeta must be directly inside that folder.
2. Copy the entire **resourcepack** folder to the client's **resourcepacks/DialogueEvent Showcase Text/** folder. Enable it in **Options → Resource Packs**. Server datapack and client resource pack are separate.
3. Enter the test world. Use **/datapack list enabled** to confirm the datapack; enable the file pack if needed. Use **/reload** after changing event JSON and **F3+T** after changing translations.
4. Right-click an awake MCA villager, then press **Talk**. All topics come from the normal DialogueEvent engine.

| What to test | In-game steps | Expected result |
| --- | --- | --- |
| Highlighted + once | First Talk with a fresh villager/player pair | **You look like you have something to say...** is highlighted. Finish its two-line branches with **Back to topics**; it then disappears for this pair. |
| Full conversation | Talk → Ask about... → **Ask about the old bridge** | Three opening lines, three natural replies, and two ending lines per reply. |
| Rewards | Complete the bridge with **No. Tell me what those days were like.** | **+5 hearts** on final acknowledgement only; neutral = 0; dismissive = -5. Closing early does not commit rewards. |
| Choice history | Finish bridge with the **listen** reply | Follow-up **Ask about the bridge again** appears because it requires completion and that particular choice. |
| Latest choice | After 5 seconds, repeat and complete bridge with **dismiss** | The listening-dependent follow-up disappears. An abandoned replay must not replace completed choice history. |
| Cooldown | Complete bridge then immediately try to select it again | It returns after 100 game ticks, normally five seconds; other events remain available. |
| Personal resume | Close midway, right-click same villager, press Talk | **We hadn't finished talking about the old bridge...** is offered. Only explicitly selecting it resumes. |
| Pause expiry | Leave the unfinished story alone for 2400 ticks | Its continuation expires; fresh topics remain selectable. |
| Topic replacement | Pause one story, then select another | New valid event replaces retained run; old deferred rewards must not execute. |
| Night condition | **/time set midnight**, reopen Talk; then **/time set noon** | **Ask about the stars tonight** appears only in the night menu. |
| Hearts condition | Reach 20 MCA relationship hearts with this villager | **Ask what matters to them** becomes available, not below that threshold. |
| Weighted ambient | Talk → **What's on your mind?**, retry after cooldown | Two eligible two-line topics share priority 150, with weights 1 and 3. Short-run outcomes need not match the exact ratio. |
| Pair isolation | Repeat with another villager or authenticated player | Completion, last choice and cooldown are specific to each player-villager pair. |
| Registry reload | Pause, then server **/reload** | Stale session tokens must fail; old pending actions must not commit. |
| Missing client text | Disable only this resource pack on a test client | Some keys may be untranslated; server authority must still control eligibility and outcomes. |

**Quick tour:** complete highlighted welcome, play bridge with **listen**, finish and open its follow-up, wait 5 seconds, replay bridge with **dismiss**, verify the follow-up disappears; try the same with a second villager.

This pack does not fabricate real zombie cure, mourning, raid, infirmary, prison, or resurrection facts. Those require natural gameplay for condition tests. Real authenticated multiplayer, actual forged network traffic, and both loader GUIs still require the [release checklist](../../docs/dialogue-release-smoke.md); this fixture is not proof of those checks.

Uninstall by disabling/removing the datapack and resource pack. Saved history may remain in a test world, so use another player/villager pair or fresh test world for first-contact testing. See [datapack guide](../../docs/dialogue-datapack-guide.md) for event authoring.
