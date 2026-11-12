# Simple Rich Dialogue Text Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add colour, bold/italic/underline, localized hover and click-to-copy to normal MCA dialogue, and make Rumours coordinates teal and clickable without changing dialogue schemas or packets.

**Architecture:** Interpret a small, allowlisted tag set in existing client-side dialogue Components after translation. Attach vanilla `COPY_TO_CLIPBOARD` and `SHOW_TEXT` styles to the known structure-location resolver argument, then ask Minecraft's font splitter for the style under the cursor in MCA's scaled, wrapped, scrollable text. No new event records, codecs, custom packet fields or dependencies.

**Tech Stack:** Java 21, Minecraft 1.21.1, MCA common source, Fabric Loader 0.20.1, NeoForge 21.1.265, vanilla `Component` / `Style` / `StringSplitter`, JUnit Jupiter.

**Spec:** `docs/superpowers/specs/2026-11-11-dialogue-rich-text-design.md`

## Global Constraints

- Work in the existing `dev/1.21.1-dialogue-system-b` development branch/worktree; do not create a worktree/branch, merge, reset, rewrite other active dialogue work, or stage unrelated changes. Read each changed file before editing because this checkout is shared and dirty.
- Existing datapack `line`, `lines`, `choices.text`, `presentation.prompt`, `resume_prompt`, async result and network codecs **stay unchanged**.
- Interpret only balanced, nested, supported tags in MCA dialogue text: vanilla named colours, `<color:#RRGGBB>`, `<bold>`, `<italic>`, `<underlined>`, `<hover:translation.key>`, `<copy>`, `<copy_coords>`; never commands, URLs or file actions. Preserve unformatted text, existing `Style`, and localized placeholder order. Unknown/malformed tags remain visible literal text. Escaped `\\<` is literal; max 16 nesting and 16,384 UTF-16 code units.
- Copy text from its *actual styled segment*, not by finding `X/Y/Z` words in the complete translated sentence. `<copy_coords>` and the built-in resolver require valid integer triplets; display `x,y,z`, copy `x y z`.
- Built-in structure-location coordinate Component: `#237B80`, underline, localized `gui.dialogue.copy_coordinates` tooltip, vanilla `COPY_TO_CLIPBOARD`. Keep the original `%2$s` location in any variant and language unchanged.
- Preserve typewriter reveal, history, scrolling, GUI scale/scissor and choice selection. Each copyable speech phrase gains tooltip/copy interaction immediately after **that phrase** is fully visible, even while later words reveal. A partially visible phrase retains its formatting but not its copy/tooltip actions. Clicks elsewhere in speech reveal the line as before. Fully visible choices and completed history remain immediately interactive. No additional chat HUD message.
- This plan is not implementation authorization by itself; execution and verification follow after user confirmation.

## Review Focus

1. A resource-pack override with literal angle brackets must display normally unless it has a recognized, balanced tag — Task 1 parser test.
2. A localization reorder of `%1$s` and `%2$s` must retain an already-styled coordinate argument — Tasks 1 and 2 tests.
3. A malformed or malicious `<copy>` / `<hover>` tag must never create a click action outside its own span — Task 1 malformed test.
4. A clipped glyph is never clickable; a fully visible coordinate span also cannot be hovered/copied while **later speech in the same line** is still revealing. First click reveals the rest without copying; next click can copy — Task 3 reveal-gate and hit-test tests.
5. Clicking a `<copy>` span inside a choice row must not submit the choice — Task 3 precedence test.

---

### Task 1: Parse Safe Colour and Interaction Tags in Existing Client Dialogue

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/DialogueRichText.java`
- Create: `common/src/main/java/net/conczin/mca/dialogue/DialogueCoordinates.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java:467-476,577-665`
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueRichTextTest.java`
- Test: `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java`

**Interfaces:**
- Produce `public static Component DialogueRichText.parse(Component source)` and `public static Optional<String> DialogueCoordinates.copyValue(String text)`. The second class is a small, common-side pure parser that validates a three-integer coordinate tuple and returns space-separated `x y z` for clipboard use; it is reused by Task 2.
- `parse` consumes an already-localized or lazily translatable `Component`, visits styled segments, and creates native styled `Component` spans. Parse only matched allowlisted tags; preserve existing styles/events on argument Components. `<copy>` copies the visible enclosed text; `<hover:key>` shows `Component.translatable(key)`; `<copy_coords>` copies validated `x y z`.
- In `InteractScreen.resolveDialogueLine(Component, boolean)`, parse the transformed speech before the presentation freezes it for grapheme reveal/history. In `dialogueRows(...)`, parse choice/prompt Components before they are measured/wrapped/rendered. Don't alter the network payload types.

