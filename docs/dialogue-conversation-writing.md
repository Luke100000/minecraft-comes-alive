# Writing natural MCA conversations

This guide complements the [DialogueEvent authoring guide](dialogue-authoring.md) and [datapack guide](dialogue-datapack-guide.md). Those documents explain the event format; this one covers what the player and villager say.

## Give the player a real answer

Read every path as a conversation, including the selected player line:

> Villager: "Are you thinking of staying here?"
>
> Player: "I'm still looking around."
>
> Villager: "Fair enough. No need to rush a decision like that."

A reply should address the answer the player chose. If a villager asks a question, provide a choice that answers it. If the answer is a polite refusal or a goodbye, a short ending is enough. Two to four meaningful exchanges work for many optional conversations, but not every greeting needs another question.

Use existing `DialogueEvent` nodes, `next` edges, `choices`, conditions and outcomes. The event graph must remain acyclic. Translation keys ending `/1` or `/2` generally represent alternative wording for a single line, **not** turns in a conversation.

## Keep claims grounded in the game

Villagers can discuss their selected biome, a real village building, known relationship state or a recorded player achievement. They should not claim an inn exists when it does not, or claim to have met the player earlier without recorded history.

A successful structure search needs its actual coordinates. Keep `%2$s` in every matching personality override. Preserve name tokens such as `%Supporter%` or `%supporter%` and any other required arguments. A failed lookup should simply say there was no useful lead; don't invent a place the villager supposedly visited and forgot.

An ordinary question may receive a reserved or irritable response, but not a random friendship penalty. Hurtful choices can have consequences if the event explicitly supports them. In family, command and hiring conversations, leave the eligibility rules, once-only effects and gameplay actions intact.

Only use a previously completed event as history when the session's completion state actually recorded it. A paused or interrupted conversation is not a completed one.

## Let personality affect the tone

A crabby villager can answer briefly; an introverted villager may hesitate; a playful villager may make a pun. Personality shouldn't make every reply a catchphrase or a personal insult. Prefer a detail specific to the place or topic over an announcement about how remarkable it is. A short blunt reply is often more convincing than an elaborate speech.

Humanizer 4.0 helps spot generic openers, inflated language, repetitive sentence shapes and awkward filler. It cannot check branching, conditions, unsupported memory claims or text arguments. Review those separately.

## Review an event as a whole

Follow every reachable player choice from its first line to its outcome. Read conditional paths with their matching village, relationship, personality and player progression states. Check alternate English variants and personality overrides, preserving the meaning of the selected event.

Use the [dialogue review inventory](dialogue-review-inventory.md) to track graph and English-key audit candidates. Static resource tests catch missing nodes, duplicate keys and lost placeholders; read the resulting conversations aloud and do an in-game playtest before declaring the content finished.
