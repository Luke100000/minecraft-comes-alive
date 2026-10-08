package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.integration.ImmersiveMelodies;
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

    private final List<Performer> performers = new ArrayList<>();
    private ResourceLocation melody;

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
        melody = closestMelody(level, villager).map(ImmersiveMelodies.PlayingMelody::melody)
                .or(() -> ImmersiveMelodies.randomMelody(villager, Config.getInstance().immersiveMelodiesNamespace))
                .orElse(null);
        return melody != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return !timedOut(time) && canPerform(villager)
               && performers.stream().anyMatch(performer -> performer.villager() == villager
                                                            && villager.getItemInHand(performer.hand()) == performer.instrument());
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (melody != null) {
            ImmersiveMelodies.PlayingMelody selectedMelody = closestMelody(level, villager)
                    .orElse(new ImmersiveMelodies.PlayingMelody(melody, level.getGameTime()));
            startPerforming(level, villager, selectedMelody);
            level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(8.0), other -> other != villager && canPerform(other)
                                                                                                               && level.getGameTime() >= nextPerformanceTimes.getOrDefault(other, 0L))
                    .stream().filter(other -> other.getRandom().nextFloat() < JOIN_CHANCE).forEach(other -> startPerforming(level, other, selectedMelody));
        }
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        Iterator<Performer> iterator = performers.iterator();
        while (iterator.hasNext()) {
            Performer performer = iterator.next();
            if (!canPerform(performer.villager())
                || performer.villager().getItemInHand(performer.hand()) != performer.instrument()) {
                stopPerforming(level, performer);
                iterator.remove();
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        performers.forEach(performer -> stopPerforming(level, performer));
        performers.clear();
        melody = null;
    }

    private static void stopPerforming(ServerLevel level, Performer performer) {
        ImmersiveMelodies.stop(performer.instrument(), level);
        if (performer.villager().getItemInHand(performer.hand()) == performer.instrument()) {
            performer.villager().setItemInHand(performer.hand(), performer.previousStack());
        }
    }

    private static boolean canPerform(VillagerEntityMCA villager) {
        return villager.isAlive() && !villager.isSleeping() && !villager.isTrading()
               && !villager.isUsingRecoveryFood()
               && villager.getBrain().isActive(Activity.MEET)
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && !villager.getVillagerBrain().isPanicking();
    }

    private void startPerforming(ServerLevel level, VillagerEntityMCA villager, ImmersiveMelodies.PlayingMelody melody) {
        ImmersiveMelodies.randomInstrument(villager).ifPresent(instrument -> {
            ItemStack previous = villager.getMainHandItem().copy();
            villager.setItemInHand(InteractionHand.MAIN_HAND, instrument);
            if (ImmersiveMelodies.play(instrument, melody, level, villager)) {
                performers.add(new Performer(villager, InteractionHand.MAIN_HAND, instrument, previous));
                nextPerformanceTimes.put(villager, level.getGameTime() + RETRY_COOLDOWN);
            } else {
                villager.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
        });
    }

    private static Optional<ImmersiveMelodies.PlayingMelody> closestMelody(ServerLevel level, VillagerEntityMCA villager) {
        return level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(8.0), other -> other != villager).stream()
                .sorted(Comparator.comparingDouble(villager::distanceToSqr))
                .map(other -> ImmersiveMelodies.playingMelody(other.getMainHandItem()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private record Performer(VillagerEntityMCA villager, InteractionHand hand, ItemStack instrument,
                             ItemStack previousStack) {
    }
}