- [ ] **Step 1: Write red tests.** `DialogueRichTextTest`: `<gold><bold>Hi</bold></gold>` produces bold gold `Hi`; `<color:#56C8C2><italic>lovely</italic></color>` produces teal italic `lovely`; `<copy><hover:gui.dialogue.copy>treasure</hover></copy>` creates `COPY_TO_CLIPBOARD("treasure")` and `SHOW_TEXT`; `<copy_coords>234,70,-980</copy_coords>` copies `234 70 -980` without changing display; invalid triplet has no click; old plain dialogue and `\\<gold>` remain literal; nested placeholders retain an existing `Style` even when reordered; malformed tags/unknown actions/deep nesting remain readable/non-executable. `DialoguePresentationTest`: a styled component passed via the existing resolver keeps style in visible grapheme prefixes and history.
- [ ] **Step 2: Run failing tests:** `.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.DialogueRichTextTest --tests net.conczin.mca.network.DialoguePresentationTest --console=plain`; expect FAIL for new coverage.
- [ ] **Step 3: Implement `DialogueCoordinates.copyValue`, `DialogueRichText.parse` and integrate only the two existing client call sites.** Use a single linear scanner/stack and retain translation argument `Style`; do not add any new serialization layer.
- [ ] **Step 4: Rerun the same tests; expect PASS.**
- [ ] **Step 5: Review task-owned hunks and commit only them:** `git commit -m "feat(dialogue): format localized text with safe tags"`.

### Task 2: Make Known Structure Coordinates a Styled Component

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java:1198-1232`
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueAsyncLifecycleTest.java`
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueTextCoordinatesTest.java`

**Interfaces:**
- Produce package-private `static Component DialogueEngine.styledStructureCoordinates(String value)`. On valid three-integer coordinates, return visible original text with `TextColor.fromRgb(0x237B80)`, `underlined=true`, `HoverEvent.Action.SHOW_TEXT(Component.translatable("gui.dialogue.copy_coordinates"))`, and `ClickEvent.Action.COPY_TO_CLIPBOARD` using space-separated integers. For invalid text, return a plain literal with no interaction.
- Inside `DialogueEngine.finishPending`, when the successful resolver is `DialogueAsyncResolver.StructureLocation`, replace *only its first* resolved argument with the styled `Component` before the existing `villager.getTranslatable(player, translationKey, args...)` call; keep all other arguments and failure cases intact. Reuse `DialogueCoordinates.copyValue` from Task 1, and keep all vanilla packets unchanged.

- [ ] **Step 1: Write failing tests.** `styledStructureCoordinates("234,70,-980")` displays `234,70,-980`, clicks copy `234 70 -980`, is `#237B80` and underlined, and has localized tooltip; invalid coordinates are plain/nonclickable. Async lifecycle test ensures a found/failed resolver still preserves the current line key, pending completion and session token, and never applies copy to player-name argument.
- [ ] **Step 2: Run red tests:** `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.DialogueTextCoordinatesTest --tests net.conczin.mca.dialogue.DialogueAsyncLifecycleTest --console=plain`; expect new test FAIL.
- [ ] **Step 3: Implement the narrow argument wrapper and keep `DialogueAsyncResolver.Result` / existing codecs untouched.** Use a strict integer-triplet parser, not regex matching against localized prose.
- [ ] **Step 4: Rerun tests; expect PASS.**
- [ ] **Step 5: Review/commit only Task 2 hunks:** `git commit -m "feat(dialogue): make rumour coordinates copyable"`.

