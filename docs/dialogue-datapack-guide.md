# Making MCA dialogue datapacks

You can add your own conversations to Minecraft Comes Alive (MCA) without writing Java or building a mod. A dialogue datapack decides **when a villager can say something**, **what they say**, **how the player can answer**, and **what happens after the conversation**.

**Want to try a complete conversation before writing JSON?** The opt-in [DialogueEvent showcase](../testpacks/dialogue-showcase/README.md) includes seven test events with multi-line branches, remembered choices, a gated follow-up, a highlighted first meeting, cooldowns, conditions and weighted ambient conversations. Install its separate datapack and resource pack in a disposable 1.21.1 test world. It is not part of normal MCA content.

This guide is for Minecraft **1.21.1** and MCA's **DialogueEvent** system. It starts with a conversation that works for any available MCA villager, then shows how to make it more interesting.

## 1. Set up two packs

Minecraft loads dialogue **rules** from a datapack, but it loads the **words shown on screen** from a resource pack. For this example, make these two folders:

```text
<your world>/datapacks/Villager Stories/
  pack.mcmeta
  data/
    villager_stories/
      dialogue_events/
        hello_neighbor.json

<Minecraft resourcepacks>/Villager Stories Text/
  pack.mcmeta
  assets/
    villager_stories/
      lang/
        en_us.json
```

The folder under `data` or `assets` is your **namespace**. Use lowercase letters, numbers, underscores and other valid Minecraft identifier characters. Here we use `villager_stories`. The event's ID will be `villager_stories:hello_neighbor`.

Put this in the **datapack's** `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My MCA villager conversations"
  }
}
```

Put this in the **resource pack's** `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 34,
    "description": "Text for my MCA villager conversations"
  }
}
```

These pack formats are for Minecraft 1.21.1. If you target another Minecraft version, check that version's datapack and resource-pack formats.

**Important:** `assets` inside a server datapack is not a replacement for an enabled client resource pack. In multiplayer, the server needs the datapack and each player needs the resource pack (or the server must provide it as a server resource pack).

### NeoForge 1.21.1 compatibility

MCA registers `DialogueEvents` as a **server datapack reload listener** on NeoForge, not as a NeoForge datapack registry. This is intentional: each file is a self-contained conversation that MCA validates and keeps server-side. `/reload` replaces the event snapshot, and normal Minecraft datapack priority determines which version wins when packs define the same resource path.

Use MCA's event-level `requirements` for conditional conversations. NeoForge's separate top-level `neoforge:conditions` format is **not part of the DialogueEvent JSON schema**; adding that field to a conversation is rejected as an unknown field. For optional addons, distribute the dialogue file only when its companion content is installed, or use supported MCA requirements to make it unavailable until a prerequisite exists. See the [NeoForge 1.21.1 codec guide](https://docs.neoforged.net/docs/1.21.1/datastorage/codecs/) for general Mojang codec conventions and the [NeoForge data load conditions guide](https://docs.neoforged.net/docs/1.21.1/resources/server/conditions/) for the separate NeoForge feature.

## 2. Write your first conversation

Create `data/villager_stories/dialogue_events/hello_neighbor.json` and paste:

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.villager_stories.hello.prompt",
    "resume_prompt": "dialogue_event.villager_stories.hello.resume"
  },
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "hello",
  "nodes": {
    "hello": {
      "line": "dialogue_event.villager_stories.hello.opening",
      "choices": [
        {
          "id": "friendly",
          "text": "dialogue_event.villager_stories.hello.friendly",
          "actions": [
            { "type": "mca:hearts", "amount": 2 }
          ],
          "next": "friendly_reply"
        },
        {
          "id": "busy",
          "text": "dialogue_event.villager_stories.hello.busy",
          "next": "busy_reply"
        }
      ]
    },
    "friendly_reply": {
      "line": "dialogue_event.villager_stories.hello.friendly_reply",
      "complete": true
    },
    "busy_reply": {
      "line": "dialogue_event.villager_stories.hello.busy_reply",
      "complete": true
    }
  }
}
```

