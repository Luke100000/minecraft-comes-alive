package net.conczin.mca.server;

import net.conczin.mca.Config;
import net.conczin.mca.ducks.IVillagerEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerFactory;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.Nationality;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpawnQueue {
    private static final SpawnQueue INSTANCE = new SpawnQueue();
    private final ConcurrentLinkedQueue<Villager> villagerSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ZombieVillager> zombieVillagerSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Zombie> zombieSpawnList = new ConcurrentLinkedQueue<>();

    public static SpawnQueue getInstance() {
        return INSTANCE;
    }

    public static boolean shouldGetConverted(Entity entity) {
        if (Config.getInstance().fractionOfVanillaVillages <= 0) {
            return true;
        } else {
            int i = Nationality.get((ServerLevel) entity.level()).getRegionId(entity.blockPosition());
            return Math.floorMod(i, 100) >= Config.getInstance().fractionOfVanillaVillages * 100.0;
        }
    }

    public void tick() {
        // After testing with 10k chunk radius with chunky pregen it seems the checks are not needed.
        // The queue system did not work properly and would build up overtime if villages arnt loaded near the player, and would clear on server stop resulting in empty villages anyways.
        // The chunk loaded check apparently does not function properly in this context, this is shown by villagers spawning fine without it, but NEVER spawn with it if far away and JUST generated.
        Villager ve = villagerSpawnQueue.poll();
        if (ve != null) {
            ve.discard();
            VillagerEntityMCA villager = VillagerFactory.newVillager(ve.level())
                    .withName(ve.hasCustomName() ? ve.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withAge(ve.getAge())
                    .withPosition(ve)
                    .withType(ve.getVillagerData().getType())
                    .withProfession(ve.getVillagerData().getProfession(), ve.getVillagerData().getLevel(), ve.getOffers())
                    .spawn(((IVillagerEntity) ve).mca$getSpawnReason());

            copyPastaIntensifies(villager, ve);
        }

        ZombieVillager zve = zombieVillagerSpawnQueue.poll();
        if (zve != null) {
            zve.discard();
            ZombieVillagerEntityMCA villager = ZombieVillagerFactory.newVillager(zve.level())
                    .withName(zve.hasCustomName() ? zve.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withPosition(zve)
                    .withType(zve.getVillagerData().getType())
                    .withProfession(zve.getVillagerData().getProfession(), zve.getVillagerData().getLevel())
                    .spawn(((IVillagerEntity) zve).mca$getSpawnReason());

            copyPastaIntensifies(villager, zve);
        }

        Zombie ze = zombieSpawnList.poll();
        if (ze != null) {
            ze.discard();
            ZombieVillagerEntityMCA villager = ZombieVillagerFactory.newVillager(ze.level())
                    .withName(ze.hasCustomName() ? ze.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withPosition(ze)
                    .withType(VillagerType.byBiome(ze.level().getBiome(ze.blockPosition())))
                    .withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getRandom(ze.getRandom()).map(Holder::value).orElse(VillagerProfession.NONE))
                    .spawn(EntitySpawnReason.NATURAL);

            copyPastaIntensifies(villager, ze);
        }
    }

    private void copyPastaIntensifies(PathfinderMob villager, PathfinderMob entity) {
        if (entity.isPersistenceRequired()) {
            villager.setPersistenceRequired();
        }
        if (entity.isInvulnerable()) {
            villager.setInvulnerable(true);
        }
        if (entity.isNoAi()) {
            villager.setNoAi(true);
        }

        for (String tag : entity.getTags()) {
            villager.addTag(tag);
        }
    }

    public boolean addVillager(Entity entity) {
        if (entity instanceof IVillagerEntity villagerEntity && !handlesSpawnReason(villagerEntity.mca$getSpawnReason())) {
            return false;
        }
        if (Config.getInstance().villagerDimensionBlacklist.contains(entity.getCommandSenderWorld().dimension().location().toString())) {
            return false;
        }
        if (Config.getInstance().overwriteOriginalVillagers
            && (entity.getClass().equals(Villager.class) ||
                Config.getInstance().moddedVillagerWhitelist.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && entity instanceof Villager)
            && shouldGetConverted(entity)
            && !villagerSpawnQueue.contains(entity)) {
            return villagerSpawnQueue.add((Villager) entity);
        }
        if (Config.getInstance().overwriteOriginalZombieVillagers
            && (entity.getClass().equals(ZombieVillager.class) ||
                Config.getInstance().moddedZombieVillagerWhitelist.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()) && entity instanceof ZombieVillager)
            && Config.getInstance().fractionOfVanillaZombies < entity.getRandom().nextFloat()
            && !zombieVillagerSpawnQueue.contains(entity)) {
            return zombieVillagerSpawnQueue.add((ZombieVillager) entity);
        }
        if (Config.getInstance().overwriteAllZombiesWithZombieVillagers
            && entity.getClass().equals(Zombie.class)
            && !zombieSpawnList.contains(entity)) {
            return zombieSpawnList.add((Zombie) entity);
        }
        return false;
    }

    private boolean handlesSpawnReason(EntitySpawnReason reason) {
        return Config.getInstance().allowedSpawnReasons.contains(reason.name().toLowerCase(Locale.ROOT));
    }

    public void convert(Villager villager) {
        villagerSpawnQueue.add(villager);
    }
}