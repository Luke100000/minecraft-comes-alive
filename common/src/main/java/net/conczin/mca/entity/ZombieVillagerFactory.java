package net.conczin.mca.entity;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalInt;

public class ZombieVillagerFactory {
    private final Level world;

    private Optional<String> name = Optional.empty();
    private Optional<Gender> gender = Optional.empty();

    private Optional<VillagerProfession> profession = Optional.empty();
    private Optional<VillagerType> type = Optional.empty();
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
        this.type = Optional.ofNullable(type);
        return this;
    }

    public ZombieVillagerFactory withProfession(VillagerProfession prof) {
        this.profession = Optional.ofNullable(prof);
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

    public ZombieVillagerFactory withPosition(BlockPos pos) {
        return withPosition(Vec3.atBottomCenterOf(pos.above()));
    }

    public ZombieVillagerEntityMCA spawn(MobSpawnType reason) {
        if (position.isEmpty()) {
            MCA.LOGGER.info("Attempted to spawn villager without a position being set!");
        }

        ZombieVillagerEntityMCA build = build();
        WorldUtils.spawnEntity(world, build, reason);
        return build;
    }

    public ZombieVillagerEntityMCA build() {
        Gender gender = this.gender.orElseGet(Gender::getRandom);
        ZombieVillagerEntityMCA zombie = gender.getZombieType().create(world);
        zombie.getGenetics().setGender(gender);
        zombie.setName(name.orElseGet(() -> Names.pickCitizenName(gender, zombie)));
        position.ifPresent(pos -> zombie.absMoveTo(pos.x(), pos.y(), pos.z()));
        VillagerData data = zombie.getVillagerData();
        zombie.setVillagerData(new VillagerData(
                        type.orElseGet(data::getType),
                        profession.orElse(VillagerProfession.NONE),
                        level.orElseGet(data::getLevel)
                )
        );
        return zombie;
    }
}
