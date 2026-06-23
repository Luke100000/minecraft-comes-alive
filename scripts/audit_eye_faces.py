#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FACE_ROOT = ROOT / "common/src/main/resources/assets/mca/skins/face"
VARIANTS = ("normal",)
FACE_FILES = [*(f"{index}.png" for index in range(11)), "blink.png"]
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


def collect_metrics() -> dict[tuple[str, int], Metrics]:
    metrics = {}
    for variant in VARIANTS:
        folder = FACE_ROOT / variant
        for index, filename in enumerate(FACE_FILES):
            path = folder / filename
            if path.exists():
                metrics[(variant, index)] = image_metrics(path)
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


def audit() -> list[str]:
    issues: list[str] = []
    metrics = collect_metrics()
    stale_paths = [
        FACE_ROOT / "zombie",
        FACE_ROOT / "normal" / "11.png",
    ]
    stale_paths.extend(FACE_ROOT.glob("normal/*_left.png"))
    stale_paths.extend(FACE_ROOT.glob("normal/*_right.png"))
    for path in stale_paths:
        if path.exists():
            issues.append(f"stale face asset should be removed: {path.relative_to(ROOT)}")

    for variant in VARIANTS:
        for index, filename in enumerate(FACE_FILES):
            metric = metrics.get((variant, index))
            if not metric:
                issues.append(f"{variant}/{filename}: missing {FACE_ROOT / variant / filename}")
                continue

            if metric.opaque == 0:
                issues.append(f"{variant}/{filename}: empty face texture")
                continue

            if metric.bbox is None:
                issues.append(f"{variant}/{filename}: missing opaque bounds")
                continue
            bounds_width = metric.bbox[2] - metric.bbox[0] + 1
            if bounds_width % 2 != 0:
                issues.append(f"{variant}/{filename}: eye bounds width must be divisible by 2 bbox={metric.bbox}")

            mirror_issues = mirrored_pixel_issues(FACE_ROOT / variant / filename)
            if mirror_issues:
                preview = "; ".join(mirror_issues[:4])
                issues.append(f"{variant}/{filename}: eyes are not mirror-consistent: {preview}")

            if filename == "blink.png":
                blink_height = 0 if metric.bbox is None else metric.bbox[3] - metric.bbox[1] + 1
                if blink_height > 1:
                    issues.append(f"{variant}/{filename}: blink is not closed-height bbox={metric.bbox}")

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
