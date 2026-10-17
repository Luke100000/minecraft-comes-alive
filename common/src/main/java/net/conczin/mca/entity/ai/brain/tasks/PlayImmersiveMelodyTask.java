package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import immersive_melodies.Items;
import immersive_melodies.item.InstrumentItem;
import immersive_melodies.resources.ServerMelodyManager;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Lets a small group of socializing villagers briefly play a server-known melody.
 */
public class PlayImmersiveMelodyTask extends Behavior<VillagerEntityMCA> {
    private static final int PERFORMANCE_DURATION = 900;
    private static final int RETRY_COOLDOWN = 2400;
    private static final float JOIN_CHANCE = 0.25f;
    private static final Map<VillagerEntityMCA, Long> nextAttemptTimes = new WeakHashMap<>();
    private static final Map<VillagerEntityMCA, Long> nextPerformanceTimes = new WeakHashMap<>();

    private InstrumentItem.Playback selectedPlayback;
    private Performance performance;

    public PlayImmersiveMelodyTask() {
        super(ImmutableMap.of(), PERFORMANCE_DURATION, PERFORMANCE_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!canPerform(villager)
            || level.getGameTime() < nextAttemptTimes.getOrDefault(villager, 0L)
            || level.getGameTime() < nextPerformanceTimes.getOrDefault(villager, 0L)
            || Config.getInstance().immersiveMelodiesChance <= 0.0f) {
            return false;
        }
        // Failed rolls consume the attempt too: RunOne can retry on the next brain tick.
        nextAttemptTimes.put(villager, level.getGameTime() + RETRY_COOLDOWN);
        if (villager.getRandom().nextFloat() >= Config.getInstance().immersiveMelodiesChance) {
            return false;
        }
        selectedPlayback = closestMelody(level, villager)
                .or(() -> ServerMelodyManager.getRandomMelody(villager.getRandom(),
                                PlayImmersiveMelodyTask::isAllowedMelody)
                        .map(id -> new InstrumentItem.Playback(id, level.getGameTime())))
                .orElse(null);
        return selectedPlayback != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return performance != null && performance.canContinue(level);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (selectedPlayback != null) {
            performance = new Performance(villager.getUUID(), selectedPlayback, time + PERFORMANCE_DURATION);
            performance.ownerInstrument = startPerforming(level, villager, performance);
            if (performance.ownerInstrument.isEmpty()) return;

            level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(8.0), other -> other != villager && canPerform(other)
                                                                                                               && level.getGameTime() >= nextPerformanceTimes.getOrDefault(other, 0L))
                    .stream().filter(other -> other.getRandom().nextFloat() < JOIN_CHANCE).forEach(other -> startPerforming(level, other, performance));
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (performance != null) {
            performance.active = false;
            stopPerforming(level, villager, performance.ownerInstrument);
        }
        performance = null;
        selectedPlayback = null;
    }

    private static void stopPerforming(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        if (stack.getItem() instanceof InstrumentItem instrument) {
            instrument.pause(stack, level);
        }

        if (villager.getMainHandItem() == stack) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static boolean canPerform(VillagerEntityMCA villager) {
        return villager.isAlive() && !villager.isRemoved() && !villager.isSleeping() && !villager.isTrading()
               && !villager.isUsingRecoveryFood() && !villager.isUsingItem()
               && villager.getBrain().isActive(Activity.MEET)
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && !villager.getVillagerBrain().isPanicking();
    }

    private static ItemStack startPerforming(ServerLevel level, VillagerEntityMCA villager, Performance performance) {
        ItemStack stack = Items.getRandomInstrument(villager.getRandom()).orElse(ItemStack.EMPTY);
        if (!(stack.getItem() instanceof InstrumentItem instrument)) return ItemStack.EMPTY;

        InventoryUtils.temporary(stack);
        instrument.play(stack, performance.playback.melody(), performance.playback.startTime(), villager);
        villager.setItemInHand(InteractionHand.MAIN_HAND, stack);

        // Each participant checks the shared session from its own tick, even after the owner unloads.
        villager.setMelodyTick(() -> {
            if (villager.level() == level && performance.canContinue(level) && canPerform(villager)
                && villager.getMainHandItem() == stack && stack.getItem() instanceof InstrumentItem) {
                return true;
            }
            stopPerforming(level, villager, stack);
            return false;
        });
        nextPerformanceTimes.put(villager, level.getGameTime() + RETRY_COOLDOWN);
        return stack;
    }

    private static boolean isAllowedMelody(ResourceLocation id) {
        String namespace = Config.getInstance().immersiveMelodiesNamespace;
        return "*".equals(namespace) || id.getNamespace().equals(namespace);
    }

    private static Optional<InstrumentItem.Playback> closestMelody(ServerLevel level, VillagerEntityMCA villager) {
        return level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(8.0), other -> other != villager).stream()
                .sorted(Comparator.comparingDouble(villager::distanceToSqr))
                .map(other -> InstrumentItem.getPlayback(other.getMainHandItem()))
                .flatMap(Optional::stream)
                .filter(playback -> isAllowedMelody(playback.melody()))
                .filter(playback -> ServerMelodyManager.getDatapackMelodies().containsKey(playback.melody())
                                    || ServerMelodyManager.getIndex().getMelodies().containsKey(playback.melody()))
                .findFirst();
    }

    private static final class Performance {
        private final UUID owner;
        private final InstrumentItem.Playback playback;
        private final long deadline;
        private ItemStack ownerInstrument = ItemStack.EMPTY;
        private boolean active = true;

        private Performance(UUID owner, InstrumentItem.Playback playback, long deadline) {
            this.owner = owner;
            this.playback = playback;
            this.deadline = deadline;
        }

        private boolean canContinue(ServerLevel level) {
            return active && level.getGameTime() < deadline
                   && ownerInstrument.getItem() instanceof InstrumentItem
                   && level.getEntity(owner) instanceof VillagerEntityMCA villager
                   && villager.getMainHandItem() == ownerInstrument && canPerform(villager);
        }
    }
}
