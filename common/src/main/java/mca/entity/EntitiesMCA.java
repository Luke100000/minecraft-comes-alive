package mca.entity;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import mca.entity.ai.ActivityMCA;
import mca.entity.ai.MemoryModuleTypeMCA;
import mca.entity.ai.ProfessionsMCA;
import mca.entity.ai.SchedulesMCA;
import mca.entity.ai.relationship.Gender;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.function.Supplier;

public interface EntitiesMCA {

    DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(MCA.MOD_ID, Registry.ENTITY_TYPE_KEY);

    RegistrySupplier<EntityType<VillagerEntityMCA>> MALE_VILLAGER = register("male_villager", EntityType.Builder
            .<VillagerEntityMCA>create((t, w) -> new VillagerEntityMCA(t, w, Gender.MALE), SpawnGroup.AMBIENT)
            .setDimensions(0.6F, 2.0F), VillagerEntityMCA::createVillagerAttributes
    );
    RegistrySupplier<EntityType<VillagerEntityMCA>> FEMALE_VILLAGER = register("female_villager", EntityType.Builder
            .<VillagerEntityMCA>create((t, w) -> new VillagerEntityMCA(t, w, Gender.FEMALE), SpawnGroup.AMBIENT)
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

    static void bootstrap() {
        ENTITY_TYPES.register();
        MemoryModuleTypeMCA.bootstrap();
        ActivityMCA.bootstrap();
        SchedulesMCA.bootstrap();
        ProfessionsMCA.bootstrap();
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
