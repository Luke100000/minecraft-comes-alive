package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.util.WorldUtils;
import net.conczin.mca.util.compat.ExtendedFuzzyPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

public record DestinyMessage(String location, boolean isClosing) implements HandleablePayload {
    public static final CustomPacketPayload.Type<DestinyMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("destiny_message"));
    public static final StreamCodec<FriendlyByteBuf, DestinyMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DestinyMessage::location,
            ByteBufCodecs.BOOL, DestinyMessage::isClosing,
            DestinyMessage::new
    );

    @Override
    public void handle(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (isClosing) {
            sp.removeEffect(MobEffects.INVISIBILITY);
            sp.removeEffect(MobEffects.HEALTH_BOOST);
        }
        if (Config.getInstance().allowDestinyTeleportation && !location.isEmpty()) {
            MCA.executorService.execute(() -> {
                if (location.charAt(0) == '#') {
                    String tagId = location.substring(1);
                    WorldUtils.getClosestStructurePosition(sp.serverLevel(), sp.blockPosition(), TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(tagId)), 128).ifPresent(pos -> handleBlockPos(sp, pos));
                } else {
                    WorldUtils.getClosestStructurePosition(sp.serverLevel(), sp.blockPosition(), ResourceLocation.parse(location), 128).ifPresent(pos -> handleBlockPos(sp, pos));
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
        player.setRespawnPosition(player.level().dimension(), pos, 0.0f, true, false);
        //noinspection DataFlowIssue
        if (player.level().getServer().isSingleplayerOwner(player.getGameProfile())) {
            player.serverLevel().setDefaultSpawnPos(pos, 0.0f);
        }
    }

    @Override
    public Type<DestinyMessage> type() {
        return TYPE;
    }
}
