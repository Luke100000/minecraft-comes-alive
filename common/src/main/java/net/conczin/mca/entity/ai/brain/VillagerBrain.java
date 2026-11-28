package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.Config;
import net.conczin.mca.entity.Status;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.*;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.InteractTask;
import net.conczin.mca.entity.ai.brain.tasks.WalkTargetRetryGate;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.util.network.datasync.*;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Handles memory and complex bodily functions. Such as walking, and not being a nitwit.
 */
public class VillagerBrain<E extends Mob & VillagerLike<E>> {
    private static final CDataParameter<CompoundTag> MEMORIES = CParameter.create("Memories", new CompoundTag());
    private static final CResourceLocationParameter PERSONALITY = CParameter.create("Personality", Personality.UNASSIGNED.getId());
    private static final CDataParameter<Integer> MOOD = CParameter.create("Mood", 0);
    private static final CEnumParameter<MoveState> MOVE_STATE = CParameter.create("MoveState", MoveState.MOVE);
    private static final CEnumParameter<Chore> ACTIVE_CHORE = CParameter.create("ActiveChore", Chore.NONE);
    private static final CDataParameter<Optional<UUID>> CHORE_ASSIGNING_PLAYER = CParameter.create("ChoreAssigningPlayer", Optional.empty());
    private static final CDataParameter<Boolean> PANICKING = CParameter.create("IsPanicking", false);
    private static final CDataParameter<Boolean> WEAR_ARMOR = CParameter.create("WearArmor", false);

    private final E entity;
    private WalkTarget choreCooldownWalkTarget;
    private int activeChoreTicks;
    private int lastChoreClockTick = Integer.MIN_VALUE;
    @Nullable
    private Map<Object, WalkTargetRetryGate> walkTargetRetryGates;
    // WALK_TARGET denotes an untyped movement command; null denotes an unclaimed runtime failure.
    @Nullable
    private MemoryModuleType<?> failureProjectionOwner;

    public VillagerBrain(E entity) {
        this.entity = entity;
    }

    public WalkTargetRetryGate getOrCreateRetryGate(Object key, Supplier<WalkTargetRetryGate> factory) {
        if (walkTargetRetryGates == null) {
            walkTargetRetryGates = new WeakHashMap<>();
        }
        return walkTargetRetryGates.computeIfAbsent(key, ignored -> factory.get());
    }

    public static <E2 extends Entity> CDataManager.Builder<E2> createTrackedData(CDataManager.Builder<E2> builder) {
        return builder.addAll(
                MEMORIES,
                PERSONALITY,
                MOOD,
                MOVE_STATE,
                ACTIVE_CHORE,
                CHORE_ASSIGNING_PLAYER,
                PANICKING,
                WEAR_ARMOR
        );
    }

    public void think() {
        // When you relog, it should continue doing the chores.
        // Chore saves but Activity doesn't, so this checks if the activity is not on there and puts it on there.
        selectAssignedChoreActivity();

        // Cooldown time belongs to CHORE, not the wall-clock ticks spent sleeping or fighting.
        if (entity.tickCount != lastChoreClockTick) {
            lastChoreClockTick = entity.tickCount;
            if (entity.getBrain().isActive(ActivitiesMCA.CHORE)
                    && !entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && !isPanicking()) {
                activeChoreTicks++;
            }
            updateRetryGatePreemption();
        }

        boolean panicking = entity.getBrain().isActive(Activity.PANIC);
        if (panicking != entity.getTrackedValue(PANICKING)) {
            entity.setTrackedValue(PANICKING, panicking);
        }

        if (entity.tickCount % 20 == 0) {
            updateMoveState();
        }

        // decrease interaction fatigue
        if (entity.tickCount % Math.max(1, Config.getInstance().interactionFatigueCooldown) == 0) {
            CompoundTag nbt = entity.getTrackedValue(MEMORIES);
            if (nbt != null && !nbt.isEmpty()) {
                CompoundTag updated = null;
                for (String uuid : nbt.getAllKeys()) {
                    Memories memories = Memories.<E>fromCNBT(null, nbt.getCompound(uuid));
                    if (memories == null) {
                        continue;
                    }
                    int fatigue = memories.getInteractionFatigue();
                    if (fatigue > 0) {
                        memories.setInteractionFatigue(fatigue - 1);
                        if (updated == null) {
                            updated = nbt.copy();
                        }
                        String playerKey = memories.getPlayerUUID().toString();
                        if (!playerKey.equals(uuid)) {
                            updated.remove(uuid);
                        }
                        updated.put(playerKey, memories.toCNBT());
                    }
                }
                if (updated != null) {
                    entity.setTrackedValue(MEMORIES, updated);
                }
            }
        }
    }

