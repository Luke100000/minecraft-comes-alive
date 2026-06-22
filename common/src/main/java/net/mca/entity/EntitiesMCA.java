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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public interface EntitiesMCA {

    DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.ENTITY_TYPE);

    RegistrySupplier<EntityType<VillagerEntityMCA>> MALE_VILLAGER = register("male_villager", EntityType.Builder
            .<VillagerEntityMCA>create((t, w) -> new VillagerEntityMCA(t, w, Gender.MALE), SpawnGroup.MISC)
            .setDimensions(0.6F, 2.0F), VillagerEntityMCA::createVillagerAttributes
    );
    RegistrySupplier<EntityType<VillagerEntityMCA>> FEMALE_VILLAGER = register("female_villager", EntityType.Builder
            .<VillagerEntityMCA>create((t, w) -> new VillagerEntityMCA(t, w, Gender.FEMALE), SpawnGroup.MISC)
            .setDimensions(0.6F, 2.0F), VillagerEntityMCA::createVillagerAttributes
    );
    RegistrySupplier<EntityType<ZombieVillagerEntityMCA>> MALE_ZOMBIE_VILLAGER = register("male_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>create((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.MALE), SpawnGroup.MONSTER)
            .setDimensions(0.6F, 2.0F), ZombieVillagerEntityMCA::createZombieAttributes
    );
    RegistrySupplier<EntityType<ZombieVillagerEntityMCA>> FEMALE_ZOMBIE_VILLAGER = register("female_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>create((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.FEMALE), SpawnGroup.MONSTER)
            .setDimensions(0.6F, 2.0F), ZombieVillagerEntityMCA::createZombieAttributes
    );
    RegistrySupplier<EntityType<GrimReaperEntity>> GRIM_REAPER = register("grim_reaper", EntityType.Builder
            .create(GrimReaperEntity::new, SpawnGroup.MONSTER)
            .setDimensions(1, 2.6F)
            .makeFireImmune(), GrimReaperEntity::createAttributes
    );
    RegistrySupplier<EntityType<CribEntity>> CRIB = registerNonLiving("crib", EntityType.Builder
    		.<CribEntity>create((t, w) -> new CribEntity(t, w), SpawnGroup.MISC)
            .setDimensions(1.2F, 1.0F)
            .makeFireImmune()
    );

    static void bootstrap() {
        ENTITY_TYPES.register();
        MemoryModuleTypeMCA.bootstrap();
        ActivityMCA.bootstrap();
        SchedulesMCA.bootstrap();
        ProfessionsMCA.bootstrap();
    }
    
    static<T extends Entity> RegistrySupplier<EntityType<T>> registerNonLiving(String name, EntityType.Builder<T> builder) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return ENTITY_TYPES.register(id, () -> {
            EntityType<T> result = builder.build(id.toString());
            return result;
        });
    }

    static <T extends LivingEntity> RegistrySupplier<EntityType<T>> register(String name, EntityType.Builder<T> builder, Supplier<DefaultAttributeContainer.Builder> attributes) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return ENTITY_TYPES.register(id, () -> {
            EntityType<T> result = builder.build(id.toString());
            EntityAttributeRegistry.register(() -> result, attributes);

            return result;
        });
    }
}
