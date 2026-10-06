# Dialogue Event Conversation Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace MCA's legacy `Question -> Answer -> Result -> Actions` conversation engine on `dev/1.21.1` with the namespaced, server-authoritative `DialogueEvent` engine from the approved design, migrate all shipped dialogue content, and remove the legacy engine after migration.

**Architecture:** `DialogueEvent` owns selection metadata and its inline node graph. `DialogueEvents` reloads immutable namespaced event definitions; `DialogueEngine` evaluates deterministic requirements, owns one server session per player, advances nodes, selects weighted event/outcome variants, and records durable pair history in `DialogueEventHistory`. Legacy `Dialogues` remains only behind a temporary adapter while shipped resources migrate, then is deleted.

**Tech Stack:** Java 21, Minecraft 1.21.1, common multiloader code, Gson-backed server resource reloads, Minecraft `SavedData`/NBT, custom payloads with `StreamCodec`, Fabric API 0.116.17+1.21.1, NeoForge 21.1.256, JUnit 5, NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-10-06-dialogue-event-conversation-system-design.md`

## Global Constraints

- Keep authoritative gameplay, dialogue, persistence, and networking logic in `common/`; loader code only registers reload listeners/lifecycle hooks.
- New dialogue resources retain full `ResourceLocation` namespace and nested path. Do not inherit legacy basename lookup.
- Event/choice eligibility is boolean. Randomness is permitted only for event weight and eligible choice-outcome weight.
- Client packets are untrusted. The server owns the current villager, event, node, offered event IDs, offered choice IDs, and generation token.
- `DialogueType`/`Messenger` phrasing fallback stays. Preserve existing translation keys during migration where practical instead of rewriting language assets for cosmetic reasons.
- Do not broaden this into a gift-system rewrite, quest engine, cutscene system, or general event bus.
- Preserve unrelated working-tree changes. Commit only files belonging to the current task.
- Before adding a loader event, codec helper, or Minecraft API workaround, verify the exact 1.21.1 source/API in this checkout's dependencies.

## Review Focus

1. **Trust boundary:** no C2S message can select an event, node, choice, or legacy answer that the server did not offer in the active generation.
2. **Save compatibility:** stable choice IDs, pair-scoped history, cooldown pruning, conversion persistence, and missing-addon prerequisites must keep the exact semantics in the spec.
3. **Selection clarity:** conditions are hard gates; priority orders eligible events; weight only varies equal-priority ambient events or eligible outcomes.
4. **Migration completeness:** the legacy adapter is temporary and removable; all 19 shipped legacy resources must be accounted for before deleting old classes/packets.
5. **Multiloader parity:** event JSON semantics and common behavior remain identical on Fabric/NeoForge; only registration/lifecycle seams differ.

---

## Task 1: Add the immutable event graph model and strict decoder

**Files:**

- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueEvent.java`
- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueCondition.java`
- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueAction.java`
- Create: `common/src/test/java/net/conczin/mca/dialogue/DialogueEventParsingTest.java`

**Target API:**

```java
public record DialogueEvent(
        ResourceLocation id,
        Trigger trigger,
        Presentation presentation,
        int priority,
        double weight,
        List<DialogueCondition> requirements,
        Repeat repeat,
        String start,
        Map<String, Node> nodes) {

    public static DialogueEvent decode(ResourceLocation id, JsonObject json);
    public void validate();
}
```

Keep `Presentation`, `Repeat`, `Node`, `Choice`, `Outcome`, and the small enums nested in `DialogueEvent` unless implementation pressure proves they need independent ownership. `Choice.id` is event-wide unique and durable; node IDs are not persisted as history identity.

`DialogueCondition` and `DialogueAction` own small type registries keyed by namespaced IDs. Unknown types throw a decode error; they are never silently ignored. Do not reuse `GiftPredicate.Condition`'s float semantics.

