# Dialogue Event Conversation Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace MCA's legacy `Question -> Answer -> Result -> Actions` conversation engine on `dev/1.21.1` with the namespaced, server-authoritative `DialogueEvent` engine from the reviewed design, migrate all shipped dialogue content, and remove the legacy engine after migration.

**Architecture:** `DialogueEvent` owns selection metadata and its inline node graph. `DialogueEvents` reloads immutable namespaced event definitions; `DialogueEngine` evaluates deterministic requirements, owns active/paused sessions, advances ordered lines and nodes using single-use offer tokens, and commits pending effects and pair history on completion. Legacy `Dialogues` remains only behind a temporary adapter while shipped resources migrate, then is deleted.

**Tech Stack:** Java 21, Minecraft 1.21.1, common multiloader code, Mojang `Codec`/`JsonOps` decoding behind Gson-backed server resource reloads, Minecraft `SavedData`/versioned NBT, custom payloads with `StreamCodec`, Fabric API 0.116.17+1.21.1, NeoForge 21.1.256, JUnit 5, NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-10-06-dialogue-event-conversation-system-design.md`

**Review status:** documentation update only; implementation has not begun.
Stories repeat after their datapack-authored per-event/pair cooldown and have
remembered-choice follow-ups; story completions replace choices with the latest completed
run (Task 3). Talk offers a personalized continuation alongside other topics;
selecting a different valid topic with the same or another villager abandons the
paused run, but browsing does not (Task 5). Reference stories, including Phyrra's
sample, and small talk author `seconds: 5` in their cooldown JSON for now (Task
11). Durations use seconds in datapacks and ticks internally. The 2400-tick pause
window is unchanged. These refinements do not authorize
implementation or reopen the remaining agreed architecture and conditions.

## Global Constraints

- Keep authoritative gameplay, dialogue, persistence, and networking logic in `common/`; loader code only registers reload listeners/lifecycle hooks.
- New dialogue resources retain full `ResourceLocation` namespace and nested path. Do not inherit legacy basename lookup.
- Event/choice eligibility is boolean. Randomness is permitted for eligible event/outcome weight and the rolled cooldown interval; phrase variants remain client presentation.
- Client packets are untrusted. The server owns the villager, event, node, line index, offered IDs, and single-use offer token. Reload generation is separate.
- Show ordered lines one at a time with Continue, natural replies with hidden effects, and a fixed 250 ms/word client typewriter; reveal complete words, not individual characters. No reveal shortcut, click acceleration or instant-display setting. Enable Continue/replies only after the relevant line finishes revealing.
- Closing/walking away pauses for 2400 overworld game ticks. Right-click opens normal controls; Talk opens options, including a personalized continuation for this pair. Only selecting continuation resumes. Invalidation/disconnect/restart discards unfinished state.
- Retain at most one active or paused run per player plus one bounded menu offer. Selecting a different validated new topic with the same or another villager discards the previous paused run; browsing menus/normal controls or sending invalid selections does not.
- Stories repeat after their authored cooldown and can lead to separate remembered-choice follow-ups. Successful completions replace durable choices with the latest completed run's set; abandoned runs never overwrite completed history. Require an explicit repeat policy; reference stories and small talk author `seconds: 5` for now. Cooldowns apply to this event for this player/villager pair, not all topics; there is no global override of other authored repeat rules.
- Completion/end refreshes Talk options when interaction is valid; never automatically start the next event. Priority controls highlighting, not forced conversation choice.
- Queue story effects until final Continue on a completing terminal. No rewards or completion merely from sending the terminal line.
- `DialogueType`/`Messenger` phrasing fallback stays. Preserve existing translation keys during migration where practical instead of rewriting language assets for cosmetic reasons.
- Do not broaden this into a gift-system rewrite, quest engine, cutscene system, or general event bus.
- Preserve unrelated working-tree changes. Commit only files belonging to the current task.
- Before adding a loader event, codec helper, or Minecraft API workaround, verify the exact 1.21.1 source/API in this checkout's dependencies.

## Review Focus

1. **Trust boundary:** no C2S message can select an event/choice or advance a line without its current single-use offer token; consumed and pre-resume packets never execute effects.
2. **Save compatibility:** stable choices, pair-scoped story versus scheduling records, versioned NBT, conversion persistence, and missing-addon prerequisites follow the spec; pause state stays transient.
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
        HistoryPolicy history,
        String start,
        Map<String, Node> nodes) {

    public static DialogueEvent decode(ResourceLocation id, JsonObject json);
    public void validate();
}
```

