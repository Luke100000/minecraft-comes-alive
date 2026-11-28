package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HarvestingTask extends AbstractChoreTask {
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
        releaseChoreHand(villager, Chore.HARVEST);

        workingTick = 0;

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

        if (!equipChoreTool(villager, Chore.HARVEST, "chore.harvesting.nohoe")) return;

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

        currentPos = TaskUtils.getNearestPoint(villager.blockPosition(), harvestable);
        if (currentPos == null) {
            currentPos = TaskUtils.getNearestPoint(villager.blockPosition(), bonemealable);
            if (currentPos == null) {
                currentPos = hasSeeds() ? TaskUtils.getNearestPoint(villager.blockPosition(), plantable) : null;
            } else {
                equipBoneMeal();
            }
        }
    }

    private boolean isValidFarmland(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);
        return state.getBlock() instanceof FarmBlock
               && state.canSurvive(villager.level(), pos)
               && villager.level().getBlockState(pos.above()).isAir();
    }

    private boolean isValidMature(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    private boolean isValidImmature(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);
        return (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state));
    }

    private void searchCrop(int rangeX, int rangeY) {
        List<BlockPos> nearbyCrops = TaskUtils.getNearbyBlocks(villager.blockPosition(), villager.level(),
                blockState -> blockState.getBlock() instanceof CropBlock, rangeX, rangeY);

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

    private boolean hasSeeds() {
        return InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), HarvestingTask::isPlantableCropItem) >= 0;
    }

    private static boolean isPlantableCropItem(ItemStack stack) {
        return !stack.isEmpty()
               && stack.getItem() instanceof BlockItem blockItem
               && blockItem.getBlock() instanceof CropBlock;
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
        super.tick(world, villager, time);
        if (villager.getVillagerBrain().getCurrentJob() != Chore.HARVEST) {
            return;
        }

        moveTowardsChorePosition(currentPos, 0.5F, 1);

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
                    if (equipChoreTool(villager, Chore.HARVEST, "chore.harvesting.nohoe")) {
                        BlockState state = world.getBlockState(currentPos);
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

    private ItemStack equipBoneMeal() {
        ItemStack stack = villager.getItemInHand(villager.getDominantHand());
        if (!stack.isEmpty() && stack.getItem() instanceof BoneMealItem) {
            rememberChoreHand(stack);
            return stack;
        }
        Container inventory = villager.getInventory();
        int slot = InventoryUtils.getFirstSlotContainingItem(inventory,
                item -> !item.isEmpty() && item.getItem() instanceof BoneMealItem);
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack selected = inventory.getItem(slot);
        villager.setItemInHand(villager.getDominantHand(), selected);
        rememberChoreHand(selected);
        return selected;
    }

    private void plantSeeds(ServerLevel world, VillagerEntityMCA villager, BlockPos target, Block block) {
        Optional<ItemStack> stack = InventoryUtils.stream(villager.getInventory())
                .filter(s -> !s.isEmpty() && s.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block)
                .findAny();

        if (stack.isEmpty()) {
            stack = InventoryUtils.stream(villager.getInventory())
                    .filter(HarvestingTask::isPlantableCropItem)
                    .findAny();
        }

        stack.ifPresentOrElse(s -> {
            world.setBlock(target, ((BlockItem) s.getItem()).getBlock().defaultBlockState(), Block.UPDATE_ALL);
            s.shrink(1);
            villager.swing(villager.getDominantHand());
            bonemealable.add(target);
        }, () -> {
            getAssigningPlayer().ifPresent(p -> villager.sendChatMessage(p, "chore.harvesting.noseed"));
        });
    }

    @SuppressWarnings("deprecation")
    private void bonemealCrop(ServerLevel world, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack boneMeal = equipBoneMeal();
        if (!boneMeal.isEmpty() && BoneMealItem.growCrop(boneMeal, world, pos)) {
            villager.swing(villager.getDominantHand());
        }
    }

    private void harvestCrops(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        ItemStack tool = villager.getItemInHand(villager.getDominantHand());
        if (world.destroyBlock(pos, false, villager)) {
            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, villager.position())
                    .withParameter(LootContextParams.TOOL, tool)
                    .withParameter(LootContextParams.THIS_ENTITY, villager)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                    .withLuck(0);

            List<ItemStack> drops = world.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable()).getRandomItems(builder.create(LootContextParamSets.BLOCK));
            for (ItemStack stack : drops) {
                InventoryUtils.insertOrDropAt(villager.getInventory(), stack, world, pos);
            }
        }
    }
}