- [ ] Add failing parser tests for full event decoding, duplicate choice IDs, missing start node, missing `next`, malformed repeat ranges, zero/negative/non-finite event weights, zero/negative/non-finite outcome weights, and unknown condition/action types.
- [ ] Add tests proving a choice may use either direct `actions`/`next` or nonempty `outcomes`, never both.
- [ ] Add graph validation tests for terminal nodes: exactly one of `complete`/`end`; `once`/`cooldown` non-completing terminal requires `retryable: true`.
- [ ] Implement the minimal records/enums plus strict decoder/validator until those tests pass.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueEventParsingTest' --console=plain
```

- [ ] Commit only Task 1 files, e.g. `Add dialogue event graph model`.

## Task 2: Load namespaced event resources on both loaders

**Files:**

- Create: `common/src/main/java/net/conczin/mca/resources/DialogueEvents.java`
- Create: `common/src/test/java/net/conczin/mca/resources/DialogueEventsTest.java`
- Modify: `fabric/src/main/java/net/conczin/mca/fabric/MCAFabric.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/neoforge/CommonNeoForge.java`

**Target API:**

```java
public final class DialogueEvents extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation ID = MCA.locate("dialogue_events");

    public Optional<DialogueEvent> get(ResourceLocation id);
    public Collection<DialogueEvent> all();
    public long generation();
}
```

`apply(...)` decodes `data/<namespace>/dialogue_events/<path>.json` without stripping namespace/path and publishes one immutable map only after the reload pass is complete. One malformed resource logs its exact ID and is skipped; it must not weaken into an unconditional event.

- [ ] Add failing tests proving `mca:personal/gloomy_reflection` and `example_addon:personal/gloomy_reflection` coexist and nested paths are retained.
- [ ] Add tests for whole-resource pack replacement semantics and malformed-resource isolation.
- [ ] Implement `DialogueEvents` using the existing 1.21.1 `SimpleJsonResourceReloadListener` seam.
- [ ] Fabric: register it through the existing generic `registerReloadListener(...)`/`FabricReloadListener` wrapper; do not add another one-off Fabric listener class.
- [ ] NeoForge: add `new DialogueEvents()` to `onAddReloadListener`.
- [ ] Keep legacy `Dialogues` registered for now; its removal is Task 12.
- [ ] Run the focused test plus both loader compilation/builds:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.resources.DialogueEventsTest' --console=plain
.\gradlew.bat :fabric:build :neoforge:build --console=plain
```

- [ ] Commit, e.g. `Load namespaced dialogue events`.

## Task 3: Add bounded pair-scoped dialogue history

**Files:**

- Create: `common/src/main/java/net/conczin/mca/server/world/data/DialogueEventHistory.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/DialogueEventHistoryTest.java`

Use the existing `WorldUtils.loadData(...)` pattern and store this once in overworld `SavedData`, not on player or villager entity NBT.

**Target API:**

```java
public final class DialogueEventHistory extends SavedData {
    public static DialogueEventHistory get(ServerLevel level);

    public boolean completed(UUID player, UUID villager, ResourceLocation event);
    public boolean chose(UUID player, UUID villager, ResourceLocation event, String choiceId);
    public long nextEligibleAt(UUID player, UUID villager, ResourceLocation event);

    public void complete(
            UUID player, UUID villager, DialogueEvent event,
            Set<String> choices, long gameTime, RandomSource random);
    public void pruneExpiredScheduling(long gameTime);
}
```

An `EventRecord` stores completion count, last completion game time, next eligible game time, and a set of stable choice IDs. It is one record per `(player UUID, villager UUID, event ID)`, never an append-only transcript.

- [ ] Add RED tests for NBT round-trip of completion, choice IDs, and cooldown timestamps.
- [ ] Test that two players talking to the same villager have independent records.
- [ ] Test fixed and random cooldown calculation; random bounds are rolled only at successful completion.
- [ ] Test pruning: expired scheduling-only records disappear; records with durable completion/choice history remain.
- [ ] Implement serialization/load/save and mutation dirtying.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.DialogueEventHistoryTest' --console=plain
```

- [ ] Commit, e.g. `Persist dialogue event history`.

## Task 4: Implement deterministic current-state conditions and building context

**Files:**

- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueContext.java`
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueCondition.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageRoomTypeQueryTest.java`
- Create: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueConditionGameTests.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/gametest/McaGameTestsRegistration.java`

