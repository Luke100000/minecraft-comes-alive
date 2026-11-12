"""Generate small, deterministic pixel-art sprites for MCA's Kai-style Talk overlay.

Run from the repository root: python tools/generate-dialogue-skin.py
The generator is kept beside the committed PNGs so colours and borders remain editable.
"""

from pathlib import Path

from PIL import Image, ImageDraw


OUT = Path(__file__).resolve().parents[1] / "common/src/main/resources/assets/mca/textures/gui/dialogue"
PAPER_TOP = (255, 243, 206)
PAPER_BOTTOM = (246, 197, 128)


def paper_colour(source_row: float) -> tuple[int, int, int, int]:
    strength = max(0.0, min(1.0, (source_row - 4) / 15))
    return tuple(round(a + (b - a) * strength) for a, b in zip(PAPER_TOP, PAPER_BOTTOM)) + (255,)


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
    draw.line((5, 20, 18, 20), fill=interior if name == "featured_frame" else outside, width=1)
    draw.line((20, 5, 20, 18), fill=outside, width=1)
    # Bake the warm paper gradient into the nine-slice's interior pixels.
    # The source rows then stretch together without a rectangular overlay seam.
    if name == "paper_frame":
        for y in range(4, 20):
            colour = paper_colour(y)
            for x in range(4, 20):
                if image.getpixel((x, y)) == (250, 232, 201, 255):
                    image.putpixel((x, y), colour)
    else:
        for x, y in ((5, 5), (17, 5), (5, 18), (18, 17)):
            draw.point((x, y), fill="#BA8A61")
    # Match visible corner colours and highlights as well as the alpha silhouette.
    # Lighting may differ top-to-bottom, but left/right must be exact reflections.
    image.paste(image.crop((0, 0, 12, 24)).transpose(Image.Transpose.FLIP_LEFT_RIGHT), (12, 0))
    save(image, name)


def tail() -> None:
    image = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    # One joined silhouette: two stepped diagonal borders converge on the
    # left-facing tip. The warm interior extends past the panel's outer stroke
    # into its fill, without the old rectangular renderer-side patch.
    for y in range(2, 19):
        distance = abs(y - 10)
        left = 1 + round(distance * 14 / 8)
        image.putpixel((left, y), (41, 25, 16, 255))
        if left + 1 < 19:
            image.putpixel((left + 1, y), (181, 128, 81, 255))
        # These source rows approximate the lower centre of the nine-slice.
        colour = paper_colour(14 + (y - 2) * 3 / 16)
        for x in range(left + 2, 19):
            image.putpixel((x, y), colour)
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
    frame("paper_frame", "#FAE8C9", "#291910", "#B58051", "#E9C99A")
    frame("menu_frame", "#21120D", "#120B09", "#78503B", "#AA7957")
    frame("featured_frame", "#694632", "#281810", "#D4A36B", "#F6CD8F")
    tail()
    icons()