Keep `Presentation`, `Repeat`, `Node`, `Choice`, `Outcome`, `HistoryPolicy`, and the small enums nested in `DialogueEvent` unless implementation pressure proves they need independent ownership. `Choice.id` is event-wide unique and durable; node IDs are not persisted as history identity.

`Presentation` includes required event-specific `resume_prompt` alongside the
existing mode/prompt/topic fields, including for ambient events. Require `repeat`;
stories use `cooldown`, while `once` remains an explicit author opt-in and `always`
allows immediate repetition. Cooldown JSON uses exactly one of `seconds` or
`min_seconds` plus `max_seconds`. Accept finite nonnegative fractional seconds,
validate range ordering and representability, then normalize with
`ceil(seconds * 20)` to internal tick bounds. Reject mixed forms and obsolete
`min_ticks`/`max_ticks` fields. Roll within inclusive tick bounds only on
completion. Use overworld game time, not wall-clock/offline elapsed time. Do not
add a separate remembered-question subsystem; event completion and stable choice
history express repeatable stories and follow-ups.

`decode(id, json)` uses Mojang codecs with `JsonOps`, injecting the resource ID
externally, then graph validation. A node uses exactly one `line` or nonempty
`lines`; normalize both to an ordered line list. Its post-line shape is exactly
one of nonempty `choices`, `next`, `complete: true`, or `end: true`. Direct choices
and every weighted outcome require `next`. Reject cycles; repeat belongs to event
policy. Preserve error paths and reject partial codec results instead of making
another hand-parsed Gson format. Check recognized fields as needed: using a codec
alone does not guarantee unknown JSON fields are rejected.

`DialogueCondition` and `DialogueAction` own small type registries keyed by namespaced IDs. Unknown types throw a decode error; they are never silently ignored. Do not reuse `GiftPredicate.Condition`'s float semantics.

- [ ] Add failing parser tests for full event decoding, duplicate choice IDs, missing start node, missing `next`, malformed repeat ranges, zero/negative/non-finite event weights, zero/negative/non-finite outcome weights, and unknown condition/action types.
- [ ] Add tests proving a choice may use either direct `actions`/`next` or nonempty `outcomes`, never both.
- [ ] Add graph validation tests for terminal nodes: exactly one of `complete`/`end`; `once`/`cooldown` non-completing terminal requires `retryable: true`.
- [ ] Test ordered multi-line nodes, `next`-only Continue nodes, conflicting continuation shapes, empty/blank lines, `line` plus `lines`, cyclic node refs, and the corrected retryable goodbye example.
- [ ] Test default story history and scheduling-only policy restricted to cooldown events.
- [ ] Test required repeat policy, explicit `once`/`always`, fixed `seconds: 5` -> 100 ticks, fractional rounding, random seconds bounds, missing/mixed duration fields, obsolete tick fields, negative/non-finite values and conversion overflow. Test required nonblank `resume_prompt` for all event modes; scheduling-only data still requires explicit cooldown.
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
- [ ] Test cross-event stable-choice validation, scheduling-only reference rejection, strict shipped `mca:` dependencies, unavailable external dependencies including under `mca:not`, and diagnostics for prerequisite cycles.
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

A story `EventRecord` stores completion count, last completion game time,
next-eligible game time, and stable choice IDs. A scheduling-only record stores
only next-eligible time. There is one record per `(player UUID, villager UUID,
event ID)`, never an append-only transcript. On successful completion of an
story, replace the saved choice set with this latest
completed run's choices; do not union repeated runs or retain the first run.
Preserve completion/count/timing
history, and leave saved choices unchanged for abandoned attempts.
Follow-ups read only this one completed choice set, never alternate among
conflicting answers from earlier runs.
Completion/choices remain remembered when the cooldown expires and the original
story becomes eligible again. A distinct follow-up reads its
`event_completed`/`event_choice` state independently of that cooldown. Optional
explicit `once` content cannot replay after completion; do not impose this policy
on the reference stories.