**Target context:**

```java
public record DialogueContext(
        VillagerEntityMCA villager,
        ServerPlayer player,
        DialogueEventHistory history) {
    public ServerLevel level();
}
```

Implement the built-in conditions required by the spec: personality, mood, hearts min/max, relationship/family constraints, age group, profession, rank, trait, current health range, time/day/night, weather, biome, advancement, `village_has_building`, `in_building`, `event_completed`, `event_choice`, and generic recent-gameplay-event support added in Task 8.

Do not import the existing float/chance behavior from `GiftPredicate`; read the same authoritative owners directly or extract only a genuinely shared lookup helper.

`Village` should own physical building-type resolution. Add a narrow query such as:

```java
public boolean isInBuildingOfType(Vec3i position, String type)
```

implemented using current physical-room lookup plus `RoomTypeResolver` effective type, so dialogue code does not duplicate logical-building/floor semantics. This must distinguish "village has an infirmary" from "villager is inside an infirmary". Existing `prison` and `infirmary` building definitions are sufficient; do not invent a new building tag system.

- [ ] RED test `Village.isInBuildingOfType` with inherited/logical room types in `VillageRoomTypeQueryTest`.
- [ ] Add GameTests for world-backed condition truth tables: health, rain/thunder, biome, time, village-has-building versus in-building, and player/villager relationship state.
- [ ] Add focused tests for `event_completed`/`event_choice`: unresolved external namespace evaluates false; broken shipped `mca:` references are caught by resource validation rather than silently evaluating true.
- [ ] Implement conditions and building query.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageRoomTypeQueryTest' --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueConditionGameTests --console=plain
```

- [ ] Commit, e.g. `Add dialogue event conditions`.

## Task 5: Implement selection, weighted outcomes, and server-owned sessions

**Files:**

- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueSession.java`
- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`
- Create: `common/src/test/java/net/conczin/mca/dialogue/DialogueSelectionTest.java`
- Create: `common/src/test/java/net/conczin/mca/dialogue/DialogueSessionTest.java`

`DialogueSession` is transient and contains the player/villager binding, generation, offered event IDs, ambient candidate IDs, current event, current node, offered choice IDs, and temporary selected stable choice IDs. It is never SavedData.

**Target engine surface:**

```java
public final class DialogueEngine {
    public DialogueOptions begin(ServerPlayer player, VillagerEntityMCA villager);
    public Optional<DialogueNodeView> select(
            ServerPlayer player, long generation,
            DialogueSelection selection, @Nullable ResourceLocation eventId);
    public Optional<DialogueNodeView> choose(
            ServerPlayer player, long generation, String choiceId);
    public void end(ServerPlayer player);
}
```

Rules:

- Gather TALK events in canonical ID order.
- Hard-filter requirements and repeat/history availability.
- HIGHLIGHTED: highest priority + canonical ID gets the direct slot; overflow remains under Ask.
- ASK: all eligible ASK + overflow highlighted, sorted priority then ID.
- AMBIENT: keep only highest eligible priority tier; weighted draw occurs when the generic ambient option is selected.
- Choice outcomes: hard-filter outcome requirements first, then weighted draw among eligible outcomes.
- Re-evaluate volatile requirements at event/choice selection before executing actions.
- One active session per player; a new `begin` invalidates the previous generation.

- [ ] RED tests for highlighted/ask overflow, ambient highest-tier behavior, deterministic canonical ordering, weighted selection with injected deterministic `RandomSource`, zero eligible outcomes, and cooldown exclusion.
- [ ] RED tests for stale generation, unoffered event ID, unoffered choice ID, duplicate choice packet, wrong villager/session, and session close.
- [ ] Implement engine/session until all pass without networking.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueSelectionTest' --tests 'net.conczin.mca.dialogue.DialogueSessionTest' --console=plain
```

