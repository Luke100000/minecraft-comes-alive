package net.mca.network.c2s;

import net.mca.Config;
import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.util.WorldUtils;
import net.mca.util.compat.ExtendedFuzzyPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import java.io.Serial;
import java.util.EnumSet;

public class DestinyMessage implements Message {
    @Serial
    private static final long serialVersionUID = -782119062565197963L;

    private final String location;
    private final boolean isClosing;

    public DestinyMessage(String location, boolean isClosing) {
        this.location = location;
        this.isClosing = isClosing;
    }

    public DestinyMessage(String location) {
        this(location, false);
    }

    public DestinyMessage(boolean isClosing) {
        this(null, isClosing);
    }

    @Override
    public void receive(ServerPlayer player) {
        if (isClosing) {
            player.removeEffect(MobEffects.INVISIBILITY);
            player.removeEffect(MobEffects.HEALTH_BOOST);
        }

        if (Config.getInstance().allowDestinyTeleportation && location != null) {
            MCA.executorService.execute(() -> {
                if (location.charAt(0) == '#') {
                    String tagId = location.substring(1);
                    WorldUtils.getClosestStructurePosition(player.serverLevel(), player.blockPosition(), TagKey.create(Registries.STRUCTURE, new ResourceLocation(tagId)), 128).ifPresent(pos -> handleBlockPos(player, pos));
                } else {
                    WorldUtils.getClosestStructurePosition(player.serverLevel(), player.blockPosition(), new ResourceLocation(location), 128).ifPresent(pos -> handleBlockPos(player, pos));
                }
            });
        }
    }


    private void handleBlockPos(ServerPlayer player, BlockPos pos) {
        player.level().getChunkAt(pos);

        if (location.equals("minecraft:ancient_city")) {
            pos = new BlockPos(pos.getX(), -50, pos.getZ());
        } else {
            pos = player.level().getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos);
        }

        pos = RandomPos.moveUpOutOfSolid(pos, player.level().getHeight(), p -> player.level().getBlockState(p).isSuffocating(player.level(), p));
        pos = ExtendedFuzzyPositions.downWhile(pos, 1, p -> !player.level().getBlockState(p.below()).isCollisionShapeFullBlock(player.level(), p));

        ChunkPos chunkPos = new ChunkPos(pos);
        player.serverLevel().getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.getId());
        player.connection.teleport(pos.getX(), pos.getY(), pos.getZ(), player.getYRot(), player.getXRot(), EnumSet.noneOf(RelativeMovement.class));

        //set spawn
        player.setRespawnPosition(player.level().dimension(), pos, 0.0f, true, false);
        if (player.level().getServer() != null && player.level().getServer().isSingleplayerOwner(player.getGameProfile())) {
            player.serverLevel().setDefaultSpawnPos(pos, 0.0f);
        }
    }
}
