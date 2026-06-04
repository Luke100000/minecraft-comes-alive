package net.conczin.mca.entity;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalInt;

public class ZombieVillagerFactory {
    private final Level world;

    private Optional<String> name = Optional.empty();
    private Optional<Gender> gender = Optional.empty();

    private Optional<Holder<VillagerProfession>> profession = Optional.empty();
    private Optional<Holder<VillagerType>> type = Optional.empty();
    private OptionalInt level = OptionalInt.empty();

    private Optional<Vec3> position = Optional.empty();

    private ZombieVillagerFactory(Level world) {
        this.world = world;
    }

    public static ZombieVillagerFactory newVillager(Level world) {
        return new ZombieVillagerFactory(world);
    }

    public ZombieVillagerFactory withGender(Gender gender) {
        this.gender = Optional.ofNullable(gender);
        return this;
    }

    public ZombieVillagerFactory withType(VillagerType type) {
        this.type = Optional.ofNullable(type).map(BuiltInRegistries.VILLAGER_TYPE::wrapAsHolder);
        return this;
    }

    public ZombieVillagerFactory withType(ResourceKey<VillagerType> type) {
        this.type = Optional.ofNullable(type)
                .map(key -> world.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(key));
        return this;
    }

    public ZombieVillagerFactory withProfession(VillagerProfession prof) {
        this.profession = Optional.ofNullable(prof).map(BuiltInRegistries.VILLAGER_PROFESSION::wrapAsHolder);
        return this;
    }

    public ZombieVillagerFactory withProfession(ResourceKey<VillagerProfession> prof) {
        this.profession = Optional.ofNullable(prof)
                .map(key -> world.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(key));
        return this;
    }

    public ZombieVillagerFactory withProfession(VillagerProfession prof, int level) {
        withProfession(prof);
        this.level = OptionalInt.of(level);
        return this;
    }

    public ZombieVillagerFactory withName(String name) {
        this.name = Optional.ofNullable(name);
        return this;
    }

    public ZombieVillagerFactory withPosition(double x, double y, double z) {
        return withPosition(new Vec3(x, y, z));
    }

    public ZombieVillagerFactory withPosition(Entity entity) {
        return withPosition(entity.getX(), entity.getY(), entity.getZ());
    }

    public ZombieVillagerFactory withPosition(Vec3 pos) {
        position = Optional.of(pos);
        return this;
    }

    public ZombieVillagerEntityMCA spawn(EntitySpawnReason reason) {
        if (position.isEmpty()) {
            MCA.LOGGER.info("Attempted to spawn villager without a position being set!");
        }

        ZombieVillagerEntityMCA build = build();
        WorldUtils.spawnEntity(world, build, reason);
        return build;
    }

    public ZombieVillagerEntityMCA build() {
        Gender gender = this.gender.orElseGet(Gender::getRandom);
        ZombieVillagerEntityMCA zombie = gender.getZombieType().create(world, EntitySpawnReason.COMMAND);
        assert zombie != null;
        zombie.getGenetics().setGender(gender);
        zombie.setCustomName(Component.literal(name.orElseGet(() -> Names.pickCitizenName(gender, zombie))));
        position.ifPresent(pos -> zombie.setPos(pos.x(), pos.y(), pos.z()));
        VillagerData data = zombie.getVillagerData();
        zombie.setVillagerData(new VillagerData(
                        type.orElseGet(data::type),
                        profession.orElseGet(data::profession),
                        level.orElseGet(data::level)
                )
        );
        return zombie;
    }
}
