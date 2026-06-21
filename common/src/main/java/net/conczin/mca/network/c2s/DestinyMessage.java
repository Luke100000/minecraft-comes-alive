package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.util.WorldUtils;
import net.conczin.mca.util.compat.ExtendedFuzzyPositions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;

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
                    WorldUtils.getClosestStructurePosition(sp.level(), sp.blockPosition(), TagKey.create(Registries.STRUCTURE, Identifier.parse(tagId)), 128).ifPresentOrElse(pos -> handleBlockPos(sp, pos), () -> notifyDestinationNotFound(sp));
                } else {
                    WorldUtils.getClosestStructurePosition(sp.level(), sp.blockPosition(), Identifier.parse(location), 128).ifPresentOrElse(pos -> handleBlockPos(sp, pos), () -> notifyDestinationNotFound(sp));
                }
            });
        }
    }

    private void notifyDestinationNotFound(ServerPlayer player) {
        player.level().getServer().execute(() -> player.sendSystemMessage(Component.translatable("destiny.teleport.failed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
    }

    private void handleBlockPos(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        level.getChunkAt(pos);
        if (location.equals("minecraft:ancient_city")) {
            pos = new BlockPos(pos.getX(), -50, pos.getZ());
        } else {
            pos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos);
        }
        pos = RandomPos.moveUpOutOfSolid(pos, level.getHeight(), p -> level.getBlockState(p).isSuffocating(level, p));
        pos = ExtendedFuzzyPositions.downWhile(pos, 1, p -> !level.getBlockState(p.below()).isCollisionShapeFullBlock(level, p));
        ChunkPos chunkPos = ChunkPos.containing(pos);
        level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, chunkPos, 1);
        player.connection.teleport(pos.getX(), pos.getY(), pos.getZ(), player.getYRot(), player.getXRot());
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(player.level().dimension(), pos, 0.0f, 0.0f), true), false);
        if (level.getServer().isSingleplayerOwner(player.nameAndId()) && level.getLevelData() instanceof WritableLevelData levelData) {
            levelData.setSpawn(LevelData.RespawnData.of(player.level().dimension(), pos, 0.0f, 0.0f));
        }
    }

    @Override
    public Type<DestinyMessage> type() {
        return TYPE;
    }
}
