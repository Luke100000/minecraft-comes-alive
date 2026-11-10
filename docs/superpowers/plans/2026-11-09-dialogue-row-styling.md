# Dialogue Row Styling Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans; execute locally in the existing dialogue worktree.

**Goal:** Restore category borders and previous soft-button colours, and clear the standard survival HUD.
**Architecture:** Change ordinary fills/borders in `InteractScreen.drawDialogueRow` and the bottom reserve in `DialogueOverlayLayout.calculate`; preserve featured rendering and derive all input geometry from the resulting menu bounds.
**Tech Stack:** Minecraft 1.21.1, Java 21, GuiGraphics.
**Spec:** `docs/superpowers/specs/2026-11-09-dialogue-row-styling-design.md`

## Constraints and review focus

No TDD, new worktrees, dependencies, or unrelated changes. Preserve clipped highlights, keyboard focus, read-only history, featured frames, and footer behavior. The checkpoint captures all pending work as explicitly requested; new styling stays uncommitted.

## Task 1: Simplify ordinary row rendering

- [x] Restore category resting fill `0xFF362318`, choice fill `0xFF422B1C`, and hover/focus fill `0xFF805032` from `0d500402d`.
- [x] Restore outlines only for category rows; retain existing row bounds, indentation, featured frame, and footer.
- [x] Reserve 44 GUI pixels for hearts and hunger, capped at one third of short screens; update the existing layout assertion. Armour clearance was removed following screenshot feedback.
- [x] Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.DialogueOverlayLayoutTest' --console=plain --no-configuration-cache --max-workers=1` and `git diff --check`; review the final diff. In-game appearance remains a manual check.

Checkpoint: `3e56f7639`. Earlier unboxed styling compiled and passed the diff check, then was revised following the user's screenshot feedback. No new styling tests or TDD; the existing geometry test is adjusted for the new HUD clearance.

Revised verification: focused layout tests and Java compilation passed (`BUILD SUCCESSFUL in 30s`); diff check passed. Source review confirms the earlier background values are restored, borders apply only to categories, and footer/featured rendering is preserved. No in-game verification performed.

## Footer frame correction

- [x] Inspect the screenshot, footer bounds, texture generator, and PNG pixels. The interior is uniformly `#21120D`; footer overlays reach into the four-pixel bottom border.
- [x] End footer action bounds and the derived centre divider at `menu.bottom() - 3`, the visible brown stroke; update the existing footer geometry assertion.
- [x] Run focused layout/resource tests and diff checks; final appearance still needs an in-game check.

Footer verification: compilation and both focused test classes passed (`BUILD SUCCESSFUL in 29s`); diff check passed. Pixel inspection confirmed all 100 centre pixels in `menu_frame.png` are opaque `#21120D`. No texture regeneration needed.

Follow-up screenshot showed a remaining gap: the previous four-pixel inset preserved an inner shadow at texture row 20. The visible brown stroke is row 21 (three pixels from the edge). Screenshot pixels confirm the hover/divider stop before a three-screen-pixel shadow band, then meet the brown stroke. Extend overlays through the shadow, preserving the stroke; add a resource test relating footer bounds to the actual texture colours.

- [x] Verify corrected footer alignment with focused layout/resource tests, compilation, and diff checks. Final in-game confirmation remains outstanding.

Corrected verification: compilation and both focused classes passed (`BUILD SUCCESSFUL in 11s`). Texture inspection demonstrates inset 4 exposes shadow row 20, inset 1 overwrites the brown stroke, and inset 3 meets brown row 21 exactly. Diff check passed.

## Hearts and hunger placement

User requested placement just above hearts while ignoring armour. Use a constant 44 GUI-pixel bottom reserve (39 for the HUD row plus a five-pixel gap), without dialogue content scaling. The docked speech panel follows the existing menu geometry. Update the existing positioning assertion; no TDD or new tests.

- [x] Run the focused layout class and diff check; in-game appearance remains unverified.

Placement verification: Java compilation and focused layout tests passed (`BUILD SUCCESSFUL in 17s`); diff check passed. Changes remain uncommitted.
