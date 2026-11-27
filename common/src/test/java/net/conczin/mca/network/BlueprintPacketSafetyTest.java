package net.conczin.mca.network;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.conczin.mca.network.c2s.ConfirmBuildingPolymorphMessage;
import net.conczin.mca.network.c2s.RenameVillageMessage;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.ExternalBuilding;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.FloorGeometry;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintPacketSafetyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blueprintRoomEditsCheckTheVillageOutsideThePlayerBorderButWithinMergeRange() throws Exception {
        BlockPos cell = new BlockPos(0, 64, 0);
        var geometry = new FloorGeometry(List.of(new FloorGeometry.Cell(cell, 68)), List.of());
        var floor = new StructureFloor(0, 0, 64, geometry);
        Building room = new Building(cell);
        room.setId(2);
        room.setStructureId(1);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(cell, cell.above(3), List.of(cell));
        Village village = new Village(42, null);
        village.registerStructure(new Structure(1, cell, List.of(floor)), room);

        var villages = new ListTag();
        villages.add(village.save());
        var tag = new CompoundTag();
        tag.put("villages", villages);
        var constructor = VillageManager.class.getDeclaredConstructor(ServerLevel.class, CompoundTag.class);
        constructor.setAccessible(true);
        VillageManager manager = constructor.newInstance(null, tag);
        var editTarget = ReportBuildingMessage.class.getDeclaredMethod("editTarget",
                VillageManager.class, BlockPos.class, ReportBuildingMessage.Action.class);
        editTarget.setAccessible(true);

        BlockPos source = cell.east(48);
        assertSame(manager.getOrEmpty(42).orElseThrow(),
                editTarget.invoke(null, manager, source, ReportBuildingMessage.Action.ADD_BUILDING));
        assertNull(editTarget.invoke(null, manager, source, ReportBuildingMessage.Action.AUTO_SCAN));
    }

    @Test
    void outOfRangeReportActionVarIntIsRejected() {
        FriendlyByteBuf reportBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            reportBuf.writeVarInt(999);
            reportBuf.writeBoolean(false);
            reportBuf.writeVarInt(-1);

            assertThrows(DecoderException.class, () -> ReportBuildingMessage.STREAM_CODEC.decode(reportBuf));
        } finally {
            reportBuf.release();
        }
    }

    @Test
    void outOfRangeConfirmActionVarIntIsRejected() {
        FriendlyByteBuf confirmBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BlockPos.STREAM_CODEC.encode(confirmBuf, BlockPos.ZERO);
            confirmBuf.writeVarInt(-50);
            confirmBuf.writeVarInt(12);
            confirmBuf.writeUtf("house");

            assertThrows(DecoderException.class,
                    () -> ConfirmBuildingPolymorphMessage.STREAM_CODEC.decode(confirmBuf));
        } finally {
            confirmBuf.release();
        }
    }

    @Test
    void oversizedStringsAreRejectedByBoundedBlueprintCodecs() {
        String over64 = "x".repeat(65);
        FriendlyByteBuf reportBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(EncoderException.class, () -> ReportBuildingMessage.STREAM_CODEC.encode(
                    reportBuf, new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, over64, 1)));

            reportBuf.clear();
            reportBuf.writeVarInt(ReportBuildingMessage.Action.FORCE_TYPE.ordinal());
            reportBuf.writeBoolean(true);
            reportBuf.writeUtf(over64);
            reportBuf.writeVarInt(1);
            assertThrows(DecoderException.class, () -> ReportBuildingMessage.STREAM_CODEC.decode(reportBuf));
        } finally {
            reportBuf.release();
        }

        String over32 = "v".repeat(33);
        FriendlyByteBuf renameBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            renameBuf.writeInt(1);
            renameBuf.writeUtf(over32);
            assertThrows(DecoderException.class, () -> RenameVillageMessage.STREAM_CODEC.decode(renameBuf));
        } finally {
            renameBuf.release();
        }
    }

    @Test
    void externalBuildingCentroidUsesLongAccumulationNearWorldBorder() throws Exception {
        JsonObject graveyardJson = new JsonObject();
        graveyardJson.addProperty("grouped", true);
        graveyardJson.addProperty("margin", 32);
        BuildingTypes.getInstance().setBuildingTypes(Map.of(
                "graveyard", new BuildingType("graveyard", graveyardJson)));

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Level stubLevel = (Level) unsafe.allocateInstance(StubBellLevel.class);

        ExternalBuilding graveyard = new ExternalBuilding(new BlockPos(29_999_980, 70, -29_999_980));
        graveyard.setId(5);
        graveyard.setType("graveyard");

        for (int i = 0; i < 100; i++) {
            graveyard.addPOI(stubLevel, new BlockPos(29_999_900 + (i % 10), 70, -29_999_900 - (i / 10)));
        }

        BlockPos center = graveyard.getCenter();
        assertEquals(29_999_904, center.getX());
        assertEquals(70, center.getY());
        assertEquals(-29_999_904, center.getZ());
    }

    private static final class StubBellLevel extends Level {
        private StubBellLevel() {
            super(null, null, null, null, false, false, 0L, 0);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.BELL.defaultBlockState();
        }

        @Override
        public int getSeaLevel() {
            throw new UnsupportedOperationException("Unexpected sea level lookup in bell-level fixture");
        }

        @Override
        public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
            throw new UnsupportedOperationException("Unexpected world border lookup in bell-level fixture");
        }

        @Override
        public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        }

        @Override
        public void explode(net.minecraft.world.entity.Entity source,
                            net.minecraft.world.damagesource.DamageSource damageSource,
                            net.minecraft.world.level.ExplosionDamageCalculator damageCalculator,
                            double x, double y, double z, float radius, boolean fire,
                            Level.ExplosionInteraction interaction,
                            net.minecraft.core.particles.ParticleOptions smallParticles,
                            net.minecraft.core.particles.ParticleOptions largeParticles,
                            net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> blockParticles,
                            net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
            throw new UnsupportedOperationException("Unexpected explosion in bell-level fixture");
        }

        @Override
        public void playSeededSound(net.minecraft.world.entity.Entity player, double x, double y, double z,
                                    net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                                    net.minecraft.sounds.SoundSource category, float volume, float pitch, long seed) {
        }

        @Override
        public void playSeededSound(net.minecraft.world.entity.Entity player, net.minecraft.world.entity.Entity entity,
                                    net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                                    net.minecraft.sounds.SoundSource category, float volume, float pitch, long seed) {
        }

        @Override
        public String gatherChunkSourceStats() {
            return "";
        }

        @Override
        public net.minecraft.world.entity.Entity getEntity(int id) {
            return null;
        }

        @Override
        public net.minecraft.world.TickRateManager tickRateManager() {
            return null;
        }

        @Override
        public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId id) {
            return null;
        }

        @Override
        public void setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData data) {
        }

        @Override
        public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() {
            return null;
        }

        @Override
        public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
        }

        @Override
        public net.minecraft.world.scores.Scoreboard getScoreboard() {
            return null;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeAccess recipeAccess() {
            return null;
        }

        @Override
        protected net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity> getEntities() {
            return null;
        }

        @Override
        public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
            return null;
        }

        @Override
        public void levelEvent(net.minecraft.world.entity.Entity player, int type, BlockPos pos, int data) {
        }

        @Override
        public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event,
                              net.minecraft.world.phys.Vec3 pos,
                              net.minecraft.world.level.gameevent.GameEvent.Context context) {
        }

        @Override
        public net.minecraft.core.RegistryAccess registryAccess() {
            return null;
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return null;
        }

        @Override
        public List<? extends net.minecraft.world.entity.player.Player> players() {
            return List.of();
        }

        @Override
        public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return null;
        }

        @Override
        public java.util.Collection<net.minecraft.world.entity.boss.enderdragon.EnderDragonPart> dragonParts() {
            return List.of();
        }

        @Override
        public net.minecraft.world.clock.ClockManager clockManager() {
            return null;
        }

        @Override
        public net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes() {
            return null;
        }

        @Override
        public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
            return null;
        }

        @Override
        public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
            return null;
        }
    }
}
