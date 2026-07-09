package net.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.TaskUtils;
import net.mca.util.InventoryUtils;
import net.minecraft.block.*;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class HarvestingTask extends AbstractChoreTask {
    private static final int ITEM_READY = 0;
    private static final int ITEM_FOUND = 1;
    private static final int ITEM_MISSING = 2;

    private final List<BlockPos> plantable = new ArrayList<>();
    private final List<BlockPos> harvestable = new ArrayList<>();
    private final List<BlockPos> bonemealable = new ArrayList<>();

    private int lastLandScan = -1200;
    private int lastCropScan = -1200;
    private int workingTick = 0;

    private BlockPos currentPos;

    public HarvestingTask() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryModuleState.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT
        ));
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.HARVEST && super.shouldRun(world, villager);
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        return currentPos != null && shouldRun(world, villager);
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        ItemStack stack = villager.getStackInHand(villager.getDominantHand());
        if (!stack.isEmpty()) {
            villager.setStackInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }

        if (currentPos != null) {
            plantable.remove(currentPos);
            harvestable.remove(currentPos);
            bonemealable.remove(currentPos);
            currentPos = null;
        }
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.run(world, villager, time);

        if (!villager.hasStackEquipped(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof HoeItem);
            if (i == -1) {
                abandonJobWithMessage("chore.harvesting.nohoe");
            } else {
                ItemStack stack = villager.getInventory().getStack(i);
                villager.setStackInHand(villager.getDominantHand(), stack);
            }
        }

        if (this.villager == null) {
            this.villager = villager;
        }

        // equip hoe
        if (!InventoryUtils.contains(villager.getInventory(), HoeItem.class) && !villager.hasStackEquipped(villager.getDominantSlot())) {
            abandonJobWithMessage("chore.harvesting.nohoe");
        } else if (!villager.hasStackEquipped(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof HoeItem);
            ItemStack stack = villager.getInventory().getStack(i);
            villager.setStackInHand(villager.getDominantHand(), stack);
        }

        // search for farmland
        if (plantable.isEmpty() && villager.age - lastLandScan > 1200) {
            searchUnusedFarmLand(32, 8);
            lastLandScan = villager.age;
        }

        // search for crops
        if ((harvestable.isEmpty() || bonemealable.isEmpty()) && villager.age - lastCropScan > 1207) {
            searchCrop(32, 8);
            lastCropScan = villager.age;
        }

        //try to find a planting task
        currentPos = TaskUtils.getNearestPoint(villager.getBlockPos(), plantable);
        if (currentPos == null) {
            currentPos = TaskUtils.getNearestPoint(villager.getBlockPos(), harvestable);
            if (currentPos == null) {
                currentPos = TaskUtils.getNearestPoint(villager.getBlockPos(), bonemealable);
                if (currentPos != null) {
                    swapItem(stack -> stack.getItem() instanceof BoneMealItem);
                }
            }
        }
    }

    private boolean isValidFarmland(BlockPos pos) {
        BlockState state = villager.getWorld().getBlockState(pos);
        return state.getBlock() instanceof FarmlandBlock
                && state.canPlaceAt(villager.getWorld(), pos)
                && villager.getWorld().getBlockState(pos.up()).isAir();
    }

    private boolean isValidMature(BlockPos pos) {
        BlockState state = villager.getWorld().getBlockState(pos);
        return (state.getBlock() instanceof CropBlock crop && crop.isMature(state))
                || state.getBlock() instanceof GourdBlock;
    }

    private boolean isValidImmature(BlockPos pos) {
        BlockState state = villager.getWorld().getBlockState(pos);
        return (state.getBlock() instanceof CropBlock crop && !crop.isMature(state));
    }

    private void searchCrop(int rangeX, int rangeY) {
        List<BlockPos> nearbyCrops = TaskUtils.getNearbyBlocks(villager.getBlockPos(), villager.getWorld(),
                blockState -> blockState.getBlock() instanceof CropBlock || blockState.getBlock() instanceof GourdBlock, rangeX, rangeY);

        harvestable.addAll(nearbyCrops.stream().filter(this::isValidMature).toList());

        if (hasBoneMeal()) {
            bonemealable.addAll(nearbyCrops.stream().filter(this::isValidImmature).toList());
        } else {
            bonemealable.clear();
        }
    }

    private boolean hasBoneMeal() {
        return InventoryUtils.contains(villager.getInventory(), BoneMealItem.class);
    }

    private void searchUnusedFarmLand(int rangeX, int rangeY) {
        plantable.addAll(TaskUtils.getNearbyBlocks(villager.getBlockPos(), villager.getWorld(),
                        blockState -> blockState.isOf(Blocks.FARMLAND), rangeX, rangeY)
                .stream()
                .filter(this::isValidFarmland)
                .toList());
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        villager.moveTowards(currentPos);

        // work
        if (villager.squaredDistanceTo(Vec3d.ofBottomCenter(currentPos)) <= 6) {
            workingTick++;
            if (workingTick % 5 == 0) {
                villager.swingHand(villager.getDominantHand());
            }
            if (workingTick > 40) { //todo magic number
                plantable.remove(currentPos);
                harvestable.remove(currentPos);
                bonemealable.remove(currentPos);

                if (isValidFarmland(currentPos)) {
                    plantSeeds(world, villager, currentPos.up(), null);
                } else if (isValidMature(currentPos)) {
                    BlockState state = world.getBlockState(currentPos);
                    if (state.getBlock() instanceof GourdBlock) {
                        harvestCrops(world, currentPos);
                    } else {
                        harvestCrops(world, currentPos);
                        plantSeeds(world, villager, currentPos, state.getBlock());
                    }
                } else if (isValidImmature(currentPos)) {
                    bonemealCrop(world, villager, currentPos);

                    if (!hasBoneMeal()) {
                        bonemealable.clear();
                    }
                }

                workingTick = 0;
                currentPos = null;
            }
        }
    }

    /**
     * Finds a matching item and places it in the villager's main hand slot.
     */
    private int swapItem(Predicate<ItemStack> find) {
        ItemStack stack = villager.getMainHandStack();
        if (find.test(stack)) {
            return ITEM_READY;
        }
        Inventory inventory = villager.getInventory();
        int slot = InventoryUtils.getFirstSlotContainingItem(inventory, find);
        if (slot < 0) {
            return ITEM_MISSING;
        }
        villager.setStackInHand(villager.getDominantHand(), inventory.getStack(slot));
        return ITEM_FOUND;
    }

    private void plantSeeds(ServerWorld world, VillagerEntityMCA villager, BlockPos target, Block block) {
        BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofBottomCenter(target),
                Direction.DOWN,
                target,
                true
        );

        Optional<ItemStack> stack = InventoryUtils.stream(villager.getInventory())
                .filter(s -> !s.isEmpty() && s.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block)
                .findAny();

        if (stack.isEmpty()) {
            stack = InventoryUtils.stream(villager.getInventory())
                    .filter(s -> !s.isEmpty() && s.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock)
                    .findAny();
        }

        stack.ifPresentOrElse(s -> {
            world.setBlockState(hitResult.getBlockPos(), ((BlockItem) s.getItem()).getBlock().getDefaultState(), Block.NOTIFY_ALL);
            s.decrement(1);
            villager.swingHand(villager.getDominantHand());
            bonemealable.add(target);
        }, () -> {
            getAssigningPlayer().ifPresent(p -> villager.sendChatMessage(p, "chore.harvesting.noseed"));
        });
    }

    @SuppressWarnings("deprecation")
    private void bonemealCrop(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (swapItem(stack -> stack.getItem() instanceof BoneMealItem) == ITEM_READY && BoneMealItem.useOnFertilizable(villager.getEquippedStack(villager.getDominantSlot()), world, pos)) {
            villager.swingHand(villager.getDominantHand());
        }
    }

    private void harvestCrops(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (world.breakBlock(pos, false, villager)) {
            LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                    .add(LootContextParameters.ORIGIN, villager.getPos())
                    .add(LootContextParameters.TOOL, ItemStack.EMPTY)
                    .add(LootContextParameters.THIS_ENTITY, villager)
                    .add(LootContextParameters.BLOCK_STATE, state)
                    .luck(0);

            List<ItemStack> drops = world.getServer().getLootManager().getLootTable(state.getBlock().getLootTableId()).generateLoot(builder.build(LootContextTypes.BLOCK));
            for (ItemStack stack : drops) {
                villager.getInventory().addStack(stack);
            }
        }
    }
}