What it does:

1. Adds an **Ask about...** topic to an MCA villager's Talk menu.
2. Lets the villager greet the player.
3. Offers two player replies, each leading to a different last line.
4. Gives **2 hearts** for the friendly reply once the player finishes the conversation.
5. Makes that event available again after a **5-second cooldown** for that player and villager.

`start` names the first node, `hello`. The `next` values point to other nodes **inside the same event file**. Every reachable path in this example ends with `complete: true`.

## 3. Add the actual words

Create `assets/villager_stories/lang/en_us.json` in the **resource pack**:

```json
{
  "dialogue_event.villager_stories.hello.prompt": "How are you doing?",
  "dialogue_event.villager_stories.hello.resume": "We were talking about your day...",
  "dialogue_event.villager_stories.hello.opening": "Oh, hello there! It's good to see a familiar face.",
  "dialogue_event.villager_stories.hello.friendly": "It's good to see you too!",
  "dialogue_event.villager_stories.hello.busy": "Sorry, I should get going.",
  "dialogue_event.villager_stories.hello.friendly_reply": "You're always welcome around here.",
  "dialogue_event.villager_stories.hello.busy_reply": "Of course. I'll see you later."
}
```

The JSON event contains **translation keys**, not the displayed sentences. Every `prompt`, `resume_prompt`, `line`, and choice `text` needs a matching key in the client language file. You can provide other languages by adding files such as `de_de.json` with the same keys.

## 4. Try it in Minecraft

1. Install a compatible MCA build for Minecraft 1.21.1. Put **Villager Stories** in the world's `datapacks` directory. A world may be a singleplayer save or a multiplayer server world.
2. Enable **Villager Stories Text** in **Options > Resource Packs** on the client.
3. Enter the world, run `/datapack list` to check the datapack is enabled, and run `/reload` after adding or editing dialogue JSON. Reload client resources with **F3+T** after editing translations.
4. Interact with an awake, available **MCA villager**, open **Talk**, then find **How are you doing?** under **Ask about...**.
5. Pick a reply. Read through to the final line, then click **Back to topics** to finish the conversation and apply any reward.

If you quit the Talk screen midway, MCA can offer a topic-specific continuation when you return to that villager. Closing the screen is **not** the same as completing the dialogue.

You can also distribute either pack as a ZIP. Keep `pack.mcmeta`, `data/`, or `assets/` at the **top level of the ZIP**, not inside an extra enclosing folder.

## 5. Make a conversation appear only in the right situation

Add a `requirements` array to the **top level** of the event, alongside `trigger` and `presentation`. For example:

```json
"requirements": [
  { "type": "mca:weather", "value": "rain" },
  { "type": "mca:hearts", "min": 20 }
]
```

Now the event appears only when **both** requirements match: the weather is rain and this villager has at least 20 hearts for the player. Remove the array to return to an always-eligible starter conversation.

Some other useful requirements:

| Condition | Example | Meaning |
| --- | --- | --- |
| `mca:time` | `{"type":"mca:time","value":"night"}` | Only at night |
| `mca:personality` | `{"type":"mca:personality","value":"crabby"}` | Only for crabby villagers |
| `mca:profession` | `{"type":"mca:profession","value":"minecraft:farmer"}` | Only for farmers |
| `mca:biome` | `{"type":"mca:biome","value":"minecraft:plains"}` | Only when the villager is in plains |
| `mca:hearts` | `{"type":"mca:hearts","min":20}` | At least 20 hearts with this player |

You can also put `requirements` on an individual **choice** or **outcome**. This hides or excludes only that reply or outcome, rather than the whole event.

For all available condition types and exact supported values, see the [developer guide](dialogue-system-guide.md).

## 6. Pick how the topic appears

Change `presentation.mode` to choose where the event appears:

