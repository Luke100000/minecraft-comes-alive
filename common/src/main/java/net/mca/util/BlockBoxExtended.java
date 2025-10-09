package net.mca.util;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class BlockBoxExtended extends BoundingBox {
    public BlockBoxExtended(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public BoundingBox inflatedBy(int margin) {
        return expand(margin, margin, margin);
    }

    public BoundingBox expand(int x, int y, int z) {
        return new BoundingBox(
                minX() - x,
                minY() - y,
                minZ() - z,
                maxX() + x,
                maxY() + y,
                maxZ() + z
        );
    }

    public int getMaxBlockCount() {
        return Math.max(Math.max(getXSpan(), getYSpan()), getZSpan());
    }
}
