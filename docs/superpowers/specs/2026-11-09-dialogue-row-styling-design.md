# Dialogue row styling

Revised in chat after visual feedback: restore category borders and the earlier soft-button backgrounds, leave ordinary choices without outlines, and raise the menu above the survival HUD.

## Approaches

- Minimal: no ordinary resting surfaces; rejected after visual feedback.
- Soft surfaces with category borders (chosen): reuse category `#362318`, choice `#422B1C`, and hover `#805032` backgrounds from commit `0d500402d`. Only category rows receive ordinary outlines.
- Framed: borders around every choice; rejected because the menu feels crowded.

## Scope

Keep the featured gold frame, existing indentation, text layout, hitboxes, dark menu texture, clean footer with full-width hover, and persisted speech-position toggle. Category borders reuse `#71513A` / highlighted `#D7A76B`. Submenu and ordinary dialogue choices have resting backgrounds without outlines or gold side stripes. Read-only history stays unhighlighted.

Reserve 44 GUI pixels below the menu: vanilla hearts and hunger start 39 pixels above the bottom, leaving a five-pixel gap. Ignore armour clearance as requested after screenshot feedback. HUD coordinates do not grow with dialogue content scaling; cap the reserve at one third of very short screens. Docked speech follows the menu automatically.

End footer hover fills and the centre divider at the visible brown bottom stroke, three GUI pixels from the menu edge. The texture has a dark inner shadow pixel immediately above that stroke; cover it to avoid an apparent gap. Preserve the brown stroke and outer outline. The menu texture already has a uniform `#21120D` interior.

Use the existing Minecraft 1.21.1 / Java 21 renderer; no new abstractions or dependencies. Skip TDD as requested. Update the existing layout test's hotbar-only assertion to protect HUD clearance and run that focused class. Compilation and geometry checks do not prove the appearance; it needs an in-game check.