Use overworld game time, `schema_version: 1`, validated IDs/collections, and
overflow-safe time/count updates. Preserve unsupported future saves and report
the issue instead of clearing them. Durable records can grow with distinct
pairs/events; document that rather than claiming a fixed global bound. Expired
scheduling-only records are pruned on relevant access or bounded maintenance,
not by scanning all history on every Talk. Removed-addon story records remain
until explicit administrative purge/migration.

- [ ] Add RED tests for NBT round-trip of completion, choice IDs, and cooldown timestamps.
- [ ] Test that two players talking to the same villager have independent records.
- [ ] Test fixed and random cooldown calculation; random bounds are rolled only at successful completion.
- [ ] Test stories and small talk authored with `seconds: 5`: unavailable at completion + 99 ticks, available at + 100 ticks, independently for each player/villager/event tuple. Other eligible topics are not blocked, and seconds follow game time rather than offline/wall-clock time.
- [ ] Test pruning: expired scheduling-only records disappear; records with durable completion/choice history remain.
- [ ] Test that scheduling completions do not create durable completion/choice data and cannot satisfy story-history requirements.
- [ ] Test latest-completed choice replacement: removed choices no longer match, completion remains true/count advances, and an abandoned later run leaves earlier completed choices intact.
- [ ] Test a repeatable story's completion/choices survive save/reload and cooldown expiry, gate a separate follow-up and permit the original story again when requirements still hold. Test explicit `once` separately, without making it the story default.
- [ ] Test schema-version loading, malformed saved fields, unsupported future version handling, and timestamp overflow.
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

Map gender, pregnancy, inventory/item/tag, ordinary memory and building assignment
requirements needed by actual migrated content to their existing authoritative
owners as well; do not leave legacy constraints unconverted merely because they
are not in the short sample list. Keep each enabled condition's serialized fields
and positive/negative tests explicit. An unavailable ownership/API needs a focused
source investigation rather than an invented permissive fallback.

Also provide explicit `mca:not` wrapping one condition. Keep unavailable lookup
distinct from an ordinary false match so negation cannot turn missing addon
prerequisites into eligible events. Do not introduce a general expression engine.

Do not import the existing float/chance behavior from `GiftPredicate`; read the same authoritative owners directly or extract only a genuinely shared lookup helper.

`Village` should own physical building-type resolution. Add a narrow query such as:

```java
public boolean isInBuildingOfType(Vec3i position, String type)
```

implemented using current physical-room lookup plus `RoomTypeResolver` effective type, so dialogue code does not duplicate logical-building/floor semantics. This must distinguish "village has an infirmary" from "villager is inside an infirmary". Existing `prison` and `infirmary` building definitions are sufficient; do not invent a new building tag system.

- [ ] RED test `Village.isInBuildingOfType` with inherited/logical room types in `VillageRoomTypeQueryTest`.
- [ ] Add GameTests for world-backed condition truth tables: health, rain/thunder, biome, time, village-has-building versus in-building, and player/villager relationship state.
- [ ] Add focused tests for `event_completed`/`event_choice`: unresolved external namespace evaluates false; broken shipped `mca:` references are caught by resource validation rather than silently evaluating true.
- [ ] Test the sample's positive adult/gloomy/night/hearts >= 20 requirements; test genuine negation separately and confirm unavailable references fail closed through it.
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
- Modify: `common/src/main/java/net/conczin/mca/MCA.java`
- Modify: `fabric/src/main/java/net/conczin/mca/fabric/MCAFabric.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/neoforge/CommonNeoForge.java`

