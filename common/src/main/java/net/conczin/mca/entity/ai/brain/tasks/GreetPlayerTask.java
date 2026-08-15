package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ConversationManager;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;

public class GreetPlayerTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_COOLDOWN = 100;
    private int cooldown = 0;

    public GreetPlayerTask() {
        super(ImmutableMap.of(), 0);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        cooldown--;
        return cooldown < 0 && entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty();
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        cooldown = MAX_COOLDOWN;
        getPlayer(villager).ifPresent(player -> {
            Memories memories = villager.getVillagerBrain().getMemoriesForPlayer(player);
            int day = (int)(villager.level().getDayTime() / 24000L);
            memories.setLastSeen(day);

            String phrase = memories.getHearts() < 0 ? "welcomeFoe" : "welcome";

            villager.conversationManager.addMessage(new ConversationManager.PhraseText(player, phrase));
        });
    }

    private static Optional<? extends Player> getPlayer(VillagerEntityMCA villager) {
        return ((ServerLevel)villager.level()).players().stream()
                .filter(p -> isWithinSeeRange(villager, p))
                .filter(p -> shouldGreet(villager, p))
                .findFirst();
    }

    private static boolean shouldGreet(VillagerEntityMCA villager, ServerPlayer player) {
        Optional<Integer> id = PlayerSaveData.get(player).getLastSeenVillageId();
        Optional<Village> village = villager.getResidency().getHomeVillage();
        if (id.isPresent() && village.isPresent() && id.get() == village.get().getId()) {
            Memories memories = villager.getVillagerBrain().getMemoriesForPlayer(player);

            int day = (int)(villager.level().getDayTime() / 24000L);

            // first check relationships, only family, friends and foes will greet you
            if (Relationship.IS_MARRIED.test(villager, player)
                    || Relationship.IS_RELATIVE.test(villager, player)
                    || Math.abs(memories.getHearts()) >= Config.getInstance().greetHeartsThreshold) {
                long diff = day - memories.getLastSeen();

                if (diff > Config.getInstance().greetAfterDays && memories.getLastSeen() > 0) {
                    return true;
                }

                if (diff > 0) {
                    //there is a diff, but not long enough
                    memories.setLastSeen(day);
                }
            } else {
                //no interest
                memories.setLastSeen(day);
            }
        }

        return false;
    }

    private static boolean isWithinSeeRange(VillagerEntityMCA villager, Player player) {
        return villager.blockPosition().closerThan(player.blockPosition(), 32);
    }
}
