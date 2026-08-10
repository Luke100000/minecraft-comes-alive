package net.mca.entity;

import net.mca.MCA;
import net.mca.ProfessionsMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.SchedulesMCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.util.RegistryRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface EntitiesMCA {

    Map<ResourceLocation, RegistryRef<? extends EntityType<?>>> ENTITY_TYPES = new LinkedHashMap<>();
    Map<RegistryRef<? extends EntityType<? extends LivingEntity>>, Supplier<AttributeSupplier.Builder>> ATTRIBUTES = new LinkedHashMap<>();

    RegistryRef<EntityType<VillagerEntityMCA>> MALE_VILLAGER = register("male_villager", EntityType.Builder
            .<VillagerEntityMCA>of((t, w) -> new VillagerEntityMCA(t, w, Gender.MALE), MobCategory.MISC)
            .sized(0.6F, 2.0F), VillagerEntityMCA::createAttributes
    );
    RegistryRef<EntityType<VillagerEntityMCA>> FEMALE_VILLAGER = register("female_villager", EntityType.Builder
            .<VillagerEntityMCA>of((t, w) -> new VillagerEntityMCA(t, w, Gender.FEMALE), MobCategory.MISC)
            .sized(0.6F, 2.0F), VillagerEntityMCA::createAttributes
    );
    RegistryRef<EntityType<ZombieVillagerEntityMCA>> MALE_ZOMBIE_VILLAGER = register("male_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>of((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.MALE), MobCategory.MONSTER)
            .sized(0.6F, 2.0F), ZombieVillagerEntityMCA::createAttributes
    );
    RegistryRef<EntityType<ZombieVillagerEntityMCA>> FEMALE_ZOMBIE_VILLAGER = register("female_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>of((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.FEMALE), MobCategory.MONSTER)
            .sized(0.6F, 2.0F), ZombieVillagerEntityMCA::createAttributes
    );
    RegistryRef<EntityType<GrimReaperEntity>> GRIM_REAPER = register("grim_reaper", EntityType.Builder
            .of(GrimReaperEntity::new, MobCategory.MONSTER)
            .sized(1, 2.6F)
            .fireImmune(), GrimReaperEntity::createAttributes
    );
    RegistryRef<EntityType<CribEntity>> CRIB = registerNonLiving("crib", EntityType.Builder
            .<CribEntity>of((t, w) -> new CribEntity(t, w), MobCategory.MISC)
            .sized(1.2F, 1.0F)
            .fireImmune()
    );

    static void bootstrap() {
        SchedulesMCA.bootstrap();
        ProfessionsMCA.bootstrap();
    }
    
    static<T extends Entity> RegistryRef<EntityType<T>> registerNonLiving(String name, EntityType.Builder<T> builder) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<EntityType<T>> ref = RegistryRef.of(id, () -> builder.build(id.toString()));
        ENTITY_TYPES.put(id, ref);
        return ref;
    }

    static <T extends LivingEntity> RegistryRef<EntityType<T>> register(String name, EntityType.Builder<T> builder, Supplier<AttributeSupplier.Builder> attributes) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<EntityType<T>> ref = RegistryRef.of(id, () -> builder.build(id.toString()));
        ENTITY_TYPES.put(id, ref);
        ATTRIBUTES.put(ref, attributes);
        return ref;
    }

    static void registerEntities(MCA.RegisterHelper<EntityType<?>> helper) {
        ENTITY_TYPES.forEach((id, ref) -> helper.register(id, ref.get()));
    }

    static void registerAttributes(MCA.AttributeRegisterHelper helper) {
        ATTRIBUTES.forEach((ref, attributes) -> helper.register(ref.get(), attributes.get()));
    }
}
