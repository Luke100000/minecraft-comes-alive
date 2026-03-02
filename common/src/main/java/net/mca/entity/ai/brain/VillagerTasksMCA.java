package net.mca.entity.ai.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.mca.Config;
import net.mca.ProfessionsMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.EquipmentSet;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.SchedulesMCA;
import net.mca.entity.ai.brain.tasks.*;
import net.mca.entity.ai.brain.tasks.chore.ChoppingTask;
import net.mca.entity.ai.brain.tasks.chore.FishingTask;
import net.mca.entity.ai.brain.tasks.chore.HarvestingTask;
import net.mca.entity.ai.brain.tasks.chore.HuntingTask;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.server.world.data.VillageManager;
import net.mca.server.world.data.villageComponents.VillageGuardsManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.brain.*;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.entity.ai.brain.task.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.Optional;

public class VillagerTasksMCA {
    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.MEETING_POINT,
            MemoryModuleType.MOBS,
            MemoryModuleType.VISIBLE_MOBS,
            MemoryModuleType.VISIBLE_VILLAGER_BABIES,
            MemoryModuleType.NEAREST_PLAYERS,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_TARGETABLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.INTERACTION_TARGET,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.DOORS_TO_CLOSE,
            MemoryModuleType.NEAREST_BED,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_HOSTILE,
            MemoryModuleType.SECONDARY_JOB_SITE,
            MemoryModuleType.HIDING_PLACE,
            MemoryModuleType.HEARD_BELL_TIME,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.LAST_SLEPT,
            MemoryModuleType.LAST_WOKEN,
            MemoryModuleType.LAST_WORKED_AT_POI,
            MemoryModuleType.GOLEM_DETECTED_RECENTLY,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ATTACK_COOLING_DOWN,
            MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(),
            MemoryModuleTypeMCA.STAYING.get(),
            MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get(),
            MemoryModuleTypeMCA.WEARS_ARMOR.get(),
            MemoryModuleTypeMCA.SMALL_BOUNTY.get(),
            MemoryModuleTypeMCA.HIT_BY_PLAYER.get(),
            MemoryModuleTypeMCA.LAST_GRIEVE.get(),
            MemoryModuleTypeMCA.FORCED_HOME.get()
    );

    public static final ImmutableList<SensorType<? extends Sensor<? super VillagerEntity>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_PLAYERS,
            SensorType.NEAREST_ITEMS,
            SensorType.NEAREST_BED,
            SensorType.HURT_BY,
            SensorType.VILLAGER_HOSTILES,
            SensorType.SECONDARY_POIS,
            SensorType.GOLEM_DETECTED,
            ActivityMCA.VILLAGER_BABIES.get(),
            ActivityMCA.EXPLODING_CREEPER.get(),
            ActivityMCA.GUARD_ENEMIES.get()
    );

    public static Brain.Profile<VillagerEntityMCA> createProfile() {
        return Brain.createProfile(MEMORY_TYPES, SENSOR_TYPES);
    }

    public static Brain<VillagerEntityMCA> initializeTasks(VillagerEntityMCA villager, Brain<VillagerEntityMCA> brain) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        AgeState age = AgeState.byCurrentAge(villager.getBreedingAge());

        boolean noDefault = false;

        if (brain.getOptionalMemory(MemoryModuleTypeMCA.STAYING.get()).isPresent()) {
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getStayingPackage());
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5f));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.setTaskList(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            noDefault = true;
        } else if (brain.getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent()) {
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getFollowingPackage());
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5f));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.setTaskList(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            noDefault = true;
        } else if (profession == ProfessionsMCA.MERCENARY.get()) {
            brain.setSchedule(SchedulesMCA.GUESTS);
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F));
            brain.setTaskList(Activity.IDLE, VillagerTasksMCA.getMercenaryPackage(0.5f));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager));
            brain.setTaskList(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            brain.setTaskList(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F));
            brain.setTaskList(ActivityMCA.CHORE.get(), VillagerTasksMCA.getChorePackage());
            noDefault = true;
        } else if (!villager.requiresHome()) {
            brain.setSchedule(SchedulesMCA.GUESTS);
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F));
            brain.setTaskList(Activity.IDLE, VillagerTasksMCA.getAdventurerPackage(0.5f));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.setTaskList(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            brain.setTaskList(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F));
            noDefault = true;
        } else if (age == AgeState.BABY) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            //todo babies may get a little bit more AI
            return brain;
        } else if (age != AgeState.ADULT) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.setTaskList(Activity.PLAY, VillagerTasksMCA.getPlayPackage(1.0F));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
        } else if (villager.isGuard()) {
            brain.setSchedule(SchedulesMCA.getTypeSchedule(villager, SchedulesMCA.GUARD, SchedulesMCA.GUARD_NIGHT));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager));
            brain.setTaskList(Activity.WORK, VillagerTasksMCA.getGuardWorkPackage());
            brain.setTaskList(Activity.PANIC, VillagerTasksMCA.getGuardPanicPackage(0.5f));
            brain.setTaskList(Activity.RAID, VillagerTasksMCA.getGuardWorkPackage());
        } else {
            brain.setSchedule(SchedulesMCA.getTypeSchedule(villager));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getWorkingCorePackage(profession, 0.5F));
            brain.setTaskList(Activity.WORK, VillagerTasksMCA.getWorkPackage(profession, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryModuleState.VALUE_PRESENT)));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.setTaskList(Activity.RAID, VillagerTasksMCA.getRaidPackage(0.5F));
        }

        brain.setTaskList(ActivityMCA.GRIEVE.get(), VillagerTasksMCA.getGrievingPackage());

        if (!noDefault) {
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F));
            brain.setTaskList(Activity.CORE, VillagerTasksMCA.getCorePackage(0.5F));
            brain.setTaskList(Activity.MEET, VillagerTasksMCA.getMeetPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryModuleState.VALUE_PRESENT)));
            brain.setTaskList(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F));
            brain.setTaskList(Activity.IDLE, VillagerTasksMCA.getIdlePackage(0.5F));
            brain.setTaskList(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            brain.setTaskList(Activity.PRE_RAID, VillagerTasksMCA.getPreRaidPackage(0.5F));
            brain.setTaskList(Activity.HIDE, VillagerTasksMCA.getHidePackage(0.5F));
            brain.setTaskList(ActivityMCA.CHORE.get(), VillagerTasksMCA.getChorePackage());
        }

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.doExclusively(Activity.IDLE);
        brain.refreshActivities(villager.getWorld().getTimeOfDay(), villager.getWorld().getTime());

        return brain;
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getStayingPackage() {
        return ImmutableList.of(
                Pair.of(0, new StayTask()),
                getFullLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getFollowingPackage() {
        return ImmutableList.of(
                Pair.of(0, new FollowTask()),
                getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getImportantCorePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new StayAboveWaterTask(0.8F)),
                Config.getInstance().useSmarterDoorAI ? Pair.of(0, new SmarterOpenDoorsTask()) : Pair.of(0, OpenDoorsTask.create()),
                Pair.of(0, new LookAroundTask(45, 90)),
                Pair.of(0, WakeUpTask.create()),
                Pair.of(0, new DeliverMessageTask()),
                Pair.of(1, new WanderOrTeleportToTargetTask()),
                Pair.of(3, new InteractTask(speedModifier)),
                Pair.of(10, new ExtendedFindPointOfInterestTask(registryEntry -> registryEntry.matchesKey(PointOfInterestTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14), (villager) -> {
                    // update villagers home/bed position
                    villager.getResidency().seekHome();
                }, (entity, pos) -> {
                    // verify that this bed is not blocked
                    VillageManager manager = VillageManager.get((ServerWorld) entity.getWorld());
                    if (entity.requiresHome()) {
                        return manager.findNearestVillage(entity).filter(v -> !v.isPositionValidBed(pos)).isEmpty();
                    } else {
                        //villagers without the need of a home may only settle in inns
                        return manager.findNearestVillage(entity).filter(v -> v.getBuildingAt(pos).filter(b -> b.getBuildingType().name().equals("inn")).isPresent()).isPresent();
                    }
                }))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getCorePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new GreetPlayerTask()),
                Pair.of(0, HideWhenBellRingsTask.create()),
                Pair.of(0, StartRaidTask.create()),
                Pair.of(5, WalkToNearestVisibleWantedItemTask.create(speedModifier, false, 4)),
                Pair.of(10, new ExtendedFindPointOfInterestTask(registryEntry -> registryEntry.matchesKey(PointOfInterestTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14), (villager) -> {
                    // update villagers home/bed position
                    villager.getResidency().seekHome();
                }, (entity, pos) -> {
                    // verify that this bed is not blocked
                    VillageManager manager = VillageManager.get((ServerWorld) entity.getWorld());
                    return manager.findNearestVillage(entity).filter(v -> {
                        return v.getBuildingAt(pos).filter(b -> b.getBuildingType().noBeds()).isPresent();
                    }).isEmpty();
                })),
                Pair.of(10, new ExtendedFindPointOfInterestTask(registryEntry -> registryEntry.matchesKey(PointOfInterestTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14), (villager) -> {
                    //report a town bell, the only building always added
                    villager.getBrain().getOptionalMemory(MemoryModuleType.MEETING_POINT).ifPresent(p -> {
                        if (villager.getWorld().getRegistryKey() == p.getDimension()) {
                            VillageManager manager = VillageManager.get((ServerWorld) villager.getWorld());
                            if (!manager.cache.contains(p.getPos())) {
                                manager.cache.add(p.getPos());
                                manager.processBuilding(p.getPos());
                            }

                            villager.getResidency().seekHome();
                        }
                    });
                }))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getWorkingCorePackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, ForgetCompletedPointOfInterestTask.create(profession.heldWorkstation(), MemoryModuleType.JOB_SITE)),
                Pair.of(0, ForgetCompletedPointOfInterestTask.create(profession.acquirableWorkstation(), MemoryModuleType.POTENTIAL_JOB_SITE)),
                Pair.of(2, WorkStationCompetitionTask.create()),
                Pair.of(3, new FollowCustomerTask(speedModifier)),
                Pair.of(6, LazyFindPointOfInterestTask.create(profession.acquirableWorkstation(), MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, true, Optional.empty())),
                Pair.of(7, new WalkTowardJobSiteTask(speedModifier)),
                Pair.of(8, TakeJobSiteTask.create(speedModifier)),
                Pair.of(10, GoToWorkTask.create()),
                Pair.of(10, LoseUnimportantJobTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getSelfDefencePackage() {
        return ImmutableList.of(
                Pair.of(0, new PanicTask()),
                Pair.of(1, new EquipmentTask(VillagerTasksMCA::isInDanger, v -> EquipmentSet.NAKED)),
                Pair.of(2, new ExtendedMeleeAttackTask(15, 2.5F, MemoryModuleType.NEAREST_HOSTILE))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getGuardCorePackage(VillagerEntityMCA villager) {
        return ImmutableList.of(
                Pair.of(0, new ConditionalTask<>(
                        new PanicTask(),
                        VillagerTasksMCA::guardTooHurt
                )),
                Pair.of(0,
                        new SayTask("villager.retreat", 100, e -> VillagerTasksMCA.guardTooHurt(e) && e.getVillagerBrain().isPanicking())
                ),
                Pair.of(0,
                        new SayTask("villager.attack", 160, e -> !VillagerTasksMCA.guardTooHurt(e) && VillagerTasksMCA.getPreferredTarget(e).isPresent())
                ),
                // self-defence while fleeing
                Pair.of(0, new ConditionalTask<>(
                        new ExtendedMeleeAttackTask(15, 2.5F, MemoryModuleType.NEAREST_HOSTILE),
                        VillagerTasksMCA::guardTooHurt
                )),
                Pair.of(1, new EquipmentTask(VillagerTasksMCA::isOnDuty, v -> v.getResidency().getHomeVillage()
                        .map(vil -> vil.getVillageGuardsManager().getGuardEquipment(v.getProfession(), v.getDominantHand())).orElse(VillageGuardsManager.getEquipmentFor(v.getDominantHand(), EquipmentSet.GUARD_0, EquipmentSet.GUARD_0_LEFT)))),
                Pair.of(2, UpdateAttackTargetTask.create(t -> true, VillagerTasksMCA::getPreferredTarget)),
                Pair.of(3, ForgetAttackTargetTask.create(livingEntity -> !VillagerTasksMCA.isPreferredTarget(villager, livingEntity))),
                Pair.of(4, new BowTask<>(20, 12)),
                Pair.of(5, TaskTriggerer.runIf(v -> v.isHolding(Items.CROSSBOW),
                        AttackTask.create(5, 0.75F)
                )),
                Pair.of(6, RangedApproachTask.create(0.75F)),
                Pair.of(7, new ExtendedMeleeAttackTask(20, 2.0F)),
                Pair.of(8, new CrossbowAttackTask<VillagerEntityMCA, VillagerEntityMCA>())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getGuardWorkPackage() {
        return ImmutableList.of(
                Pair.of(10, new PatrolVillageTask(4, 0.4f)),
                Pair.of(99, ScheduleActivityTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getGuardPanicPackage(float speedModifier) {
        float f = speedModifier * 1.5F;
        return ImmutableList.of(
                Pair.of(1, StopPanickingTask.create()),
                Pair.of(2, GoToRememberedPositionTask.createEntityBased(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
                Pair.of(2, GoToRememberedPositionTask.createEntityBased(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
                Pair.of(3, FindWalkTargetTask.create(f, 2, 2)),
                getMinimalLookBehavior()
        );
    }

    private static boolean guardTooHurt(VillagerEntityMCA villager) {
        return villager.getHealth() < villager.getMaxHealth() * 0.25;
    }

    private static Optional<? extends LivingEntity> getPreferredTarget(VillagerEntityMCA villager) {
        if (guardTooHurt(villager)) {
            return Optional.empty();
        } else {
            Optional<LivingEntity> primary = villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get());
            if (primary.isPresent() && (getActivity(villager) != Activity.REST || primary.get().distanceTo(villager) < 8.0)) {
                return primary;
            } else {
                return villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    private static boolean isPreferredTarget(VillagerEntityMCA villager, LivingEntity entity) {
        Optional<? extends LivingEntity> target = getPreferredTarget(villager);
        return target.filter(livingEntity -> livingEntity == entity).isPresent();
    }

    public static boolean isOnDuty(VillagerEntityMCA villager) {
        return getActivity(villager) == Activity.WORK || villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    public static boolean isInDanger(VillagerEntityMCA villager) {
        return villager.getVillagerBrain().isPanicking()
                || villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static Activity getActivity(VillagerEntityMCA villager) {
        return villager.getBrain().getSchedule().getActivityForTime((int) (villager.getWorld().getTimeOfDay() % 24000L));
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getGrievingPackage() {
        return ImmutableList.of(
                Pair.of(0, new SequenceTask<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT),
                        ImmutableList.of(
                                new EnterBuildingTask("graveyard", 0.5f),
                                new RandomTask<>(
                                        ImmutableList.of(
                                                Pair.of(new HoldItemTask(Hand.MAIN_HAND, Items.WHITE_TULIP), 1),
                                                Pair.of(new HoldItemTask(Hand.MAIN_HAND, Items.RED_TULIP), 1),
                                                Pair.of(new HoldItemTask(Hand.MAIN_HAND, Items.ORANGE_TULIP), 1),
                                                Pair.of(new HoldItemTask(Hand.MAIN_HAND, Items.PINK_TULIP), 1)
                                        )
                                ),
                                new WanderOrTeleportToTargetTask(),
                                new WaitTask(100, 300),
                                new SayTask("villager.grieving"),
                                new WaitTask(100, 300),
                                new SayTask("villager.grieving"),
                                new WaitTask(100, 300),
                                new SayTask("villager.grieving"),
                                new HoldItemTask(Hand.MAIN_HAND, ItemStack.EMPTY),
                                new LambdaTask<>((v) -> {
                                    v.getVillagerBrain().justGrieved();
                                    v.getBrain().refreshActivities(v.getWorld().getTimeOfDay(), v.getWorld().getTime());
                                })

                        )
                ))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getWorkPackage(VillagerProfession profession, float speedModifier) {
        VillagerWorkTask villagerWorkTask;
        if (profession == VillagerProfession.FARMER) {
            villagerWorkTask = new FarmerWorkTask();
        } else {
            villagerWorkTask = new VillagerWorkTask();
        }

        return ImmutableList.of(
                getMinimalLookBehavior(),
                Pair.of(5, new RandomTask<>(
                        ImmutableList.of(Pair.of(villagerWorkTask, 7),
                                Pair.of(GoToIfNearbyTask.create(MemoryModuleType.JOB_SITE, 0.4F, 4), 2),
                                Pair.of(GoToNearbyPositionTask.create(MemoryModuleType.JOB_SITE, 0.4F, 1, 10), 5),
                                Pair.of(GoToSecondaryPositionTask.create(MemoryModuleType.SECONDARY_JOB_SITE, speedModifier, 1, 6, MemoryModuleType.JOB_SITE), 5),
                                Pair.of(new FarmerVillagerTask(), profession == VillagerProfession.FARMER ? 2 : 5),
                                Pair.of(new BoneMealTask(), profession == VillagerProfession.FARMER ? 4 : 7))
                )),
                Pair.of(10, new HoldTradeOffersTask(400, 1600)),
                Pair.of(10, FindInteractionTargetTask.create(EntityType.PLAYER, 4)),
                Pair.of(2, VillagerWalkTowardsTask.create(MemoryModuleType.JOB_SITE, speedModifier, 9, 100, 1200)),
                Pair.of(3, new GiveGiftsToHeroTask(100)),
                Pair.of(99, ScheduleActivityTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getPlayPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new WanderAroundTask(80, 120)),
                getFullLookBehavior(),
                Pair.of(5, PlayWithVillagerBabiesTask.create()),
                Pair.of(5, new RandomTask<>(
                        ImmutableMap.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryModuleState.VALUE_ABSENT),
                        ImmutableList.of(
                                Pair.of(FindEntityTask.create(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                                Pair.of(FindEntityTask.create(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                                Pair.of(FindWalkTargetTask.create(speedModifier), 1),
                                Pair.of(GoTowardsLookTargetTask.create(speedModifier, 2), 1),
                                Pair.of(new JumpInBedTask(speedModifier), 2),
                                Pair.of(new WaitTask(20, 40), 2)
                        ))),
                Pair.of(99, ScheduleActivityTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getRestPackage(float speed) {
        return ImmutableList.of(
                // try to reach the bed, and if not a set home, forget if out of range
                Pair.of(2, ExtendedWalkTowardsTask.create(MemoryModuleType.HOME, speed, 1, 192, 1200, (v) -> {
                    Optional<Boolean> memory = v.getBrain().getOptionalMemory(MemoryModuleTypeMCA.FORCED_HOME.get());
                    boolean forced = memory != null && memory.isPresent();
                    if (forced) {
                        v.sendChatToAllAround("villager.cant_find_bed");
                    }
                    return !forced;
                }, v -> {
                    v.getResidency().seekHome();
                })),
                //verify the bed, occupancies state and similar
                Pair.of(3, new ConditionalSingleTickTask<>(ExtendedForgetCompletedPointOfInterestTask.create(
                        registryEntry -> registryEntry.matchesKey(PointOfInterestTypes.HOME), MemoryModuleType.HOME, (entity) -> {
                            // update villagers home/bed position
                            if (entity instanceof VillagerEntityMCA villager) {
                                villager.getResidency().seekHome();
                            }
                        }), (v) -> {
                    Optional<Boolean> memory = v.getBrain().getOptionalMemory(MemoryModuleTypeMCA.FORCED_HOME.get());
                    //noinspection OptionalAssignedToNull
                    return memory == null || memory.isEmpty();
                })),
                Pair.of(3, new SleepTask()),
                Pair.of(5, new RandomTask<>(ImmutableMap.of(MemoryModuleType.HOME, MemoryModuleState.VALUE_ABSENT), ImmutableList.of(
                        Pair.of(WalkHomeTask.create(speed), 1),
                        Pair.of(WanderIndoorsTask.create(speed), 4),
                        Pair.of(GoToPointOfInterestTask.create(speed, 4), 2),
                        Pair.of(new WaitTask(20, 40), 2)))),
                Pair.of(99, ScheduleActivityTask.create()));
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getMeetPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(2, new RandomTask<>(ImmutableList.of(
                        Pair.of(GoToIfNearbyTask.create(MemoryModuleType.MEETING_POINT, 0.4F, 40), 2),
                        Pair.of(MeetVillagerTask.create(), 2))
                )),
                Pair.of(10, new HoldTradeOffersTask(400, 1600)),
                Pair.of(10, FindInteractionTargetTask.create(EntityType.PLAYER, 4)),
                Pair.of(2, VillagerWalkTowardsTask.create(MemoryModuleType.MEETING_POINT, speedModifier, 6, 100, 200)),
                Pair.of(3, new GiveGiftsToHeroTask(100)),
                Pair.of(3, ForgetCompletedPointOfInterestTask.create(registryEntry -> registryEntry.matchesKey(PointOfInterestTypes.MEETING), MemoryModuleType.MEETING_POINT)),
                Pair.of(3, new CompositeTask<>(
                        ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        CompositeTask.Order.ORDERED,
                        CompositeTask.RunMode.RUN_ONE,
                        ImmutableList.of(Pair.of(new GatherItemsVillagerTask(), 1)) // GOSSIP TASK
                )),
                getFullLookBehavior(),
                Pair.of(99, ScheduleActivityTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getIdlePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(1, new EnterFavoredBuildingTask(0.5f)),
                Pair.of(2, new RandomTask<>(ImmutableList.of(
                        Pair.of(FindEntityTask.create(EntitiesMCA.FEMALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(FindEntityTask.create(EntitiesMCA.MALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(FindEntityTask.create(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                        Pair.of(FindWalkTargetTask.create(speedModifier), 1),
                        Pair.of(GoTowardsLookTargetTask.create(speedModifier, 2), 1),
                        Pair.of(new JumpInBedTask(speedModifier), 1),
                        Pair.of(new WaitTask(30, 60), 1))
                )),
                Pair.of(3, new GiveGiftsToHeroTask(100)),
                Pair.of(3, FindInteractionTargetTask.create(EntityType.PLAYER, 4)),
                Pair.of(3, new HoldTradeOffersTask(400, 1600)),
                Pair.of(3, new GrieveTask()),
                Pair.of(3, new CompositeTask<>(ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        CompositeTask.Order.ORDERED,
                        CompositeTask.RunMode.RUN_ONE,
                        ImmutableList.of(
                                Pair.of(new GatherItemsVillagerTask(), 1))
                )),
                getFullLookBehavior(),
                Pair.of(99, ScheduleActivityTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getPanicPackage(float speedModifier) {
        float f = speedModifier * 1.5F;
        return ImmutableList.of(
                Pair.of(0, StopPanickingTask.create()),
                Pair.of(1, GoToRememberedPositionTask.createEntityBased(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
                Pair.of(1, GoToRememberedPositionTask.createEntityBased(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
                Pair.of(3, FindWalkTargetTask.create(f, 2, 2)),
                getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getPreRaidPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, RingBellTask.create()),
                Pair.of(0, new RandomTask<>(ImmutableList.of(
                        Pair.of(VillagerWalkTowardsTask.create(MemoryModuleType.MEETING_POINT, speedModifier * 1.5F, 2, 150, 200), 6),
                        Pair.of(FindWalkTargetTask.create(speedModifier * 1.5F), 2))
                )),
                getMinimalLookBehavior(),
                Pair.of(99, EndRaidTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getRaidPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new RandomTask<>(ImmutableList.of(
                        Pair.of(SeekSkyTask.create(speedModifier), 5),
                        Pair.of(FindWalkTargetTask.create(speedModifier * 1.1F), 2)
                ))),
                Pair.of(0, new CelebrateRaidWinTask(600, 600)),
                Pair.of(2, HideInHomeTask.create(24, speedModifier * 1.4F, 1)),
                getMinimalLookBehavior(),
                Pair.of(99, EndRaidTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getHidePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, ForgetBellRingTask.create(15, 3)),
                Pair.of(1, HideInHomeTask.create(32, speedModifier * 1.25F, 2)),
                getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getChorePackage() {
        return ImmutableList.of(
                Pair.of(0, new ChoppingTask()),
                Pair.of(0, new FishingTask()),
                Pair.of(0, new HarvestingTask()),
                Pair.of(0, new HuntingTask())
        );
    }

    private static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getAdventurerPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(5, FindEntityTask.create(EntitiesMCA.FEMALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, FindEntityTask.create(EntitiesMCA.MALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, FindEntityTask.create(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, FindWalkTargetTask.create(speedModifier)),
                Pair.of(5, GoTowardsLookTargetTask.create(speedModifier, 2)),
                Pair.of(5, new EnterBuildingTask("inn", 0.5f))
        );
    }

    private static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntityMCA>>> getMercenaryPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(5, FindWalkTargetTask.create(speedModifier)),
                Pair.of(5, GoTowardsLookTargetTask.create(speedModifier, 2))
        );
    }

    // Reference: VillagerTaskListProvider#createFreeFollowTask
    private static Pair<Integer, Task<LivingEntity>> getFullLookBehavior() {
        return Pair.of(5, new RandomTask<>(ImmutableList.of(
                Pair.of(LookAtMobTask.create(EntityType.CAT, 8.0F), 8),
                Pair.of(LookAtMobTask.create(EntityType.VILLAGER, 8.0F), 2),
                Pair.of(LookAtMobTask.create(EntityType.PLAYER, 8.0F), 2),
                Pair.of(LookAtMobTask.create(SpawnGroup.CREATURE, 8.0F), 1),
                Pair.of(LookAtMobTask.create(SpawnGroup.WATER_CREATURE, 8.0F), 1),
                Pair.of(LookAtMobTask.create(SpawnGroup.WATER_AMBIENT, 8.0F), 1),
                Pair.of(LookAtMobTask.create(SpawnGroup.MONSTER, 8.0F), 1),
                Pair.of(new WaitTask(30, 60), 2)))
        );
    }

    // Reference: VillagerTaskListProvider#createBusyFollowTask
    private static Pair<Integer, Task<LivingEntity>> getMinimalLookBehavior() {
        return Pair.of(5, new RandomTask<>(ImmutableList.of(
                Pair.of(LookAtMobTask.create(EntityType.VILLAGER, 8.0F), 2),
                Pair.of(LookAtMobTask.create(EntityType.PLAYER, 8.0F), 2),
                Pair.of(new WaitTask(30, 60), 8)))
        );
    }
}
