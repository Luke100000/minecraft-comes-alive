package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ConversationManager;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Relationship;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.Village;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

public class GreetPlayerTask extends MultiTickTask<VillagerEntityMCA> {
    private static final int MAX_COOLDOWN = 100;
    private int cooldown = 0;

    public GreetPlayerTask() {
        super(ImmutableMap.of(), 0);
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA entity) {
        cooldown--;
        return cooldown < 0;
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        cooldown = MAX_COOLDOWN;
        getPlayer(villager).ifPresent(player -> {
            Memories memories = villager.getVillagerBrain().getMemoriesForPlayer(player);
            int day = (int)(villager.getWorld().getTimeOfDay() / 24000L);
            memories.setLastSeen(day);

            String phrase = memories.getHearts() < 0 ? "welcomeFoe" : "welcome";

            villager.conversationManager.addMessage(new ConversationManager.PhraseText(player, phrase));
        });
    }

    private static Optional<? extends PlayerEntity> getPlayer(VillagerEntityMCA villager) {
        return ((ServerWorld)villager.getWorld()).getPlayers().stream()
                .filter(p -> isWithinSeeRange(villager, p))
                .filter(p -> shouldGreet(villager, p))
                .findFirst();
    }

    private static boolean shouldGreet(VillagerEntityMCA villager, ServerPlayerEntity player) {
        Optional<Integer> id = PlayerSaveData.get(player).getLastSeenVillageId();
        Optional<Village> village = villager.getResidency().getHomeVillage();
        if (id.isPresent() && village.isPresent() && id.get() == village.get().getId()) {
            Memories memories = villager.getVillagerBrain().getMemoriesForPlayer(player);

            int day = (int)(villager.getWorld().getTimeOfDay() / 24000L);

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

    private static boolean isWithinSeeRange(VillagerEntityMCA villager, PlayerEntity player) {
        return villager.getBlockPos().isWithinDistance(player.getBlockPos(), 32);
    }
}