| Mode | Where the conversation appears | Good for |
| --- | --- | --- |
| `ask` | Selectable topic under **Ask about...** | Ordinary topics and questions |
| `highlighted` | Featured conversation; other eligible highlighted stories can still appear in Ask | Something important happening right now |
| `ambient` | Candidate for **What's on your mind?** | Small talk the villager starts |

`ask` and `highlighted` require a `prompt`. `ambient` does not need a `prompt`, but **every mode requires a `resume_prompt`** so unfinished conversations have a recognizable continuation.

Optional `priority` (default `0`) decides which eligible highlighted story comes first and which ambient priority tier can be picked. Optional positive `weight` (default `1`) controls random selection **among eligible ambient events at the same highest priority**. Changing weight does not override requirements.

Tip: start with `ask` while testing. It's easier to verify than a random ambient conversation.

## 7. Add more lines, replies and outcomes

Replace a node's single `line` with `lines` when you want the villager to speak in several consecutive passages:

```json
"lines": [
  "dialogue_event.villager_stories.story.first",
  "dialogue_event.villager_stories.story.second"
]
```

Add translations for **both** keys. After those lines, the node still needs exactly one continuation: `next`, `choices`, `complete: true`, or `end: true`.

If one player reply should sometimes go well and sometimes badly, give it weighted `outcomes` instead of a direct `next` and direct `actions`:

```json
{
  "id": "tell_joke",
  "text": "dialogue_event.villager_stories.story.joke",
  "outcomes": [
    {
      "weight": 3,
      "actions": [{ "type": "mca:hearts", "amount": 2 }],
      "next": "laughs"
    },
    {
      "weight": 1,
      "next": "does_not_laugh"
    }
  ]
}
```

That snippet is **one choice inside a node's `choices` array**. You must also create the `laughs` and `does_not_laugh` nodes and add all referenced translations. The 3-to-1 weights are relative chances among eligible outcomes.

Keep these limits in mind: at most **32 replies per node**, unique choice IDs of at most **96 characters** per event, valid `next` destinations, and **no circular node paths**.

## 8. Rewards, repeat rules and follow-up stories

Choices or their selected outcomes can apply built-in actions such as:

```json
{ "type": "mca:hearts", "amount": 3 }
{ "type": "mca:mood", "amount": -2 }
{ "type": "mca:remember", "id": "heard_about_storm", "var": "player" }
```

Place actions inside an `actions` array on the choice or outcome. These are separate examples, **not** one valid JSON document. `mca:command` also exists, but accepts only certain MCA interaction commands, not arbitrary `/` commands. See the developer guide before using it.

Every event needs a `repeat` policy:

| Policy | Example | Effect |
| --- | --- | --- |
| Always | `{"type":"always"}` | No repeat restriction after a successful run |
| Once | `{"type":"once"}` | Can complete once per player/villager pair |
| Cooldown | `{"type":"cooldown","seconds":5}` | Available again after a successful run and the specified delay |

With `history: "story"`, MCA saves the completed event and **which reply IDs were picked** for that player and villager. Another event can require the starter conversation's friendly reply:

```json
"requirements": [
  {
    "type": "mca:event_completed",
    "event": "villager_stories:hello_neighbor"
  },
  {
    "type": "mca:event_choice",
    "event": "villager_stories:hello_neighbor",
    "choice": "friendly"
  }
]
```

Use these requirements in a **separate new event file** with its own `presentation`, `repeat`, `start`, `nodes`, and translation keys. The follow-up becomes available after the first event **successfully finishes**; it does not start automatically.

For throwaway small talk with a cooldown, `history: "scheduling"` is an option, but those events cannot be referenced by `event_completed` or `event_choice`.

**Rewards and story history are committed only when the player acknowledges the final `complete: true` line with Back to topics.** Walking away, closing the menu or reaching an `end: true` node does not count as a successful completion.

