package net.mca.entity;

import net.mca.ClientProxy;
import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves MCA genetics into player hitbox scale on both logical sides.
 */
public final class PlayerDimensions {
    private PlayerDimensions() {
    }

    public static Optional<Scale> getScale(PlayerEntity player) {
        if (!Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return Optional.empty();
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            return data.isEntityDataSet() ? Optional.of(data.getDimensionsScale()) : Optional.empty();
        }

        if (player.getWorld().isClient) {
            return ClientProxy.getPlayerData(player.getUuid()).map(PlayerDimensions::fromVillager);
        }

        return Optional.empty();
    }

    public static Scale fromVillager(VillagerLike<?> villager) {
        return new Scale(villager.getRawHorizontalScaleFactor(), villager.getRawVerticalScaleFactor());
    }

    public static Scale fromPlayerData(PlayerSaveData playerData) {
        VillagerLike<?> villager = VillagerLike.toVillager(playerData);
        if (villager == null) {
            Scale fallback = fromEntityData(playerData.getEntityData());
            debugScale("fallback player-data scale", playerData.getUUID(), fallback);
            return fallback;
        }
        Scale scale = fromVillager(villager);
        debugScale("projected player-data scale", playerData.getUUID(), scale);
        return scale;
    }

    private static Scale fromEntityData(NbtCompound entityData) {
        NbtCompound mcaData = entityData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10)
                ? entityData.getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                : entityData;
        NbtCompound traits = mcaData.contains("Traits", 10) ? mcaData.getCompound("Traits") : new NbtCompound();
        AgeScale age = getAgeScale(entityData);
        Gender gender = Genetics.readGender(mcaData);
        if (gender == Gender.UNASSIGNED && mcaData != entityData) {
            gender = Genetics.readGender(entityData);
        }

        float width = geneScale(entityData, mcaData, Genetics.WIDTH)
                * getTraitsHorizontalScaleFactor(traits)
                * age.width()
                * gender.getHorizontalScaleFactor();
        float height = geneScale(entityData, mcaData, Genetics.SIZE)
                * getTraitsVerticalScaleFactor(traits)
                * age.height()
                * gender.getScaleFactor();

        return new Scale(width, height);
    }

    public static void debugAppliedScale(PlayerEntity player, EntityDimensions vanilla, EntityDimensions scaled, Scale scale) {
        if (MCA.LOGGER.isDebugEnabled()) {
            MCA.LOGGER.debug("[MCA player hitbox] apply player={} uuid={} side={} pose={} scale={}x{} vanilla={}x{} scaled={}x{} cachedBb={}x{}",
                    player.getName().getString(),
                    player.getUuid(),
                    player.getWorld().isClient ? "client" : "server",
                    player.getPose(),
                    scale.width(),
                    scale.height(),
                    vanilla.width,
                    vanilla.height,
                    scaled.width,
                    scaled.height,
                    player.getWidth(),
                    player.getHeight());
        }
    }

    public static void debugRefresh(PlayerEntity player, String reason) {
        if (MCA.LOGGER.isDebugEnabled()) {
            Box box = player.getBoundingBox();
            MCA.LOGGER.debug("[MCA player hitbox] refresh {} player={} uuid={} side={} pose={} cached={}x{} bb=[{}, {}, {} -> {}, {}, {}]",
                    reason,
                    player.getName().getString(),
                    player.getUuid(),
                    player.getWorld().isClient ? "client" : "server",
                    player.getPose(),
                    player.getWidth(),
                    player.getHeight(),
                    box.minX,
                    box.minY,
                    box.minZ,
                    box.maxX,
                    box.maxY,
                    box.maxZ);
        }
    }

    private static void debugScale(String reason, UUID uuid, Scale scale) {
        if (MCA.LOGGER.isDebugEnabled()) {
            MCA.LOGGER.debug("[MCA player hitbox] {} uuid={} scale={}x{}", reason, uuid, scale.width(), scale.height());
        }
    }

    private static float geneScale(NbtCompound entityData, NbtCompound mcaData, Genetics.GeneType gene) {
        String key = "Gene" + gene.key();
        float value = mcaData.contains(key)
                ? mcaData.getFloat(key)
                : entityData.contains(key) ? entityData.getFloat(key) : 0.5F;
        return 0.75F + value / 2.0F;
    }

    private static AgeScale getAgeScale(NbtCompound entityData) {
        int age = entityData.contains("Age") ? entityData.getInt("Age") : 0;
        AgeState current = AgeState.byCurrentAge(age);
        AgeState next = current.getNext();
        if (next == current) {
            return new AgeScale(current.getWidth(), current.getHeight());
        }

        float delta = AgeState.getDelta(age);
        return new AgeScale(
                MathHelper.lerp(delta, current.getWidth(), next.getWidth()),
                MathHelper.lerp(delta, current.getHeight(), next.getHeight())
        );
    }

    private static float getTraitsHorizontalScaleFactor(NbtCompound traits) {
        return (hasTrait(traits, Traits.DWARFISM) ? 0.85F : 1.0F)
                * (hasTrait(traits, Traits.TOUGH) ? 1.2F : 1.0F)
                * (hasTrait(traits, Traits.WEAK) ? 0.85F : 1.0F);
    }

    private static float getTraitsVerticalScaleFactor(NbtCompound traits) {
        return hasTrait(traits, Traits.DWARFISM) ? 0.65F : 1.0F;
    }

    private static boolean hasTrait(NbtCompound traits, Traits.Trait trait) {
        return traits.getBoolean(trait.getId().toString());
    }

    private record AgeScale(float width, float height) {
    }

    public record Scale(float width, float height) {
    }
}
