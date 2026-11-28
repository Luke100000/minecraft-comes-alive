package net.conczin.mca;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.UUID;

public final class FamilyTreeTestSupport {
    private FamilyTreeTestSupport() {
    }

    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    public static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
