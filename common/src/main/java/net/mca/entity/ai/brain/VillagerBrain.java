package net.mca.entity.ai.brain;

import net.mca.Config;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.entity.Status;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.*;
import net.mca.entity.ai.relationship.Personality;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CEnumParameter;
import net.mca.util.network.datasync.CParameter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static net.mca.entity.ai.MemoryModuleTypeMCA.LAST_GRIEVE;

/**
 * Handles memory and complex bodily functions. Such as walking, and not being a nitwit.
 */
public class VillagerBrain<E extends MobEntity & VillagerLike<E>> {
    private static final CDataParameter<NbtCompound> MEMORIES = CParameter.create("memories", new NbtCompound());
    private static final CEnumParameter<Personality> PERSONALITY = CParameter.create("personality", Personality.UNASSIGNED);
    private static final CDataParameter<Integer> MOOD = CParameter.create("mood", 0);
    private static final CEnumParameter<MoveState> MOVE_STATE = CParameter.create("moveState", MoveState.MOVE);
    private static final CEnumParameter<Chore> ACTIVE_CHORE = CParameter.create("activeChore", Chore.NONE);
    private static final CDataParameter<Optional<UUID>> CHORE_ASSIGNING_PLAYER = CParameter.create("choreAssigningPlayer", Optional.empty());
    private static final CDataParameter<Boolean> PANICKING = CParameter.create("isPanicking", false);
    private static final CDataParameter<Boolean> WEAR_ARMOR = CParameter.create("wearArmor", false);

    public static <E2 extends Entity> CDataManager.Builder<E2> createTrackedData(CDataManager.Builder<E2> builder) {
        return builder.addAll(MEMORIES, PERSONALITY, MOOD, MOVE_STATE, ACTIVE_CHORE, CHORE_ASSIGNING_PLAYER, PANICKING, WEAR_ARMOR);
    }

