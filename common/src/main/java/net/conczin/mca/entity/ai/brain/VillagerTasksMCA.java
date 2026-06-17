package net.conczin.mca.entity.ai.brain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.SensorsMCA;
import net.conczin.mca.entity.ai.brain.tasks.*;
import net.conczin.mca.entity.ai.brain.tasks.chore.ChoppingTask;
import net.conczin.mca.entity.ai.brain.tasks.chore.FishingTask;
import net.conczin.mca.entity.ai.brain.tasks.chore.HarvestingTask;
import net.conczin.mca.entity.ai.brain.tasks.chore.HuntingTask;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.villageComponents.VillageGuardsManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

public class VillagerTasksMCA {
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
            MemoryModuleTypeMCA.PLAYER_FOLLOWING,
            MemoryModuleTypeMCA.STAYING,
            MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY,
            MemoryModuleTypeMCA.WEARS_ARMOR,
            MemoryModuleTypeMCA.SMALL_BOUNTY,
            MemoryModuleTypeMCA.HIT_BY_PLAYER,
            MemoryModuleTypeMCA.LAST_GRIEVE,
            MemoryModuleTypeMCA.FORCED_HOME
    );

    public static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_PLAYERS,
            SensorType.NEAREST_ITEMS,
            SensorType.NEAREST_BED,
            SensorType.HURT_BY,
            SensorType.VILLAGER_HOSTILES,
            SensorType.SECONDARY_POIS,
            SensorType.GOLEM_DETECTED,
            SensorsMCA.VILLAGER_BABIES,
            SensorsMCA.EXPLODING_CREEPER,
            SensorsMCA.GUARD_ENEMIES
    );

    @SuppressWarnings("deprecation")
    public static Brain.Provider<VillagerEntityMCA> createProfile() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES, body -> java.util.List.of());
    }

    public static Brain<VillagerEntityMCA> initializeTasks(VillagerEntityMCA villager, Brain<VillagerEntityMCA> brain) {
        VillagerProfession profession = villager.getVillagerData().profession().value();
        AgeState age = AgeState.byCurrentAge(villager.getAge());

        boolean noDefault = false;

        if (brain.getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent()) {
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getStayingPackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5f), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            noDefault = true;
        } else if (brain.getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent()) {
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getFollowingPackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5f), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            noDefault = true;
        } else if (profession == ProfessionsMCA.MERCENARY) {
            brain.setSchedule(SchedulesMCA.GUESTS);
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.IDLE, VillagerTasksMCA.getMercenaryPackage(0.5f), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)), ImmutableSet.of());
            brain.addActivity(ActivitiesMCA.CHORE, VillagerTasksMCA.getChorePackage(), ImmutableSet.of(), ImmutableSet.of());
            noDefault = true;
        } else if (!villager.requiresHome()) {
            brain.setSchedule(SchedulesMCA.GUESTS);
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.IDLE, VillagerTasksMCA.getAdventurerPackage(0.5f), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)), ImmutableSet.of());
            noDefault = true;
        } else if (age == AgeState.BABY) {
            brain.setSchedule(EnvironmentAttributes.BABY_VILLAGER_ACTIVITY);
            //todo babies may get a little bit more AI
            return brain;
        } else if (age != AgeState.ADULT) {
            brain.setSchedule(EnvironmentAttributes.BABY_VILLAGER_ACTIVITY);
            brain.addActivity(Activity.PLAY, VillagerTasksMCA.getPlayPackage(1.0F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage(), ImmutableSet.of(), ImmutableSet.of());
        } else if (villager.isGuard()) {
            brain.setSchedule(SchedulesMCA.getTypeSchedule(villager, SchedulesMCA.GUARD, SchedulesMCA.GUARD_NIGHT));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getGuardCorePackage(villager), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.WORK, VillagerTasksMCA.getGuardWorkPackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getGuardPanicPackage(0.5f), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.RAID, VillagerTasksMCA.getGuardWorkPackage(), ImmutableSet.of(), ImmutableSet.of());
        } else {
            brain.setSchedule(SchedulesMCA.getTypeSchedule(villager));
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getWorkingCorePackage(profession, 0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(
                    Activity.WORK,
                    VillagerTasksMCA.getWorkPackage(profession, 0.5F),
                    ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)),
                    ImmutableSet.of()
            );
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getSelfDefencePackage(), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.RAID, VillagerTasksMCA.getRaidPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
        }

        brain.addActivity(ActivitiesMCA.GRIEVE, VillagerTasksMCA.getGrievingPackage(), ImmutableSet.of(), ImmutableSet.of());

        if (!noDefault) {
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getImportantCorePackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.CORE, VillagerTasksMCA.getCorePackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(
                    Activity.MEET,
                    VillagerTasksMCA.getMeetPackage(0.5F),
                    ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)),
                    ImmutableSet.of()
            );
            brain.addActivity(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)), ImmutableSet.of());
            brain.addActivity(Activity.IDLE, VillagerTasksMCA.getIdlePackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PANIC, VillagerTasksMCA.getPanicPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.PRE_RAID, VillagerTasksMCA.getPreRaidPackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(Activity.HIDE, VillagerTasksMCA.getHidePackage(0.5F), ImmutableSet.of(), ImmutableSet.of());
            brain.addActivity(ActivitiesMCA.CHORE, VillagerTasksMCA.getChorePackage(), ImmutableSet.of(), ImmutableSet.of());
        }

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(villager.level().environmentAttributes(), villager.level().getGameTime(), villager.position());

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

    @SuppressWarnings("unchecked")
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
                Pair.of(1, new EquipmentTask(VillagerTasksMCA::isOnDuty, v -> v.getResidency().getHomeVillage()
                        .map(village -> village.getVillageGuardsManager().getGuardEquipment(v.getProfession(), v.getDominantHand()))
                        .orElse(VillageGuardsManager.getEquipmentFor(v.getDominantHand(),
                                v.getProfession() == ProfessionsMCA.ARCHER ? EquipmentSet.ARCHER_0 : EquipmentSet.GUARD_0,
                                v.getProfession() == ProfessionsMCA.ARCHER ? EquipmentSet.ARCHER_0_LEFT : EquipmentSet.GUARD_0_LEFT)))),
                Pair.of(2, StartAttacking.create((level, body) -> true, (level, body) -> VillagerTasksMCA.getPreferredTarget(body))),
                Pair.of(3, StopAttackingIfTargetInvalid.create((level, livingEntity) -> !VillagerTasksMCA.isPreferredTarget(villager, livingEntity))),
                Pair.of(4, new BowTask<>(20, 12)),
                Pair.of(5, BehaviorBuilder.triggerIf(v -> v.isHolding(Items.CROSSBOW),
                        BackUpIfTooClose.create(5, 0.75F)
                )),
                Pair.of(6, new ConditionalTask<>(
                        SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.75F),
                        (VillagerEntityMCA v) -> !VillagerTasksMCA.isHoldingRangedWeapon(v)
                )),
                Pair.of(7, new ConditionalTask<>(
                        new ExtendedMeleeAttackTask(20, 2.0F),
                        (VillagerEntityMCA v) -> !VillagerTasksMCA.isHoldingRangedWeapon(v)
                )),
                Pair.of(8, new CrossbowAttack<VillagerEntityMCA, VillagerEntityMCA>())
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getGuardWorkPackage() {
        return ImmutableList.of(
                Pair.of(10, new PatrolVillageTask(4, 0.4f)),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
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
        } else {
            Optional<LivingEntity> primary = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            if (primary.isPresent() && shouldRespondToGuardEnemy(villager, primary.get())) {
                return primary;
            } else {
                return villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    private static boolean shouldRespondToGuardEnemy(VillagerEntityMCA villager, LivingEntity target) {
        return getActivity(villager) != Activity.REST
               || target.distanceTo(villager) < 8.0
               || villager.getResidency().getHomeVillage().filter(village -> village.isWithinBorder(villager)).isEmpty();
    }

    private static boolean isPreferredTarget(VillagerEntityMCA villager, LivingEntity entity) {
        Optional<? extends LivingEntity> target = getPreferredTarget(villager);
        return target.filter(livingEntity -> livingEntity == entity).isPresent();
    }

    public static boolean isOnDuty(VillagerEntityMCA villager) {
        return getActivity(villager) == Activity.WORK || villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static boolean isHoldingRangedWeapon(VillagerEntityMCA villager) {
        return villager.isHolding(Items.BOW) || villager.isHolding(Items.CROSSBOW);
    }

    public static boolean isInDanger(VillagerEntityMCA villager) {
        return villager.getVillagerBrain().isPanicking()
               || villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static Activity getActivity(VillagerEntityMCA villager) {
        return villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getGrievingPackage() {
        return ImmutableList.of(
                Pair.of(0, new SequenceTask<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                new EnterBuildingTask("graveyard", 0.5f),
                                new RunOne<>(
                                        ImmutableList.of(
                                                Pair.of(new HoldItemTask(InteractionHand.MAIN_HAND, Items.WHITE_TULIP), 1),
                                                Pair.of(new HoldItemTask(InteractionHand.MAIN_HAND, Items.RED_TULIP), 1),
                                                Pair.of(new HoldItemTask(InteractionHand.MAIN_HAND, Items.ORANGE_TULIP), 1),
                                                Pair.of(new HoldItemTask(InteractionHand.MAIN_HAND, Items.PINK_TULIP), 1)
                                        )
                                ),
                                new WanderOrTeleportToTargetTask(),
                                new DoNothing(100, 300),
                                new SayTask("villager.grieving"),
                                new DoNothing(100, 300),
                                new SayTask("villager.grieving"),
                                new DoNothing(100, 300),
                                new SayTask("villager.grieving"),
                                new HoldItemTask(InteractionHand.MAIN_HAND, ItemStack.EMPTY),
                                new LambdaTask<>((v) -> {
                                    v.getVillagerBrain().justGrieved();
                                    v.getBrain().updateActivityFromSchedule(v.level().environmentAttributes(), v.level().getGameTime(), v.position());
                                })

                        )
                ))
        );
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> getWorkPackage(VillagerProfession profession, float speedModifier) {
        boolean isFarmer = profession.equals(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FARMER).value());
        WorkAtPoi villagerWorkTask;
        if (isFarmer) {
            villagerWorkTask = new WorkAtComposter();
        } else {
            villagerWorkTask = new WorkAtPoi();
        }

        return ImmutableList.of(
                getMinimalLookBehavior(),
                Pair.of(5, new RunOne<>(
                        ImmutableList.of(Pair.of(villagerWorkTask, 7),
                                Pair.of(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 4), 2),
                                Pair.of(StrollToPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 1, 10), 5),
                                Pair.of(StrollToPoiList.create(MemoryModuleType.SECONDARY_JOB_SITE, speedModifier, 1, 6, MemoryModuleType.JOB_SITE), 5),
                                Pair.of(new HarvestFarmland(), isFarmer ? 2 : 5),
                                Pair.of(new UseBonemeal(), isFarmer ? 4 : 7))
                )),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
                Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, speedModifier, 9, 100, 1200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
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
                    Optional<Boolean> memory = v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME);
                    boolean forced = memory != null && memory.isPresent();
                    if (forced) {
                        v.sendChatToAllAround("villager.cant_find_bed");
                    }
                    return !forced;
                }, v -> {
                    v.getResidency().seekHome();
                }, ExtendedWalkTowardsTask::findBedStandPosition)),
                //verify the bed, occupancies state and similar
                Pair.of(3, new ConditionalSingleTickTask<>(ExtendedForgetCompletedPointOfInterestTask.create(
                        registryEntry -> registryEntry.is(PoiTypes.HOME), MemoryModuleType.HOME, (entity) -> {
                            // update villagers home/bed position
                            if (entity instanceof VillagerEntityMCA villager) {
                                villager.getResidency().seekHome();
                            }
                        }), (v) -> {
                    Optional<Boolean> memory = v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME);
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
                        Pair.of(InteractWith.of(EntitiesMCA.FEMALE_VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                        Pair.of(InteractWith.of(EntitiesMCA.MALE_VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
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
                Pair.of(5, InteractWith.of(EntitiesMCA.FEMALE_VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
                Pair.of(5, InteractWith.of(EntitiesMCA.MALE_VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2)),
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
