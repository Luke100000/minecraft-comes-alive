# Dialogue layout at large GUI scales

Keep the inspector visible and honor Minecraft's GUI scale. Docked speech and choices share a horizontal position and width. Keep them centered where the labels fit, then shift and narrow the pair as needed. Give long inspector labels a maximum width so they cannot consume the choices column. Show the full label on hover.

Collapsing the inspector into a button would give speech more width, but would add a state and a click to see villager details. The approved mockup keeps the inspector visible. The existing corner position remains available.

Keep the existing scrolling and completion flow. Remove the decorative scroll arrows, make category chevrons easier to see, replace the menu-like position glyph with a centered position indicator, and make history controls visually secondary. Label terminal acknowledgement “Finish & return to topics”. Greeting and resolver behavior follow the existing flow.

Row icons use the list scissor just like row text and backgrounds. Show the visible portion while an icon crosses the viewport edge, rather than hiding the whole icon until it fits.

The user selected star C from the visual comparison. Replace the featured star with a compact five-point sprite, balanced shorter points, warm gold fill, a pale upper-left highlight and a small lower-right shadow. Draw the artwork on a native 12-pixel grid matching the existing icon slot.

Remove the unused speech and heart cells from the generated atlas. The live screen obtains those two icons from vanilla and MCA respectively. Keep the book, scroll, dots and featured star and update their atlas coordinates together with the renderer.