- [ ] Commit, e.g. `Add dialogue event engine and sessions`.

## Task 6: Implement typed dialogue actions and node progression

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueAction.java`
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`
- Create: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueActionGameTests.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/gametest/McaGameTestsRegistration.java`

Implement only action semantics required by shipped dialogue migration and new story content:

- `mca:hearts` — modify the active player/villager relationship hearts.
- `mca:mood` — adjust/set mood through the existing villager-brain owner.
- `mca:remember` — write a normal `LongTermMemory` key/expiry where authored content genuinely uses that memory.
- `mca:command` — delegate to the existing `VillagerCommandHandler.handle(player, command)` for gameplay interactions that already have command ownership; do not copy those commands into dialogue code.

`next` is graph control, not an action. `complete`/`end` are terminal node semantics, not arbitrary targetable actions.

- [ ] RED GameTests proving hearts/mood/memory/command actions execute once after a valid offered choice and never on rejected/stale choices.
- [ ] Test explicit completion writes durable history and cooldown state; retryable end does not.
- [ ] Test `Messenger.getTranslatable(...)` is used for villager line components so `DialogueType` profession/personality/gender/type fallback is preserved.
- [ ] Implement actions and progression.
- [ ] Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests --console=plain
```

- [ ] Commit, e.g. `Execute dialogue event actions server side`.

## Task 7: Add the new payload protocol and minimally extend InteractScreen

**Files:**

- Create: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueBeginMessage.java`
- Create: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueSelectMessage.java`
- Create: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueChoiceMessage.java`
- Create: `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueOptionsResponse.java`
- Create: `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueNodeResponse.java`
- Create: `common/src/test/java/net/conczin/mca/network/DialogueEventCodecTest.java`
- Modify: `common/src/main/java/net/conczin/mca/network/MessagesMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandler.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/InteractionCloseRequest.java`

Protocol shape:

```text
C2S begin:  villager UUID
S2C options: generation + event options(mode,id,prompt) + ambientAvailable + legacyAvailable
C2S select: generation + selection kind(EVENT|AMBIENT|LEGACY) + optional event ID
S2C node:   generation + event ID + line Component + silent + offered choices(id,text Component)
C2S choice: generation + choice ID
```

The server does not accept node IDs, arbitrary action/result IDs, or villager UUIDs after `begin`; those come from the active session. Put explicit list/string bounds on codecs where the current networking utilities allow it.

`InteractScreen` changes only the Talk state: render one highlighted prompt, `Ask about...` choices, generic `What's on your mind?`, and the existing answer-style list for a node's choices. Keep Gift/Hug/etc outer interaction UI behavior intact. Choice labels are supplied as translatable Components; do not reconstruct event translation keys from client-side IDs.

