# Simple rich text in MCA dialogue

**Status:** Approved for implementation on 2026-11-11. Updated for the user's later correction: individual copy phrases become interactive as soon as fully revealed.

## Intent

Allow datapack/resource-pack authors to colour any villager dialogue, prompt, or player choice and optionally make a phrase hoverable and click-to-copy. Keep existing string translation keys and packets untouched. First bundled example: Rumours coordinates appear in deep teal `#237B80`, underlined, with a localized **Copy coordinates** tooltip, and clicking copies `234 70 -980` while the displayed translation still says `234,70,-980`.

## Approaches

1. Detect coordinate-looking substrings in the rendered sentence: brittle after localization or when similar numbers occur; rejected.
2. Introduce new typed text records, schema codecs and custom packet metadata: flexible but changes several existing owners for a formatting feature; rejected as overengineered.
3. **Chosen: small safe markup in resource-pack language values**, interpreted into native Minecraft `Component` / `Style` objects at existing client dialogue display boundaries, plus a narrow typed style on structure-location resolver arguments. Vanilla `COPY_TO_CLIPBOARD` and `SHOW_TEXT` provide interactions; `StringSplitter.componentStyleAtWidth` identifies a styled glyph under the cursor. No new dependencies, codecs, or packet fields.

## Resource-pack authoring

The datapack continues using the same string keys for `line`, `lines`, choices, prompts and resume prompts. The separately installed resource pack provides localized values containing **optional** markup:

```json
{
  "dialogue_event.example.greeting": "<gold><bold>Good morning!</bold></gold> What a <color:#56C8C2><italic>lovely</italic></color> day.",
  "dialogue_event.example.hint": "Look for <copy><hover:gui.dialogue.copy>the hidden treasure</hover></copy>."
}
```

Supported complete, properly nested tags:

- `<gold>`, `<aqua>`, other vanilla named colours, or `<color:#RRGGBB>`, each with its matching closing tag.
- `<bold>`, `<italic>`, `<underlined>` with matching closing tags.
- `<hover:translation.key>...</hover>`: localized `SHOW_TEXT` tooltip (no custom hover payload).
- `<copy>...</copy>`: copy the visible, localized text inside the span, excluding formatting tags.
- `<copy_coords>...</copy_coords>`: recognize only a validated integer coordinate triplet (`x,y,z` or `x y z`) and copy `x y z`; if invalid, leave the text readable but nonclickable.

There is **no explicit opt-in datapack schema**: use of a recognized balanced formatting tag in a dialogue translation is the opt-in. Unknown/unbalanced tags are shown literally and never execute actions; literal `<` can be escaped as `\\<`. Max 16 nested tags and 16,384 UTF-16 input units. Do not parse free-form commands, URLs, JSON text components, or arbitrary MiniMessage actions. Colour without a copy tag does not add a click target. When nested, preserve pre-existing argument styles.

### Rumours: reuse the resolved argument, not the sentence

The actual built-in Rumours location response picks one of the existing structure-specific keys, e.g. `dialogue.location.igloo/1`, and MCA's translator renders the resolver's coordinate string into `%2$s`. The main English resource pack contains **41 matching location variants**, plus potential overrides from personality packs. Editing every language variant is unnecessary.

When `DialogueEngine.finishPending` receives a successful `DialogueAsyncResolver.StructureLocation` result, wrap **its first result argument only** in `Component.literal(coordinates)` with colour `#237B80`, underline, `SHOW_TEXT(Component.translatable("gui.dialogue.copy_coordinates"))` and `COPY_TO_CLIPBOARD("x y z")`. Pass this styled component to the existing `villager.getTranslatable` call. Do not alter resolver values, graph state, event codecs or packets. Preserve the current wording, chosen variant and `%2$s` positioning in **all locales**. Validate the integer triplet before adding copy; invalid values display as plain text.

For authors wanting similar behavior on *other* translated phrases, `<copy>` and `<copy_coords>` remain available as safe markup. The built-in Rumours case needs no translation markup at all, only a localized tooltip key.

## Minimal integration

- Server-side event records, async resolver contracts and S2C packet formats stay unchanged; one narrow `DialogueEngine.finishPending` change styles a known resolved coordinate argument as a normal vanilla `Component`.
- Client: at `InteractScreen.resolveDialogueLine`, apply the markup parser to the speech component before `DialoguePresentation.resolveOnce` freezes styled spans for grapheme reveal and history. Apply the same parser to ordinary choice/prompt text when creating dialogue rows, not on every draw call. Use `Component.visit` to preserve existing styles on server-supplied coordinate arguments and localized placeholder ordering.
- Client: speech and choice hit testing uses the **same wrapped `FormattedCharSequence`, line positions, text scale, scissor and scroll offsets as rendering**. `font.getSplitter().componentStyleAtWidth(...)` returns the span's `Style`. Hover `SHOW_TEXT` displays a tooltip; clicking `COPY_TO_CLIPBOARD` copies only that span. Copy clicks in choices never submit the choice.
- **Each speech copy span becomes interactive when that complete phrase has appeared.** Compare the full styled line to the visible typewriter prefix; preserve colour/bold/italic/underline but suppress the clipboard action and its hover tooltip on a partially drawn span. A completed coordinate or arbitrary `<copy>` phrase can be copied while later words are still appearing. Clicking elsewhere in the speech viewport still invokes the existing `revealFully()` behavior. Static choices and previously completed history speech remain immediately interactive; choices retain their existing selection behavior.
- Any plain dialogue lacking recognized markup remains visually and behaviorally unchanged. MCA's baby/zombie speech transformations may intentionally replace components with plain text; ensure they never show raw formatting delimiters or expose unexpected actions.

## Verification

Add focused parser tests (colour, nesting, placeholders, copy, malformed tags, limits and legacy text), check styled typewriter/history in `DialoguePresentationTest`, and test screen glyph hit mapping for wrapping, clipping, scrolling, scaling and choice-click precedence. Verify that incomplete copy phrases have no clipboard/tooltip action, completed phrases copy even while later words animate, and clicks outside copy phrases finish the reveal. Confirm the Rumours styled argument is independent of the specific translated location variant, plus a generic coloured phrase. Run common tests and both loader builds at execution time; inspect the actual game UI at more than one GUI scale.

## Scope

Work only in the existing `dev/1.21.1-dialogue-system-b` development worktree. No new worktree or branch, no merge, no unrelated edits. The user subsequently authorized implementation with “Okay lets execute it” and clarified the per-phrase reveal behavior.
