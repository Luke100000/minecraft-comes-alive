#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path


PRESETS: dict[str, list[tuple[str, str, bool]]] = {
    "resource_location_to_identifier": [
        (r"\bnet\.minecraft\.resources\.ResourceLocation\b", "net.minecraft.resources.Identifier", True),
        (r"\bnet\.minecraft\.ResourceLocationException\b", "net.minecraft.IdentifierException", True),
        (r"\bResourceLocationException\b", "IdentifierException", True),
        (r"\bResourceLocation\b", "Identifier", True),
    ],
    "minecraft_26_1_package_churn": [
        (r"\bnet\.minecraft\.Util\b", "net.minecraft.util.Util", True),
        (r"\bnet\.minecraft\.world\.entity\.MobSpawnType\b", "net.minecraft.world.entity.EntitySpawnReason", True),
        (r"\bnet\.minecraft\.util\.FastColor\b", "net.minecraft.util.ARGB", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.Villager\b", "net.minecraft.world.entity.npc.villager.Villager", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.VillagerData\b", "net.minecraft.world.entity.npc.villager.VillagerData", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.VillagerDataHolder\b", "net.minecraft.world.entity.npc.villager.VillagerDataHolder", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.VillagerProfession\b", "net.minecraft.world.entity.npc.villager.VillagerProfession", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.VillagerType\b", "net.minecraft.world.entity.npc.villager.VillagerType", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.WanderingTrader\b", "net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader", True),
        (r"\bnet\.minecraft\.world\.entity\.monster\.ZombieVillager\b", "net.minecraft.world.entity.monster.zombie.ZombieVillager", True),
        (r"\bnet\.minecraft\.world\.entity\.monster\.Zombie\b", "net.minecraft.world.entity.monster.zombie.Zombie", True),
        (r"\bnet\.minecraft\.world\.entity\.animal\.Sheep\b", "net.minecraft.world.entity.animal.sheep.Sheep", True),
        (r"\bnet\.minecraft\.world\.entity\.animal\.IronGolem\b", "net.minecraft.world.entity.animal.golem.IronGolem", True),
        (r"\bnet\.minecraft\.world\.entity\.animal\.horse\.AbstractHorse\b", "net.minecraft.world.entity.animal.equine.AbstractHorse", True),
        (r"\bnet\.minecraft\.world\.entity\.projectile\.AbstractArrow\b", "net.minecraft.world.entity.projectile.arrow.AbstractArrow", True),
        (r"\bnet\.minecraft\.world\.entity\.npc\.VillagerTrades\b", "net.minecraft.world.item.trading.VillagerTrades", True),
    ],
    "minecraft_26_1_symbol_churn": [
        (r"\bnet\.minecraft\.advancements\.critereon\b", "net.minecraft.advancements.criterion", True),
        (r"\bnet\.minecraft\.client\.renderer\.RenderType\b", "net.minecraft.client.renderer.rendertype.RenderType", True),
        (r"\bnet\.minecraft\.world\.entity\.monster\.AbstractIllager\b", "net.minecraft.world.entity.monster.illager.AbstractIllager", True),
        (r"\bMobSpawnType\b", "EntitySpawnReason", True),
        (r"\bFastColor\b", "ARGB", True),
    ],
    "minecraft_26_1_gui_churn": [
        (r"\bnet\.minecraft\.client\.gui\.GuiGraphics\b", "net.minecraft.client.gui.GuiGraphicsExtractor", True),
        (r"\bGuiGraphics\b", "GuiGraphicsExtractor", True),
        (r"\.drawCenteredString\(", ".centeredText(", True),
        (r"\.drawString\(", ".text(", True),
        (r"\.renderComponentTooltip\(", ".setComponentTooltipForNextFrame(", True),
        (r"\.renderTooltip\(", ".setTooltipForNextFrame(", True),
        (r"\.renderItem\(", ".item(", True),
        (r"\.hLine\(", ".horizontalLine(", True),
        (r"\.vLine\(", ".verticalLine(", True),
        (r"\.renderOutline\(", ".outline(", True),
    ]
}


def apply_rewrites(text: str, rewrites: list[tuple[str, str, bool]]) -> str:
    updated = text
    for pattern, replacement, use_regex in rewrites:
        if use_regex:
            updated = re.sub(pattern, replacement, updated)
        else:
            updated = updated.replace(pattern, replacement)
    return updated


def iter_java_files(paths: list[Path]) -> list[Path]:
    results: list[Path] = []
    for path in paths:
        if path.is_file() and path.suffix == ".java":
            results.append(path)
            continue
        if path.is_dir():
            results.extend(sorted(path.rglob("*.java")))
    return sorted(set(results))


def main() -> int:
    parser = argparse.ArgumentParser(description="Apply mechanical deobfuscated symbol rewrites to Java sources.")
    parser.add_argument(
        "--preset",
        choices=sorted(PRESETS.keys()),
        default="resource_location_to_identifier",
        help="Named rewrite preset to apply.",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="Write changes back to disk. Without this flag the script only reports candidate files.",
    )
    parser.add_argument(
        "paths",
        nargs="+",
        help="Java files or directories to scan.",
    )
    args = parser.parse_args()

    paths = [Path(raw).resolve() for raw in args.paths]
    java_files = iter_java_files(paths)
    rewrites = PRESETS[args.preset]

    changed_files: list[Path] = []
    for file_path in java_files:
        original = file_path.read_text(encoding="utf-8")
        updated = apply_rewrites(original, rewrites)
        if updated == original:
            continue
        changed_files.append(file_path)
        if args.write:
            file_path.write_text(updated, encoding="utf-8")

    for file_path in changed_files:
        print(file_path)

    print(f"changed_files={len(changed_files)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
