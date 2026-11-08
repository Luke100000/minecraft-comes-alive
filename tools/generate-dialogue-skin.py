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
    image = Image.new("RGBA", (24, 24), interior)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 23, 23), outline=outside, width=2)
    draw.rectangle((2, 2, 21, 21), outline=border, width=2)
    draw.line((4, 4, 19, 4), fill=shine, width=1)
    draw.line((4, 4, 4, 19), fill=shine, width=1)
    draw.line((5, 21, 20, 21), fill=outside, width=1)
    # A little stepped corner detail, without affecting the stretchable centre.
    for x, y in ((4, 4), (19, 4), (4, 19), (19, 19)):
        draw.point((x, y), fill=outside)
    save(image, name)


def tail() -> None:
    image = Image.new("RGBA", (16, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    d.polygon(((1, 0), (15, 0), (1, 19)), fill="#382820", outline="#382820", width=1)
    d.polygon(((4, 0), (12, 0), (4, 14)), fill="#D9B780")
    d.line((5, 1, 5, 10), fill="#F5DB9C", width=1)
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
    frame("paper_frame", "#E8CB99", "#34261F", "#A8794D", "#FFDFAB")
    frame("menu_frame", "#2C1D19", "#160F0D", "#765039", "#94684A")
    frame("featured_frame", "#694632", "#281810", "#D4A36B", "#F6CD8F")
    tail()
    icons()