- [ ] Add codec round-trip tests for all new records and bounded lists.
- [ ] Replace the Talk button send path with `InteractionDialogueBeginMessage`.
- [ ] Add client handlers/state for options and current node.
- [ ] Send select/choice messages only from currently rendered server-provided options.
- [ ] `InteractionCloseRequest` calls `DialogueEngine.end(player)` in addition to stopping the villager interaction.
- [ ] Add/verify disconnect cleanup using the exact 1.21.1 loader lifecycle hook only if close handling cannot cover abrupt disconnects; keep cleanup delegation to `DialogueEngine.end`.
- [ ] Run focused codec tests and both loader builds:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.network.DialogueEventCodecTest' --console=plain
.\gradlew.bat :fabric:build :neoforge:build --console=plain
```

- [ ] Run a focused client smoke check on one loader: Talk opens options, selecting an event shows the line/choices, close/reopen invalidates old generation.
- [ ] Commit, e.g. `Wire server authoritative dialogue protocol`.

## Task 8: Add authoritative recent-life-event facts at their gameplay owners

**Files:**

- Create: `common/src/main/java/net/conczin/mca/entity/ai/RecentVillagerEvents.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ZombieVillagerEntityMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/Relationship.java`
- Modify: `common/src/main/java/net/conczin/mca/block/TombstoneBlock.java`
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueCondition.java`
- Create: `common/src/test/java/net/conczin/mca/entity/ai/RecentVillagerEventsTest.java`
- Create: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueLifeEventGameTests.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/gametest/McaGameTestsRegistration.java`

Use a bounded gameplay-fact owner separate from pair conversation history:

```java
public final class RecentVillagerEvents {
    public void record(ResourceLocation event, long gameTime);
    public boolean occurredWithin(ResourceLocation event, long gameTime, long withinTicks);
    public void writeToNbt(CompoundTag tag);
    public void readFromNbt(CompoundTag tag);
}
```

Store only the latest game-time occurrence per namespaced fact ID. Add `mca:recent_event` condition with `event` + `within_ticks`.

Gameplay producers, not dialogue code, record facts:

- `Relationship.onTragedy(...)`: record family/relative death on affected villager where the relationship type is spouse/parent/child/sibling.
- `VillagerEntityMCA.hurt(...)`: record `mca:attacked` only after damage is actually accepted server-side.
- Villager -> zombie conversion owner: record zombification before conversion state is copied.
- `ZombieVillagerEntityMCA.convertTo(...)`: record cure on the converted MCA villager.
- `TombstoneBlock` successful resurrection: record revival after the entity is successfully restored.

Do not create a parallel LongTermMemory conversion mechanism. The existing `writeAdditionalConversionData`/`readAdditionalConversionData` pipeline already preserves LongTermMemory; extend the same conversion-data owner for `RecentVillagerEvents` and add regression coverage.

Raid aftermath is intentionally a separate producer step within this task: first inspect the exact 1.21.1 vanilla `Raid`/villager brain lifecycle and existing `CelebrateVillagersSurvivedRaid`/`ResetRaidStatus` path. Add `mca:raid_survived` at the narrowest existing completion owner; do not poll every tick and do not add a Mixin if an existing MCA/vanilla behavior or loader event can own it.

- [ ] Unit-test NBT round-trip and `within_ticks` boundary behavior.
- [ ] GameTest villager<->zombie conversion preserving existing recent facts and LongTermMemory.
- [ ] GameTest family death, attack, cure, revival, and raid-survival producer semantics.
- [ ] Add positive/negative `mca:recent_event` condition tests.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.RecentVillagerEventsTest' --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueLifeEventGameTests --console=plain
```

- [ ] Commit, e.g. `Track recent villager life events`.

## Task 9: Add the temporary legacy adapter and close the old packet trust gap

**Files:**

- Create: `common/src/main/java/net/conczin/mca/dialogue/LegacyDialogueAdapter.java`
- Modify: `common/src/main/java/net/conczin/mca/resources/Dialogues.java`
- Modify: `common/src/main/java/net/conczin/mca/resources/data/dialogue/Actions.java`
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueMessage.java`
- Create: `common/src/test/java/net/conczin/mca/dialogue/LegacyDialogueAdapterTest.java`

The adapter exists only so unconverted shipped resources remain usable during the migration commits. It must not become a public addon API.

Track the legacy question and offered answers for the player/villager/generation. The old C2S answer packet is accepted only while a LEGACY session is active and only for the exact current question/offered answer. Existing `Dialogues.selectAnswer` constraint revalidation remains a second gate.

Route legacy `Actions.next` question presentation through the adapter so offered-answer state advances with the old tree. Do not retrofit new event/history semantics into old JSON.

- [ ] RED tests: arbitrary question, arbitrary answer, hidden answer, stale previous question, replayed answer, and wrong villager all fail without executing actions.
- [ ] Test a valid legacy root -> next -> answer path still works while the adapter is enabled.
- [ ] Implement adapter/state bridge.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.LegacyDialogueAdapterTest' --console=plain
```

- [ ] Commit, e.g. `Contain legacy dialogue behind migration adapter`.

## Task 10: Migrate ordinary/social dialogue content to DialogueEvents

