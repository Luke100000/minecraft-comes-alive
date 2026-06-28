package net.conczin.mca.entity;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class PlayerDimensions {
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
        return new Scale(villager.getRawHorizontalScaleFactor(), villager.getRawVerticalScaleFactor());
    }

    public static Scale fromEntityData(CompoundTag entityData) {
        CompoundTag mcaData = entityData.contains(VillagerEntityMCA.MCA_DATA_KEY, 10)
                ? entityData.getCompound(VillagerEntityMCA.MCA_DATA_KEY)
                : entityData;
        CompoundTag traits = mcaData.contains("Traits", 10) ? mcaData.getCompound("Traits") : new CompoundTag();
        AgeScale age = getAgeScale(entityData);
        Gender gender = Gender.byId(mcaData.contains("Gender") ? mcaData.getInt("Gender") : Gender.UNASSIGNED.ordinal());

        float width = geneScale(mcaData, Genetics.WIDTH)
                * getTraitsHorizontalScaleFactor(traits)
                * age.width()
                * gender.getHorizontalScaleFactor();
        float height = geneScale(mcaData, Genetics.SIZE)
                * getTraitsVerticalScaleFactor(traits)
                * age.height()
                * gender.getScaleFactor();

        return new Scale(width, height);
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
        return (traits.getBoolean(Traits.DWARFISM.id()) ? 0.85F : 1.0F)
                * (traits.getBoolean(Traits.TOUGH.id()) ? 1.2F : 1.0F)
                * (traits.getBoolean(Traits.WEAK.id()) ? 0.85F : 1.0F);
    }

    private static float getTraitsVerticalScaleFactor(CompoundTag traits) {
        return traits.getBoolean(Traits.DWARFISM.id()) ? 0.65F : 1.0F;
    }

    private record AgeScale(float width, float height) {
    }

    public record Scale(float width, float height) {
    }
}