## 9. Why isn't my dialogue showing up?

Check these common problems in order:

1. **Wrong folder or namespace:** The server event must be in `data/<namespace>/dialogue_events/*.json`. `dialogues/` is the old format, not the new `dialogue_events/` directory.
2. **Pack isn't enabled:** Check `/datapack list`, then `/reload`. On a server, edit the server world's datapack, not just your local client.
3. **The event was rejected:** Check the server log for `Dialogue event <id> was not loaded`. The loader rejects unknown fields, bad conditions, missing nodes, invalid branches and other schema errors.
4. **Conditions don't match:** Temporarily remove `requirements` and use `mode: "ask"`. Check villager availability, hearts, personality, weather and time as appropriate.
5. **Text displays as a key:** Enable the separate client resource pack and check the exact spelling of the translation key in `en_us.json`. Try F3+T.
6. **The story is unavailable again:** Check `repeat`, a previous completion, or whether you have a paused run with that villager.
7. **The reward doesn't happen:** Finish at `complete: true` and click **Back to topics**. Verify the action is on the selected choice or outcome.

For examples shipped with MCA, inspect `common/src/main/resources/data/mca/dialogue_events/` in the source repository. Good starting points are `social/joke.json` (choices and reactions), `ambient/crabby_night.json` (context-sensitive small talk), and `personal/cured_zombies_followup.json` (a story that checks an earlier reply).

For every supported condition, action, and field, read the [DialogueEvent developer guide](dialogue-system-guide.md). For additional writing patterns and templates, see the [DialogueEvent authoring guide](dialogue-authoring.md). Both remain separate from this getting-started guide.

## Addon-defined conditions and actions (Java mods)

Datapacks can author arbitrary events, graphs, requirements, choices and effects using **registered** condition/action types. A datapack alone cannot execute new Java behavior. A Fabric or NeoForge addon can register new namespaced types from its common mod initialization code, **before the server loads dialogue datapacks**:

```java
import com.mojang.serialization.Codec;
import net.conczin.mca.dialogue.DialogueAction;
import net.conczin.mca.dialogue.DialogueCondition;
import net.minecraft.resources.ResourceLocation;

DialogueCondition.register(
        ResourceLocation.fromNamespaceAndPath("myaddon", "min_experience"),
        Codec.INT.fieldOf("levels").codec(),
        (levels, context) -> context.player().experienceLevel >= levels
                ? DialogueCondition.Evaluation.MATCH
                : DialogueCondition.Evaluation.NO_MATCH
);
DialogueAction.register(
        ResourceLocation.fromNamespaceAndPath("myaddon", "give_experience"),
        Codec.INT.fieldOf("levels").codec(),
        (levels, context) -> context.player().giveExperienceLevels(levels)
);
```

Reference those types in `data/myaddon/dialogue_events/my_story.json` (only the relevant fragments are shown):

```json
"requirements": [{ "type": "myaddon:min_experience", "levels": 5 }],
"actions": [{ "type": "myaddon:give_experience", "levels": 1 }]
```

The codec decodes **all fields except `type`** and must encode a JSON object. It is responsible for validating its own parameters. Conditions return `MATCH`, `NO_MATCH`, or `UNAVAILABLE` (for unavailable dependencies); `mca:not` does not invert `UNAVAILABLE` into a match. Addon condition types work in event, choice and outcome requirements, and addon actions work in choices and outcomes. Addon action executors run only on the **logical server** when the conversation's successful completion is committed; effects should be deterministic and avoid throwing after modifying world state.

Registrations are process-wide and are not removed on `/reload`. Duplicate IDs, including MCA built-in IDs, throw during registration. Unregistered types and invalid parameter payloads cause the referencing dialogue event to fail decoding, with the event ID and reason in the server log. Install the implementing addon on the server before enabling the datapack; this is a new API and does not offer adapters for the removed legacy `Actions.register` or `GiftPredicate.register` dialogue formats.
