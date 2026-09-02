package net.conczin.mca.entity;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.UUID;

public final class PlayerDimensions {
    private static final Scale VANILLA_SCALE = new Scale(1.0F, 1.0F);

    private PlayerDimensions() {
    }

    public static Optional<Scale> getScale(Player player) {
        if (!Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            return Optional.empty();
        }

        if (player instanceof ServerPlayer serverPlayer) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            return data.isEntityDataSet() ? Optional.of(data.getDimensionsScale()) : Optional.empty();
        }

        if (player.level().isClientSide()) {
            return ClientProxy.getPlayerData(player.getUUID()).map(PlayerDimensions::fromVillager);
        }

        return Optional.empty();
    }

    public static Scale fromVillager(VillagerLike<?> villager) {
        if (villager.getPlayerModel() == VillagerLike.PlayerModel.VANILLA) {
            return VANILLA_SCALE;
        }
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

    private static Scale fromEntityData(CompoundTag entityData) {
        if (VillagerLike.PlayerModel.byId(entityData.getInt("PlayerModel")) == VillagerLike.PlayerModel.VANILLA) {
            return VANILLA_SCALE;
        }
        CompoundTag traits = entityData.contains("Traits", 10) ? entityData.getCompound("Traits") : new CompoundTag();
        AgeScale age = getAgeScale(entityData);
        Gender gender = Gender.byId(entityData.contains("Gender") ? entityData.getInt("Gender") : Gender.UNASSIGNED.ordinal());

        float width = geneScale(entityData, Genetics.WIDTH)
                * getTraitsHorizontalScaleFactor(traits)
                * age.width()
                * gender.getHorizontalScaleFactor();
        float height = geneScale(entityData, Genetics.SIZE)
                * getTraitsVerticalScaleFactor(traits)
                * age.height()
                * gender.getScaleFactor();

        return new Scale(width, height);
    }

    public static void debugAppliedScale(Player player, EntityDimensions vanilla, EntityDimensions scaled, Scale scale) {
        if (MCA.LOGGER.isDebugEnabled()) {
            MCA.LOGGER.debug("[MCA player hitbox] apply player={} uuid={} side={} pose={} scale={}x{} vanilla={}x{} scaled={}x{} cachedBb={}x{}",
                    player.getName().getString(),
                    player.getUUID(),
                    player.level().isClientSide() ? "client" : "server",
                    player.getPose(),
                    scale.width(),
                    scale.height(),
                    vanilla.width(),
                    vanilla.height(),
                    scaled.width(),
                    scaled.height(),
                    player.getBbWidth(),
                    player.getBbHeight());
        }
    }

    public static void debugRefresh(Player player, String reason) {
        if (MCA.LOGGER.isDebugEnabled()) {
            AABB box = player.getBoundingBox();
            MCA.LOGGER.debug("[MCA player hitbox] refresh {} player={} uuid={} side={} pose={} cached={}x{} bb=[{}, {}, {} -> {}, {}, {}]",
                    reason,
                    player.getName().getString(),
                    player.getUUID(),
                    player.level().isClientSide() ? "client" : "server",
                    player.getPose(),
                    player.getBbWidth(),
                    player.getBbHeight(),
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

    private static float geneScale(CompoundTag mcaData, Genetics.GeneType gene) {
        return 0.75F + (mcaData.contains("Gene" + gene.key()) ? mcaData.getFloat("Gene" + gene.key()) : 0.5F) / 2.0F;
    }

    private static AgeScale getAgeScale(CompoundTag entityData) {
        int age = entityData.contains("Age") ? entityData.getInt("Age") : 0;
        AgeState current = AgeState.byCurrentAge(age);
        AgeState next = current.getNext();
        if (next == current) {
            return new AgeScale(current.getWidth(), current.getHeight());
        }

        float delta = AgeState.getDelta(age);
        return new AgeScale(
                Mth.lerp(delta, current.getWidth(), next.getWidth()),
                Mth.lerp(delta, current.getHeight(), next.getHeight())
        );
    }

    private static float getTraitsHorizontalScaleFactor(CompoundTag traits) {
        return (hasTrait(traits, Traits.DWARFISM) ? 0.85F : 1.0F)
                * (hasTrait(traits, Traits.TOUGH) ? 1.2F : 1.0F)
                * (hasTrait(traits, Traits.WEAK) ? 0.85F : 1.0F);
    }

    private static float getTraitsVerticalScaleFactor(CompoundTag traits) {
        return hasTrait(traits, Traits.DWARFISM) ? 0.65F : 1.0F;
    }

    private static boolean hasTrait(CompoundTag traits, Traits.Trait trait) {
        return traits.getBoolean(trait.getId().toString());
    }

    private record AgeScale(float width, float height) {
    }

    public record Scale(float width, float height) {
    }
}
