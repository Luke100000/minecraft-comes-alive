package net.conczin.mca.entity.ai.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.ProfessionsMCA;
import net.conczin.mca.entity.EntitiesMCA;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ActivityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.conczin.mca.entity.ai.brain.tasks.*;
import net.mca.entity.ai.brain.tasks.*;
import net.conczin.mca.entity.ai.brain.tasks.chore.ChoppingTask;
import net.conczin.mca.entity.ai.brain.tasks.chore.FishingTask;
import net.conczin.mca.entity.ai.brain.tasks.chore.HarvestingTask;
import net.conczin.mca.entity.ai.brain.tasks.chore.HuntingTask;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.villageComponents.VillageGuardsManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.CelebrateVillagersSurvivedRaid;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.GoToClosestVillage;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.ai.behavior.GoToWantedItem;
import net.minecraft.world.entity.ai.behavior.InsideBrownianWalk;
import net.minecraft.world.entity.ai.behavior.InteractWith;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.JumpOnBed;
import net.minecraft.world.entity.ai.behavior.LocateHidingPlace;
import net.minecraft.world.entity.ai.behavior.LookAndFollowTradingPlayerSink;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToSkySeeingSpot;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.PlayTagWithOtherKids;
import net.minecraft.world.entity.ai.behavior.PoiCompetitorScan;
import net.minecraft.world.entity.ai.behavior.ReactToBell;
import net.minecraft.world.entity.ai.behavior.ResetRaidStatus;
import net.minecraft.world.entity.ai.behavior.RingBell;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetClosestHomeAsWalkTarget;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetHiddenState;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.SetRaidStatus;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.SocializeAtBell;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.TradeWithVillager;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.behavior.VillagerCalmDown;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.behavior.YieldJobSite;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import java.util.Optional;

public class VillagerTasksMCA {
    private static final float GRIEVING_WALK_SPEED = 0.5F;
    private static final int GRIEVING_PATH_TIMEOUT = 1200;

