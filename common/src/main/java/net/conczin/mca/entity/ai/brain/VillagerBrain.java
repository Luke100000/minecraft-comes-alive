package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.Config;
import net.conczin.mca.entity.Status;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.*;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CEnumParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static net.conczin.mca.entity.ai.MemoryModuleTypeMCA.LAST_GRIEVE;

/**
 * Handles memory and complex bodily functions. Such as walking, and not being a nitwit.
 */
public class VillagerBrain<E extends Mob & VillagerLike<E>> {
    private static final CDataParameter<CompoundTag> MEMORIES = CParameter.create("Memories", new CompoundTag());
    private static final CEnumParameter<Personality> PERSONALITY = CParameter.create("Personality", Personality.UNASSIGNED);
    private static final CDataParameter<Integer> MOOD = CParameter.create("Mood", 0);
    private static final CEnumParameter<MoveState> MOVE_STATE = CParameter.create("MoveState", MoveState.MOVE);
    private static final CEnumParameter<Chore> ACTIVE_CHORE = CParameter.create("ActiveChore", Chore.NONE);
    private static final CDataParameter<Optional<UUID>> CHORE_ASSIGNING_PLAYER = CParameter.create("ChoreAssigningPlayer", Optional.empty());
    private static final CDataParameter<Boolean> PANICKING = CParameter.create("IsPanicking", false);
    private static final CDataParameter<Boolean> WEAR_ARMOR = CParameter.create("WearArmor", false);

    private static final int PANIC_ANIMATION_HOLD_TICKS = 40;
    private static final float PANIC_ANIMATION_STEP = 0.25F;
    private static final long GRIEVE_COOLDOWN = 24000 * 7;
    private final Random random = new Random();
    private final E entity;
    private int panicAnimationHoldTicks;
    private float panicAnimationProgress;
    private float panicAnimationProgressO;

    public VillagerBrain(E entity) {
        this.entity = entity;
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

        if (entity.getTrackedValue(ACTIVE_CHORE) != Chore.NONE) {
            // find something to do
            //todo here switch between rest and chore
            entity.getBrain().getActiveNonCoreActivity().ifPresent(activity -> {
                if (!activity.equals(ActivitiesMCA.CHORE)) {
                    entity.getBrain().setActiveActivityIfPossible(ActivitiesMCA.CHORE);
                }
            });
        }

        boolean panicking = entity.getBrain().isActive(Activity.PANIC);
        if (panicking != entity.getTrackedValue(PANICKING)) {
            entity.setTrackedValue(PANICKING, panicking);
        }

        if (entity.tickCount % 20 != 0) {
            updateMoveState();
        }

        // decrease interaction fatigue
        if (entity.tickCount % Math.max(1, Config.getInstance().interactionFatigueCooldown) == 0) {
            CompoundTag nbt = entity.getTrackedValue(MEMORIES);
            if (nbt != null) {
                for (String uuid : nbt.keySet()) {
                    Memories memories = Memories.fromCNBT(entity, nbt.getCompound(uuid).orElseGet(CompoundTag::new));
                    int fatigue = memories.getInteractionFatigue();
                    if (fatigue > 0) {
                        memories.setInteractionFatigue(fatigue - 1);
                    }
                }
            }
        }
    }

    public Chore getCurrentJob() {
        return entity.getTrackedValue(ACTIVE_CHORE);
    }

    public Optional<Player> getJobAssigner() {
        return entity.getTrackedValue(CHORE_ASSIGNING_PLAYER).map(id -> entity.level().getPlayerByUUID(id));
    }

    /**
     * Tells the villager to stop doing whatever it's doing.
     */
    public void abandonJob() {
        entity.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        entity.setTrackedValue(ACTIVE_CHORE, Chore.NONE);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.empty());

