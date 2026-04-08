package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.HARVEST && super.checkExtraStartConditions(world, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return currentPos != null && checkExtraStartConditions(world, villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        if (!stack.isEmpty()) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        }

        if (currentPos != null) {
            plantable.remove(currentPos);
            harvestable.remove(currentPos);
            bonemealable.remove(currentPos);
            currentPos = null;
        }
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof HoeItem);
            if (i == -1) {
                abandonJobWithMessage("chore.harvesting.nohoe");
            } else {
                ItemStack stack = villager.getInventory().getItem(i);
                villager.setItemInHand(villager.getDominantHand(), stack);
            }
        }

        if (this.villager == null) {
            this.villager = villager;
        }

        // equip hoe
        if (!InventoryUtils.contains(villager.getInventory(), HoeItem.class) && !villager.hasItemInSlot(villager.getDominantSlot())) {
            abandonJobWithMessage("chore.harvesting.nohoe");
        } else if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.getItem() instanceof HoeItem);
            ItemStack stack = villager.getInventory().getItem(i);
            villager.setItemInHand(villager.getDominantHand(), stack);
        }

        // search for farmland
        if (plantable.isEmpty() && villager.tickCount - lastLandScan > 1200) {
            searchUnusedFarmLand(32, 8);
            lastLandScan = villager.tickCount;
        }

        // search for crops
        if ((harvestable.isEmpty() || bonemealable.isEmpty()) && villager.tickCount - lastCropScan > 1207) {
            searchCrop(32, 8);
            lastCropScan = villager.tickCount;
        }

        //try to find a planting task
        currentPos = TaskUtils.getNearestPoint(villager.blockPosition(), plantable);
        if (currentPos == null) {
            currentPos = TaskUtils.getNearestPoint(villager.blockPosition(), harvestable);
            if (currentPos == null) {
                currentPos = TaskUtils.getNearestPoint(villager.blockPosition(), bonemealable);
                if (currentPos != null) {
                    swapItem(stack -> stack.getItem() instanceof BoneMealItem);
                }
            }
        }
    }

    private boolean isValidFarmland(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);
        return state.getBlock() instanceof FarmlandBlock
               && state.canSurvive(villager.level(), pos)
               && villager.level().getBlockState(pos.above()).isAir();
    }

    private boolean isValidMature(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);
        return (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state))
               || state.getBlock() instanceof StemBlock;
    }

    private boolean isValidImmature(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);
        return (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state));
    }

    private void searchCrop(int rangeX, int rangeY) {
        List<BlockPos> nearbyCrops = TaskUtils.getNearbyBlocks(villager.blockPosition(), villager.level(),
                blockState -> blockState.getBlock() instanceof CropBlock || blockState.getBlock() instanceof StemBlock, rangeX, rangeY);

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
        plantable.addAll(TaskUtils.getNearbyBlocks(villager.blockPosition(), villager.level(),
                        blockState -> blockState.is(Blocks.FARMLAND), rangeX, rangeY)
                .stream()
                .filter(this::isValidFarmland)
                .toList());
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.moveTowards(currentPos);

        // work
        if (villager.distanceToSqr(Vec3.atBottomCenterOf(currentPos)) <= 6) {
            workingTick++;
            if (workingTick % 5 == 0) {
                villager.swing(villager.getDominantHand());
            }
            if (workingTick > 40) { //todo magic number
                plantable.remove(currentPos);
                harvestable.remove(currentPos);
                bonemealable.remove(currentPos);

                if (isValidFarmland(currentPos)) {
                    plantSeeds(world, villager, currentPos.above(), null);
                } else if (isValidMature(currentPos)) {
                    BlockState state = world.getBlockState(currentPos);
                    if (state.getBlock() instanceof StemBlock) {
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
        ItemStack stack = villager.getMainHandItem();
        if (find.test(stack)) {
            return ITEM_READY;
        }
        Container inventory = villager.getInventory();
        int slot = InventoryUtils.getFirstSlotContainingItem(inventory, find);
        if (slot < 0) {
            return ITEM_MISSING;
        }
        villager.setItemInHand(villager.getDominantHand(), inventory.getItem(slot));
        return ITEM_FOUND;
    }

    private void plantSeeds(ServerLevel world, VillagerEntityMCA villager, BlockPos target, Block block) {
        BlockHitResult hitResult = new BlockHitResult(
                Vec3.atBottomCenterOf(target),
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
            world.setBlock(hitResult.getBlockPos(), ((BlockItem) s.getItem()).getBlock().defaultBlockState(), Block.UPDATE_ALL);
            s.shrink(1);
            villager.swing(villager.getDominantHand());
            bonemealable.add(target);
        }, () -> {
            getAssigningPlayer().ifPresent(p -> villager.sendChatMessage(p, "chore.harvesting.noseed"));
        });
    }

    private void bonemealCrop(ServerLevel world, VillagerEntityMCA villager, BlockPos pos) {
        if (swapItem(stack -> stack.getItem() instanceof BoneMealItem) == ITEM_READY && BoneMealItem.growCrop(villager.getItemBySlot(villager.getDominantSlot()), world, pos)) {
            villager.swing(villager.getDominantHand());
        }
    }

    private void harvestCrops(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (world.destroyBlock(pos, false, villager)) {
            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, villager.position())
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.THIS_ENTITY, villager)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withLuck(0);

            state.getBlock().getLootTable().ifPresent(lootTable -> {
                List<ItemStack> drops = world.getServer().reloadableRegistries().getLootTable(lootTable).getRandomItems(builder.create(LootContextParamSets.BLOCK));
                for (ItemStack stack : drops) {
                    villager.getInventory().addItem(stack);
                }
            });
        }
    }
}
