package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HoldItemTask extends Behavior<VillagerEntityMCA> {
    private final InteractionHand hand;
    private final ItemStack item;

    public HoldItemTask(InteractionHand hand, Item item) {
        this(hand, new ItemStack(item));
    }

    public HoldItemTask(InteractionHand hand, ItemStack item) {
        super(ImmutableMap.of());

        this.hand = hand;
        this.item = item;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.setItemInHand(hand, item);
    }
}
