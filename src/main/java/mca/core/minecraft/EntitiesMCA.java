package mca.core.minecraft;

import mca.core.MCA;
import mca.core.forge.Registration;
import mca.entity.EntityGrimReaper;
import mca.entity.EntityVillagerMCA;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;

public class EntitiesMCA {
    public static EntityType<EntityVillagerMCA> VILLAGER =  EntityType.Builder.<EntityVillagerMCA>of((entityType, world) -> new EntityVillagerMCA(world), EntityClassification.AMBIENT).sized(0.6F, 1.8F).build(new ResourceLocation(MCA.MOD_ID, "villager").toString());
    public static EntityType<EntityGrimReaper> GRIM_REAPER = EntityType.Builder.of(EntityGrimReaper::new, EntityClassification.MONSTER).sized(1.0F, 2.6F).build(new ResourceLocation(MCA.MOD_ID, "grim_reaper").toString());

    public static void init() {
        Registration.ENTITY_TYPES.register("villager",() -> VILLAGER);
        Registration.ENTITY_TYPES.register("grim_repear", () -> GRIM_REAPER);
    }



}
