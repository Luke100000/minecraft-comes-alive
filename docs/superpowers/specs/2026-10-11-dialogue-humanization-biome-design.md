# Dialogue consistency, Humanizer review, and biome conversations

**Status:** Proposed design for review. Approval of this specification precedes a separate implementation plan; no gameplay or dialogue implementation is authorized by this document alone.

**Target:** Minecraft Comes Alive 1.21.1, shared Fabric/NeoForge code on `dev/1.21.1-dialogue-system`.

## Purpose and decisions

Restore believable dialogue without losing the villager's established personality or MCA's local flavor. Review the full English dialogue corpus, selectively rewrite only lines with a real problem, and fix conversations whose replies do not follow the player's question. Keep natural, distinctive passages intact. Use the local Humanizer 3.1.0 skill as a writing checklist, not a mechanical word-replacement filter.

The agreed biome experience has two entry points: occasional ambient remarks and a selectable Ask topic about the local area. Village-life remarks use the villager's home-village biome; descriptions of the immediate surroundings use their current biome. Support exact biome IDs, tag selectors beginning with `#`, and multiple alternatives. Prefer exact dialogue over category dialogue, with a generic fallback. Datapacks can extend the categories and add specialized conversations.

Preserve current DialogueEvent gameplay behavior, live event and choice identities, placeholders, heart changes, history, pause/resume and completion semantics, and unrelated uncommitted changes. This is a development-only format cutover: no old dialogue engine, old-schema support or compatibility adapter. Existing translation keys can be replaced or deleted only after verifying the new engine's actual references and dynamic personality fallback.

## Evidence and existing behavior

- The old `dev/1.21.1` branch routed Chat through `data/mca/dialogues/chat.topic.json`, with weighted branches for plains, savanna, taiga, snowy plains, and desert. It has many English personality-specific biome lines.
- The current `data/mca/dialogue_events/social/chat.json` still routes the `village` choice to these legacy pools. Some lines describe scenery rather than answering a question about village life.
- `DialogueCondition` already implements `mca:biome` for one exact ID, evaluated at the villager's current block position; it distinguishes a missing registry entry (`UNAVAILABLE`) from a nonmatch. It does not accept biome tags, selector lists, or home-village scope.
- `DialogueEngine` uses weighted outcome selection in both choice-driven and automatic routes; existing outcomes have no precedence field. Thus a tagged route and an exact route can otherwise compete randomly.
- The earlier Humanizer audit counted 5,605 English localization entries in the base dialogue pack and nine personality packs. Heuristics flagged 144 potentially archaic/formal, 117 filler-heavy, and 159 entries exceeding 220 characters. Flags overlap and do not prove a line is defective. The 47 newly authored Chat/Joke/Story reaction keys currently occur only in the base English dialogue pack, leaving the new reactions largely personality-neutral.

## Biome selector design

Extend the existing `mca:biome` condition instead of adding a parallel condition type. Use one schema for one or many selectors:

```json
{ "type": "mca:biome", "values": ["minecraft:plains"] }
{ "type": "mca:biome", "values": ["minecraft:plains"], "scope": "current" }
```

The expanded form supports one or more IDs and/or tags:

```json
{
  "type": "mca:biome",
  "values": [
    "minecraft:snowy_plains",
    "minecraft:grove",
    "#mca:snowy_biomes"
  ],
  "scope": "home"
}
```

- `values` is required and must be a nonempty array; reject the former single-string `value` field. Each entry is a biome resource location or a `#`-prefixed biome tag location. Match **any** listed selector (OR). Reject malformed selectors, empty values and repeated selector entries during decoding. Migrate all shipped event resources to `values` in the same change.
- `scope` permits `current` (the default) or `home`. `current` reads the villager's actual biome. `home` reads the home village's biome at its authoritative center when safely accessible. If the villager has **no** home village, use the current biome as the agreed fallback, but author the line neutrally so it does not claim a nonexistent home. If a known home exists but its biome cannot be resolved safely without loading chunks or crossing dimensions, do not invent the answer; route to generic dialogue.
- Resolve IDs and tags against the active server biome registry and tag data for each evaluation, so modded biomes and datapack reloads work without a separate cached list. Use the registry's native biome-tag membership API and standard `data/<namespace>/tags/worldgen/biome/*.json` files.
- Missing IDs/tags must not make a negated selector accidentally match. If any alternative matches, return `MATCH`; otherwise, if any selector is unresolved, return `UNAVAILABLE`; return `NO_MATCH` only when all selectors resolve and none matches. Keep the existing `mca:not` handling for `UNAVAILABLE`. A real, empty tag has no matching biome; distinguish it from a tag absent from the registry when possible.
- MCA ships a modest set of category tags for the writing actually provided (for instance `mca:snowy_biomes` and forest/desert equivalents). Tag files can be extended by datapacks; do not infer category membership from a biome's display name or namespace.

### Exact, category, fallback precedence

Add an **optional integer `priority`** to dialogue `Outcome`, default `0`. When selecting either a choice outcome or a hidden automatic-node outcome, filter eligible outcomes to the greatest priority, then apply the existing weighted random selection within that tier. Omitted priorities keep current DialogueEvent content working; this is the new engine's default, not legacy support. Validate the field and preserve the chosen outcome's original index for session history.

