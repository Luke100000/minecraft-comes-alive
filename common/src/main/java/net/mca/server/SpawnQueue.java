package net.mca.server;

import net.mca.Config;
import net.mca.ducks.IVillagerEntity;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerFactory;
import net.mca.entity.ZombieVillagerEntityMCA;
import net.mca.entity.ZombieVillagerFactory;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.Nationality;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpawnQueue {
    private static final SpawnQueue INSTANCE = new SpawnQueue();

    public static SpawnQueue getInstance() {
        return INSTANCE;
    }

    private final ConcurrentLinkedQueue<VillagerEntity> villagerSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ZombieVillagerEntity> zombieVillagerSpawnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ZombieEntity> zombieSpawnList = new ConcurrentLinkedQueue<>();

    public void tick() {
        // After testing with 10k chunk radius with chunky pregen it seems the checks are not needed.
        // The queue system did not work properly and would build up overtime if villages arnt loaded near the player, and would clear on server stop resulting in empty villages anyways.
        // The chunk loaded check apparently does not function properly in this context, this is shown by villagers spawning fine without it, but NEVER spawn with it if far away and JUST generated.
        VillagerEntity ve = villagerSpawnQueue.poll();
        if (ve != null) {
            ve.discard();
            VillagerEntityMCA villager = VillagerFactory.newVillager(ve.world)
                    .withName(ve.hasCustomName() ? ve.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withAge(ve.getBreedingAge())
                    .withPosition(ve)
                    .withType(ve.getVillagerData().getType())
                    .withProfession(ve.getVillagerData().getProfession(), ve.getVillagerData().getLevel(), ve.getOffers())
                    .spawn(((IVillagerEntity)ve).getSpawnReason());

            copyPastaIntensifies(villager, ve);
        }

        ZombieVillagerEntity zve = zombieVillagerSpawnQueue.poll();
        if (zve != null) {
            zve.discard();
            ZombieVillagerEntityMCA villager = ZombieVillagerFactory.newVillager(zve.world)
                    .withName(zve.hasCustomName() ? zve.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withPosition(zve)
                    .withType(zve.getVillagerData().getType())
                    .withProfession(zve.getVillagerData().getProfession(), zve.getVillagerData().getLevel())
                    .spawn(((IVillagerEntity)zve).getSpawnReason());

            copyPastaIntensifies(villager, zve);
        }

        ZombieEntity ze = zombieSpawnList.poll();
        if (ze != null) {
            ze.discard();
            ZombieVillagerEntityMCA villager = ZombieVillagerFactory.newVillager(ze.world)
                    .withName(ze.hasCustomName() ? ze.getName().getString() : null)
                    .withGender(Gender.getRandom())
                    .withPosition(ze)
                    .withType(VillagerType.forBiome(ze.world.getBiome(ze.getBlockPos())))
                    .withProfession(Registry.VILLAGER_PROFESSION.getRandom(ze.getRandom()).map(RegistryEntry::value).orElse(VillagerProfession.NONE))
                    .spawn(SpawnReason.NATURAL);

            copyPastaIntensifies(villager, ze);
        }
    }

    private void copyPastaIntensifies(PathAwareEntity villager, PathAwareEntity entity) {
        if (entity.isPersistent()) {
            villager.setPersistent();
        }
        if (entity.isInvulnerable()) {
            villager.setInvulnerable(true);
        }
        if (entity.isAiDisabled()) {
            villager.setAiDisabled(true);
        }

        for (String tag : entity.getScoreboardTags()) {
            villager.addScoreboardTag(tag);
        }
    }

    public static boolean shouldGetConverted(Entity entity) {
        if (Config.getInstance().fractionOfVanillaVillages <= 0) {
            return true;
        } else {
            int i = Nationality.get((ServerWorld)entity.getWorld()).getRegionId(entity.getBlockPos());
            return Math.floorMod(i, 100) >= Config.getInstance().fractionOfVanillaVillages * 100.0;
        }
    }

    public boolean addVillager(Entity entity) {
        if (entity instanceof IVillagerEntity villagerEntity && !handlesSpawnReason(villagerEntity.getSpawnReason())) {
            return false;
        }
        if (Config.getInstance().villagerDimensionBlacklist.contains(entity.getEntityWorld().getRegistryKey().getValue().toString())) {
            return false;
        }
        if (Config.getInstance().overwriteOriginalVillagers
                && (entity.getClass().equals(VillagerEntity.class) ||
                Config.getInstance().moddedVillagerWhitelist.contains(Registry.ENTITY_TYPE.getId(entity.getType()).toString()) && entity instanceof VillagerEntity)
                && shouldGetConverted(entity)
                && !villagerSpawnQueue.contains(entity)) {
            return villagerSpawnQueue.add((VillagerEntity)entity);
        }
        if (Config.getInstance().overwriteOriginalZombieVillagers
                && (entity.getClass().equals(ZombieVillagerEntity.class) ||
                Config.getInstance().moddedZombieVillagerWhitelist.contains(Registry.ENTITY_TYPE.getId(entity.getType()).toString()) && entity instanceof ZombieVillagerEntity)
                && Config.getInstance().fractionOfVanillaZombies < ((ZombieVillagerEntity)entity).getRandom().nextFloat()
                && !zombieVillagerSpawnQueue.contains(entity)) {
            return zombieVillagerSpawnQueue.add((ZombieVillagerEntity)entity);
        }
        if (Config.getInstance().overwriteAllZombiesWithZombieVillagers
                && entity.getClass().equals(ZombieEntity.class)
                && !zombieSpawnList.contains(entity)) {
            return zombieSpawnList.add((ZombieEntity)entity);
        }
        return false;
    }

    private boolean handlesSpawnReason(SpawnReason reason) {
        return Config.getInstance().allowedSpawnReasons.contains(reason.name().toLowerCase(Locale.ROOT));
    }

    public void convert(VillagerEntity villager) {
        villagerSpawnQueue.add(villager);
    }
}