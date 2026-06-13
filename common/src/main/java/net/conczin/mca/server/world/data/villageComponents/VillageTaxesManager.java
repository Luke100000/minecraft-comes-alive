package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VillageTaxesManager {
    private static final int MAX_STORAGE_SIZE = 1024;

    private final Village village;

    public VillageTaxesManager(Village village) {
        this.village = village;
    }

    public void taxes(ServerLevel world) {
        double taxes = Config.getInstance().taxesFactor * village.getPopulation() * village.getTaxes() + world.random.nextDouble();
        int moodImpact = 0;

        //response
        Component msg;
        float r = village.getTaxes() + (world.random.nextFloat() - 0.5f) * world.random.nextFloat();
        if (village.getTaxes() == 0.0f) {
            msg = Component.translatable("gui.village.taxes.no", village.getName()).withStyle(ChatFormatting.GREEN);
            moodImpact = 5;
            taxes = 0.0;
        } else if (r < 0.1) {
            msg = Component.translatable("gui.village.taxes.more", village.getName()).withStyle(ChatFormatting.GREEN);
            taxes += village.getPopulation() * 0.25;
        } else if (r < 0.3) {
            msg = Component.translatable("gui.village.taxes.happy", village.getName()).withStyle(ChatFormatting.DARK_GREEN);
            moodImpact = 5;
        } else if (r < 0.7) {
            msg = Component.translatable("gui.village.taxes", village.getName());
        } else if (r < 0.8) {
            msg = Component.translatable("gui.village.taxes.sad", village.getName()).withStyle(ChatFormatting.GOLD);
            moodImpact = -5;
        } else if (r < 0.9) {
            msg = Component.translatable("gui.village.taxes.angry", village.getName()).withStyle(ChatFormatting.RED);
            moodImpact = -10;
        } else {
            msg = Component.translatable("gui.village.taxes.riot", village.getName()).withStyle(ChatFormatting.DARK_RED);
            taxes = 0;
        }

        //send all player with rank merchant a notification
        world.players().stream()
                .filter(v -> Tasks.getRank(village, v).isAtLeast(Rank.MERCHANT))
                .forEach(player -> player.displayClientMessage(msg, true));

        //upgrades
        if (village.hasBuilding("library")) {
            taxes *= 1.5;
        }

        //choose as many items as possible
        while (taxes > 0.0) {
            double finalTaxes = taxes;

            // create a weighted list of all available items
            List<String> valids = Config.getInstance().taxesMap.entrySet().stream()
                    .filter(e -> e.getValue() * world.random.nextFloat() < finalTaxes)
                    .map(Map.Entry::getKey)
                    .toList();

            if (valids.isEmpty()) {
                break;
            }

            // pick a random item
            String itemName = valids.get(world.random.nextInt(valids.size()));
            Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(itemName));

            if (item == Items.AIR) {
                throw new RuntimeException("The taxes map contains an invalid item %s!".formatted(itemName));
            }

            // pay the price
            taxes -= Config.getInstance().taxesMap.get(itemName);

            // stack it or create a new item
            Optional<ItemStack> stack = village.storageBuffer.stream().filter(i -> i.is(item) && i.getCount() < i.getMaxStackSize()).findAny();
            if (stack.isPresent()) {
                stack.get().grow(1);
            } else if (village.storageBuffer.size() < MAX_STORAGE_SIZE) {
                village.storageBuffer.add(new ItemStack(item, 1));
            }
        }

        if (moodImpact != 0) {
            village.pushMood(moodImpact);
        }

        deliverTaxes(world);
    }

    public void deliverTaxes(ServerLevel world) {
        if (village.hasStoredResource() && WorldUtils.isChunkLoaded(world, village.getCenter())) {
            village.getBuildingsOfType("storage").forEach(building -> building.getBlocks().values().stream()
                    .flatMap(Collection::stream)
                    .forEach(p -> {
                        if (village.hasStoredResource()) {
                            tryToPutIntoInventory(world, p);
                        }
                    }));
        }
    }

    private void tryToPutIntoInventory(ServerLevel world, BlockPos p) {
        BlockState state = world.getBlockState(p);
        if (state.hasBlockEntity()) {
            BlockEntity blockEntity = world.getBlockEntity(p);
            if (blockEntity instanceof Container inventory) {
                Block block = state.getBlock();
                if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock chest) {
                    inventory = ChestBlock.getContainer(chest, state, world, p, true);
                    if (inventory != null) {
                        putIntoInventory(inventory);
                    }
                }
            }
        }
    }

    private void putIntoInventory(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            boolean changes = true;
            while (changes) {
                changes = false;
                ItemStack stack = inventory.getItem(i);
                ItemStack tax = village.storageBuffer.getFirst();
                if (stack.getItem() == tax.getItem()) {
                    int diff = Math.min(tax.getCount(), stack.getMaxStackSize() - stack.getCount());
                    if (diff > 0) {
                        stack.grow(diff);
                        tax.shrink(diff);
                        if (tax.isEmpty()) {
                            village.storageBuffer.removeFirst();
                            changes = true;
                        }
                        inventory.setChanged();
                    }
                } else if (stack.isEmpty()) {
                    inventory.setItem(i, tax);
                    inventory.setChanged();
                    village.storageBuffer.removeFirst();
                    changes = true;
                }
                if (!village.hasStoredResource()) {
                    return;
                }
            }
        }
    }
}