    public Chore getCurrentJob() {
        return entity.getTrackedValue(ACTIVE_CHORE);
    }

    public int getActiveChoreTicks() {
        return activeChoreTicks;
    }

    private void updateRetryGatePreemption() {
        if (!(entity instanceof VillagerEntityMCA villager) || walkTargetRetryGates == null) {
            return;
        }
        long gameTime = villager.level().getGameTime();
        for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
            MemoryModuleType<GlobalPos> memory = gate.destinationMemory();
            GlobalPos assignment = gate.assignment();
            if (memory == null || assignment == null) {
                continue;
            }
            GlobalPos remembered = villager.getBrain().getMemoryInternal(memory).orElse(null);
            if (!assignment.equals(remembered) || assignment.dimension() != villager.level().dimension()) {
                WalkTargetFailureMemory.clearForProducer(villager, memory, assignment);
                continue;
            }
            gate.observePreemption(gameTime, isJourneyPreempted(villager, memory));
        }
    }

    @Nullable
    MemoryModuleType<?> failureProjectionOwner() {
        return failureProjectionOwner;
    }

    void setFailureProjectionOwner(@Nullable MemoryModuleType<?> owner) {
        failureProjectionOwner = owner;
    }

    private long establishedFailureSince(@Nullable MemoryModuleType<?> owner, GlobalPos target) {
        if (owner == null || walkTargetRetryGates == null) {
            return Long.MIN_VALUE;
        }
        long unresolvedSince = Long.MIN_VALUE;
        for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
            if (gate.destinationMemory() == owner && target.equals(gate.assignment())) {
                // A matching gate may have already closed a shared preemption interval.
                unresolvedSince = Math.max(unresolvedSince, gate.unresolvedFailureSince(target.pos()));
            }
        }
        return unresolvedSince;
    }

    long syncFailureToRetryGates(@Nullable MemoryModuleType<?> owner, GlobalPos target, long since) {
        if (owner == null || walkTargetRetryGates == null) {
            return since;
        }
        long unresolvedSince = establishedFailureSince(owner, target);
        long failureSince = unresolvedSince == Long.MIN_VALUE ? since : unresolvedSince;
        for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
            if (gate.destinationMemory() == owner && target.equals(gate.assignment())) {
                gate.noteFailure(target.pos(), failureSince);
            }
        }
        return failureSince;
    }

    long resumeFailureFromRetryGates(WalkTargetRetryGate resumed, GlobalPos target, long time,
                                     Optional<Long> projection) {
        MemoryModuleType<GlobalPos> owner = resumed.destinationMemory();
        if (owner != null && walkTargetRetryGates != null) {
            for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
                if (gate.destinationMemory() == owner && target.equals(gate.assignment())
                        && (gate != resumed || gate.unresolvedFailureSince(target.pos()) != Long.MIN_VALUE)) {
                    gate.observePreemption(time, false);
                }
            }
        }
        long failureSince = establishedFailureSince(owner, target);
        if (failureSince == Long.MIN_VALUE) {
            // Only when no matching gate owns a clock can the runtime projection initialize it.
            projection.ifPresent(since -> resumed.noteFailure(target.pos(), since));
            resumed.observePreemption(time, false);
            failureSince = resumed.unresolvedFailureSince(target.pos());
        } else {
            // The established clock already excludes the closed interval. Close the resumed
            // gate's own pause before adopting it, so that interval cannot be added twice.
            resumed.observePreemption(time, false);
        }
        if (failureSince != Long.MIN_VALUE && owner != null && walkTargetRetryGates != null) {
            for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
                if (gate.destinationMemory() == owner && target.equals(gate.assignment())) {
                    gate.adoptEstablishedFailure(target.pos(), failureSince);
                }
            }
        }
        return failureSince;
    }

    void clearProducerRetryGates(MemoryModuleType<?> owner, GlobalPos target, boolean endEpisode) {
        if (walkTargetRetryGates == null) {
            return;
        }
        for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
            if (gate.destinationMemory() == owner && target.equals(gate.assignment())) {
                if (endEpisode) {
                    gate.forgetAssignment();
                } else {
                    gate.clearFailure();
                }
            }
        }
    }

    void clearProjectedProducerFailure(GlobalPos target) {
        if (!(entity instanceof VillagerEntityMCA villager) || walkTargetRetryGates == null) {
            return;
        }
        for (WalkTargetRetryGate gate : walkTargetRetryGates.values()) {
            MemoryModuleType<GlobalPos> memory = gate.destinationMemory();
            if (memory != null && memory == failureProjectionOwner && target.equals(gate.assignment())
                    && !isJourneyPreempted(villager, memory)) {
                gate.clearFailure();
            }
        }
    }

    private boolean isJourneyPreempted(VillagerEntityMCA villager, MemoryModuleType<GlobalPos> destinationMemory) {
        var brain = villager.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || brain.isActive(Activity.PANIC)
                || villager.hasIgnitedCreeperThreat()
                || InteractTask.shouldRun(villager)
                || brain.hasMemoryValue(MemoryModuleTypeMCA.STAYING)
                || brain.hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING)) {
            return true;
        }
        return brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !ExtendedWalkTowardsTask.ownsWalkTarget(villager, destinationMemory);
    }

    public Optional<Player> getJobAssigner() {
        return entity.getTrackedValue(CHORE_ASSIGNING_PLAYER).map(id -> entity.level().getPlayerByUUID(id));
    }

    /**
     * Tells the villager to stop doing whatever it's doing.
     */
    public void abandonJob() {
        releaseChoreCooldownMovement();
        entity.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        entity.setTrackedValue(ACTIVE_CHORE, Chore.NONE);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.empty());

        resetsBrain();
    }

    /**
     * Assigns a job for the villager to do.
     */
    public void assignJob(Chore chore, Player player) {
        entity.setTrackedValue(ACTIVE_CHORE, chore);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.of(player.getUUID()));
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);

        resetsBrain();
        selectAssignedChoreActivity();
    }

    public void selectAssignedChoreActivity() {
        if (getCurrentJob() == Chore.NONE || entity.getBrain().isActive(Activity.PANIC)
                || entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return;
        }
        if (entity instanceof VillagerEntityMCA villager && villager.hasIgnitedCreeperThreat()) {
            if (entity.getBrain().isActive(ActivitiesMCA.CHORE)) {
                releaseChoreCooldownMovement();
                entity.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            }
            return;
        }

        long dayTime = entity.level().getDayTime();
        Activity scheduled = entity.getBrain().getSchedule().getActivityAt((int) (dayTime % 24000L));
        Activity desired = scheduled == Activity.REST ? Activity.REST : ActivitiesMCA.CHORE;
        if (desired == Activity.REST) {
            releaseChoreCooldownMovement();
        }
        entity.getBrain().getActiveNonCoreActivity().ifPresent(activity -> {
            if (activity != desired && activity != ActivitiesMCA.INTERACT) {
                entity.getBrain().setActiveActivityIfPossible(desired);
            }
        });
    }

    public void rememberChoreCooldownMovement(WalkTarget issued) {
        choreCooldownWalkTarget = issued;
    }

    private void releaseChoreCooldownMovement() {
        if (choreCooldownWalkTarget != null
                && entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null)
                == choreCooldownWalkTarget) {
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        choreCooldownWalkTarget = null;
    }

    public void randomize() {
        randomize(entity.getAgeState());
    }

    public void randomize(AgeState ageState) {
        setPersonality(Personality.getRandom(ageState));
        entity.setTrackedValue(MOOD, entity.level().random.nextInt(MoodGroup.MAX_LEVEL - MoodGroup.NORMAL_MIN_LEVEL + 1) + MoodGroup.NORMAL_MIN_LEVEL);
    }

    public void updateMemories(Memories memories) {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);

        nbt = nbt == null ? new CompoundTag() : nbt.copy();
        nbt.put(memories.getPlayerUUID().toString(), memories.toCNBT());
        entity.setTrackedValue(MEMORIES, nbt);
    }

    public Map<UUID, Memories> getMemories() {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);
        if (nbt == null || nbt.isEmpty()) {
            return new HashMap<>();
        }
        Map<UUID, Memories> memories = new HashMap<>();
        for (String uuid : nbt.getAllKeys()) {
            Memories entry = Memories.fromCNBT(entity, nbt.getCompound(uuid));
            if (entry != null) {
                memories.put(entry.getPlayerUUID(), entry);
            }
        }
        return memories;
    }

    public Memories getMemoriesForPlayer(Player player) {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);
        CompoundTag compoundTag = nbt == null ? null : nbt.getCompound(player.getUUID().toString());
        Memories returnMemories = Memories.fromCNBT(entity, compoundTag);
        if (returnMemories == null) {
            returnMemories = new Memories(this, player.level().getDayTime(), player.getUUID());
            if (!entity.level().isClientSide) {
                updateMemories(returnMemories);
            }
        }
        return returnMemories;
    }

    /** Reads the displayed relationship without creating or deserializing a memory. */
    public int getHeartsForPlayer(Player player) {
        CompoundTag memories = entity.getTrackedValue(MEMORIES);
        return memories == null ? 0 : memories.getCompound(player.getUUID().toString()).getInt("hearts");
    }

    public int getInteractionFatigueForPlayer(Player player) {
        CompoundTag memories = entity.getTrackedValue(MEMORIES);
        return memories == null ? 0 : memories.getCompound(player.getUUID().toString()).getInt("interactionFatigue");
    }

    public Personality getPersonality() {
        return Personality.get(getPersonalityId()).orElse(Personality.UNASSIGNED);
    }

    public ResourceLocation getPersonalityId() {
        return entity.getTrackedValue(PERSONALITY);
    }

    public void setPersonality(Personality personality) {
        entity.setTrackedValue(PERSONALITY, personality.getId());
    }

    public Mood getMood() {
        return MoodGroup.INSTANCE.getMood(entity.getTrackedValue(MOOD));
    }

    public boolean isPanicking() {
        return entity.getTrackedValue(PANICKING);
    }

    public void modifyMoodValue(int mood) {
        entity.setTrackedValue(MOOD, MoodGroup.clampMood(this.getMoodValue() + mood));
    }

    public int getMoodValue() {
        return entity.getTrackedValue(MOOD);
    }

    public MoveState getMoveState() {
        return entity.getTrackedValue(MOVE_STATE);
    }

    public void setMoveState(MoveState state, @Nullable Player leader) {
        Optional<LivingEntity> combatWalkTarget = getCombatWalkTargetToRestore(state);
        boolean refreshBrain = true;
        entity.setTrackedValue(MOVE_STATE, state);
        if (state == MoveState.MOVE) {
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);
        }
        if (state == MoveState.STAY) {
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
            entity.getBrain().setMemory(MemoryModuleTypeMCA.STAYING, true);
        }
        if (state == MoveState.FOLLOW) {
            entity.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING, leader);
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);
            abandonJob();
            refreshBrain = false;
        }

        if (refreshBrain) {
            resetsBrain();
        }
        combatWalkTarget.ifPresent(this::restoreCombatWalkTarget);
    }

    private Optional<LivingEntity> getCombatWalkTargetToRestore(MoveState state) {
        if (state == MoveState.FOLLOW && entity.asEntity() instanceof VillagerEntityMCA villager && villager.isGuard()) {
            return villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET)
                    .filter(target -> target.isAlive() && target.level() == villager.level());
        }
        return Optional.empty();
    }

    private void restoreCombatWalkTarget(LivingEntity target) {
        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        if (!RangedWeaponHelper.isHoldingSupportedWeapon(entity)) {
            entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 0.75F, 0));
        }
    }

    private void resetsBrain() {
        if (entity.asEntity() instanceof VillagerEntityMCA villager) {
            villager.refreshBrain((ServerLevel) villager.level());
        }
    }

    public boolean getArmorWear() {
        return entity.getTrackedValue(WEAR_ARMOR);
    }

    public void setArmorWear(boolean s) {
        entity.setTrackedValue(WEAR_ARMOR, s);
    }

    /**
     * Read the move state from the active memory.
     */
    public void updateMoveState() {
        if (getMoveState() == MoveState.FOLLOW && entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isEmpty()) {
            if (entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent()) {
                entity.setTrackedValue(MOVE_STATE, MoveState.STAY);
            } else {
                entity.setTrackedValue(MOVE_STATE, MoveState.MOVE);
            }
        }
    }

    public void rewardHearts(ServerPlayer player, int hearts) {
        Memories memory = entity.getVillagerBrain().getMemoriesForPlayer(player);

        if (hearts == 0) {
            return;
        }

        //spawn particles
        if (hearts > 0) {
            entity.level().broadcastEntityEvent(entity, Status.MCA_VILLAGER_POS_INTERACTION);
        } else {
            entity.level().broadcastEntityEvent(entity, Status.MCA_VILLAGER_NEG_INTERACTION);

            //sensitive people doubles the loss
            if (entity.getVillagerBrain().getPersonality() == Personality.SENSITIVE) {
                hearts *= 2;
            }
        }

        if (Config.getInstance().interactionFatigueEnabled) {
            memory.modInteractionFatigue(1);
        }
        memory.modHearts(hearts);
        CriterionMCA.HEARTS.trigger(player, memory.getHearts(), hearts, "interaction");
        entity.getVillagerBrain().modifyMoodValue(hearts);
    }
}
