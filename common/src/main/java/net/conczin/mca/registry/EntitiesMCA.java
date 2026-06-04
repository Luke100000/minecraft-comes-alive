package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.CribEntity;
import net.conczin.mca.entity.GrimReaperEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface EntitiesMCA {
    Map<Identifier, EntityType<?>> ENTITIES = new HashMap<>();
    Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> ATTRIBUTES = new HashMap<>();

    EntityType<VillagerEntityMCA> MALE_VILLAGER = register("male_villager", EntityType.Builder
            .<VillagerEntityMCA>of((t, w) -> new VillagerEntityMCA(t, w, Gender.MALE), MobCategory.MISC)
            .sized(0.6F, 2.0F), VillagerEntityMCA::createAttributes
    );
    EntityType<VillagerEntityMCA> FEMALE_VILLAGER = register("female_villager", EntityType.Builder
            .<VillagerEntityMCA>of((t, w) -> new VillagerEntityMCA(t, w, Gender.FEMALE), MobCategory.MISC)
            .sized(0.6F, 2.0F), VillagerEntityMCA::createAttributes
    );
    EntityType<ZombieVillagerEntityMCA> MALE_ZOMBIE_VILLAGER = register("male_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>of((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.MALE), MobCategory.MONSTER)
            .sized(0.6F, 2.0F), ZombieVillagerEntityMCA::createAttributes
    );
    EntityType<ZombieVillagerEntityMCA> FEMALE_ZOMBIE_VILLAGER = register("female_zombie_villager", EntityType.Builder
            .<ZombieVillagerEntityMCA>of((t, w) -> new ZombieVillagerEntityMCA(t, w, Gender.FEMALE), MobCategory.MONSTER)
            .sized(0.6F, 2.0F), ZombieVillagerEntityMCA::createAttributes
    );
    EntityType<GrimReaperEntity> GRIM_REAPER = register("grim_reaper", EntityType.Builder
            .of(GrimReaperEntity::new, MobCategory.MONSTER)
            .sized(1, 2.6F)
            .fireImmune(), GrimReaperEntity::createAttributes
    );
    EntityType<CribEntity> CRIB = register("crib", EntityType.Builder
            .of(CribEntity::new, MobCategory.MISC)
            .sized(1.2F, 1.0F)
            .fireImmune()
    );

    static <T extends LivingEntity> EntityType<T> register(String name, EntityType.Builder<T> builder, Supplier<AttributeSupplier.Builder> attributes) {
        Identifier id = MCA.locate(name);
        EntityType<T> entity = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        ATTRIBUTES.put(entity, attributes.get());
        ENTITIES.put(id, entity);
        return entity;
    }

    static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = MCA.locate(name);
        EntityType<T> entity = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        ENTITIES.put(id, entity);
        return entity;
    }

    static void registerEntities(MCA.RegisterHelper<EntityType<?>> helper) {
        ENTITIES.forEach(helper::register);
    }

    static void registerAttributes(MCA.AttributeRegisterHelper helper) {
        ATTRIBUTES.forEach(helper::register);
    }
}