`DialogueSession` is transient and contains the player/villager binding,
session identity, resource reload generation, single-use offer token, active/paused
status, pause deadline, current node/line index, offered event/choice IDs,
ambient candidates, temporary stable choices, selected outcomes, and pending
effects. Store UUIDs and immutable definition references, not strong live entity
references. It is never SavedData. Retain at most one active or paused run per
player. Store one bounded current `DialogueOptions` offer alongside that run,
not in place of it or as another session. Browsing options preserves a paused
run; selecting a different new run with the same or another villager abandons
the old run only after the new selection is valid. Discard its temporary choices/
effects without completion or cooldown. Invalid selections leave it intact.

**Target engine surface:**

```java
public final class DialogueEngine {
    public static final long PAUSE_TICKS = 2400L;
    public DialogueOptions begin(ServerPlayer player, VillagerEntityMCA villager);
    public Optional<DialogueNodeView> select(
            ServerPlayer player, long offerToken,
            DialogueSelection selection, @Nullable ResourceLocation eventId);
    public TransitionResult choose(
            ServerPlayer player, long offerToken, String choiceId);
    public TransitionResult advance(ServerPlayer player, long offerToken);
    public void pause(ServerPlayer player);
    public void end(ServerPlayer player);
    public void tick(MinecraftServer server);
    public void clear(MinecraftServer server);
}
```

Keep `DialogueOptions`, `DialogueNodeView`, `DialogueSelection`, and
`TransitionResult` as nested engine/session data carriers rather than introducing
independent managers. `TransitionResult` distinguishes rejected, advanced,
completed, and ended transitions, with a node view only where applicable. A node
view carries session ID, offer token, complete current line Component, silent
flag, offered choices, and `canContinue`; it never carries client-executable
actions. `DialogueOptions` includes an optional personalized continuation prompt,
ordinary event options and ambient availability, bound to the target villager,
generation and single-use menu token. `DialogueSelection` includes `RESUME` in
addition to `EVENT`, `AMBIENT` and migration-only `LEGACY`; only EVENT supplies a
client event ID. The Task 7 begin handler always calls `begin` to offer options,
never to auto-resume. `select(..., RESUME, null)` validates the offered paused run
and resumes it internally with a fresh active-run offer token. Opening the normal
interaction GUI sends neither begin nor select.

Rules:

- Gather TALK events in canonical ID order.
- Hard-filter requirements and repeat/history availability.
- Offer a valid same-pair continuation independently of the new-event priority tiers. Resolve its event-specific localized `resume_prompt` with the current villager display name as optional `%1$s` argument; no generic resume label or client-generated event-key reconstruction.
- Exclude the paused event from new-start offers and ambient candidates. Retain other eligible topics; browsing and menu refresh do not reset the pause deadline.
- HIGHLIGHTED: highest priority + canonical ID gets the direct slot; overflow remains under Ask.
- ASK: all eligible ASK + overflow highlighted, sorted priority then ID.
- AMBIENT: keep only highest eligible priority tier; weighted draw occurs when the generic ambient option is selected.
- Choice outcomes: hard-filter outcome requirements first, then weighted draw among eligible outcomes.
- Check event requirements/repeat policy before starting; do not recheck original weather/time/mood/recent-fact gates during an ongoing or resumed run.
- Recheck interaction validity, explicit choice/outcome conditions and action-specific constraints at their owning transitions.
- One retained active or paused session per player. A validated different event/ambient selection with the same or another villager invalidates the old run's offers and discards its unfinished state. Explicit continuation retains it with a new token. Merely opening normal controls/Talk options or submitting invalid selections does not replace it.
- The bounded menu offer is separate from the paused run's progress; new menu tokens invalidate prior menu offers, not the run/deadline. RESUME validates same-pair pause state and offered continuation; expired resume never falls back to restarting the story.
- Every accepted select/choice/Continue consumes its offer token before progression. Resource reload generation is independent of offer tokens.
- Ordered lines advance only on server-accepted Continue; terminal completion occurs only on final Continue, not when the final line is sent.
- Pause on screen closure/range loss/unload; after 2400 overworld ticks discard unfinished state with at most one nearby timeout line and no hearts penalty. Opening the GUI does not reset the deadline.
- Reuse existing loader server-tick/stop callbacks to delegate to `tick`/`clear`. Tick only retained sessions for validity/expiry; do not add a global villager scan. Disconnect clears the player's run; reload invalidates active and paused runs.

