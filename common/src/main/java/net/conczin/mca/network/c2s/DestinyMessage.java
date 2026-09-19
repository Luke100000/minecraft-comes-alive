package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.DestinyLocationResolver;
import net.conczin.mca.util.compat.ExtendedFuzzyPositions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record DestinyMessage(Optional<DestinyDestination> destination) implements HandleablePayload {
    public static final CustomPacketPayload.Type<DestinyMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("destiny_message"));
    public static final StreamCodec<FriendlyByteBuf, DestinyMessage> STREAM_CODEC = StreamCodec.composite(
            DestinyDestination.STREAM_CODEC.apply(ByteBufCodecs::optional), DestinyMessage::destination,
            DestinyMessage::new
    );

    public DestinyMessage {
        Objects.requireNonNull(destination, "destination");
    }

    public static DestinyMessage select(DestinyDestination destination) {
        return new DestinyMessage(Optional.of(destination));
    }

    public static DestinyMessage close() {
        return new DestinyMessage(Optional.empty());
    }

    @Override
    public void handle(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (destination.isEmpty()) {
            serverPlayer.removeEffect(MobEffects.INVISIBILITY);
            serverPlayer.removeEffect(MobEffects.HEALTH_BOOST);
            return;
        }

        DestinyDestination selectedDestination = destination.get();
        List<DestinyDestination> allowedDestinations =
                DestinyLocationResolver.getCachedDestinations(serverPlayer.server);
        if (!allowedDestinations.contains(selectedDestination)) {
            notifyDestinationNotFound(serverPlayer);
            return;
        }

        if (!Config.getInstance().allowDestinyTeleportation || selectedDestination.dimension().isEmpty()) {
            return;
        }

        ResourceKey<Level> targetDimension = selectedDestination.dimension().orElseThrow();
        ServerLevel targetLevel = serverPlayer.server.getLevel(targetDimension);
        if (targetLevel == null) {
            notifyDestinationNotFound(serverPlayer);
            return;
        }

        BlockPos searchOrigin = getSearchOrigin(serverPlayer, targetLevel);
        MCA.executorService.execute(() -> {
            Optional<BlockPos> result = DestinyLocationResolver.findNearest(
                    targetLevel,
                    searchOrigin,
                    selectedDestination,
                    128
            );
            result.ifPresentOrElse(
                    pos -> serverPlayer.server.execute(() -> handleBlockPos(
                            serverPlayer,
                            targetLevel,
                            selectedDestination.location(),
                            pos
                    )),
                    () -> notifyDestinationNotFound(serverPlayer)
            );
        });
    }

    private static BlockPos getSearchOrigin(ServerPlayer player, ServerLevel targetLevel) {
        double scale = DimensionType.getTeleportationScale(
                player.serverLevel().dimensionType(),
                targetLevel.dimensionType()
        );
        return targetLevel.getWorldBorder().clampToBounds(
                player.getX() * scale,
                player.getY(),
                player.getZ() * scale
        );
    }

    private static void notifyDestinationNotFound(ServerPlayer player) {
        player.server.execute(() -> player.sendSystemMessage(
                Component.translatable("destiny.teleport.failed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        ));
    }

    private static void handleBlockPos(
            ServerPlayer player,
            ServerLevel targetLevel,
            String location,
            BlockPos pos
    ) {
        targetLevel.getChunkAt(pos);
        if (location.equals("minecraft:ancient_city")) {
            pos = new BlockPos(pos.getX(), -50, pos.getZ());
        } else {
            pos = targetLevel.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos);
        }
        pos = RandomPos.moveUpOutOfSolid(
                pos,
                targetLevel.getHeight(),
                candidate -> targetLevel.getBlockState(candidate).isSuffocating(targetLevel, candidate)
        );
        pos = ExtendedFuzzyPositions.downWhile(
                pos,
                1,
                candidate -> !targetLevel.getBlockState(candidate.below()).isCollisionShapeFullBlock(targetLevel, candidate)
        );
        if (!targetLevel.isInWorldBounds(pos) || !targetLevel.getWorldBorder().isWithinBounds(pos)) {
            notifyDestinationNotFound(player);
            return;
        }

        player.teleportTo(
                targetLevel,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                Set.<RelativeMovement>of(),
                player.getYRot(),
                player.getXRot()
        );
        player.setRespawnPosition(targetLevel.dimension(), pos, 0.0F, true, false);
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            targetLevel.setDefaultSpawnPos(pos, 0.0F);
        }
    }

    @Override
    public Type<DestinyMessage> type() {
        return TYPE;
    }
}
