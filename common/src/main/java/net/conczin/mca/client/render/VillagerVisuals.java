package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.world.entity.LivingEntity;

public record VillagerVisuals(
        String genderDataName,
        boolean female,
        boolean baby,
        float breastSize,
        Dimensions dimensions,
        float rawHorizontalScaleFactor,
        float rawVerticalScaleFactor,
        boolean burned,
        boolean albinism,
        boolean rainbow,
        boolean heterochromia,
        float skinGene,
        float melaninGene,
        float hemoglobinGene,
        float faceGene,
        float eumelaninGene,
        float pheomelaninGene,
        String skin,
        String hair,
        int hairDye,
        String clothes,
        float infectionProgress,
        int tickCount,
        int entityId,
        boolean sleeping,
        boolean deadOrDying
) {
    public static VillagerVisuals require(Object state) {
        VillagerVisuals visuals = VillagerStateHolder.require(state).mca$getVisuals();
        if (visuals == null) {
            throw new IllegalStateException("No villager visuals available for render state");
        }
        return visuals;
    }

    public static VillagerVisuals capture(VillagerLike<?> villager) {
        Genetics genetics = villager.getGenetics();
        Traits traits = villager.getTraits();
        LivingEntity entity = villager.asEntity();
        Gender gender = genetics.getGender();
        AgeState ageState = villager.getAgeState();
        VillagerDimensions dimensions = ageState == AgeState.UNASSIGNED && entity.isBaby()
                ? AgeState.TODDLER
                : villager.getVillagerDimensions();
        float rawHorizontalScaleFactor = genetics.getHorizontalScaleFactor()
                * traits.getHorizontalScaleFactor()
                * dimensions.getWidth()
                * gender.getHorizontalScaleFactor();
        float rawVerticalScaleFactor = genetics.getVerticalScaleFactor()
                * traits.getVerticalScaleFactor()
                * dimensions.getHeight()
                * gender.getScaleFactor();
        return new VillagerVisuals(
                gender.getDataName(),
                gender == Gender.FEMALE,
                ageState == AgeState.BABY,
                genetics.getBreastSize(),
                new Dimensions(
                        dimensions.getWidth(),
                        dimensions.getHeight(),
                        dimensions.getBreasts(),
                        dimensions.getHead()
                ),
                rawHorizontalScaleFactor,
                rawVerticalScaleFactor,
                villager.isBurned(),
                traits.hasTrait(Traits.ALBINISM),
                traits.hasTrait(Traits.RAINBOW),
                traits.hasTrait(Traits.HETEROCHROMIA),
                genetics.getGene(Genetics.SKIN),
                genetics.getGene(Genetics.MELANIN),
                genetics.getGene(Genetics.HEMOGLOBIN),
                genetics.getGene(Genetics.FACE),
                genetics.getGene(Genetics.EUMELANIN),
                genetics.getGene(Genetics.PHEOMELANIN),
                villager.getSkin(),
                villager.getHair(),
                villager.getHairDye(),
                villager.getClothes(),
                villager.getInfectionProgress(),
                (int) (entity.getId() + entity.level().getGameTime()),
                entity.getId(),
                entity.isSleeping(),
                entity.isDeadOrDying()
        );
    }

    public boolean isBlinking() {
        int time = tickCount / 2 + (int) (hemoglobinGene * 65536);
        return time % 50 == 1 || time % 57 == 1 || sleeping || deadOrDying;
    }

    public record Dimensions(
            float width,
            float height,
            float breasts,
            float head
    ) implements VillagerDimensions {
        @Override
        public float getWidth() {
            return width;
        }

        @Override
        public float getHeight() {
            return height;
        }

        @Override
        public float getBreasts() {
            return breasts;
        }

        @Override
        public float getHead() {
            return head;
        }
    }
}