- [ ] RED tests for highlighted/ask overflow, ambient highest-tier behavior, deterministic canonical ordering, weighted selection with injected deterministic `RandomSource`, zero eligible outcomes, and cooldown exclusion.
- [ ] RED tests for unoffered IDs, consumed offer tokens, duplicate choice/Continue, wrong villager, reload generation mismatch, final-line acknowledgement and zero-valid-choice safe exit.
- [ ] Test pause at a mid-passage line, explicit continuation at 2399 ticks, expiry at 2400 ticks, stale pre-pause packets, preserved selected outcome, and no deadline extension from merely opening the GUI.
- [ ] Test Talk at 2399 ticks returns continuation plus other topics without resuming; only offered RESUME selects the saved line. Expiry while browsing rejects RESUME without restart/effects. Menu refresh and rejected selections do not extend the deadline.
- [ ] Test time/weather/mood changes do not interrupt a started or resumed run; explicit choice/action requirements still revalidate.
- [ ] Test disconnect, server stop, villager death/conversion, temporary unload and paused reload cleanup; expiry does not load chunks or send a distant reprimand.
- [ ] Test starting a second integrated-server world does not inherit sessions or offers from the first; keep engine state scoped to its server lifecycle.
- [ ] Test Alice pause -> Alice/Bob menu browsing leaves Alice resumable; selecting another valid topic with Alice or Bob abandons the old run, clears its queued effects/choices and invalidates its offers without affecting durable history. Failed selections leave it intact. Assert at most one retained run and one menu offer per player.
- [ ] Test paused-event exclusion from new/ambient candidates, continuation not hidden by priority, personalized prompt binding and refreshed options after completion/end with no automatic event chain.
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

Queue effects from accepted choices/outcomes. Execute them only on accepted final
Continue at a completing terminal, together with history/cooldown commit.
After completion or non-completing end, report the transition so the handler can
return refreshed Talk options when interaction is still valid, never auto-start
the next event. Newly eligible follow-ups remain player-selectable.
Pause/resume retains the queue. Retryable end, expiry, disconnect and invalidation
discard it. Gameplay commands must be on completing paths and revalidated through
their owner at commit; failed commands must not grant rewards/completion.
Review each migrated command's success/close semantics rather than assuming a
generic command return value provides transactional rollback. Do not add a general
world-state transaction system.

`rewardHearts` has mood/fatigue/advancement side effects. Document and test the
selected owner semantics so migrated rewards do not double-apply a mood effect.

- [ ] RED GameTests proving valid choice acceptance queues but does not yet apply effects; final Continue applies them once; rejected/stale choices/Continue never apply them.
- [ ] Test pause/resume preserves effects without replay; abandoning a rewarding branch cannot farm hearts; non-completing end or timeout discards its queue.
- [ ] Test completion writes history and cooldown once, scheduling-only events retain only timing, and failed commands do not grant story rewards/completion.
- [ ] Test hearts/mood/fatigue/advancement side effects against the existing owner and intentional legacy migration behaviour.
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
- Create: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueAdvanceMessage.java`
- Create: `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueOptionsResponse.java`
- Create: `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueNodeResponse.java`
- Create: `common/src/test/java/net/conczin/mca/network/DialogueEventCodecTest.java`
- Create: `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java`
- Modify: `common/src/main/java/net/conczin/mca/network/MessagesMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandler.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/InteractionCloseRequest.java`
- Modify: `common/src/main/java/net/conczin/mca/MCAClient.java`

Protocol shape:

```text
C2S begin:  villager UUID
S2C options: offerToken + optional continuation(prompt Component)
             + event options(mode,id,prompt) + ambientAvailable + legacyAvailable
C2S select: offerToken + selection kind(RESUME|EVENT|AMBIENT|LEGACY) + event ID only for EVENT
S2C node:   sessionId + offerToken + state(ACTIVE|PAUSED|ENDED) + optional active node view
            active view = event ID + current line Component + silent + canContinue + choices(id,text Component)
