package net.mca.network.c2s;

import net.mca.Config;
import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.util.WorldUtils;
import net.mca.util.compat.ExtendedFuzzyPositions;
import net.minecraft.entity.ai.FuzzyPositions;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.io.Serial;
import java.util.EnumSet;
import java.util.Optional;

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
    public void receive(ServerPlayerEntity player) {
        if (isClosing) {
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
            player.removeStatusEffect(StatusEffects.HEALTH_BOOST);
        }

        if (!Config.getInstance().allowDestinyTeleportation || location == null || location.equals("somewhere")) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        MCA.executorService.execute(() -> {
            Optional<BlockPos> position;
            if (location.charAt(0) == '#') {
                String tagId = location.substring(1);
                position = WorldUtils.getClosestStructurePosition(player.getServerWorld(), player.getBlockPos(), TagKey.of(RegistryKeys.STRUCTURE, new Identifier(tagId)), 128);
            } else {
                position = WorldUtils.getClosestStructurePosition(player.getServerWorld(), player.getBlockPos(), new Identifier(location), 128);
            }

            server.execute(() -> position.ifPresentOrElse(
                    pos -> handleBlockPos(player, pos),
                    () -> player.sendMessage(Text.translatable("destiny.teleport.failed"), false)
            ));
        });
    }


    private void handleBlockPos(ServerPlayerEntity player, BlockPos pos) {
        player.getWorld().getWorldChunk(pos);

        if (location.equals("minecraft:ancient_city")) {
            pos = new BlockPos(pos.getX(), -50, pos.getZ());
        } else {
            pos = player.getWorld().getTopPosition(Heightmap.Type.WORLD_SURFACE, pos);
        }

        pos = FuzzyPositions.upWhile(pos, player.getWorld().getHeight(), p -> player.getWorld().getBlockState(p).shouldSuffocate(player.getWorld(), p));
        pos = ExtendedFuzzyPositions.downWhile(pos, 1, p -> !player.getWorld().getBlockState(p.down()).isFullCube(player.getWorld(), p));

        if (!player.getWorld().getWorldBorder().contains(pos) || pos.getY() < player.getWorld().getBottomY() || pos.getY() >= player.getWorld().getTopY()) {
            player.sendMessage(Text.translatable("destiny.teleport.failed"), false);
            return;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        player.getServerWorld().getChunkManager().addTicket(ChunkTicketType.POST_TELEPORT, chunkPos, 1, player.getId());
        player.networkHandler.requestTeleport(pos.getX(), pos.getY(), pos.getZ(), player.getYaw(), player.getPitch(), EnumSet.noneOf(PositionFlag.class));

        //set spawn
        player.setSpawnPoint(player.getWorld().getRegistryKey(), pos, 0.0f, true, false);
        if (player.getWorld().getServer() != null && player.getWorld().getServer().isHost(player.getGameProfile())) {
            player.getServerWorld().setSpawnPos(pos, 0.0f);
        }
    }
}
