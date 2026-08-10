package net.mca.entity.ai.brain;

import net.mca.Config;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.entity.Status;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.*;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Personality;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CEnumParameter;
import net.mca.util.network.datasync.CParameter;
import net.mca.util.network.datasync.CResourceLocationParameter;
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

import java.util.*;

import static net.mca.entity.ai.MemoryModuleTypeMCA.LAST_GRIEVE;

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

    public static <E2 extends Entity> CDataManager.Builder<E2> createTrackedData(CDataManager.Builder<E2> builder) {
        return builder.addAll(MEMORIES, PERSONALITY, MOOD, MOVE_STATE, ACTIVE_CHORE, CHORE_ASSIGNING_PLAYER, PANICKING, WEAR_ARMOR);
    }

    private static final long GRIEVE_COOLDOWN = 24000 * 7;
    private static final long GRIEVE_RETRY_DELAY = 1200L;

    private final Random random = new Random();

    private final E entity;

    public VillagerBrain(E entity) {
        this.entity = entity;
    }

    public void think() {
        // When you relog, it should continue doing the chores.
        // Chore saves but Activity doesn't, so this checks if the activity is not on there and puts it on there.
        if (entity.getTrackedValue(ACTIVE_CHORE) != Chore.NONE) {
            // find something to do
            //todo here switch between rest and chore
            entity.getBrain().getActiveNonCoreActivity().ifPresent(activity -> {
                if (!activity.equals(ActivityMCA.CHORE.get())) {
                    entity.getBrain().setActiveActivityIfPossible(ActivityMCA.CHORE.get());
                }
            });
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
            if (nbt != null) {
                for (String uuid : nbt.getAllKeys()) {
                    Memories memories = Memories.fromCNBT(entity, nbt.getCompound(uuid));
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
        entity.getBrain().setActiveActivityIfPossible(ActivityMCA.CHORE.get());
        entity.setTrackedValue(ACTIVE_CHORE, chore);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.of(player.getUUID()));
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
        entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING.get());

        resetsBrain();
    }

    public void randomize() {
        randomize(entity.getAgeState());
    }

    public void randomize(AgeState ageState) {
        entity.setTrackedValue(PERSONALITY, Personality.getRandom(ageState).getId());
        entity.setTrackedValue(MOOD, entity.level().random.nextInt(MoodGroup.MAX_LEVEL - MoodGroup.NORMAL_MIN_LEVEL + 1) + MoodGroup.NORMAL_MIN_LEVEL);
    }

    public void setPersonality(Personality p) {
        entity.setTrackedValue(PERSONALITY, p.getId());
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
        for (String uuid : nbt.getAllKeys()) {
            memories.put(UUID.fromString(uuid), Memories.fromCNBT(entity, nbt.getCompound(uuid)));
        }
        return memories;
    }

    public Memories getMemoriesForPlayer(Player player) {
        CompoundTag nbt = entity.getTrackedValue(MEMORIES);
        nbt = nbt == null ? new CompoundTag() : nbt;
        CompoundTag compoundTag = nbt.getCompound(player.getUUID().toString());
        Memories returnMemories = Memories.fromCNBT(entity, compoundTag);
        if (returnMemories == null) {
            returnMemories = new Memories(this, player.level().getDayTime(), player.getUUID());
            nbt.put(player.getUUID().toString(), returnMemories.toCNBT());
            entity.setTrackedValue(MEMORIES, nbt);
        }
        return returnMemories;
    }

    public Personality getPersonality() {
        return Personality.get(getPersonalityId()).orElse(Personality.UNASSIGNED);
    }

    public ResourceLocation getPersonalityId() {
        return entity.getTrackedValue(PERSONALITY);
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
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING.get());
        }
        if (state == MoveState.STAY) {
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            entity.getBrain().setMemory(MemoryModuleTypeMCA.STAYING.get(), true);
        }
        if (state == MoveState.FOLLOW) {
            entity.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), leader);
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING.get());
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
        entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), 0.75F, 0));
    }

    private void resetsBrain() {
        if (entity.asEntity() instanceof VillagerEntityMCA villager) {
            Optional<LivingEntity> attackTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
            Optional<Player> following = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            Optional<Boolean> staying = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING.get());

            villager.refreshBrain((ServerLevel)villager.level());

            attackTarget.ifPresent(target -> villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target));
            following.ifPresent(player -> villager.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), player));
            staying.ifPresent(value -> villager.getBrain().setMemory(MemoryModuleTypeMCA.STAYING.get(), value));
        }
    }

    public void setArmorWear(boolean s) {
        entity.setTrackedValue(WEAR_ARMOR, s);
    }

    public boolean getArmorWear() {
        return entity.getTrackedValue(WEAR_ARMOR);
    }

    public void setGrieving() {
        entity.getBrain().setMemory(LAST_GRIEVE.get(), -GRIEVE_COOLDOWN);
    }

    public void retryGrievingLater() {
        entity.getBrain().setMemory(LAST_GRIEVE.get(), entity.level().getGameTime() - GRIEVE_COOLDOWN + GRIEVE_RETRY_DELAY);
    }

    public void justGrieved() {
        entity.getBrain().setMemory(LAST_GRIEVE.get(), entity.level().getGameTime());
    }

    public boolean shouldGrieve() {
        Optional<Long> memory = entity.getBrain().getMemoryInternal(LAST_GRIEVE.get());
        if (memory.isPresent()) {
            return entity.level().getGameTime() - memory.get() > GRIEVE_COOLDOWN;
        } else {
            entity.getBrain().setMemory(LAST_GRIEVE.get(), entity.level().getGameTime() - random.nextLong(GRIEVE_COOLDOWN));
            return false;
        }
    }

    /**
     * Read the move state from the active memory.
     */
    public void updateMoveState() {
        if (getMoveState() == MoveState.FOLLOW && entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isEmpty()) {
            if (entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING.get()).isPresent()) {
                entity.setTrackedValue(MOVE_STATE, MoveState.STAY);
            } else if (entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent()) {
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
            //spawn particles
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
        CriterionMCA.HEARTS_CRITERION.trigger(player, memory.getHearts(), hearts, "interaction");
        entity.getVillagerBrain().modifyMoodValue(hearts);
    }
}
