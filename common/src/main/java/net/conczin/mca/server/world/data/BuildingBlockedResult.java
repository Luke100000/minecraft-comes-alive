package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import java.util.Set;

public record BuildingBlockedResult(
    Set<BlockPos> blocked,
    Building existingBuilding,
    Village village
) {}