    public static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.MEETING_POINT,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.VISIBLE_VILLAGER_BABIES,
            MemoryModuleType.NEAREST_PLAYERS,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
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
            MemoryModuleTypeMCA.MOURNING_SITE.get(),
            MemoryModuleTypeMCA.MOURNING_POSITION.get(),
            MemoryModuleTypeMCA.FORCED_HOME.get()
    );

    public static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
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

    public static Brain.Provider<VillagerEntityMCA> createProfile() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    public static Brain<VillagerEntityMCA> initializeTasks(VillagerEntityMCA villager, Brain<VillagerEntityMCA> brain) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        AgeState age = AgeState.byCurrentAge(villager.getAge());

        boolean noDefault = false;

        if (brain.getMemoryInternal(MemoryModuleTypeMCA.STAYING.get()).isPresent()) {
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getStayingPackage());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5f));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            noDefault = true;
        } else if (brain.getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent()) {
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getFollowingPackage());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5f));
            if (villager.isGuard()) {
                brain.addActivity(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager));
                brain.addActivity(Activity.PANIC, VillagerTasksMCA.getGuardPanicPackage(0.5F));
            } else {
                brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
                brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            }
            noDefault = true;
        } else if (profession == ProfessionsMCA.MERCENARY.get()) {
            brain.setSchedule(SchedulesMCA.GUESTS);
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F));
            brain.addActivity(Activity.IDLE, VillagerTasksMCA.getMercenaryPackage(0.5f));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager));
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            brain.addActivityWithConditions(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)));
            brain.addActivity(ActivityMCA.CHORE.get(), VillagerTasksMCA.getChorePackage());
            noDefault = true;
        } else if (!villager.requiresHome()) {
            brain.setSchedule(SchedulesMCA.GUESTS);
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F));
            brain.addActivity(Activity.IDLE, VillagerTasksMCA.getAdventurerPackage(0.5f));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            brain.addActivityWithConditions(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)));
            noDefault = true;
        } else if (age == AgeState.BABY) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            //todo babies may get a little bit more AI
            return brain;
        } else if (age != AgeState.ADULT) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.addActivity(Activity.PLAY, VillagerTasksMCA.getPlayPackage(1.0F));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
        } else if (villager.isGuard()) {
            brain.setSchedule(SchedulesMCA.getTypeSchedule(villager, SchedulesMCA.GUARD, SchedulesMCA.GUARD_NIGHT));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager));
            brain.addActivity(Activity.WORK, VillagerTasksMCA.getGuardWorkPackage());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getGuardPanicPackage(0.5f));
            brain.addActivity(Activity.RAID, VillagerTasksMCA.getGuardWorkPackage());
        } else {
            brain.setSchedule(SchedulesMCA.getTypeSchedule(villager));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getWorkingCorePackage(profession, 0.5F));
            brain.addActivityWithConditions(Activity.WORK, VillagerTasksMCA.getWorkPackage(profession, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage());
            brain.addActivity(Activity.RAID, VillagerTasksMCA.getRaidPackage(0.5F));
        }

        brain.addActivity(ActivityMCA.GRIEVE.get(), VillagerTasksMCA.getGrievingPackage());

        if (!noDefault) {
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getCorePackage(0.5F));
            brain.addActivityWithConditions(Activity.MEET, VillagerTasksMCA.getMeetPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
            brain.addActivityWithConditions(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)));
            brain.addActivity(Activity.IDLE, VillagerTasksMCA.getIdlePackage(0.5F));
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F));
            brain.addActivity(Activity.PRE_RAID, VillagerTasksMCA.getPreRaidPackage(0.5F));
            brain.addActivity(Activity.HIDE, VillagerTasksMCA.getHidePackage(0.5F));
            brain.addActivity(ActivityMCA.CHORE.get(), VillagerTasksMCA.getChorePackage());
        }

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(villager.level().getDayTime(), villager.level().getGameTime());

        return brain;
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getStayingPackage() {
        return ImmutableList.of(
                Pair.of(0, new StayTask()),
                getFullLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getFollowingPackage() {
        return ImmutableList.of(
                Pair.of(0, new FollowTask()),
                getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getImportantCorePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Config.getInstance().useSmarterDoorAI ? Pair.of(0, new SmarterOpenDoorsTask()) : Pair.of(0, InteractWithDoor.create()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, WakeUp.create()),
                Pair.of(0, new DeliverMessageTask()),
                Pair.of(1, new WanderOrTeleportToTargetTask()),
                Pair.of(3, new InteractTask(speedModifier)),
                Pair.of(10, new ExtendedFindPointOfInterestTask(registryEntry -> registryEntry.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14), (villager) -> {
                    // update villagers home/bed position
                    villager.getResidency().seekHome();
                }, (entity, pos) -> {
                    // verify that this bed is not blocked
                    VillageManager manager = VillageManager.get((ServerLevel) entity.level());
                    if (entity.requiresHome()) {
                        return manager.findNearestVillage(entity).filter(v -> !v.isPositionValidBed(pos)).isEmpty();
                    } else {
                        //villagers without the need of a home may only settle in inns
                        return manager.findNearestVillage(entity).filter(v -> v.getBuildingAt(pos).filter(b -> b.getBuildingType().name().equals("inn")).isPresent()).isPresent();
                    }
                }))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getCorePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new GreetPlayerTask()),
                Pair.of(0, ReactToBell.create()),
                Pair.of(0, SetRaidStatus.create()),
                Pair.of(5, GoToWantedItem.create(speedModifier, false, 4)),
                Pair.of(10, new ExtendedFindPointOfInterestTask(registryEntry -> registryEntry.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14), (villager) -> {
                    // update villagers home/bed position
                    villager.getResidency().seekHome();
                }, (entity, pos) -> {
                    // verify that this bed is not blocked
                    VillageManager manager = VillageManager.get((ServerLevel) entity.level());
                    return manager.findNearestVillage(entity).filter(v -> {
                        return v.getBuildingAt(pos).filter(b -> b.getBuildingType().noBeds()).isPresent();
                    }).isEmpty();
                })),
                Pair.of(10, new ExtendedFindPointOfInterestTask(registryEntry -> registryEntry.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14), (villager) -> {
                    //report a town bell, the only building always added
                    villager.getBrain().getMemoryInternal(MemoryModuleType.MEETING_POINT).ifPresent(p -> {
                        if (villager.level().dimension() == p.dimension()) {
                            VillageManager manager = VillageManager.get((ServerLevel) villager.level());
                            if (!manager.cache.contains(p.pos())) {
                                manager.cache.add(p.pos());
                                manager.processBuilding(p.pos());
                            }

                            villager.getResidency().seekHome();
                        }
                    });
                }))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getWorkingCorePackage(VillagerProfession profession, float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, ValidateNearbyPoi.create(profession.heldJobSite(), MemoryModuleType.JOB_SITE)),
                Pair.of(0, ValidateNearbyPoi.create(profession.acquirableJobSite(), MemoryModuleType.POTENTIAL_JOB_SITE)),
                Pair.of(2, PoiCompetitorScan.create()),
                Pair.of(3, new LookAndFollowTradingPlayerSink(speedModifier)),
                Pair.of(6, LazyFindPointOfInterestTask.create(profession.acquirableJobSite(), MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, true, Optional.empty())),
                Pair.of(7, new GoToPotentialJobSite(speedModifier)),
                Pair.of(8, YieldJobSite.create(speedModifier)),
                Pair.of(10, AssignProfessionFromJobSite.create()),
                Pair.of(10, LoseUnimportantJobTask.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getSelfDefencePackage() {
        return ImmutableList.of(
                Pair.of(0, new VillagerPanicTrigger()),
                Pair.of(1, new EquipmentTask(VillagerTasksMCA::isInDanger, v -> EquipmentSet.NAKED)),
                Pair.of(2, new ExtendedMeleeAttackTask(15, 2.5F, MemoryModuleType.NEAREST_HOSTILE))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getGuardCorePackage(VillagerEntityMCA villager) {
        return ImmutableList.of(
                Pair.of(0, new ConditionalTask<>(
                        new VillagerPanicTrigger(),
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
                Pair.of(1, new EquipmentTask(VillagerTasksMCA::shouldWearGuardEquipment, v -> v.getResidency().getHomeVillage()
                        .map(vil -> vil.getVillageGuardsManager().getGuardEquipment(v.getProfession(), v.getDominantHand()))
                        .orElseGet(() -> v.getProfession() == ProfessionsMCA.ARCHER.get()
                                ? VillageGuardsManager.getEquipmentFor(v.getDominantHand(), EquipmentSet.ARCHER_0, EquipmentSet.ARCHER_0_LEFT)
                                : VillageGuardsManager.getEquipmentFor(v.getDominantHand(), EquipmentSet.GUARD_0, EquipmentSet.GUARD_0_LEFT)))),
                Pair.of(2, StartAttacking.create(t -> true, VillagerTasksMCA::getPreferredTarget)),
                Pair.of(3, StopAttackingIfTargetInvalid.create(
                        livingEntity -> !VillagerTasksMCA.isPreferredTarget(villager, livingEntity),
                        VillagerTasksMCA::onGuardTargetErased,
                        false
                )),
                Pair.of(4, new ArcherMovementTask<>(15)),
                Pair.of(5, new BowTask<>(20, 15)),
                Pair.of(7, new ConditionalTask<>(
                        SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.75F),
                        (VillagerEntityMCA v) -> !VillagerTasksMCA.isHoldingRangedWeapon(v)
                )),
                Pair.of(8, new ConditionalTask<>(
                        new ExtendedMeleeAttackTask(20, 2.0F),
                        (VillagerEntityMCA v) -> !VillagerTasksMCA.isHoldingRangedWeapon(v)
                )),
                Pair.of(9, new ExtendedCrossbowAttackTask<VillagerEntityMCA, VillagerEntityMCA>())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getGuardWorkPackage() {
        return ImmutableList.of(
                Pair.of(10, new PatrolVillageTask(4, 0.4f)),
                Pair.of(10, new ConditionalTask<>(
                        VillageBoundRandomStroll.create(0.4f),
                        VillagerTasksMCA::shouldUseHomelessGuardStroll
                )),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    private static boolean shouldUseHomelessGuardStroll(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().isEmpty()
                && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
                && villager.getBrain().getMemoryInternal(MemoryModuleType.INTERACTION_TARGET).isEmpty();
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getGuardPanicPackage(float speedModifier) {
        float f = speedModifier * 1.5F;
        return ImmutableList.of(
                Pair.of(1, VillagerCalmDown.create()),
                Pair.of(2, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
                Pair.of(2, SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
                Pair.of(3, VillageBoundRandomStroll.create(f, 2, 2)),
                getMinimalLookBehavior()
        );
    }

    private static boolean guardTooHurt(VillagerEntityMCA villager) {
        return villager.getHealth() < villager.getMaxHealth() * 0.25;
    }

    private static Optional<? extends LivingEntity> getPreferredTarget(VillagerEntityMCA villager) {
        if (guardTooHurt(villager)) {
            return Optional.empty();
        }

        Optional<LivingEntity> current = villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        if (current.isPresent() && shouldKeepAttackTarget(villager, current.get())) {
            return current;
        }

        Optional<LivingEntity> primary = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get());
        if (primary.isPresent() && shouldRespondToGuardEnemy(villager, primary.get())) {
            return primary;
        }

        return Optional.empty();
    }

    private static boolean shouldKeepAttackTarget(VillagerEntityMCA villager, LivingEntity target) {
        return target.isAlive()
                && !target.isRemoved()
                && target.level() == villager.level()
                && villager.canAttack(target);
    }

    private static boolean shouldRespondToGuardEnemy(VillagerEntityMCA villager, LivingEntity target) {
        return shouldKeepAttackTarget(villager, target)
                && GuardEnemiesSensor.isGuardEnemy(target, villager)
                && shouldRespondToAttackTarget(villager, target);
    }

    private static boolean shouldRespondToAttackTarget(VillagerEntityMCA villager, LivingEntity target) {
        return isFollowingPlayer(villager)
                || getActivity(villager) != Activity.REST
                || target.distanceTo(villager) < 8.0F
                || isOutsideHomeVillage(villager);
    }

    private static boolean isOutsideHomeVillage(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .filter(village -> village.isWithinBorder(villager))
                .isEmpty();
    }

    private static boolean isFollowingPlayer(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent();
    }

    private static void onGuardTargetErased(VillagerEntityMCA villager, LivingEntity target) {
        if (target instanceof Player && !target.isAlive()) {
            villager.pardonPlayers(Integer.MAX_VALUE);
        }
    }

    private static boolean isPreferredTarget(VillagerEntityMCA villager, LivingEntity entity) {
        Optional<? extends LivingEntity> target = getPreferredTarget(villager);
        return target.filter(livingEntity -> livingEntity == entity).isPresent();
    }

    public static boolean isOnDuty(VillagerEntityMCA villager) {
        return getActivity(villager) == Activity.WORK
                || villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent()
                || getPreferredTarget(villager).isPresent();
    }

    private static boolean shouldWearGuardEquipment(VillagerEntityMCA villager) {
        return isOnDuty(villager)
                || villager.isGuard() && (isFollowingPlayer(villager) || isOutsideHomeVillage(villager));
    }

    private static boolean isHoldingRangedWeapon(VillagerEntityMCA villager) {
        return RangedWeaponHelper.isHoldingSupportedWeapon(villager);
    }

    public static boolean isInDanger(VillagerEntityMCA villager) {
        return villager.getVillagerBrain().isPanicking()
               || villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static Activity getActivity(VillagerEntityMCA villager) {
        return villager.getBrain().getSchedule().getActivityAt((int) (villager.level().getDayTime() % 24000L));
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getGrievingPackage() {
        MournAtGraveTask mournAtGrave = new MournAtGraveTask();
        return ImmutableList.of(
                Pair.of(2, ExtendedWalkTowardsTask.create(
                        MemoryModuleTypeMCA.MOURNING_POSITION.get(),
                        GRIEVING_WALK_SPEED,
                        0,
                        Config.getInstance().getVillagerPathfindingDistance(),
                        GRIEVING_PATH_TIMEOUT,
                        villager -> true,
                        villager -> {
                            if (MCA.platformHelper.isDevelopmentEnvironment()) {
                                MCA.LOGGER.info("[MOURNING_TRACE_V3] path-timeout villager={} position={} grave={}",
                                        villager.getName().getString(),
                                        villager.blockPosition(),
                                        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null));
                            }
                        },
                        villager -> !mournAtGrave.hasArrived()
                )),
                Pair.of(0, new SequenceTask<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                new EnterGraveyardTask(GRIEVING_WALK_SPEED),
                                mournAtGrave,
                                new LambdaTask<>((v) -> {
                                    boolean completed = mournAtGrave.hasCompleted();
                                    boolean hadAssignedSite = v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get()).isPresent();
                                    boolean targetStillMournable = EnterGraveyardTask.hasMournableSite(v);
                                    boolean periodicCandidateStillExists = !hadAssignedSite
                                            && EnterGraveyardTask.hasPeriodicMourningCandidate(v);
                                    if (MCA.platformHelper.isDevelopmentEnvironment()) {
                                        MCA.LOGGER.info("[MOURNING_TRACE_V3] session-finish villager={} completed={} position={} grave={} stand={} walkTarget={}",
                                                v.getName().getString(),
                                                completed,
                                                v.blockPosition(),
                                                v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null),
                                                v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION.get()).orElse(null),
                                                v.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null));
                                    }
                                    v.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE.get());
                                    v.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION.get());
                                    if (completed) {
                                        v.getVillagerBrain().justGrieved();
                                    } else if (targetStillMournable || periodicCandidateStillExists) {
                                        v.getVillagerBrain().retryGrievingLater();
                                    } else {
                                        // The grave was emptied, removed, or entered resurrection.
                                        // Cancel this mourning cycle instead of repeatedly retargeting it.
                                        v.getVillagerBrain().justGrieved();
                                    }
                                    v.getBrain().updateActivityFromSchedule(v.level().getDayTime(), v.level().getGameTime());
                                })

                        )
                ))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getWorkPackage(VillagerProfession profession, float speedModifier) {
        // Keep MCA's public package method, but source the task list from vanilla so mods that
        // transform VillagerTaskListProvider#createWorkTasks also affect MCA villagers.
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> tasks = ImmutableList.builder();
        for (Pair<Integer, ? extends BehaviorControl<? super Villager>> task : VillagerGoalPackages.getWorkPackage(profession, speedModifier)) {
            tasks.add(Pair.of(task.getFirst(), task.getSecond()));
        }
        return tasks.build();
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getPlayPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new MoveToTargetSink(80, 120)),
                getFullLookBehavior(),
                Pair.of(5, PlayTagWithOtherKids.create()),
                Pair.of(5, new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                Pair.of(InteractWith.of(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                                Pair.of(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                                Pair.of(VillageBoundRandomStroll.create(speedModifier), 1),
                                Pair.of(SetWalkTargetFromLookTarget.create(speedModifier, 2), 1),
                                Pair.of(new JumpOnBed(speedModifier), 2),
                                Pair.of(new DoNothing(20, 40), 2)
                        ))),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getRestPackage(float speed) {
        return ImmutableList.of(
                // try to reach the bed, and if not a set home, forget if out of range
                Pair.of(2, ExtendedWalkTowardsTask.create(MemoryModuleType.HOME, speed, 1, Config.getInstance().getVillagerPathfindingDistance(), 1200, (v) -> {
                    Optional<Boolean> memory = v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME.get());
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
                        registryEntry -> registryEntry.is(PoiTypes.HOME), MemoryModuleType.HOME, (entity) -> {
                            // update villagers home/bed position
                            if (entity instanceof VillagerEntityMCA villager) {
                                villager.getResidency().seekHome();
                            }
                        }), (v) -> {
                    Optional<Boolean> memory = v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME.get());
                    //noinspection OptionalAssignedToNull
                    return memory == null || memory.isEmpty();
                })),
                Pair.of(3, new SleepInBed()),
                Pair.of(5, new RunOne<>(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT), ImmutableList.of(
                        Pair.of(SetClosestHomeAsWalkTarget.create(speed), 1),
                        Pair.of(InsideBrownianWalk.create(speed), 4),
                        Pair.of(GoToClosestVillage.create(speed, 4), 2),
                        Pair.of(new DoNothing(20, 40), 2)))),
                Pair.of(99, UpdateActivityFromSchedule.create()));
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getMeetPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(2, new RunOne<>(ImmutableList.of(
                        Pair.of(StrollAroundPoi.create(MemoryModuleType.MEETING_POINT, 0.4F, 40), 2),
                        Pair.of(SocializeAtBell.create(), 2))
                )),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
                Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, speedModifier, 6, 100, 200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, ValidateNearbyPoi.create(registryEntry -> registryEntry.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT)),
                Pair.of(3, new GateBehavior<>(
                        ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        GateBehavior.OrderPolicy.ORDERED,
                        GateBehavior.RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new TradeWithVillager(), 1)) // GOSSIP TASK
                )),
                getFullLookBehavior(),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getIdlePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(1, new EnterFavoredBuildingTask(0.5f)),
                Pair.of(2, new RunOne<>(ImmutableList.of(
                        Pair.of(InteractWith.of(EntitiesMCA.FEMALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(InteractWith.of(EntitiesMCA.MALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                        Pair.of(VillageBoundRandomStroll.create(speedModifier), 1),
                        Pair.of(SetWalkTargetFromLookTarget.create(speedModifier, 2), 1),
                        Pair.of(new JumpOnBed(speedModifier), 1),
                        Pair.of(new DoNothing(30, 60), 1))
                )),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, SetLookAndInteract.create(EntityType.PLAYER, 4)),
                Pair.of(3, new ShowTradesToPlayer(400, 1600)),
                Pair.of(3, new GrieveTask()),
                Pair.of(3, new GateBehavior<>(ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        GateBehavior.OrderPolicy.ORDERED,
                        GateBehavior.RunningPolicy.RUN_ONE,
                        ImmutableList.of(
                                Pair.of(new TradeWithVillager(), 1))
                )),
                getFullLookBehavior(),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getPanicPackage(float speedModifier) {
        float f = speedModifier * 1.5F;
        return ImmutableList.of(
                Pair.of(0, VillagerCalmDown.create()),
                Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
                Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
                Pair.of(3, VillageBoundRandomStroll.create(f, 2, 2)),
                getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getPreRaidPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, RingBell.create()),
                Pair.of(0, new RunOne<>(ImmutableList.of(
                        Pair.of(SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, speedModifier * 1.5F, 2, 150, 200), 6),
                        Pair.of(VillageBoundRandomStroll.create(speedModifier * 1.5F), 2))
                )),
                getMinimalLookBehavior(),
                Pair.of(99, ResetRaidStatus.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getRaidPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, new RunOne<>(ImmutableList.of(
                        Pair.of(MoveToSkySeeingSpot.create(speedModifier), 5),
                        Pair.of(VillageBoundRandomStroll.create(speedModifier * 1.1F), 2)
                ))),
                Pair.of(0, new CelebrateVillagersSurvivedRaid(600, 600)),
                Pair.of(2, LocateHidingPlace.create(24, speedModifier * 1.4F, 1)),
                getMinimalLookBehavior(),
                Pair.of(99, ResetRaidStatus.create())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getHidePackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(0, SetHiddenState.create(15, 3)),
                Pair.of(1, LocateHidingPlace.create(32, speedModifier * 1.25F, 2)),
                getMinimalLookBehavior()
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getChorePackage() {
        return ImmutableList.of(
                Pair.of(0, new ChoppingTask()),
                Pair.of(0, new FishingTask()),
                Pair.of(0, new HarvestingTask()),
                Pair.of(0, new HuntingTask())
        );
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getAdventurerPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(5, InteractWith.of(EntitiesMCA.FEMALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, InteractWith.of(EntitiesMCA.MALE_VILLAGER.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, VillageBoundRandomStroll.create(speedModifier)),
                Pair.of(5, SetWalkTargetFromLookTarget.create(speedModifier, 2)),
                Pair.of(5, new EnterBuildingTask("inn", 0.5f))
        );
    }

    private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getMercenaryPackage(float speedModifier) {
        return ImmutableList.of(
                Pair.of(5, VillageBoundRandomStroll.create(speedModifier)),
                Pair.of(5, SetWalkTargetFromLookTarget.create(speedModifier, 2))
        );
    }

    // Reference: VillagerTaskListProvider#createFreeFollowTask
    private static Pair<Integer, BehaviorControl<LivingEntity>> getFullLookBehavior() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(SetEntityLookTarget.create(EntityType.CAT, 8.0F), 8),
                Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
                Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                Pair.of(SetEntityLookTarget.create(MobCategory.CREATURE, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.WATER_CREATURE, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.WATER_AMBIENT, 8.0F), 1),
                Pair.of(SetEntityLookTarget.create(MobCategory.MONSTER, 8.0F), 1),
                Pair.of(new DoNothing(30, 60), 2)))
        );
    }

    // Reference: VillagerTaskListProvider#createBusyFollowTask
    private static Pair<Integer, BehaviorControl<LivingEntity>> getMinimalLookBehavior() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(SetEntityLookTarget.create(EntityType.VILLAGER, 8.0F), 2),
                Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                Pair.of(new DoNothing(30, 60), 8)))
        );
    }
}