        resetsBrain();
    }

    /**
     * Assigns a job for the villager to do.
     */
    public void assignJob(Chore chore, Player player) {
        entity.getBrain().setActiveActivityIfPossible(ActivitiesMCA.CHORE);
        entity.setTrackedValue(ACTIVE_CHORE, chore);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.of(player.getUUID()));
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);

        resetsBrain();
    }

    public void randomize() {
        randomize(entity.getAgeState());
    }

    public void randomize(AgeState ageState) {
        entity.setTrackedValue(PERSONALITY, Personality.getRandom(ageState));
        entity.setTrackedValue(MOOD, entity.getRandom().nextInt(MoodGroup.MAX_LEVEL - MoodGroup.NORMAL_MIN_LEVEL + 1) + MoodGroup.NORMAL_MIN_LEVEL);
    }

    public void updateMemories(Memories memories) {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);

        nbt = nbt == null ? new CompoundTag() : nbt.copy();
        nbt.put(memories.getPlayerUUID().toString(), memories.toCNBT());
        entity.setTrackedValue(MEMORIES, nbt);
    }

    public Map<UUID, Memories> getMemories() {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);
        Map<UUID, Memories> memories = new HashMap<>();
        for (String uuid : nbt.keySet()) {
            memories.put(UUID.fromString(uuid), Memories.fromCNBT(entity, nbt.getCompound(uuid).orElseGet(CompoundTag::new)));
        }
        return memories;
    }

    public Memories getMemoriesForPlayer(Player player) {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);
        nbt = nbt == null ? new CompoundTag() : nbt;
        CompoundTag compoundTag = nbt.getCompound(player.getUUID().toString()).orElseGet(CompoundTag::new);
        Memories returnMemories = Memories.fromCNBT(entity, compoundTag);
        if (returnMemories == null) {
            returnMemories = new Memories(this, player.level().getGameTime(), player.getUUID());
            nbt.put(player.getUUID().toString(), returnMemories.toCNBT());
            entity.setTrackedValue(MEMORIES, nbt);
        }
        return returnMemories;
    }

    public Personality getPersonality() {
        return entity.getTrackedValue(PERSONALITY);
    }

    public void setPersonality(Personality p) {
        entity.setTrackedValue(PERSONALITY, p);
    }

    public Mood getMood() {
        return MoodGroup.INSTANCE.getMood(entity.getTrackedValue(MOOD));
    }

    public boolean isPanicking() {
        return entity.getTrackedValue(PANICKING);
    }

    public void tickPanicAnimation() {
        panicAnimationProgressO = panicAnimationProgress;

        if (isPanicking() || hasPanicStimulus()) {
            panicAnimationHoldTicks = PANIC_ANIMATION_HOLD_TICKS;
        } else if (panicAnimationHoldTicks > 0) {
            panicAnimationHoldTicks--;
        }

        float target = panicAnimationHoldTicks > 0 ? 1.0F : 0.0F;
        float delta = Mth.clamp(target - panicAnimationProgress, -PANIC_ANIMATION_STEP, PANIC_ANIMATION_STEP);
        panicAnimationProgress = Mth.clamp(panicAnimationProgress + delta, 0.0F, 1.0F);
    }

    public float getPanicAnimationProgress(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, panicAnimationProgressO, panicAnimationProgress), 0.0F, 1.0F);
    }

    private boolean hasPanicStimulus() {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
               || entity.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
               || entity.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
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
        }

        resetsBrain();
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

    public void setGrieving() {
        entity.getBrain().setMemory(LAST_GRIEVE, -GRIEVE_COOLDOWN);
    }

    public void justGrieved() {
        entity.getBrain().setMemory(LAST_GRIEVE, entity.level().getGameTime());
    }

    public boolean shouldGrieve() {
        Optional<Long> memory = entity.getBrain().getMemoryInternal(LAST_GRIEVE);
        if (memory.isPresent()) {
            return entity.level().getGameTime() - memory.get() > GRIEVE_COOLDOWN;
        } else {
            entity.getBrain().setMemory(LAST_GRIEVE, entity.level().getGameTime() - random.nextLong(GRIEVE_COOLDOWN));
            return false;
        }
    }

    /**
     * Read the move state from the active memory.
     */
    public void updateMoveState() {
        if (getMoveState() == MoveState.FOLLOW && entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isEmpty()) {
            if (entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING).isPresent()) {
                entity.setTrackedValue(MOVE_STATE, MoveState.STAY);
            } else if (entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent()) {
                entity.setTrackedValue(MOVE_STATE, MoveState.FOLLOW);
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

        memory.modInteractionFatigue(1);
        memory.modHearts(hearts);
        CriterionMCA.HEARTS.trigger(player, memory.getHearts(), hearts, "interaction");
        entity.getVillagerBrain().modifyMoodValue(hearts);
    }
}