In authored biome routing, use explicit priorities such as **20** for exact IDs, **10** for category tags and **0** for the unconditional generic route. A combined `values` condition is one matching group; use separate outcomes when exact-versus-tag precedence matters. This gives datapack authors an explicit override mechanism without teaching the core engine biome-specific precedence rules. A failing or unavailable high-priority selector does not suppress a lower-priority eligible fallback.

Tests must cover the interaction between priorities and personality-weighted outcomes; none of the existing social reaction probabilities should change unless the author explicitly sets priority.

## Conversation ownership and content

### Separate Chat from place descriptions

Keep Chat's day, village and news branches internally consistent. Its village branch should answer a village-life question and should not jump to an unrelated legacy biome remark. Move environmental subjects to a discoverable Ask event, e.g. `social/local_area`, with natural player speech. Give it a generic path for every villager, plus biome-specific passages selected using the condition design above. A village-history/home option may use `scope: home`; a remark about what the player and villager see uses `scope: current`.

Author occasional ambient biome remarks using the existing ambient event format, cooldowns and preview rules. Keep their priority competitive with other everyday ambient entries, so biome remarks do not starve personality, weather, or time-of-day conversations. Avoid forcing a player choice on a one-line observation.

Keep the five established biome themes from the legacy branch but selectively rewrite misleading, stilted, repetitive or anachronistic lines. Additional modded biomes can use the category tags and generic fallback without requiring new Java branches. All lines remain localization keys in the ordinary dialogue asset packs; do not embed English prose into event JSON.

### Humanizer editorial workflow

Review **all 5,605 current English entries** across the base and nine personality dialogue packs. Build a repeatable inventory by namespace, topic, personality and length, with flagged examples; inspect every entry in its dialogue context instead of rewriting from keyword counts. Prioritize event-visible Chat/Joke/Story reactions, introductions, biome pools and known outliers, then review the remaining pool. Leave lines that already sound natural unchanged.

Apply the Humanizer skill's full process: identify artificial patterns, rewrite for the original speaker, and recheck meaning, voice and context. In MCA dialogue, preserve deliberate jokes, strange observations and varying levels of formality when they genuinely suit the speaker. Fix repeated jokes explained back to the player, mirrored Story claims, advertising-like language, excessive `um`/ellipses, forced declarations, overlong exposition and out-of-world references that break immersion. Vary reactions across personalities instead of adding a catchphrase to every line.

Create contextual reactions for the new Chat, Joke and Story keys in the nine existing personality packs where the personality translation scheme supports them. Retain neutral base translations as the fallback. Inspect other built-in personalities and their resolution paths before assuming every personality owns a dedicated pack. Respect age restrictions for romantic content and do not assert unrecorded raids, family events, harvest failures, or personal histories.

When changing locale values, preserve actively used keys, `/1` alternatives, `%` formatting placeholders, escapes and valid JSON. Verify the new engine's direct and dynamic personality/pool references before deleting unused keys. Changes to prompts, choices or line meaning require checking neighboring new-engine branches; there is no old translation contract to maintain. Do not modify other languages or remove referenced translated keys merely because an English sentence changed.

## Scope and delivery

This is one coordinated overhaul delivered as three separable implementation phases, with a review and verification step between them:

1. **Routing and conditions:** implement and test selector lists, biome tags, home/current scope, outcome priority, dedicated local-area Ask topic and ambient selection. Repair context mismatches in Chat.
2. **Humanizer editing:** review the complete English corpus and selectively rewrite problem entries in manageable topic/personality batches. Resolve awkward new Chat/Joke/Story reactions, personality introductions, missing personality-specific reaction lines and legacy biome pools. Keep an inventory of reviewed versus modified entries, reasons and known limitations.
3. **Validation:** resource and parser/codec tests, event graph reachability, missing translation and placeholder checks, biome tag reload behavior, exact/tag/fallback selection, history effects, and relevant regression tests. Finish with Fabric/NeoForge builds and in-game checks of text wrapping, reply sequencing and variation across biomes and personalities. Do not claim an in-game check occurred unless it was run.

One integrator owns merges into the shared base localization file. Authors can work on independent personalities or events, but must not concurrently overwrite a shared JSON resource. Existing worktree changes and the separate parchment UI work remain untouched except for directly relevant dialogue issues.

## Acceptance criteria

1. Chat/Joke/Story and first introductions have coherent question-to-reply relationships across successful and unsuccessful outcomes; resumed prompts remain from the player's perspective where they are selectable player text.
2. The five original biome families remain representable. The new local-area topic and ambient observations use correct home/current contexts and have a generic fallback.
3. Single exact IDs continue working unchanged. Mixed ID/tag arrays use OR matching, tags match modded biome members, missing selectors remain safe under negation, and routing honors exact > category > generic priority.
4. Existing dialogue selection without a new outcome priority produces the same weighted behavior. Story completion, rewards, history, server authority, protocol and UI interactions remain intact.
5. The full English corpus is inventoried and reviewed; changed lines have identifiable editorial reasons. The best natural and personality-distinctive originals survive. New reaction lines gain distinct voice where existing personality-pack behavior supports it.
6. All event graphs and localization keys validate; builds/tests pass or failures are reported precisely. At least one in-game sample per original biome, one unmatched/modded biome, one no-home villager, and representative personality/child-safe paths are checked or explicitly marked unverified.

## Explicit exclusions

No generative runtime dialogue, new NPC memories or simulated lore facts, wholesale rewriting merely to change style, additional translated languages, UI redesign, old-schema compatibility or unrelated Java refactoring. The old dialogue branch can inform writing only; it is not an execution engine or a compatibility target.
