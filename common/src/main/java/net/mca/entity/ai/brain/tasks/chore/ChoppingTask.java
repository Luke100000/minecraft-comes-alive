package net.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonSyntaxException;
import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.TaskUtils;
import net.mca.util.InventoryUtils;
import net.mca.util.RegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChoppingTask extends AbstractChoreTask {
    private int chopTicks, targetTreeTicks;
    private BlockPos targetTree;

    public ChoppingTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.CHOP && super.checkExtraStartConditions(world, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return checkExtraStartConditions(world, villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        if (!stack.isEmpty()) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof AxeItem);
            if (i == -1) {
                abandonJobWithMessage("chore.chopping.noaxe");
            } else {
                villager.setItemInHand(villager.getDominantHand(), villager.getInventory().getItem(i));
            }
        }
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (this.villager == null) this.villager = villager;

        if (!InventoryUtils.contains(villager.getInventory(), AxeItem.class) && !villager.hasItemInSlot(villager.getDominantSlot())) {
            abandonJobWithMessage("chore.chopping.noaxe");
        } else if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof AxeItem);
            ItemStack stack = villager.getInventory().getItem(i);
            villager.setItemInHand(villager.getDominantHand(), stack);
        }

        if (targetTree == null) {
            List<BlockPos> nearbyLogs = TaskUtils.getNearbyBlocks(villager.blockPosition(), world, (blockState -> blockState.is(BlockTags.LOGS)), 15, 5);
            List<BlockPos> nearbyTrees = new ArrayList<>();

            // valid "trees" are logs on the ground with leaves around them
            nearbyLogs.stream()
                    .filter(log -> isTreeStartLog(world, log))
                    .forEach(nearbyTrees::add);
            targetTree = TaskUtils.getNearestPoint(villager.blockPosition(), nearbyTrees);

            if (targetTree != null) {
                ItemStack stack = villager.getItemInHand(villager.getDominantHand());
                BlockPos pos = targetTree;
                BlockState state;
                while ((state = world.getBlockState(pos)).is(BlockTags.LOGS)) {
                    targetTreeTicks += getTicksFor(state, 60) / stack.getDestroySpeed(state);
                    pos = pos.offset(0, 1, 0);
                }
            }

            failedTicks = FAILED_COOLDOWN;
            return;
        }

        villager.moveTowards(targetTree);

        BlockState state = world.getBlockState(targetTree);
        if (state.is(BlockTags.LOGS)) {
            villager.swing(villager.getDominantHand());
            chopTicks++;

            // cut down a tree every few seconds, dependent on config + the mining speed multiplier
            if (chopTicks >= targetTreeTicks) {
                chopTicks = 0;

                destroyTree(world, targetTree);
            }
        } else {
            targetTree = null;
            targetTreeTicks = 0;
        }

        super.tick(world, villager, time);
    }

    /**
     * Returns trues if origin is bottom point of tree.
     */
    private boolean isTreeStartLog(ServerLevel world, BlockPos origin) {
        if (!world.getBlockState(origin).is(BlockTags.LOGS)) {
            return false;
        }

        // ensure we're looking at a valid tree before continuing
        if (!isValidTree(world, origin.below())) {
            return false;
        }

        // check upside continues and valid leaves exist.
        BlockPos.MutableBlockPos posUp = origin.mutable(); // copy as mutable for reduce resources
        for (int y = 0; y < Config.getInstance().maxTreeHeight; y++) {
            BlockState up = world.getBlockState(posUp.setY(posUp.getY() + 1)); // use set directly instead of "pos_up.move(Direction.UP)" (set is faster)
            if (up.is(BlockTags.LOGS)) {continue;} else return up.is(BlockTags.LEAVES);
        }
        return false;
    }

    private void destroyTree(ServerLevel world, BlockPos origin) {
        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        BlockPos pos = origin;
        BlockState state;

        while ((state = world.getBlockState(pos)).is(BlockTags.LOGS)) {
            if (world.destroyBlock(pos, false, villager)) {
                pos = pos.offset(0, 1, 0);
                villager.getInventory().addItem(new ItemStack(state.getBlock(), 1));
                stack.hurtAndBreak(1, villager, e -> e.broadcastBreakEvent(e.getDominantSlot()));
            } else {
                break;
            }
        }
    }

    private boolean isValidTree(ServerLevel world, BlockPos pos) {
        // Similar logic to WanderOrTeleportToTargetTask#isAreaSafe
        final BlockState state = world.getBlockState(pos);
        final ResourceLocation stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String blockId : Config.getInstance().validTreeSources) {
            if (blockId.equals(stateId.toString())) {
                return true;
            } else if (blockId.charAt(0) == '#') {
                ResourceLocation identifier = new ResourceLocation(blockId.substring(1));
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                if (tag != null && !RegistryHelper.isTagEmpty(tag)) {
                    if (state.is(tag)) {
                        return true;
                    }
                } else {
                    throw new JsonSyntaxException("Unknown block tag in validTreeSources '" + identifier + "'");
                }
            }
        }
        return false;
    }

    private int getTicksFor(BlockState state, int fallback) {
        // Similar logic to WanderOrTeleportToTargetTask#isAreaSafe
        final Map<String, Integer> sources = Config.getInstance().maxTreeTicks;
        final ResourceLocation stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String blockId : sources.keySet()) {
            if (blockId.equals(stateId.toString())) {
                return sources.get(blockId);
            } else if (blockId.charAt(0) == '#') {
                ResourceLocation identifier = new ResourceLocation(blockId.substring(1));
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                if (tag != null && !RegistryHelper.isTagEmpty(tag)) {
                    if (state.is(tag)) {
                        return sources.get(blockId);
                    }
                } else {
                    throw new JsonSyntaxException("Unknown block tag in maxTreeTicks '" + identifier + "'");
                }
            }
        }
        return fallback;
    }
}