    private static final long GRIEVE_COOLDOWN = 24000 * 7;

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
            entity.getBrain().getFirstPossibleNonCoreActivity().ifPresent(activity -> {
                if (!activity.equals(ActivityMCA.CHORE.get())) {
                    entity.getBrain().doExclusively(ActivityMCA.CHORE.get());
                }
            });
        }

        boolean panicking = entity.getBrain().hasActivity(Activity.PANIC);
        if (panicking != entity.getTrackedValue(PANICKING)) {
            entity.setTrackedValue(PANICKING, panicking);
        }

        if (entity.age % 20 != 0) {
            updateMoveState();
        }

        // decrease interaction fatigue
        if (entity.age % Math.max(1, Config.getInstance().interactionFatigueCooldown) == 0) {
            NbtCompound nbt = entity.getTrackedValue(MEMORIES);
            if (nbt != null) {
                for (String uuid : nbt.getKeys()) {
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

    public Optional<PlayerEntity> getJobAssigner() {
        return entity.getTrackedValue(CHORE_ASSIGNING_PLAYER).map(id -> entity.getWorld().getPlayerByUuid(id));
    }

    /**
     * Tells the villager to stop doing whatever it's doing.
     */
    public void abandonJob() {
        entity.getBrain().doExclusively(Activity.IDLE);
        entity.setTrackedValue(ACTIVE_CHORE, Chore.NONE);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.empty());

        resetsBrain();
    }

    /**
     * Assigns a job for the villager to do.
     */
    public void assignJob(Chore chore, PlayerEntity player) {
        entity.getBrain().doExclusively(ActivityMCA.CHORE.get());
        entity.setTrackedValue(ACTIVE_CHORE, chore);
        entity.setTrackedValue(CHORE_ASSIGNING_PLAYER, Optional.of(player.getUuid()));
        entity.getBrain().forget(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
        entity.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());

        resetsBrain();
    }

    public void randomize() {
        entity.setTrackedValue(PERSONALITY, Personality.getRandom());
        entity.setTrackedValue(MOOD, entity.getWorld().random.nextInt(MoodGroup.MAX_LEVEL - MoodGroup.NORMAL_MIN_LEVEL + 1) + MoodGroup.NORMAL_MIN_LEVEL);
    }

    public void setPersonality(Personality p) {
        entity.setTrackedValue(PERSONALITY, p);
    }

    public void updateMemories(Memories memories) {
        NbtCompound nbt = entity.getTrackedValue(MEMORIES);

        nbt = nbt == null ? new NbtCompound() : nbt.copy();
        nbt.put(memories.getPlayerUUID().toString(), memories.toCNBT());
        entity.setTrackedValue(MEMORIES, nbt);
    }

    public Map<UUID, Memories> getMemories() {
        NbtCompound nbt = entity.getTrackedValue(MEMORIES);
        Map<UUID, Memories> memories = new HashMap<>();
        for (String uuid : nbt.getKeys()) {
            memories.put(UUID.fromString(uuid), Memories.fromCNBT(entity, nbt.getCompound(uuid)));
        }
        return memories;
    }

    public Memories getMemoriesForPlayer(PlayerEntity player) {
        NbtCompound nbt = entity.getTrackedValue(MEMORIES);
        nbt = nbt == null ? new NbtCompound() : nbt;
        NbtCompound compoundTag = nbt.getCompound(player.getUuid().toString());
        Memories returnMemories = Memories.fromCNBT(entity, compoundTag);
        if (returnMemories == null) {
            returnMemories = new Memories(this, player.getWorld().getTimeOfDay(), player.getUuid());
            nbt.put(player.getUuid().toString(), returnMemories.toCNBT());
            entity.setTrackedValue(MEMORIES, nbt);
        }
        return returnMemories;
    }

    public Personality getPersonality() {
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

    public void setMoveState(MoveState state, @Nullable PlayerEntity leader) {
        entity.setTrackedValue(MOVE_STATE, state);
        if (state == MoveState.MOVE) {
            entity.getBrain().forget(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            entity.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
        }
        if (state == MoveState.STAY) {
            entity.getBrain().forget(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            entity.getBrain().remember(MemoryModuleTypeMCA.STAYING.get(), true);
        }
        if (state == MoveState.FOLLOW) {
            entity.getBrain().remember(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), leader);
            entity.getBrain().forget(MemoryModuleTypeMCA.STAYING.get());
            abandonJob();
        }

        resetsBrain();
    }

    private void resetsBrain() {
        if (entity.asEntity() instanceof VillagerEntityMCA villager) {
            villager.reinitializeBrain((ServerWorld)villager.getWorld());
        }
    }

    public void setArmorWear(boolean s) {
        entity.setTrackedValue(WEAR_ARMOR, s);
    }

    public boolean getArmorWear() {
        return entity.getTrackedValue(WEAR_ARMOR);
    }

    public void setGrieving() {
        entity.getBrain().remember(LAST_GRIEVE.get(), -GRIEVE_COOLDOWN);
    }

    public void justGrieved() {
        entity.getBrain().remember(LAST_GRIEVE.get(), entity.getWorld().getTime());
    }

    public boolean shouldGrieve() {
        Optional<Long> memory = entity.getBrain().getOptionalMemory(LAST_GRIEVE.get());
        if (memory.isPresent()) {
            return entity.getWorld().getTime() - memory.get() > GRIEVE_COOLDOWN;
        } else {
            entity.getBrain().remember(LAST_GRIEVE.get(), entity.getWorld().getTime() - random.nextLong(GRIEVE_COOLDOWN));
            return false;
        }
    }

    /**
     * Read the move state from the active memory.
     */
    public void updateMoveState() {
        if (getMoveState() == MoveState.FOLLOW && entity.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isEmpty()) {
            if (entity.getBrain().getOptionalMemory(MemoryModuleTypeMCA.STAYING.get()).isPresent()) {
                entity.setTrackedValue(MOVE_STATE, MoveState.STAY);
            } else if (entity.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent()) {
                entity.setTrackedValue(MOVE_STATE, MoveState.FOLLOW);
            } else {
                entity.setTrackedValue(MOVE_STATE, MoveState.MOVE);
            }
        }
    }

    public void rewardHearts(ServerPlayerEntity player, int hearts) {
        Memories memory = entity.getVillagerBrain().getMemoriesForPlayer(player);

        if (hearts == 0) {
            return;
        }

        //spawn particles
        if (hearts > 0) {
            entity.getWorld().sendEntityStatus(entity, Status.MCA_VILLAGER_POS_INTERACTION);
        } else {
            entity.getWorld().sendEntityStatus(entity, Status.MCA_VILLAGER_NEG_INTERACTION);

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
