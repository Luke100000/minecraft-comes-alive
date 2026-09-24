package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.MoodGroup;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.brain.tasks.EnterGraveyardTask;
import net.conczin.mca.entity.ai.brain.tasks.GrieveTask;
import net.conczin.mca.entity.ai.brain.tasks.MournAtGraveTask;
import net.conczin.mca.entity.ai.navigation.LongDistancePathTarget;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillageMourningGameTests {
    private VillageMourningGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyTombstoneIsNotMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);

        helper.assertTrue(!Mourning.isMournableTombstone(helper.getLevel(), grave),
                "empty tombstone must not be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedTombstoneIsMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Mourning Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        helper.assertTrue(Mourning.isMournableTombstone(helper.getLevel(), grave),
                "occupied tombstone must be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resurrectingTombstoneIsNotMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 1))))
                .withName("Resurrection Probe")
                .build();
        helper.assertTrue(helper.getLevel().getEntity(deceased.getUUID()) == null,
                "resurrection fixture must not keep the deceased villager loaded with the tombstone UUID");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        data.startResurrecting(false);

        helper.assertTrue(!Mourning.isMournableTombstone(helper.getLevel(), grave),
                "resurrecting tombstone must not be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void graveDiscoveryDoesNotLoadUnloadedChunks(GameTestHelper helper) {
        BlockPos farPosition = helper.absolutePos(new BlockPos(4_096, 1, 4_096));
        helper.assertTrue(!helper.getLevel().isLoaded(farPosition),
                "fixture requires the distant chunk to start unloaded");
        Village village = new Village(1, helper.getLevel());
        registerGraveyard(village, farPosition);

        helper.assertTrue(Mourning.getMournableGraves(village, helper.getLevel()).isEmpty(),
                "an unloaded graveyard must not produce ambient mourning graves");
        helper.assertTrue(!helper.getLevel().isLoaded(farPosition),
                "ambient grave discovery must not force-load graveyard chunks");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_long_distance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void personalMourningDoesNotLoadDistantGraveChunk(GameTestHelper helper) {
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Distant Personal Mourner");
        BlockPos grave = mourner.blockPosition().offset(4_096, 0, 4_096);
        helper.assertTrue(!helper.getLevel().isLoaded(grave),
                "fixture requires the assigned grave chunk to start unloaded");

        Mourning.start(mourner, grave);
        mourner.getBrain().tick(helper.getLevel(), mourner);

        helper.assertTrue(!helper.getLevel().isLoaded(grave),
                "personal mourning must not force-load a distant assigned grave");
        helper.assertTrue(isMourningAt(mourner, helper.getLevel(), grave),
                "unloaded personal grave intent must be retained until it can be validated nearby");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .filter(GlobalPos.of(helper.getLevel().dimension(), grave)::equals).isPresent(),
                "unloaded personal grave must remain the provisional long-distance destination");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void panicInterruptsAndMourningCanResume(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(3, 1, 3));
        prepareFlatArea(helper, grave, 3, 3);
        occupyGrave(helper, grave);
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(4, 1, 3), "Interrupted Mourner");

        Mourning.start(mourner, grave);
        EnterGraveyardTask enter = new EnterGraveyardTask();
        helper.assertTrue(enter.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                "mourning fixture could not assign a graveside standing position");

        MournAtGraveTask mourn = new MournAtGraveTask();
        helper.assertTrue(mourn.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                "mourning fixture could not start graveside mourning");
        mourner.getBrain().setActiveActivityIfPossible(Activity.PANIC);
        mourn.tickOrStop(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 1L);

        helper.assertTrue(mourn.getStatus() == Behavior.Status.STOPPED,
                "PANIC must interrupt a running graveside mourning task");
        helper.assertTrue(isMourningAt(mourner, helper.getLevel(), grave),
                "panic interruption must preserve the assigned grave for resumption");

        mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 2L),
                "retained mourning intent must resume after panic without requiring a failed-path retry timestamp");
        helper.assertTrue(mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "resumed mourning must reactivate the GRIEVE activity");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void combatInterruptsMourningApproach(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(6, 1, 3));
        prepareFlatArea(helper, grave, 6, 3);
        occupyGrave(helper, grave);
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(1, 1, 3), "Combat Interrupted Mourner");
        VillagerEntityMCA threat = spawnVillager(helper, new BlockPos(1, 1, 5), "Combat Interruption Probe");

        Mourning.start(mourner, grave);
        mourner.getBrain().tick(helper.getLevel(), mourner);
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION).isPresent(),
                "mourning approach fixture must publish a destination before interruption");

        mourner.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threat);
        EnterGraveyardTask interrupt = new EnterGraveyardTask();
        helper.assertTrue(interrupt.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 1L),
                "mourning lifecycle task must remain able to observe combat interruption");

        helper.assertTrue(isMourningAt(mourner, helper.getLevel(), grave),
                "combat interruption must preserve the assigned grave for later resumption");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION).isEmpty(),
                "combat interruption must clear mourning-owned movement intent");
        helper.assertTrue(!mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "combat interruption must leave the GRIEVE activity");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void mourningSitePreservesDimensionIdentity(GameTestHelper helper) {
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Cross Dimension Mourner");
        GlobalPos netherGrave = GlobalPos.of(Level.NETHER, helper.absolutePos(new BlockPos(1, 1, 1)));
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE, netherGrave);
        mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                "mourning must not start toward a grave in another dimension");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                        .filter(netherGrave::equals).isPresent(),
                "dimension-aware mourning intent must be preserved rather than mistaken for a local grave");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void mourningFlowerRestoresOnlyWhileItOwnsTheHand(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(3, 1, 3));
        prepareFlatArea(helper, grave, 3, 3);
        occupyGrave(helper, grave);
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(4, 1, 3), "Flower Ownership Mourner");
        mourner.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance());

        Mourning.start(mourner, grave);
        EnterGraveyardTask enter = new EnterGraveyardTask();
        helper.assertTrue(enter.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                "mourning fixture could not assign a graveside standing position");

        MournAtGraveTask restore = new MournAtGraveTask();
        helper.assertTrue(restore.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                "mourning fixture could not start temporary flower ownership");
        helper.assertTrue(!mourner.getMainHandItem().is(Items.IRON_SWORD),
                "mourning must temporarily replace the previous main-hand item");
        restore.doStop(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 1L);
        helper.assertTrue(mourner.getMainHandItem().is(Items.IRON_SWORD),
                "stopping mourning while the flower still owns the hand must restore the previous item");

        Mourning.start(mourner, grave);
        EnterGraveyardTask secondEnter = new EnterGraveyardTask();
        helper.assertTrue(secondEnter.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 2L),
                "mourning fixture could not reassign the graveside standing position");
        MournAtGraveTask preserveReplacement = new MournAtGraveTask();
        helper.assertTrue(preserveReplacement.tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 2L),
                "mourning fixture could not restart temporary flower ownership");
        mourner.setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.getDefaultInstance());
        preserveReplacement.doStop(helper.getLevel(), mourner, helper.getLevel().getGameTime() + 3L);
        helper.assertTrue(mourner.getMainHandItem().is(Items.BOW),
                "mourning cleanup must not overwrite a newer combat/equipment hand item");

        mourner.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance());
        long gameTime = helper.getLevel().getGameTime() + 4L;
        Mourning.start(mourner, grave);
        EnterGraveyardTask reloadEnter = new EnterGraveyardTask();
        helper.assertTrue(reloadEnter.tryStart(helper.getLevel(), mourner, gameTime),
                "mourning fixture could not assign a graveside standing position before reload");
        MournAtGraveTask beforeReload = new MournAtGraveTask();
        helper.assertTrue(beforeReload.tryStart(helper.getLevel(), mourner, gameTime),
                "mourning fixture could not start temporary flower ownership before reload");

        CompoundTag saved = mourner.saveWithoutId(new CompoundTag());
        VillagerEntityMCA reloaded = VillagerFactory.newVillager(helper.getLevel()).build();
        reloaded.load(saved);
        reloaded.refreshBrain(helper.getLevel());
        reloaded.getBrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);

        helper.assertTrue(reloaded.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND)
                        .filter(stack -> stack.is(Items.IRON_SWORD)).isPresent(),
                "reload lost the displaced mourning main-hand item");
        helper.assertTrue(reloaded.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_FLOWER)
                        .filter(stack -> net.minecraft.world.item.ItemStack.matches(reloaded.getMainHandItem(), stack))
                        .isPresent(),
                "reload lost mourning ownership of the temporary flower");

        MournAtGraveTask reloadedMourn = new MournAtGraveTask();
        helper.assertTrue(reloadedMourn.tryStart(helper.getLevel(), reloaded, gameTime + 1L),
                "reloaded mourning behavior did not resume at the assigned grave");
        reloadedMourn.doStop(helper.getLevel(), reloaded, gameTime + 2L);

        helper.assertTrue(reloaded.getMainHandItem().is(Items.IRON_SWORD),
                "reloaded mourning cleanup did not restore the displaced main-hand item");
        helper.assertTrue(reloaded.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND).isEmpty(),
                "reloaded mourning cleanup retained stale previous-hand ownership");
        helper.assertTrue(reloaded.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_FLOWER).isEmpty(),
                "reloaded mourning cleanup retained stale flower ownership");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void mourningStartClearsCompetingInteractionState(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Mourning State Probe");
        VillagerEntityMCA target = spawnVillager(helper, new BlockPos(4, 1, 1), "Mourning State Target");
        mourner.getBrain().setMemory(MemoryModuleType.BREED_TARGET, target);
        mourner.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, target);

        Mourning.start(mourner, grave);

        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.BREED_TARGET).isEmpty(),
                "starting mourning must stop an existing breeding interaction");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.INTERACTION_TARGET).isEmpty(),
                "starting mourning must stop an existing social interaction");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_long_distance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void distantMourningKeepsRealDestination(GameTestHelper helper) {
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Distant Deceased Probe");
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Distant Mourner Probe");
        BlockPos start = mourner.blockPosition();
        int pathfindingDistance = Config.getInstance().getVillagerPathfindingDistance();
        BlockPos grave = start.east(pathfindingDistance + 32);
        prepareFlatArea(helper, start, 16, 3);
        prepareFlatArea(helper, grave, 2, 3);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        Mourning.start(mourner, grave);
        mourner.getBrain().tick(helper.getLevel(), mourner);

        BlockPos mourningPosition = mourner.getBrain()
                .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                .orElseThrow(() -> new AssertionError("distant mourning did not choose a standing position"))
                .pos();
        var walkTarget = mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null,
                "distant mourning did not publish a walk target on its first brain tick");
        helper.assertTrue(walkTarget.getTarget() instanceof LongDistancePathTarget,
                "distant mourning did not use long-distance path intent");
        LongDistancePathTarget target = (LongDistancePathTarget)walkTarget.getTarget();
        helper.assertTrue(target.currentBlockPosition().equals(mourningPosition),
                "distant mourning replaced the real graveside destination with an intermediate point");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientMourningOnlyUsesIdleOrMeet(GameTestHelper helper) {
        VillagerEntityMCA idle = spawnVillager(helper, new BlockPos(1, 1, 1), "Idle Mourning Probe");
        VillagerEntityMCA meet = spawnVillager(helper, new BlockPos(2, 1, 1), "Meet Mourning Probe");
        VillagerEntityMCA work = spawnVillager(helper, new BlockPos(3, 1, 1), "Work Mourning Probe");
        VillagerEntityMCA rest = spawnVillager(helper, new BlockPos(4, 1, 1), "Rest Mourning Probe");
        VillagerEntityMCA chore = spawnVillager(helper, new BlockPos(5, 1, 1), "Chore Mourning Probe");
        GlobalPos poi = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)));

        meet.getBrain().setMemory(MemoryModuleType.MEETING_POINT, poi);
        meet.getBrain().setActiveActivityIfPossible(Activity.MEET);
        work.getBrain().setMemory(MemoryModuleType.JOB_SITE, poi);
        work.getBrain().setActiveActivityIfPossible(Activity.WORK);
        rest.getBrain().setActiveActivityIfPossible(Activity.REST);
        chore.getBrain().setActiveActivityIfPossible(ActivitiesMCA.CHORE);

        helper.assertTrue(Mourning.canMournAmbiently(idle), "IDLE resident should be eligible for ambient mourning");
        helper.assertTrue(Mourning.canMournAmbiently(meet), "MEET resident should be eligible for ambient mourning");
        helper.assertTrue(!Mourning.canMournAmbiently(work), "WORK resident must not be interrupted by ambient mourning");
        helper.assertTrue(!Mourning.canMournAmbiently(rest), "REST resident must not be interrupted by ambient mourning");
        helper.assertTrue(!Mourning.canMournAmbiently(chore), "CHORE resident must not be interrupted by ambient mourning");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_retry", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void assignedMourningRetryWaitsForRetryTimestamp(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Retry Deceased Probe");
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Retry Mourner Probe");
        helper.getLevel().setBlock(grave.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        long now = helper.getLevel().getGameTime();
        long retryAt = now + 20L;
        mourner.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_SITE,
                GlobalPos.of(helper.getLevel().dimension(), grave)
        );
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, retryAt);
        mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        helper.onEachTick(() -> {
            long tick = helper.getLevel().getGameTime();
            if (tick < retryAt) {
                helper.assertTrue(!mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                        "retry must not start before MOURNING_RETRY_AT");
                helper.assertTrue(isMourningAt(mourner, helper.getLevel(), grave),
                        "waiting retry must preserve the assigned grave");
                return;
            }

            if (mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).isPresent()) {
                mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), mourner, tick),
                        "mourning retry task must become eligible once its timestamp is reached");
            }
            helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).isEmpty(),
                    "retry start must consume MOURNING_RETRY_AT");
            helper.assertTrue(isMourningAt(mourner, helper.getLevel(), grave),
                    "retry must restart the same assigned grave");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void spouseTragedyStartsExactMourningWhenEnabled(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Spouse Deceased Probe");
        VillagerEntityMCA spouse = spawnVillager(helper, new BlockPos(3, 1, 1), "Spouse Mourner Probe");
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            spouse.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.SPOUSE, deceased);

            helper.assertTrue(isMourningAt(spouse, helper.getLevel(), grave),
                    "spouse tragedy should target the exact burial site");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_witnesses", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void witnessedDeathStartsSmallVisibleMourningGroup(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Witnessed Death Probe");
        List.of(
                spawnVillager(helper, new BlockPos(2, 1, 3), "Witness Mourner 1"),
                spawnVillager(helper, new BlockPos(3, 1, 3), "Witness Mourner 2"),
                spawnVillager(helper, new BlockPos(4, 1, 3), "Witness Mourner 3"),
                spawnVillager(helper, new BlockPos(5, 1, 3), "Witness Mourner 4")
        );
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        List<VillagerEntityMCA> eligibleWitnesses = helper.getLevel()
                .getEntitiesOfClass(VillagerEntityMCA.class, deceased.getBoundingBox().inflate(32.0D))
                .stream()
                .filter(VillagerEntityMCA::isAlive)
                .filter(villager -> !villager.getUUID().equals(deceased.getUUID()))
                .filter(villager -> villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty())
                .filter(villager -> !Mourning.isTemporarilyBlocked(villager))
                .sorted(java.util.Comparator.comparingDouble(deceased::distanceToSqr))
                .toList();
        List<VillagerEntityMCA> expectedMourners = eligibleWitnesses.stream().limit(2).toList();

        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            deceased.getRelationships().onTragedy(helper.getLevel().damageSources().generic(), grave);

            List<VillagerEntityMCA> mourners = eligibleWitnesses.stream()
                    .filter(villager -> isMourningAt(villager, helper.getLevel(), grave))
                    .toList();
            helper.assertTrue(mourners.size() == 2,
                    "a witnessed death should send exactly two nearby non-family villagers to the grave");
            helper.assertTrue(mourners.containsAll(expectedMourners),
                    "witness mourning must select the two nearest eligible villagers");
            helper.assertTrue(expectedMourners.stream().allMatch(villager -> villager.getBrain().isActive(ActivitiesMCA.GRIEVE)),
                    "selected witnesses should immediately enter the GRIEVE activity");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void personalMourningWaitsForPlayerDirectedMovement(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        Player followedPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        VillagerEntityMCA follower = spawnVillager(helper, new BlockPos(3, 1, 1), "Following Mourner Probe");
        VillagerEntityMCA staying = spawnVillager(helper, new BlockPos(4, 1, 1), "Staying Mourner Probe");

        follower.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING, followedPlayer);
        staying.getBrain().setMemory(MemoryModuleTypeMCA.STAYING, true);

        Mourning.start(follower, grave);
        Mourning.start(staying, grave);

        helper.assertTrue(isMourningAt(follower, helper.getLevel(), grave),
                "personal mourning should preserve the assigned grave while following a player");
        helper.assertTrue(isMourningAt(staying, helper.getLevel(), grave),
                "personal mourning should preserve the assigned grave while staying");
        helper.assertTrue(follower.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent(),
                "personal mourning must not cancel a player's follow command");
        helper.assertTrue(staying.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent(),
                "personal mourning must not cancel a player's stay command");
        helper.assertTrue(!follower.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "following villagers must wait before entering GRIEVE");
        helper.assertTrue(!staying.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "staying villagers must wait before entering GRIEVE");

        follower.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        staying.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), follower, now),
                "following mourner should resume once the player direction clears");
        helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), staying, now),
                "staying mourner should resume once the player direction clears");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void parentTragedyStartsExactMourningForChild(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceasedParent = spawnVillager(helper, new BlockPos(6, 1, 1), "Parent Deceased Probe");
        VillagerEntityMCA child = spawnVillager(helper, new BlockPos(3, 1, 1), "Child Mourner Probe");
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            child.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.PARENT, deceasedParent);

            helper.assertTrue(isMourningAt(child, helper.getLevel(), grave),
                    "a child should target their deceased parent's exact burial site");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_family", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void selfTragedyPropagatesParentDeathToChild(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceasedParent = spawnVillager(helper, new BlockPos(6, 1, 1), "Propagated Parent Probe");
        VillagerEntityMCA child = spawnVillager(helper, new BlockPos(3, 1, 1), "Propagated Child Mourner");
        child.getRelationships().getFamilyEntry().assignParent(deceasedParent.getRelationships().getFamilyEntry());
        child.getBrain().setMemory(MemoryModuleTypeMCA.STAYING, true);

        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            deceasedParent.getRelationships().onTragedy(helper.getLevel().damageSources().generic(), grave);

            helper.assertTrue(isMourningAt(child, helper.getLevel(), grave),
                    "SELF tragedy propagation should notify a child that their parent died");
            helper.assertTrue(child.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent(),
                    "parent mourning must retain an existing stay command while waiting to resume");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void deceasedDoesNotMournOwnGrave(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Self Mourning Probe");
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            deceased.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.SELF, deceased);

            helper.assertTrue(deceased.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                    "the deceased villager must not be assigned its own grave for mourning");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void strangerTragedyDoesNotStartMourning(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Stranger Deceased Probe");
        VillagerEntityMCA stranger = spawnVillager(helper, new BlockPos(3, 1, 1), "Stranger Mourner Probe");

        stranger.getRelationships().onTragedy(
                helper.getLevel().damageSources().generic(), grave, RelationshipType.STRANGER, deceased);

        helper.assertTrue(stranger.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                "stranger tragedy must not start grave mourning");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disabledMourningKeepsTragedyMoodWithoutGraveAssignment(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Disabled Deceased Probe");
        VillagerEntityMCA spouse = spawnVillager(helper, new BlockPos(3, 1, 1), "Disabled Mourner Probe");
        spouse.getVillagerBrain().modifyMoodValue(
                MoodGroup.MAX_LEVEL - spouse.getVillagerBrain().getMoodValue());
        int moodBefore = spouse.getVillagerBrain().getMoodValue();
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = false;
            spouse.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.SPOUSE, deceased);

            helper.assertTrue(spouse.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                    "disabled mourning must not assign a grave");
            helper.assertTrue(spouse.getVillagerBrain().getMoodValue() < moodBefore,
                    "tragedy mood penalty must remain when mourning is disabled");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_resurrection", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 650)
    public static void resurrectionClearsAmbientRecencyAndRetryState(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(8, 1, 1))))
                .withName("Resurrection Memory Probe")
                .build();
        long now = helper.getLevel().getGameTime();
        deceased.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, now);
        deceased.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, now + 1_000L);
        helper.getLevel().setBlock(grave.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        data.startResurrecting(false);

        helper.onEachTick(() -> {
            long tick = helper.getLevel().getGameTime();
            if (tick < now + 500L) {
                return;
            }

            VillagerEntityMCA resurrected = helper.getLevel()
                    .getEntitiesOfClass(VillagerEntityMCA.class, new AABB(grave).inflate(3.0D))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (resurrected != null) {
                helper.assertTrue(resurrected.getBrain()
                                .getMemoryInternal(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING).isEmpty(),
                        "resurrected villager must not inherit ambient mourning recency");
                helper.assertTrue(resurrected.getBrain()
                                .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).isEmpty(),
                        "resurrected villager must not inherit a mourning retry deadline");
                helper.succeed();
            } else if (tick > now + 580L) {
                helper.fail("resurrected MCA villager was not recreated near its grave");
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void firstVillageTickOnlySchedulesMourning(GameTestHelper helper) {
        Village village = new Village(1, helper.getLevel());
        long now = helper.getLevel().getGameTime();

        village.tick(helper.getLevel(), now);

        helper.assertTrue(village.getNextMourningTime() == now + 2_400L,
                "first tick should schedule mourning exactly two minutes later");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dueVillageWithoutMournableGraveReschedules(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(new Village(1, helper.getLevel()), now, helper.getLevel());

        due.tick(helper.getLevel(), now);

        helper.assertTrue(due.getNextMourningTime() > now,
                "empty due burst must still schedule the following burst");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dueVillageReleasesOneSmallBurstAndSchedulesAnother(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 10, "Burst Probe");
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);
        int firstCount = mourningSites(residents).size();
        long nextBurst = due.getNextMourningTime();

        helper.assertTrue(firstCount >= 2 && firstCount <= 4,
                "one due burst must select only two to four residents");
        helper.assertTrue(nextBurst == now + 2_400L,
                "one due burst must schedule the next occurrence two minutes later");

        due.tick(helper.getLevel(), nextBurst - 1L);
        helper.assertTrue(mourningSites(residents).size() == firstCount,
                "village must not release another burst before its timestamp");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_safety_ambient", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nearbyMonsterDefersAmbientMourning(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(4, 1, 4));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 4, "Unsafe Ambient Probe");
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            throw new IllegalStateException("failed to create zombie");
        }
        BlockPos zombiePos = grave.east(8);
        zombie.absMoveTo(zombiePos.getX() + 0.5D, zombiePos.getY(), zombiePos.getZ() + 0.5D);
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);

        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);

        helper.assertTrue(mourningSites(residents).isEmpty(),
                "ambient mourning must not start while a monster is within the vanilla bed-safety range of the grave");
        helper.assertTrue(due.getNextMourningTime() >= now + 600L
                        && due.getNextMourningTime() <= now + 1_200L,
                "unsafe ambient mourning should retry in thirty to sixty seconds");

        long retry = due.getNextMourningTime();
        zombie.discard();
        due.tick(helper.getLevel(), retry);

        helper.assertTrue(!mourningSites(residents).isEmpty(),
                "ambient mourning should start after the nearby monster is gone");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_safety_personal", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void personalMourningWaitsForNearbyMonster(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(4, 1, 4));
        occupyGrave(helper, grave);
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(2, 1, 4), "Unsafe Personal Mourner");
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (zombie == null) {
            throw new IllegalStateException("failed to create zombie");
        }
        BlockPos zombiePos = grave.north(8);
        zombie.absMoveTo(zombiePos.getX() + 0.5D, zombiePos.getY(), zombiePos.getZ() + 0.5D);
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);

        Mourning.start(mourner, grave);

        helper.assertTrue(isMourningAt(mourner, helper.getLevel(), grave),
                "unsafe personal mourning should preserve the exact assigned grave");
        helper.assertTrue(!mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "personal mourning must wait while a monster is near the grave");

        long retryAt = mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                .orElseThrow();
        zombie.discard();
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(retryAt > now,
                "unsafe personal mourning should schedule a future retry");
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, now);
        mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), mourner, now),
                "personal mourning should resume after the monster leaves");
        helper.assertTrue(mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "safe personal mourning should enter GRIEVE");
        helper.succeed();
    }

    @GameTest(batch = "mca_mourning_fallback_graves", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientMourningFallsBackToIndexedVillageTombstone(GameTestHelper helper) {
        BlockPos formalAnchor = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(formalAnchor, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(formalAnchor.east(), BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(formalAnchor.west(), BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        BlockPos strayGrave = formalAnchor.south(5);
        occupyGrave(helper, strayGrave);

        Village village = new Village(1, helper.getLevel());
        registerGraveyard(village, formalAnchor);
        village = new Village(village.save(), helper.getLevel());

        List<BlockPos> graves = Mourning.getMournableGraves(village, helper.getLevel());

        helper.assertTrue(graves.size() == 1 && graves.getFirst().equals(strayGrave),
                "when a formal graveyard has no occupied grave, ambient mourning should fall back to a loaded indexed tombstone inside the village border");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dueAmbientBurstWaitsUntilDaytime(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 4, "Night Burst Probe");
        long now = helper.getLevel().getGameTime();
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        helper.getLevel().setDayTime(18_000L);
        due.tick(helper.getLevel(), now);
        helper.assertTrue(mourningSites(residents).isEmpty(),
                "ambient mourning must not start during the night");
        helper.assertTrue(due.getNextMourningTime() == now,
                "a due nighttime burst must remain due instead of being consumed");

        helper.getLevel().setDayTime(6_000L);
        due.tick(helper.getLevel(), now + 1L);
        helper.assertTrue(!mourningSites(residents).isEmpty(),
                "the deferred ambient burst should run once daytime returns");
        helper.assertTrue(due.getNextMourningTime() > now + 1L,
                "the daytime burst must schedule the next random occurrence");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientBurstPreservesWorkAndRestActivities(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        VillagerEntityMCA idle = spawnVillager(helper, new BlockPos(2, 1, 1), "Burst Idle Probe");
        VillagerEntityMCA meet = spawnVillager(helper, new BlockPos(3, 1, 1), "Burst Meet Probe");
        VillagerEntityMCA work = spawnVillager(helper, new BlockPos(4, 1, 1), "Burst Work Probe");
        VillagerEntityMCA rest = spawnVillager(helper, new BlockPos(5, 1, 1), "Burst Rest Probe");
        GlobalPos poi = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)));
        meet.getBrain().setMemory(MemoryModuleType.MEETING_POINT, poi);
        meet.getBrain().setActiveActivityIfPossible(Activity.MEET);
        work.getBrain().setMemory(MemoryModuleType.JOB_SITE, poi);
        work.getBrain().setActiveActivityIfPossible(Activity.WORK);
        rest.getBrain().setActiveActivityIfPossible(Activity.REST);
        List<VillagerEntityMCA> residents = List.of(idle, meet, work, rest);
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);

        helper.assertTrue(idle.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent(),
                "IDLE resident should be available to an ambient burst");
        helper.assertTrue(meet.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent(),
                "MEET resident should be available to an ambient burst");
        helper.assertTrue(work.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                "WORK resident must not be pulled into ambient mourning");
        helper.assertTrue(rest.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                "REST resident must not be pulled into ambient mourning");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientFairnessPrefersNeverSelectedResidents(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 8, "Fairness Probe");
        long now = helper.getLevel().getGameTime();
        for (VillagerEntityMCA recent : residents.subList(0, 4)) {
            recent.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, now);
        }
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);

        List<VillagerEntityMCA> selected = residents.stream()
                .filter(villager -> villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent())
                .toList();
        helper.assertTrue(selected.size() >= 2 && selected.size() <= 4,
                "an ambient burst must select two to four eligible residents");
        helper.assertTrue(selected.stream().noneMatch(residents.subList(0, 4)::contains),
                "ambient fairness must prefer never/older-selected residents when enough are available");
        helper.assertTrue(selected.stream().allMatch(villager -> villager.getBrain()
                        .getMemoryInternal(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING)
                        .filter(last -> last == now).isPresent()),
                "selected ambient mourners must record the current game time");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disabledMourningFreezesNextAmbientBurst(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 4, "Disabled Ambient Probe");
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);
        long beforeBurst = due.getNextMourningTime();
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = false;
            due.tick(helper.getLevel(), now);

            helper.assertTrue(mourningSites(residents).isEmpty(),
                    "disabled mourning must not assign ambient mourners");
            helper.assertTrue(due.getNextMourningTime() == beforeBurst,
                    "disabled mourning must freeze the next ambient burst timestamp");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    private static Village villageWithGraveyard(GameTestHelper helper, BlockPos grave) {
        Village village = new Village(1, helper.getLevel());
        registerGraveyard(village, grave);
        return village;
    }

    private static void registerGraveyard(Village village, BlockPos grave) {
        ExternalBuilding graveyard = new ExternalBuilding(grave);
        graveyard.setId(100);
        graveyard.setType("graveyard");
        graveyard.setTypeForced(true);
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave);
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave.east());
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave.west());
        village.registerExternalBuilding(graveyard);
    }

    private static Village withNextMourningTime(Village village, long nextMourningTime, ServerLevel level) {
        CompoundTag tag = village.save();
        tag.putLong("nextMourningTime", nextMourningTime);
        return new Village(tag, level);
    }

    private static void occupyGrave(GameTestHelper helper, BlockPos grave) {
        helper.getLevel().getEntitiesOfClass(Monster.class, new AABB(grave).inflate(12.0D))
                .forEach(Monster::discard);
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(7, 1, 1), "Ambient Grave Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
    }

    private static List<VillagerEntityMCA> spawnResidents(GameTestHelper helper, int count, String prefix) {
        List<VillagerEntityMCA> residents = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            residents.add(spawnVillager(helper, new BlockPos(2 + index, 1, 3), prefix + " " + index));
        }
        return residents;
    }

    private static List<GlobalPos> mourningSites(List<VillagerEntityMCA> residents) {
        return residents.stream()
                .flatMap(villager -> villager.getBrain()
                        .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).stream())
                .toList();
    }

    private static boolean isMourningAt(VillagerEntityMCA villager, ServerLevel level, BlockPos grave) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(GlobalPos.of(level.dimension(), grave)::equals)
                .isPresent();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos, String name) {
        BlockPos pos = helper.absolutePos(relativePos);
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName(name)
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        return villager;
    }
}