### Task 3: Hover and Click the Actual Rendered Text

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/DialogueTextHitTest.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java:187-240,775-810,1002-1090`
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueOverlayLayoutTest.java`
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueTextHitTestTest.java` (new)

**Interfaces:**
- Produce `static Optional<DialogueTextHitTest.Position> DialogueTextHitTest.locate(double mouseX, double mouseY, int originX, int originY, float scale, int lineHeight, int scrollY, int lineCount, DialogueOverlayLayout.Rect clip)`; `record Position(int lineIndex, int unscaledX)`. This tests the geometry without a Minecraft `Font` object.
- `InteractScreen.dialogueTextStyleAt(double mouseX, double mouseY)` uses the **exact same** `font.split` lines, origin, scale, scissor and scroll as rendering; invoke `locate` then `font.getSplitter().componentStyleAtWidth(FormattedCharSequence, int)` for glyph lookup, and reject positions beyond rendered glyph width. Before drawing speech, compare full and revealed styled Components and remove copy/hover styles only from a partially revealed copy span. Completed spans can be copied immediately. Include choice row label geometry and preserve row/scrollbar action precedence; history lines are already complete.

- [ ] **Step 1: Write red geometry tests** for `DialogueTextHitTest.locate` at two GUI scales, wrapped-line index, scrolling and clipping (outside text viewport returns empty). Test that an unfinished coordinate or generic copy phrase has no action, a completed phrase copies while the remaining sentence reveals, and choices still use click-to-select unless their clicked glyph explicitly has a copy style. The actual font-style lookup needs interactive verification in Step 4.
- [ ] **Step 2: Run red tests:** `.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.DialogueTextHitTestTest --tests net.conczin.mca.client.gui.DialogueOverlayLayoutTest --console=plain`.
- [ ] **Step 3: Implement `DialogueTextHitTest.locate` and screen hover tooltip/copy-click dispatch.** Determine action readiness for each speech copy span from the currently visible prefix; suppress a partially visible span's copy and tooltip until its final glyph appears. Keep `revealFully()` for clicks elsewhere in the speech viewport. Allow only existing `SHOW_TEXT` and `COPY_TO_CLIPBOARD`; keep static choice interactions unchanged.
- [ ] **Step 4: Rerun focused tests; expect PASS**, then check in-game hover and clicks before/during/after typewriter reveal, including an early completed coordinate span, a partially drawn span, history review, wrapped/scrolled text and two GUI scales. A click on a complete copy phrase copies immediately; a click elsewhere during animation reveals the line. Compilation is not visual verification.
- [ ] **Step 5: Review/commit only the screen and test hunks:** `git commit -m "feat(dialogue): interact with styled text in parchment"`.

### Task 4: Tooltip Translation, Datapack Guide and Integration Verification

**Files:**
- Modify: `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` (or owning localization generator, if generated)
- Modify: `docs/dialogue-datapack-guide.md`
- Test: `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java`
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueShowcasePackTest.java`

**Interfaces:**
- Add `gui.dialogue.copy_coordinates` translation, for example `"Copy coordinates"`. Document separate resource-pack translations for coloured static text and the `<copy>` / `<hover:key>` tags; no change to `rumors.json` or the 41+ location translation variants is required.

- [ ] **Step 1: Add failing resource tests** for the tooltip translation, a general coloured text sample, legacy unformatted dialogue and existing Rumours keys/structure-location resolver.
- [ ] **Step 2: Run red resource tests:** `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.McaDialogueEventResourcesTest --tests net.conczin.mca.dialogue.DialogueShowcasePackTest --console=plain`.
- [ ] **Step 3: Add the localization key and write concise examples in `docs/dialogue-datapack-guide.md`.** Edit generator/source if resource is generated; don't duplicate 41 variant translations.
- [ ] **Step 4: Run focused tests, then `.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain`; expect PASS or record exact failures.** Manual checks: one general coloured choice, one copyable arbitrary phrase, Rumours coordinates in two translation variants, tooltip/copy value, first click during reveal only completes the line, subsequent click copies, history/review, scrolled text and GUI scaling; both loaders where practical.
- [ ] **Step 5: Review/commit only documentation, localization and resource-test hunks:** `git commit -m "docs(dialogue): explain rich text and copyable coordinates"`.

## Final Review Before Any Merge

- [ ] Confirm no changed event record, codec, packet schema or new dependency. Inspect shared working-tree status before staging.
- [ ] Compare the spec's markup and interaction requirements with parser, argument, screen and resource tests.
- [ ] Request a whole-change review, preserve the development branch as-is and leave merging to the user.

## Execution Handoff

This plan replaces the earlier oversized schema/network proposal with **four small implementation tasks**. It is documentation only; implementation tests, commits and in-game checks are future steps.
