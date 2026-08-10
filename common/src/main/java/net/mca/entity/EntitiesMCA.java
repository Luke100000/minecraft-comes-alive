package net.mca.entity;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.ProfessionsMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.SchedulesMCA;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import java.util.function.Supplier;

public interface EntitiesMCA {

    DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(MCA.MOD_ID, Registries.ENTITY_TYPE);

    RegistrySupplier<EntityType<VillagerEntityMCA>> MALE_VILLAGER = register("male_villager", EntityType.Builder
            .<VillagerEntityMCA>of((t, w) -> new VillagerEntityMCA(t, w, Gender.MALE), MobCategory.MISC)
            .sized(0.6F, 2.0F), VillagerEntityMCA::createAttributes
    );
    RegistrySupplier<EntityType<VillagerEntityMCA>> FEMALE_VILLAGER = register("female_villager", EntityType.Builder
            .<VillagerEntityMCA>of((t, w) -> new VillagerEntityMCA(t, w, Gender.FEMALE), MobCategory.MISC)
            .sized(0.6F, 2.0F), VillagerEntityMCA::createAttributes
    );
    RegistrySupplier<EntityType<ZombieVillagerEntityMCA>> MALE_ZOMBIE_VILLAGER = register("male_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>of((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.MALE), MobCategory.MONSTER)
            .sized(0.6F, 2.0F), ZombieVillagerEntityMCA::createAttributes
    );
    RegistrySupplier<EntityType<ZombieVillagerEntityMCA>> FEMALE_ZOMBIE_VILLAGER = register("female_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>of((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.FEMALE), MobCategory.MONSTER)
            .sized(0.6F, 2.0F), ZombieVillagerEntityMCA::createAttributes
    );
    RegistrySupplier<EntityType<GrimReaperEntity>> GRIM_REAPER = register("grim_reaper", EntityType.Builder
            .of(GrimReaperEntity::new, MobCategory.MONSTER)
            .sized(1, 2.6F)
            .fireImmune(), GrimReaperEntity::createAttributes
    );
    RegistrySupplier<EntityType<CribEntity>> CRIB = registerNonLiving("crib", EntityType.Builder
            .<CribEntity>of((t, w) -> new CribEntity(t, w), MobCategory.MISC)
            .sized(1.2F, 1.0F)
            .fireImmune()
    );

    static void bootstrap() {
        ENTITY_TYPES.register();
        MemoryModuleTypeMCA.bootstrap();
        ActivityMCA.bootstrap();
        SchedulesMCA.bootstrap();
        ProfessionsMCA.bootstrap();
    }
    
    static<T extends Entity> RegistrySupplier<EntityType<T>> registerNonLiving(String name, EntityType.Builder<T> builder) {
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, name);
        return ENTITY_TYPES.register(id, () -> {
            EntityType<T> result = builder.build(id.toString());
            return result;
        });
    }

    static <T extends LivingEntity> RegistrySupplier<EntityType<T>> register(String name, EntityType.Builder<T> builder, Supplier<AttributeSupplier.Builder> attributes) {
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, name);
        return ENTITY_TYPES.register(id, () -> {
            EntityType<T> result = builder.build(id.toString());
            EntityAttributeRegistry.register(() -> result, attributes);

            return result;
        });
    }
}