C2S choice: offerToken + choice ID
C2S advance: offerToken
```

The server does not accept node IDs, arbitrary action/result IDs, or villager
UUIDs after `begin`; derive the target from the current menu offer for selection
and the retained run for active progression. Put explicit list/string bounds on
codecs where the current networking utilities allow it.

`InteractionDialogueBeginMessage` is sent only by the existing Talk button.
Its handler sends `begin` options, including a valid same-pair continuation and
other eligible topics, without resuming or replacing the paused run. Selecting
the continuation sends `RESUME` through the existing select message; no dedicated
resume packet is needed. Do not add an automatic resume request from GUI initialization
or change right-click into direct dialogue launch. `PAUSED`/`ENDED` responses
clear stale controls, and late responses for a replaced session or older offer
cannot reopen it or overwrite newer controls.

`InteractScreen` changes only the Talk state: render an optional authored,
personalized continuation in its own slot, one highlighted prompt,
`Ask about...` choices, generic `What's on your mind?`, Back/Leave, and the existing
answer-style list for a node's choices. Keep Gift/Hug/etc outer interaction UI
behavior intact. Labels are supplied as translatable Components; do not
reconstruct keys from client-side IDs. The server binds continuation to the
paused event and supplies the current villager display name as optional `%1$s`
argument. Use topic-specific phrasing rather than "Continue our conversation".
After completion/end, clear the old node view and show a fresh server menu if
interaction is valid; newly unlocked events never start automatically. Late old
responses must not overwrite that newer menu.

Render one complete server-provided line through a client reveal cursor, with the
agreed fixed interval of 250 ms per complete displayed word.
Use locale-aware word boundaries; preserve punctuation, spacing and Unicode/
Component formatting. Provide no skip, accelerated reveal or instant-display
setting. Disable Continue until the current line is fully revealed; clicks during
reveal must neither expose the rest of the line nor advance or queue advancement.
An enabled Continue sends `InteractionDialogueAdvanceMessage`.
Show replies only when the final passage line is fully revealed. No positive/
neutral/negative labels or reward/probability previews.

Keep the resolved current pooled phrase and reveal progress in a bounded client
presentation state nested in `ClientHandlerImpl`, not a second authoritative
dialogue graph. Clear it on session replacement/completion/expiry/disconnect;
do not reroll `/1` alternatives when explicit continuation is selected. Use the existing
client tick/lifecycle seam in `MCAClient` to maintain that transient state.

