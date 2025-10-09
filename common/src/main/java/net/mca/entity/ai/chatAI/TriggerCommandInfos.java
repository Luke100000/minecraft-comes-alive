package net.mca.entity.ai.chatAI;

import com.google.common.collect.ImmutableList;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Optional;

public class TriggerCommandInfos {
    public static final List<TriggerCommandInfo> triggerCommands = ImmutableList.of(
            new TriggerCommandInfo("follow-player", "Follow the player talking to you",
                    (p, v) -> v.getVillagerBrain().setMoveState(MoveState.FOLLOW, p),
                    (p, v) -> v.getVillagerBrain().getMoveState() != MoveState.FOLLOW),
            new TriggerCommandInfo("stay-here", "Stay here for a while",
                    (p, v) -> v.getVillagerBrain().setMoveState(MoveState.STAY, p),
                    (p, v) -> v.getVillagerBrain().getMoveState() != MoveState.STAY),
            new TriggerCommandInfo("move-freely", "Move freely when asked to leave, move, or done talking",
                    (p, v) -> v.getVillagerBrain().setMoveState(MoveState.MOVE, p),
                    (p, v) -> v.getVillagerBrain().getMoveState() != MoveState.MOVE),
            new TriggerCommandInfo("wear-armor", "Equip any armor you have",
                    (p, v) -> v.getVillagerBrain().setArmorWear(true),
                    (p, v) -> !v.getVillagerBrain().getArmorWear()),
            new TriggerCommandInfo("remove-armor", "Remove all the armor currently equipped",
                    (p, v) -> v.getVillagerBrain().setArmorWear(false),
                    (p, v) -> v.getVillagerBrain().getArmorWear()),
            new TriggerCommandInfo("try-go-home", "Try to go to your home in the village if possible",
                    (p, v) -> v.getResidency().goHome(p)),
            new TriggerCommandInfo("open-trade-window", "Open the trade menu when the player is interested in trading, wants to check your prices or your inventory.",
                    (p, v) -> v.startTrading(p),
                    (p, v) -> v.hasTradeOffers())
    );

    public static Optional<TriggerCommandInfo> findCommand(String command, ServerPlayer player, VillagerEntityMCA villagerEntityMCA) {
        for (TriggerCommandInfo commandInfo : TriggerCommandInfos.triggerCommands) {
            if (command.equals(commandInfo.command)) {
                if (commandInfo.isActive != null && !commandInfo.isActive.test(player, villagerEntityMCA)) {
                    // not active or available now
                    continue;
                }
                return Optional.of(commandInfo);
            }
        }
        return Optional.empty();
    }
}
