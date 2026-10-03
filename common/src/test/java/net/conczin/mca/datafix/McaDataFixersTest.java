package net.conczin.mca.datafix;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McaDataFixersTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyMourningSiteDefaultsToOverworld() {
        BlockPos grave = new BlockPos(12, 64, -7);
        CompoundTag mourningSite = new CompoundTag();
        mourningSite.put("value", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, grave).result().orElseThrow());
        mourningSite.putLong("ttl", 123L);

        CompoundTag untouchedMemory = new CompoundTag();
        untouchedMemory.putString("marker", "keep-me");

        CompoundTag memories = new CompoundTag();
        memories.put("mca:mourning_site", mourningSite);
        memories.put("minecraft:home", untouchedMemory);

        CompoundTag brain = new CompoundTag();
        brain.put("memories", memories);

        CompoundTag input = new CompoundTag();
        input.putInt(McaDataFixers.DATA_VERSION_KEY, 1);
        input.put("Brain", brain);

        CompoundTag migrated = McaDataFixers.update(input);

        assertEquals(2, migrated.getInt(McaDataFixers.DATA_VERSION_KEY).orElseThrow());
        CompoundTag migratedMemories = migrated.getCompound("Brain").orElseThrow()
                .getCompound("memories").orElseThrow();
        CompoundTag migratedSite = migratedMemories.getCompound("mca:mourning_site").orElseThrow();
        GlobalPos globalPos = GlobalPos.CODEC
                .parse(NbtOps.INSTANCE, migratedSite.get("value"))
                .result()
                .orElseThrow();

        assertEquals(Level.OVERWORLD, globalPos.dimension());
        assertEquals(grave, globalPos.pos());
        assertEquals(123L, migratedSite.getLong("ttl").orElseThrow());
        assertEquals(untouchedMemory, migratedMemories.getCompound("minecraft:home").orElseThrow());
    }

    @Test
    void alreadyGlobalMourningSiteIsNotWrappedAgain() {
        GlobalPos grave = GlobalPos.of(Level.NETHER, new BlockPos(4, 70, 9));
        CompoundTag mourningSite = new CompoundTag();
        mourningSite.put("value", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, grave).result().orElseThrow());

        CompoundTag memories = new CompoundTag();
        memories.put("mca:mourning_site", mourningSite);

        CompoundTag brain = new CompoundTag();
        brain.put("memories", memories);

        CompoundTag input = new CompoundTag();
        input.putInt(McaDataFixers.DATA_VERSION_KEY, 1);
        input.put("Brain", brain);

        CompoundTag migrated = McaDataFixers.update(input);
        GlobalPos migratedPos = GlobalPos.CODEC
                .parse(NbtOps.INSTANCE, migrated.getCompound("Brain").orElseThrow()
                        .getCompound("memories").orElseThrow()
                        .getCompound("mca:mourning_site").orElseThrow()
                        .get("value"))
                .result()
                .orElseThrow();

        assertEquals(grave, migratedPos);
    }

    @Test
    void malformedMourningSiteIsDroppedWithoutTouchingOtherMemories() {
        CompoundTag mourningSite = new CompoundTag();
        mourningSite.putString("value", "not-a-position");

        CompoundTag untouchedMemory = new CompoundTag();
        untouchedMemory.putString("marker", "keep-me");

        CompoundTag memories = new CompoundTag();
        memories.put("mca:mourning_site", mourningSite);
        memories.put("minecraft:home", untouchedMemory);

        CompoundTag brain = new CompoundTag();
        brain.put("memories", memories);

        CompoundTag input = new CompoundTag();
        input.putInt(McaDataFixers.DATA_VERSION_KEY, 1);
        input.put("Brain", brain);

        CompoundTag migrated = McaDataFixers.update(input);
        CompoundTag migratedMemories = migrated.getCompound("Brain").orElseThrow()
                .getCompound("memories").orElseThrow();

        assertFalse(migratedMemories.contains("mca:mourning_site"));
        assertEquals(untouchedMemory, migratedMemories.getCompound("minecraft:home").orElseThrow());
    }
}