**Files:**

- Create/modify under: `common/src/main/resources/data/mca/dialogue_events/`
- Modify existing language resources only when a required prompt/choice key does not already exist.
- Create: `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java`

Migrate these legacy resources first because they prove ordinary Talk, ambient weighting, direct choices, weighted outcomes, cooldown/history, and DialogueType phrasing without gameplay-command coupling:

```text
root.json
first.json
first.question.json
greet.json
chat.json
chat.topic.json
joke.json
story.json
rumors.json
rock_paper_scissor.json
```

The new resources should expose normal conversation as new always/ambient events, not as permanent legacy-root fallback. Preserve existing `dialogue.*` translation keys where practical by putting those complete keys in event `line`/choice fields.

Translate old weighted conditional results deliberately:

- hard state requirements become boolean requirements;
- equal-purpose random reactions become explicit weighted `outcomes`;
- cooldown memories become event repeat/history policy where they represent conversation scheduling;
- arbitrary non-dialogue memories stay `mca:remember` only when they truly represent gameplay memory.

- [ ] Add a resource test that loads every new MCA event through the production decoder and asserts no duplicate event/choice IDs, broken node refs, or missing strict `mca:` event prerequisites.
- [ ] Add assertions that ordinary Talk has at least one eligible baseline path in shipped data.
- [ ] Migrate the ten resources above and keep their legacy copies only while the adapter comparison is useful.
- [ ] Run production resource tests + common suite:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.McaDialogueEventResourcesTest' --console=plain
.\gradlew.bat :common:test --console=plain
```

- [ ] Client-smoke Talk with adult/child, differing personality, day/night, and rain to verify visible prompt/line fallback.
- [ ] Commit, e.g. `Migrate core social dialogues to events`.

## Task 11: Migrate command-backed relationship/gameplay dialogues and add reference contextual stories

**Files:**

- Create/modify under: `common/src/main/resources/data/mca/dialogue_events/`
- Modify: `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java`
- Add language entries under the existing MCA language resources for genuinely new reference conversations.

Migrate the remaining nine shipped legacy resources:

```text
main.json
apologize.json
flirt.json
hug.json
kiss.json
hire.json
procreate.json
divorce.json
adopt.json
```

Use `mca:command` only to delegate to existing `VillagerCommandHandler` owners such as hire/stay/adopt/procreate/divorce behavior. Do not copy command implementation into dialogue actions.

Add the spec's reference contextual stories in the same final schema:

- gloomy nighttime reflection + later choice-dependent follow-up;
- grumpy nighttime cooldown conversation;
- weather/ambience conversation;
- trait/lactose example;
- mourning conversation;
- zombie-cure identity conversation;
- recently revived conversation;
- infirmary/prison current-building conversation.

- [ ] Resource tests prove every reference story decodes and its cross-event/choice prerequisites resolve as specified.
- [ ] GameTests cover one life-event prerequisite chain and one building-context event end-to-end through `DialogueEngine`.
- [ ] Verify command-backed migrations still reach the existing gameplay command owner once and respect server constraints.
- [ ] Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.McaDialogueEventResourcesTest' --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueLifeEventGameTests --console=plain
```

- [ ] Commit, e.g. `Migrate relationship dialogues and add contextual stories`.

## Task 12: Remove the legacy conversation engine after migration proves complete

**Files to delete:**

- `common/src/main/java/net/conczin/mca/resources/Dialogues.java`
- `common/src/main/java/net/conczin/mca/resources/data/dialogue/Question.java`
- `common/src/main/java/net/conczin/mca/resources/data/dialogue/Answer.java`
- `common/src/main/java/net/conczin/mca/resources/data/dialogue/Result.java`
- `common/src/main/java/net/conczin/mca/resources/data/dialogue/Actions.java`
- `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueInitMessage.java`
- `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueMessage.java`
- `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueQuestionResponse.java`
- `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueResponse.java`
- `fabric/src/main/java/net/conczin/mca/fabric/resources/FabricDialogues.java`
- all 19 files under `common/src/main/resources/data/mca/dialogues/`

