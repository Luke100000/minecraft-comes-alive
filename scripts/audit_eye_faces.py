#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FACE_ROOT = ROOT / "common/src/main/resources/assets/mca/skins/face"
VARIANTS = ("normal", "zombie")
FACE_COUNT = 12
BLINK_INDEX = 2
FACE_MIRROR_SUM = 23
SCLERA_MIN_CHANNEL = 160
SCLERA_MAX_CHANNEL_SPREAD = 32


@dataclass(frozen=True)
class Metrics:
    path: Path
    width: int
    height: int
    opaque: int
    transparent: int
    sclera: int
    iris: int
    bbox: tuple[int, int, int, int] | None


def is_sclera_pixel(alpha: int, red: int, green: int, blue: int) -> bool:
    if alpha == 1:
        return True
    if alpha != 255:
        return False

    minimum = min(red, green, blue)
    maximum = max(red, green, blue)
    return minimum >= SCLERA_MIN_CHANNEL and maximum - minimum <= SCLERA_MAX_CHANNEL_SPREAD


def image_metrics(path: Path) -> Metrics:
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        width, height = rgba.size
        opaque = transparent = sclera = iris = 0
        min_x = min_y = 10_000
        max_x = max_y = -1

        pixels = rgba.get_flattened_data() if hasattr(rgba, "get_flattened_data") else rgba.getdata()
        for position, (red, green, blue, alpha) in enumerate(pixels):
            y, x = divmod(position, width)
            if alpha == 0:
                transparent += 1
                continue

            opaque += 1
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)
            if is_sclera_pixel(alpha, red, green, blue):
                sclera += 1
            else:
                iris += 1

        bbox = None if opaque == 0 else (min_x, min_y, max_x, max_y)
        return Metrics(path, width, height, opaque, transparent, sclera, iris, bbox)


def ratio_delta(left: int, right: int) -> float:
    larger = max(left, right)
    if larger == 0:
        return 0.0
    return abs(left - right) / larger


def collect_metrics() -> dict[tuple[str, int, str], Metrics]:
    metrics = {}
    for variant in VARIANTS:
        folder = FACE_ROOT / variant
        for index in range(FACE_COUNT):
            for suffix in ("", "_left", "_right"):
                key = (variant, index, suffix or "_base")
                path = folder / f"{index}{suffix}.png"
                if path.exists():
                    metrics[key] = image_metrics(path)
    return metrics


def mirrored_pixel_issues(path: Path) -> list[str]:
    issues = []
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        for y in range(rgba.height):
            for x in range(8, 11):
                left_x = FACE_MIRROR_SUM - x
                right_pixel = rgba.getpixel((x, y))
                left_pixel = rgba.getpixel((left_x, y))
                if right_pixel != left_pixel:
                    issues.append(f"({x},{y})={right_pixel} mirror=({left_x},{y})={left_pixel}")
    return issues


def split_mirror_issues(right_path: Path, left_path: Path) -> list[str]:
    issues = []
    with Image.open(right_path) as right_image, Image.open(left_path) as left_image:
        right_rgba = right_image.convert("RGBA")
        left_rgba = left_image.convert("RGBA")
        for y in range(right_rgba.height):
            for x in range(8, 11):
                left_x = FACE_MIRROR_SUM - x
                right_pixel = right_rgba.getpixel((x, y))
                left_pixel = left_rgba.getpixel((left_x, y))
                if right_pixel != left_pixel:
                    issues.append(f"right({x},{y})={right_pixel} left({left_x},{y})={left_pixel}")
    return issues


def audit() -> list[str]:
    issues: list[str] = []
    metrics = collect_metrics()

    for variant in VARIANTS:
        for index in range(FACE_COUNT):
            expected = {
                "_base": FACE_ROOT / variant / f"{index}.png",
                "_left": FACE_ROOT / variant / f"{index}_left.png",
                "_right": FACE_ROOT / variant / f"{index}_right.png",
            }
            for suffix, path in expected.items():
                if (variant, index, suffix) not in metrics:
                    issues.append(f"{variant}/{index}{suffix}: missing {path.relative_to(ROOT)}")

            left = metrics.get((variant, index, "_left"))
            right = metrics.get((variant, index, "_right"))
            if not left or not right:
                continue

            if left.opaque == 0 or right.opaque == 0:
                issues.append(
                    f"{variant}/{index}: empty split side left={left.opaque if left else 'missing'} "
                    f"right={right.opaque if right else 'missing'}"
                )
                continue

            if left.iris == 0 or right.iris == 0:
                issues.append(f"{variant}/{index}: tint layer would be empty left_iris={left.iris} right_iris={right.iris}")

            if left.sclera == 0 and right.sclera > 0 or right.sclera == 0 and left.sclera > 0:
                issues.append(f"{variant}/{index}: one-sided sclera left={left.sclera} right={right.sclera}")

            # Split-eye sprites are allowed to be stylized, but a large coverage mismatch usually
            # means one eye was omitted or copied into the wrong side.
            opaque_delta = ratio_delta(left.opaque, right.opaque)
            iris_delta = ratio_delta(left.iris, right.iris)
            if opaque_delta > 0.60:
                issues.append(f"{variant}/{index}: split coverage mismatch left={left.opaque} right={right.opaque}")
            if iris_delta > 0.75:
                issues.append(f"{variant}/{index}: tint coverage mismatch left_iris={left.iris} right_iris={right.iris}")

            mirror_issues = mirrored_pixel_issues(FACE_ROOT / variant / f"{index}.png")
            if mirror_issues:
                preview = "; ".join(mirror_issues[:4])
                issues.append(f"{variant}/{index}: eyes are not mirror-consistent: {preview}")

            split_issues = split_mirror_issues(
                FACE_ROOT / variant / f"{index}_right.png",
                FACE_ROOT / variant / f"{index}_left.png",
            )
            if split_issues:
                preview = "; ".join(split_issues[:4])
                issues.append(f"{variant}/{index}: split eyes are not mirror-consistent: {preview}")

    for variant in VARIANTS:
        left = metrics.get((variant, BLINK_INDEX, "_left"))
        right = metrics.get((variant, BLINK_INDEX, "_right"))
        if not left or not right:
            continue

        if ratio_delta(left.opaque, right.opaque) > 0.25:
            issues.append(
                f"{variant}/{BLINK_INDEX}: blink is asymmetric left_pixels={left.opaque} right_pixels={right.opaque}"
            )
        if ratio_delta(left.iris, right.iris) > 0.25:
            issues.append(
                f"{variant}/{BLINK_INDEX}: blink tint pixels asymmetric left_iris={left.iris} right_iris={right.iris}"
            )

    return issues


def print_summary(metrics: Iterable[Metrics]) -> None:
    for metric in metrics:
        rel = metric.path.relative_to(ROOT)
        print(
            f"{rel}: opaque={metric.opaque} sclera={metric.sclera} "
            f"iris={metric.iris} bbox={metric.bbox}"
        )


def main() -> int:
    issues = audit()
    if issues:
        print("Eye face audit failed:")
        for issue in issues:
            print(f"- {issue}")
        return 1

    print("Eye face audit passed.")
    metrics = collect_metrics()
    print_summary(metrics[key] for key in sorted(metrics))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
