package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ConversationManager;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

public class DeliverMessageTask extends Behavior<VillagerEntityMCA> {
    private static final int TALKING_TIME_MIN = 100;
    private static final int TALKING_TIME_MAX = 500;
    private static final long MIN_TIME_BETWEEN_SOUND = 20 * 60 * 5;

    private ConversationManager.Message message = null;
    private Entity receiver = null;

    private int talked;

    private long lastInteraction = Long.MIN_VALUE;
    private Vec3 lastInteractionPos;

    public DeliverMessageTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.REGISTERED
        ), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        // Get potential message
        Optional<ConversationManager.Message> optionalMessage = getMessage(villager);
        // Set new message if it exists
        optionalMessage.ifPresent((m) -> {
            message = m;
            receiver = message.getReceiver();
        });

        // We only run if a message exists and the villager can see the player
        return optionalMessage.isPresent() && isWithinSeeRange(villager, receiver);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        talked = 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return message != null
               && talked < getMaxTalkingTime()
               && !villager.getVillagerBrain().isPanicking()
               && !villager.isSleeping();
    }

    private int getMaxTalkingTime() {
        if (lastInteractionPos != null) {
            Vec3 pos = receiver.position();
            if (lastInteractionPos.closerThan(pos, 1.0)) {
                return TALKING_TIME_MAX;
            }
        }
        return TALKING_TIME_MIN;
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        // Look at the Receiver
        if (receiver instanceof LivingEntity e) {
            villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, e);
            BehaviorUtils.lookAtEntity(villager, e);
        }

        // Walk towards receiver
        BehaviorUtils.setWalkAndLookTargetMemories(villager, receiver, 0.5F, 2);

        // Wait until the next talk cycle if receiver changed
        if (message.getReceiver() != receiver) {
            return;
        }

        // If our message is still valid and we're next to the receiver, deliver it
        if (message.stillValid() && isWithinRange(villager, receiver)) {
            if (time - lastInteraction > MIN_TIME_BETWEEN_SOUND) {
                villager.playWelcomeSound();
            }
            lastInteraction = time;
            lastInteractionPos = receiver.position();
            message.deliver();
        } else if (!message.stillValid() && isWithinRange(villager, receiver)) {
            // Increase reply time timer
            talked++;
            // Message no longer valid. Get new one
            // Get potential new message
            Optional<ConversationManager.Message> optionalMessage = getMessage(villager);
            // Set new message if it exists. Don't set new receiver, so we can check it against the old one
            optionalMessage.ifPresent((m) -> {
                message = m;
                // Reset conversation timer if same receiver
                if (m.getReceiver() == receiver) {
                    talked = 0;
                }
            });
        }
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        message = null;
        receiver = null;
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static Optional<ConversationManager.Message> getMessage(VillagerEntityMCA villager) {
        return villager.conversationManager.getCurrentMessage();
    }

    private static boolean isWithinRange(VillagerEntityMCA villager, Entity player) {
        // Staying villagers can't reach you
        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING.get()).isPresent()) {
            return true;
        }
        return villager.blockPosition().closerThan(player.blockPosition(), 3);
    }

    private static boolean isWithinSeeRange(VillagerEntityMCA villager, Entity player) {
        return villager.blockPosition().closerThan(player.blockPosition(), 64);
    }
}
