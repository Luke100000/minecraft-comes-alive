package net.conczin.mca.item;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.item.components.BabyParentsComponent;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class BabyItem extends Item {
    private final Gender gender;

    public BabyItem(Gender gender, Item.Properties properties) {
        super(properties);
        this.gender = gender;
    }

    public static ItemStack createItem(Entity mother, Entity father, long seed) {
        Gender gender = Gender.getRandom();
        ItemStack stack = (gender.binary() == Gender.MALE ? ItemsMCA.BABY_BOY : ItemsMCA.BABY_GIRL).getDefaultInstance();

        VillagerLike<?> motherVillager = VillagerLike.toVillager(mother);
        VillagerLike<?> fatherVillager = VillagerLike.toVillager(father);

        // Create dummy child to generate genes and traits
        VillagerEntityMCA child = VillagerFactory.newVillager(mother.level())
                .withPosition(mother.position())
                .withGender(gender)
                .withAge(-AgeState.getMaxAge())
                .build();

        // Combine genes
        child.getGenetics().combine(
                motherVillager.getGenetics(),
                fatherVillager.getGenetics(),
                seed
        );

        // Inherit traits
        child.getTraits().inherit(motherVillager.getTraits(), seed);
        child.getTraits().inherit(fatherVillager.getTraits(), seed);

        // Save child for later
        CompoundTag compound = new CompoundTag();
        child.save(compound);
        stack.set(DataComponentsMCA.BABY_NBT, CustomData.of(compound));
        stack.set(DataComponentsMCA.BABY_AGE, 0);
        stack.set(DataComponentsMCA.BABY_PARENTS, new BabyParentsComponent(
                mother.getUUID(),
                father.getUUID(),
                mother.getName().getString(),
                father.getName().getString()
        ));

        // Make sure family tree entries exist
        FamilyTree tree = FamilyTree.get((ServerLevel) mother.level());
        tree.getOrCreate(mother);
        tree.getOrCreate(father);

        return stack;
    }

    public static boolean hasBeenInvalidated(ItemStack stack) {
        return stack.getOrDefault(DataComponentsMCA.BABY_INVALIDATED, false);
    }

    private static boolean canGrow(int age) {
        return age >= Config.getServerConfig().babyItemGrowUpTime;
    }

    private static boolean isReadyToGrowUp(ItemStack stack) {
        return canGrow(stack.getOrDefault(DataComponentsMCA.BABY_AGE, 0));
    }

    public Gender getGender() {
        return gender;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    public boolean onDropped(ItemStack stack, Player player) {
        if (!hasBeenInvalidated(stack)) {
            if (!player.level().isClientSide) {
                int count = stack.getOrDefault(DataComponentsMCA.BABY_DROP_ATTEMPTS, 0) + 1;
                stack.set(DataComponentsMCA.BABY_DROP_ATTEMPTS, count);
                CriterionMCA.BABY_DROPPED.trigger((ServerPlayer) player, count);
                player.displayClientMessage(Component.translatable("item.mca.baby.no_drop"), true);
            }
            return false;
        }

        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (world.isClientSide) {
            return;
        }

        // update
        if (world.getGameTime() % 20 == 0) {
            stack.set(DataComponentsMCA.BABY_AGE, stack.getOrDefault(DataComponentsMCA.BABY_AGE, 0) + 20);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return Component.translatable(getDescriptionId(stack) + ".named", stack.get(DataComponents.CUSTOM_NAME));
        } else {
            return super.getName(stack);
        }
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        if (hasBeenInvalidated(stack)) {
            return super.getDescriptionId(stack) + ".blanket";
        }
        return super.getDescriptionId(stack);
    }

    @Override
    public final InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (world.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }

        // Right-clicking an unnamed baby allows you to name it
        if (!stack.has(DataComponents.CUSTOM_NAME)) {
            if (player instanceof ServerPlayer serverPlayer) {
                Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BABY_NAME), serverPlayer);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Not old enough
        if (!isReadyToGrowUp(stack)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("item.mca.baby.not_ready"), true);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Name is good and we're ready to grow
        if (player instanceof ServerPlayer serverPlayer) {
            birthChild(stack, (ServerLevel) world, serverPlayer);
        }
        stack.shrink(1);

        return InteractionResultHolder.success(stack);
    }

    protected VillagerEntityMCA birthChild(ItemStack stack, ServerLevel world, ServerPlayer player) {
        VillagerEntityMCA child = VillagerFactory.newVillager(world)
                .withPosition(player.position())
                .withGender(gender)
                .build();

        CompoundTag savedBaby = stack.getOrDefault(DataComponentsMCA.BABY_NBT, CustomData.EMPTY).copyTag();
        child.readAdditionalSaveData(savedBaby);

        child.setCustomName(stack.getOrDefault(DataComponents.CUSTOM_NAME, Component.literal("Unnamed")));

        WorldUtils.spawnEntity(world, child, MobSpawnType.BREEDING);

        FamilyTree tree = FamilyTree.get(world);

        // Assign parents
        child.getRelationships().getFamilyEntry().removeMother();
        child.getRelationships().getFamilyEntry().removeFather();

        BabyParentsComponent parents = stack.get(DataComponentsMCA.BABY_PARENTS);

        if (parents != null) {
            Stream.of(parents.mother(), parents.father()).forEach(uuid -> {
                Optional.ofNullable(world.getEntity(uuid))
                        .map(tree::getOrCreate)
                        .or(() -> tree.getOrEmpty(uuid)).ifPresent(entry -> {
                            child.getRelationships().getFamilyEntry().assignParent(entry);
                        });
            });

            // notify parents
            Stream.of(parents.mother(), parents.father()).map(world::getEntity).filter(Objects::nonNull)
                    .filter(e -> e instanceof ServerPlayer)
                    .map(ServerPlayer.class::cast)
                    .distinct()
                    .forEach(ply -> {
                        // advancement
                        CriterionMCA.FAMILY.trigger(ply);

                        // set proper dialogue type
                        Memories memories = child.getVillagerBrain().getMemoriesForPlayer(ply);
                        memories.setHearts(Config.getInstance().childInitialHearts);
                    });
        }

        return child;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Player player = ClientProxy.getClientPlayer();
        int age = stack.getOrDefault(DataComponentsMCA.BABY_AGE, 0);

        // Name
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            tooltip.add(Component.translatable("item.mca.baby.name", name.copy().withColor(gender.getColor())).withStyle(ChatFormatting.GRAY));

            if (age > 0) {
                tooltip.add(Component.translatable("item.mca.baby.age", StringUtil.formatTickDuration(age, 20)).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("item.mca.baby.give_name").withStyle(ChatFormatting.YELLOW));
        }

        // Parents
        BabyParentsComponent parents = stack.get(DataComponentsMCA.BABY_PARENTS);
        if (parents != null) {
            tooltip.add(Component.translatable("item.mca.baby.mother",
                    player != null && parents.mother().equals(player.getUUID())
                            ? Component.translatable("item.mca.baby.owner.you")
                            : parents.motherName()
            ).withStyle(ChatFormatting.GRAY));

            tooltip.add(Component.translatable("item.mca.baby.father",
                    player != null && parents.father().equals(player.getUUID())
                            ? Component.translatable("item.mca.baby.owner.you")
                            : parents.fatherName()
            ).withStyle(ChatFormatting.GRAY));
        }

        // Ready to yeet
        if (stack.has(DataComponents.CUSTOM_NAME) && canGrow(age)) {
            tooltip.add(Component.translatable("item.mca.baby.state.ready").withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
