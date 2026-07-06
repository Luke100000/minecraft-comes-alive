package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import java.util.List;

public record BuildingScanResult(
    Building.validationResult result,
    BlockPos source,
    boolean strictScan,
    Building building,
    List<String> matchingTypes,
    Village village
) {}
