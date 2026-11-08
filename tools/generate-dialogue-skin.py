"""Generate small, deterministic pixel-art sprites for MCA's Kai-style Talk overlay.

Run from the repository root: python tools/generate-dialogue-skin.py
The generator is kept beside the committed PNGs so colours and borders remain editable.
"""

from pathlib import Path

from PIL import Image, ImageDraw


OUT = Path(__file__).resolve().parents[1] / "common/src/main/resources/assets/mca/textures/gui/dialogue"


def save(image: Image.Image, name: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / (name + ".png"), format="PNG", optimize=True)


def frame(name: str, interior: str, outside: str, border: str, shine: str) -> None:
    image = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # Stepped silhouette with three border layers. The corner artwork stays
    # pixel-sized while the nine-slice renderer stretches only the straight edges.
    # One symmetric rounded-rectangle primitive prevents hand-authored corners
    # from acquiring different step profiles on the left and right.
    draw.rounded_rectangle((0, 0, 23, 23), radius=5, fill=outside)
    draw.rounded_rectangle((2, 2, 21, 21), radius=4, fill=border)
    draw.rounded_rectangle((4, 4, 19, 19), radius=3, fill=interior)
    draw.line((5, 3, 18, 3), fill=shine, width=1)
    draw.line((3, 5, 3, 18), fill=shine, width=1)
    draw.line((5, 20, 18, 20), fill=outside, width=1)
    draw.line((20, 5, 20, 18), fill=outside, width=1)
    # Light scuffs make the parchment and wood frames less like plain rectangles.
    if name == "paper_frame":
        for x, y in ((5, 5), (17, 6), (6, 17), (18, 16)):
            draw.point((x, y), fill="#FFF4D9")
    else:
        for x, y in ((5, 5), (17, 5), (5, 18), (18, 17)):
            draw.point((x, y), fill="#BA8A61")
    # Match visible corner colours and highlights as well as the alpha silhouette.
    # Lighting may differ top-to-bottom, but left/right must be exact reflections.
    image.paste(image.crop((0, 0, 12, 24)).transpose(Image.Transpose.FLIP_LEFT_RIGHT), (12, 0))
    save(image, name)


def tail() -> None:
    image = Image.new("RGBA", (16, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    # The reference has a left-facing point near the lower edge of the bubble.
    # The rightmost edge sits beneath the parchment frame's left border.
    d.polygon(((15, 0), (15, 19), (0, 16), (9, 8)), fill="#291910")
    d.polygon(((14, 2), (14, 17), (4, 15), (11, 8)), fill="#FAE8C9")
    d.line((5, 15, 13, 17), fill="#FFE6B5", width=1)
    save(image, "paper_tail")


def icons() -> None:
    image = Image.new("RGBA", (96, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    ink = "#E7D6BF"
    shadow = "#563929"

    # Speech bubble.
    d.rectangle((2, 2, 12, 10), outline=shadow, fill=ink, width=1)
    d.polygon(((3, 10), (3, 13), (6, 10)), fill=ink)
    for x in (5, 8, 11):
        d.point((x, 6), fill=shadow)

    # Heart.
    d.polygon(((16 + 2, 5), (16 + 4, 2), (16 + 7, 3), (16 + 9, 2),
               (16 + 13, 5), (16 + 12, 9), (16 + 7, 14), (16 + 3, 10)),
              fill="#EF9298", outline=shadow, width=1)

    # Open book, personal stories.
    d.polygon(((34, 3), (39, 4), (40, 13), (34, 11)), fill=ink, outline=shadow)
    d.polygon(((40, 4), (45, 3), (45, 11), (40, 13)), fill=ink, outline=shadow)
    d.line((40, 4, 40, 13), fill=shadow)

    # Scroll / practical matters.
    d.rectangle((51, 3, 60, 13), fill=ink, outline=shadow, width=1)
    d.line((53, 6, 58, 6), fill=shadow)
    d.line((53, 9, 58, 9), fill=shadow)

    # More / other topics.
    for x in (67, 72, 77):
        d.rectangle((x, 7, x + 2, 9), fill=ink)

    # Star for the current featured story.
    d.polygon(((88, 1), (90, 6), (95, 6), (91, 10), (92, 15),
               (88, 12), (84, 15), (85, 10), (81, 6), (86, 6)),
              fill="#FFD66C", outline=shadow)
    save(image, "icons")


if __name__ == "__main__":
    frame("paper_frame", "#FAE8C9", "#291910", "#B58051", "#FFE6B5")
    frame("menu_frame", "#2B1C18", "#120B09", "#78503B", "#AA7957")
    frame("featured_frame", "#694632", "#281810", "#D4A36B", "#F6CD8F")
    tail()
    icons()