**Files to modify:**

- `common/src/main/java/net/conczin/mca/network/MessagesMCA.java`
- `common/src/main/java/net/conczin/mca/network/ClientHandler.java`
- `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- `fabric/src/main/java/net/conczin/mca/fabric/MCAFabric.java`
- `neoforge/src/main/java/net/conczin/mca/neoforge/CommonNeoForge.java`
- delete `common/src/main/java/net/conczin/mca/dialogue/LegacyDialogueAdapter.java`
- delete its compatibility test.

- [ ] Run `rg` proving production code has no references to `Dialogues`, legacy dialogue data classes, or old packet classes before deletion.
- [ ] Remove old network registrations/client handlers and legacy rendering state.
- [ ] Remove loader registration for legacy `Dialogues`/`FabricDialogues`.
- [ ] Delete old resources/classes/adapter.
- [ ] Run the resource test to prove all shipped conversational content now lives under `dialogue_events`.
- [ ] Run:

```powershell
rg -n 'Dialogues|InteractionDialogueInitMessage|InteractionDialogueMessage|InteractionDialogueResponse|InteractionDialogueQuestionResponse|resources\.data\.dialogue' common fabric neoforge
.\gradlew.bat :common:test --console=plain
.\gradlew.bat :fabric:build :neoforge:build --console=plain
```

The `rg` result should contain no production legacy-engine references; investigate any remaining hits rather than blanket-deleting unrelated text.

- [ ] Commit, e.g. `Remove legacy dialogue engine`.

## Task 13: Final runtime/security verification and port-ready handoff

**Files:**

- Modify only tests/docs if final verification exposes a genuine gap; do not add cleanup abstractions without evidence.

- [ ] Run focused dialogue unit suites and the full common suite.
- [ ] Run all dialogue/life-event GameTests added by this plan.
- [ ] Run both loader builds serially if shared Gradle output shows any concurrency corruption:

```powershell
.\gradlew.bat :common:test --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueConditionGameTests --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueLifeEventGameTests --console=plain
.\gradlew.bat :fabric:build :neoforge:build --console=plain
```

- [ ] Dedicated-server smoke: datapack reload with valid + intentionally invalid addon event; verify invalid event logs precise ID without weakening other events.
- [ ] Multiplayer smoke with two players and one villager: independent completion/choices/cooldowns.
- [ ] Security smoke: replay previous generation, forged event ID, forged choice ID, double-click duplicate choice, walk out of interaction range, close screen, unload/kill villager, disconnect/reconnect.
- [ ] Reload smoke while a session is active: invalidate session if the `DialogueEvents.generation()` changed; removed event must never continue executing stale actions.
- [ ] Client/server resource mismatch smoke: server authority remains correct even if client lacks an optional translation (display may show key, behavior must remain valid).
- [ ] Inspect final diff for temporary adapter residue, duplicate state, unused legacy translations/resources, and accidental unrelated changes.
- [ ] Record porting notes for 26.1.2/26.2 limited to actual seams observed: reload listener/identifier APIs, networking codec registration, and SavedData APIs. Do not fork the architecture.
- [ ] Commit any verification-only corrections separately with a precise message.

## Definition of Done

- TALK runs entirely through `DialogueEvent`/`DialogueEngine` and server-owned sessions.
- Every shipped legacy dialogue resource is migrated or intentionally replaced.
- `Dialogues`, `Question`, `Answer`, `Result`, `Actions`, old dialogue payloads, `FabricDialogues`, and `LegacyDialogueAdapter` are gone.
- Contextual events support hard current-state conditions, pair history, previous stable choices, cooldowns, weighted ambient/outcomes, physical infirmary/prison context, and owned recent-life facts.
- Datapacks can add namespaced events without Java and missing optional addon prerequisites fail closed.
- Both loaders build and focused runtime/security verification passes on 1.21.1.
- The resulting common architecture is ready to port to 26.1.2 and 26.2 without changing JSON/history semantics.