- [ ] Add codec round-trip tests for all new records and bounded lists.
- [ ] Replace the Talk button send path with `InteractionDialogueBeginMessage`.
- [ ] Add client handlers/state for options and current node.
- [ ] Test optional continuation serialization and authored prompt/name binding; Talk shows it alongside other topics, and only its explicit RESUME selection starts reading. Back/Leave/browsing preserves the paused deadline.
- [ ] Send select/choice messages only from currently rendered server-provided options.
- [ ] `InteractionCloseRequest` pauses an active event with `DialogueEngine.pause(player)` while stopping the current villager interaction; it must not call `end` for ordinary closure. Closing/browsing menus with an already paused run leaves its original deadline and saved line unchanged.
- [ ] Wire disconnect cleanup through the exact 1.21.1 lifecycle hook or server session validation: disconnect always calls `end`, not `pause`; server stop calls `clear`.
- [ ] Add presentation tests using controllable elapsed time for one complete word per 250 ms (not individual characters), locale-aware word and Unicode/formatting boundaries, punctuation/spacing and pooled-line preservation. Verify clicks cannot skip/accelerate reveal or advance/queue an advance, no instant-display control exists, and Continue/replies enable only after the relevant line finishes. Codec tests cover advance messages and state-clearing responses.
- [ ] Run focused codec tests and both loader builds:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.network.DialogueEventCodecTest' --console=plain
.\gradlew.bat :common:test --tests 'net.conczin.mca.network.DialoguePresentationTest' --console=plain
.\gradlew.bat :fabric:build :neoforge:build --console=plain
```

- [ ] Client smoke: right-click opens ordinary controls; Talk opens options without auto-resume. Test personalized continuation beside other topics, multi-line Continue, hidden consequences, fixed word reveal without shortcuts, close -> right-click -> Talk -> select continuation, topic switching, completion/end back to menu, expiry and stale controls cleared on reload.
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

Store only the latest game-time occurrence per registered namespaced fact ID.
Unknown input must not grow an arbitrary map. Add `mca:recent_event` condition
with `event` + `within_ticks`, using the overworld timebase consistently even
when the producer/entity is in another dimension.

This owner covers generic recent-fact prose. Naming the deceased relative,
distinguishing multiple bereavements, or knowing which player performed a cure
requires explicit payload from the relevant gameplay owner; a timestamp alone
does not provide those facts. Do not claim those personalized variants exist
without implementing their source of truth.

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
- Modify: `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueResponse.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` (temporary legacy offer token)
- Create: `common/src/test/java/net/conczin/mca/dialogue/LegacyDialogueAdapterTest.java`

The adapter exists only so unconverted shipped resources remain usable during the migration commits. It must not become a public addon API.

Track the legacy question and offered answers for the player/villager/current
single-use offer token. Add the token to the temporary legacy response/answer
payloads and send it from the legacy answer controls, so returning to the same
question cannot make a stale answer valid again. Accept an old answer only in an
active LEGACY session for that exact offered question/answer/token, consuming its
offer before actions. Existing `Dialogues.selectAnswer` constraint revalidation
remains a second gate. Preserve the user's local fail-closed constraint changes.

The adapter is temporary compatibility, not a second implementation of the new
ordered-line, deferred-reward and pause features. Legacy runs retain their old
close behaviour while migrated event content demonstrates the final experience;
the adapter and altered temporary payloads are deleted in Task 12.

Route legacy `Actions.next` question presentation through the adapter so offered-answer state advances with the old tree. Do not retrofit new event/history semantics into old JSON.

- [ ] RED tests: arbitrary question, arbitrary answer, hidden answer, stale previous question, replayed answer, and wrong villager all fail without executing actions.
- [ ] Test replay after returning to the same legacy question is rejected by the consumed token, not just by a different question string.
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

The new resources should expose normal conversation as new always/ambient events,
not as permanent legacy-root fallback. Preserve existing `dialogue.*` translation
keys where practical in `line`/`lines`/choice fields. Never interpret pooled `/1`,
`/2` translations as consecutive speech; intentionally authored passages use
distinct semantic keys in `lines`. Give repeatable ambient content nonzero
cooldowns where needed to avoid consecutive repeats; use scheduling-only storage
unless a later story needs durable history.
Give story conversations explicit cooldowns authored in seconds and expose later
references as separate events where useful. Reference stories use five seconds
for now; preserve deliberate interaction-specific repeat restrictions during
migration rather than applying a global override. Add authored continuation translation keys
for every migrated event, including ambient events, while reusing existing line
translations where appropriate.

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
- Create: `docs/dialogue-authoring.md` (final-format examples and personality writing guide)

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

Use `mca:command` only to delegate to existing `VillagerCommandHandler` owners
such as hire/stay/adopt/procreate/divorce behavior. Put them on completing paths
and review commit success/failure/close semantics as specified in Task 6. Do not
copy command implementation into dialogue actions.

Add the spec's reference contextual stories in the same final schema:

- gloomy nighttime reflection + later choice-dependent follow-up;
- grumpy nighttime cooldown conversation;
- weather/ambience conversation;
- trait/lactose example;
- mourning conversation;
- zombie-cure identity conversation;
- Phyrra's speculative cured-zombie discussion with the confirmed positive
  adult/gloomy/night/hearts >= 20 requirements, ordered intro/branch/closing
  lines, distinct player-response keys, a personalized continuation and repeatable pair-scoped story history;
- recently revived conversation;
- infirmary/prison current-building conversation.

- [ ] Resource tests prove every reference story decodes and its cross-event/choice prerequisites resolve as specified.
- [ ] Author reference stories, including Phyrra's sample and its distinct remembered-choice follow-up, with `repeat: { "type": "cooldown", "seconds": 5 }`; use the same authored interval for reference small talk. Document random `min_seconds`/`max_seconds` intervals, other explicit repeat policies and the unchanged 2400-tick pause deadline. General speculation must not require the speaker to have been cured.
- [ ] Test duplicate localization source keys are rejected and all ordered sample lines/player replies/continuation prompts use valid distinct keys. Test all three sample paths through final Continue, per-event/pair cooldown exclusion then renewed eligibility, remembered reply persistence/latest-completed replacement and conditional follow-up availability without automatically starting any event. Each newly completed permitted run applies its authored effects once; duplicate packets never repeat those effects.
- [ ] Write the personality authoring guide: weather/time/mob preferences, tone, consistent exceptions, event-specific continuation labels with optional `%1$s` name, seconds-based story/small-talk cooldowns and separate remembered-choice follow-ups. Add writer templates rather than inventing approved favourites for every personality or adding a gameplay preference registry. Link the verified Stardew documentation and explicitly distinguish MCA's repeatable stories from Stardew's answered-question suppression.
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
- [ ] Verify stories repeat after their authored seconds cooldown while completion stays remembered and latest completed choices gate follow-ups. Abandonment leaves durable history intact, selecting a different validated topic retains only that run, and five-second reference story/small-talk cooldown changes eligibility at 100 ticks without blocking other events or changing pause expiry.
- [ ] Security smoke: replay consumed offer token, forged IDs, duplicate choice/Continue, pause/resume with stale packets, final-line acknowledgement, unload/death/conversion, disconnect/reconnect and attempted reward farming before completion.
- [ ] Presentation smoke: fixed 250 ms/word reveal without shortcuts, Continue/replies after full reveal, ordered passages and hidden consequences; right-click shows ordinary UI, Talk offers topic-specific continuation plus other events, and only selecting it resumes. Test browsing versus topic replacement and post-completion menus without chaining. Verify timeout at 2400 ticks without automatic penalty/distant speech.
- [ ] Reload smoke with active and paused sessions: invalidate when `DialogueEvents.generation()` changes; removed events never execute stale pending effects or resume.
- [ ] Client/server resource mismatch smoke: server authority remains correct even if client lacks an optional translation (display may show key, behavior must remain valid).
- [ ] Inspect final diff for temporary adapter residue, duplicate state, unused legacy translations/resources, and accidental unrelated changes.
- [ ] Record porting notes for 26.1.2/26.2 limited to actual seams observed: reload listener/identifier APIs, networking codec registration, and SavedData APIs. Do not fork the architecture.
- [ ] Commit any verification-only corrections separately with a precise message.

## Definition of Done

- TALK runs entirely through `DialogueEvent`/`DialogueEngine` and server-owned sessions.
- Every shipped legacy dialogue resource is migrated or intentionally replaced.
- `Dialogues`, `Question`, `Answer`, `Result`, `Actions`, old dialogue payloads, `FabricDialogues`, and `LegacyDialogueAdapter` are gone.
- Contextual events support hard current-state conditions, pair history, previous stable choices, cooldowns, weighted ambient/outcomes, physical infirmary/prison context, and owned recent-life facts.
- Ordered lines, fixed 250 ms/word reveal without shortcuts, Talk-menu personalized continuation selected within 2400 ticks, completion-based effects and single-use offer tokens match the spec; no automatic resume or event chaining.
- The personality writing guide and speculative/personal cure examples are included; repeatable stories with authored follow-ups, latest-completed-choice history, single retained session and datapack-authored seconds cooldowns (five seconds for reference stories/small talk) follow the spec's approved decisions.
- Datapacks can add namespaced events without Java and missing optional addon prerequisites fail closed.
- Both loaders build and focused runtime/security verification passes on 1.21.1.
- The resulting common architecture is ready to port to 26.1.2 and 26.2 without changing JSON/history semantics.
