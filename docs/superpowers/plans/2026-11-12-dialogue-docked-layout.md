# Dialogue Docked Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan. Steps use checkbox syntax for tracking.

**Goal:** Keep the docked dialogue readable alongside the inspector at larger Minecraft GUI scales.

**Architecture:** Let `DialogueOverlayLayout` compute one horizontal column for the docked panels. Use that same width for measuring text, laying out the scroll viewports, rendering, and input. `InteractScreen` owns the inspector's rendered labels and presentation controls.

**Tech Stack:** Minecraft 1.21.1, Java 21, JUnit, Fabric and NeoForge.

**Spec:** [Dialogue layout at large GUI scales](../specs/2026-11-12-dialogue-docked-layout-design.md)

- [x] Check the centered, shifted and width-limited docked geometry with `DialogueOverlayLayoutTest`. Use screenshot-sized logical dimensions and a tighter case with a wide translated label.
- [x] Measure the persistent inspector labels and pass their right edge into the docked layout. Use the resulting width before wrapping either panel. Verify corner and normal centered layouts still behave as designed.
- [x] Adjust the position toggle, history presentation, category chevrons, decorative overflow indicators, and terminal label. Check the interaction paths for scrolling, toggling, and completion.
- [x] Run the relevant common tests, compile both loaders, and review the final diff. Record client verification separately.

Verification on 2026-11-12 passed `:common:test :fabric:compileJava :neoforge:compileJava`, including 984 common tests with no failures or skips. The list-icon containment guard was removed after the user supplied a scrolling screenshot, and the same verification command passed again. In-game visual acceptance of the final patch remains outstanding.

A fresh read-only review found no blocking issues in inspector padding, shared widths, corner mode, partial icon clipping, or position-toggle placement. The client launched successfully before the icon adjustment, but final visual acceptance was not performed.

## Selected star C

- [x] Update the featured-icon crop to 12 by 12 and run the existing crop-to-texture test to expose the mismatch with the old artwork.
- [x] Draw the selected star in the sprite generator, regenerate the atlas, and confirm the other generated textures are unchanged.
- [x] Run the resource tests and common compilation, then inspect the sprite.

The seven resource tests and common compilation passed for the new star. The user then requested removal of unused sprites.

- [x] Trace all icon callers and identify the unused atlas speech bubble and heart.
- [x] Update existing resource tests for the reduced atlas and observe failure on the old atlas.
- [x] Regenerate the 64 by 16 atlas with book, scroll, dots and star. Run the resource tests and compilation.

The seven resource tests and Fabric/NeoForge compilation passed with the reduced atlas. Sprite pixels were inspected, but appearance in a running game remains unverified.
