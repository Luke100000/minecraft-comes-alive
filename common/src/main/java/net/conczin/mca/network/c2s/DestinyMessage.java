package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.DestinyLocationResolver;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.world.data.PlayerSaveData;
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
import java.util.UUID;

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
        UUID playerId = serverPlayer.getUUID();
        ServerInteractionManager interactionManager = ServerInteractionManager.getInstance();

        if (destination.isEmpty()) {
            // Also sent when the story finishes: close UI session and effects without cancelling an in-flight teleport.
            boolean closedOpenUi = interactionManager.closeDestinyUi(playerId);
            if (!closedOpenUi && !interactionManager.hasPendingDestinyTeleport(serverPlayer)) {
                return;
            }
            finishDestinyOnboarding(serverPlayer);
            return;
        }

        if (!interactionManager.hasActiveDestinySession(serverPlayer)) {
            return;
        }

        DestinyDestination selectedDestination = destination.get();
        List<DestinyDestination> allowedDestinations =
                DestinyLocationResolver.getCachedDestinations(serverPlayer.server);
        if (!allowedDestinations.contains(selectedDestination)) {
            notifyDestinationNotFound(serverPlayer);
            return;
        }

        if (!Config.getInstance().allowDestinyTeleportation) {
            interactionManager.clearDestinySession(playerId);
            finishDestinyOnboarding(serverPlayer);
            return;
        }
        if (selectedDestination.dimension().isEmpty()) {
            markDestinyCompleted(serverPlayer);
            return;
        }

        ResourceKey<Level> targetDimension = selectedDestination.dimension().orElseThrow();
        ServerLevel targetLevel = serverPlayer.server.getLevel(targetDimension);
        if (targetLevel == null) {
            notifyDestinationNotFound(serverPlayer);
            return;
        }

        if (!interactionManager.beginDestinyTeleport(playerId)) {
            return;
        }

        BlockPos searchOrigin = getSearchOrigin(serverPlayer, targetLevel);
        ServerLevel sourceLevel = serverPlayer.serverLevel();
        DestinyLocationResolver.findNearestAsync(
                playerId,
                targetLevel,
                searchOrigin,
                selectedDestination,
                128
        ).whenComplete((result, error) -> {
            // The locator completes on the server thread and holds its chunk ticket through this callback.
            if (!interactionManager.consumePendingDestinyTeleport(playerId)
                    || serverPlayer.hasDisconnected() || serverPlayer.isRemoved()
                    || !serverPlayer.connection.isAcceptingMessages()
                    || !serverPlayer.server.isRunning() || serverPlayer.server.isStopped()) {
                return;
            }

            finishDestinyOnboarding(serverPlayer);
            if (error != null || result == null || result.isEmpty()
                    || serverPlayer.serverLevel() != sourceLevel || !Config.getInstance().allowDestinyTeleportation
                    || !DestinyLocationResolver.getCachedDestinations(serverPlayer.server).contains(selectedDestination)) {
                notifyDestinationNotFound(serverPlayer);
                return;
            }
            handleBlockPos(serverPlayer, targetLevel, selectedDestination.location(), result.orElseThrow());
        });
    }

    private static void finishDestinyOnboarding(ServerPlayer player) {
        markDestinyCompleted(player);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.HEALTH_BOOST);
    }

    private static void markDestinyCompleted(ServerPlayer player) {
        PlayerSaveData data = PlayerSaveData.get(player);
        if (!data.hasUnreadableSavedData()) {
            data.setEntityDataSet(true);
        }
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
        player.sendSystemMessage(
                Component.translatable("destiny.teleport.failed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
    }

    public static Optional<BlockPos> resolveSafeTeleportPos(
            ServerLevel targetLevel,
            String location,
            BlockPos rawPos
    ) {
        // The async locator has already requested the destination at FULL status.
        if (targetLevel.getChunkSource().getChunkNow(rawPos.getX() >> 4, rawPos.getZ() >> 4) == null) {
            return Optional.empty();
        }
        BlockPos pos = location.equals("minecraft:ancient_city")
                ? new BlockPos(rawPos.getX(), -50, rawPos.getZ())
                : targetLevel.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, rawPos);
        pos = RandomPos.moveUpOutOfSolid(
                pos,
                targetLevel.getMaxBuildHeight(),
                candidate -> targetLevel.getBlockState(candidate).isSuffocating(targetLevel, candidate)
        );
        pos = ExtendedFuzzyPositions.downWhile(
                pos,
                targetLevel.getMinBuildHeight() + 1,
                candidate -> !targetLevel.getBlockState(candidate.below()).isCollisionShapeFullBlock(targetLevel, candidate.below())
        );
        BlockPos below = pos.below();
        if (!targetLevel.isInWorldBounds(pos)
                || !targetLevel.isInWorldBounds(below)
                || !targetLevel.getWorldBorder().isWithinBounds(pos)
                || !targetLevel.getBlockState(below).isCollisionShapeFullBlock(targetLevel, below)) {
            return Optional.empty();
        }
        return Optional.of(pos);
    }

    private static void handleBlockPos(
            ServerPlayer player,
            ServerLevel targetLevel,
            String location,
            BlockPos rawPos
    ) {
        Optional<BlockPos> resolved = resolveSafeTeleportPos(targetLevel, location, rawPos);
        if (resolved.isEmpty()) {
            notifyDestinationNotFound(player);
            return;
        }
        BlockPos pos = resolved.get();
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
