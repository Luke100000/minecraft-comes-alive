package net.conczin.mca.item;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.advancement.criterion.CriterionMCA;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static net.minecraft.Util.NIL_UUID;

public class BabyItem extends Item {
    private final Gender gender;

    public BabyItem(Gender gender, Item.Properties properties) {
        super(properties);
        this.gender = gender;
    }

    public static ItemStack createItem(Entity mother, Entity father, long seed) {
        Gender gender = Gender.getRandom();
        ItemStack stack = (gender.binary() == Gender.MALE ? ItemsMCA.BABY_BOY : ItemsMCA.BABY_GIRL).get().getDefaultInstance();

        CompoundTag nbt = getBabyNbt(stack);

        nbt.putUUID("mother", mother.getUUID());
        nbt.putUUID("father", father.getUUID());

        nbt.putString("motherName", mother.getName().getString());
        nbt.putString("fatherName", father.getName().getString());

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
        child.addAdditionalSaveData(compound);
        nbt.put("child", compound);

        // Make sure family tree entries exist
        FamilyTree tree = FamilyTree.get((ServerLevel)mother.level());
        tree.getOrCreate(mother);
        tree.getOrCreate(father);

        return stack;
    }

    public static CompoundTag getBabyNbt(ItemStack stack) {
        CompoundTag nbt = stack.getOrCreateTag();
        if (!nbt.contains("baby")) {
            CompoundTag baby = stack.getOrCreateTagElement("baby");
            baby.putUUID("mother", NIL_UUID);
            baby.putUUID("father", NIL_UUID);
            baby.putString("motherName", "Unknown");
            baby.putString("fatherName", "Unknown");
            baby.putInt("age", 0);
        }
        return stack.getTagElement("baby");
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
                int count = 0;
                if (stack.getOrCreateTag().contains("dropAttempts", Tag.TAG_INT)) {
                    count = stack.getOrCreateTag().getInt("dropAttempts") + 1;
                }
                stack.getOrCreateTag().putInt("dropAttempts", count);
                CriterionMCA.BABY_DROPPED_CRITERION.trigger((ServerPlayer)player, count);
                player.displayClientMessage(Component.translatable("item.mca.baby.no_drop"), true);
            }
            return false;
        }

        return true;
    }

    public static boolean shouldCancelDrop(ItemStack stack, Player player) {
        return stack.getItem() instanceof BabyItem baby && !baby.onDropped(stack, player);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (world.isClientSide) {
            return;
        }

        // use an anvil to rename your baby (in case of typos like I did)
        if (stack.hasCustomHoverName()) {
            getBabyNbt(stack).putString("babyName", stack.getHoverName().getString());
            stack.resetHoverName();

            if (entity instanceof ServerPlayer player) {
                CriterionMCA.GENERIC_EVENT_CRITERION.trigger(player, "rename_baby");
            }
        }

        // update
        if (world.getGameTime() % 100 == 0) {
            getBabyNbt(stack).putInt("age", getBabyNbt(stack).getInt("age") + 100);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        if (getBabyNbt(stack).contains("babyName")) {
            return Component.translatable(getDescriptionId(stack) + ".named", getBabyNbt(stack).getString("babyName"));
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
        if (!getBabyNbt(stack).contains("babyName")) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BABY_NAME), serverPlayer);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Not old enough
        if (!isReadyToGrowUp(stack)) {
            return InteractionResultHolder.pass(stack);
        }

        // Name is good and we're ready to grow
        if (player instanceof ServerPlayer serverPlayer) {
            birthChild(stack, (ServerLevel)world, serverPlayer);
        }
        stack.shrink(1);

        return InteractionResultHolder.success(stack);
    }

    protected VillagerEntityMCA birthChild(ItemStack stack, ServerLevel world, ServerPlayer player) {
        VillagerEntityMCA child = VillagerFactory.newVillager(world)
                .withPosition(player.position())
                .withGender(gender)
                .withAge(-AgeState.getMaxAge())
                .build();

        if (getBabyNbt(stack).contains("child")) {
            child.readAdditionalSaveData(getBabyNbt(stack).getCompound("child"));
        }

        child.setName(getBabyNbt(stack).getString("babyName"));

        WorldUtils.spawnEntity(world, child, MobSpawnType.BREEDING);

        FamilyTree tree = FamilyTree.get(world);

        // Assign parents
        child.getRelationships().getFamilyEntry().replaceParents(Stream.of("mother", "father")
                .map(key -> getBabyNbt(stack).getUUID(key))
                .map(uuid -> Optional.ofNullable(world.getEntity(uuid))
                        .map(tree::getOrCreate)
                        .or(() -> tree.getOrEmpty(uuid)))
                .flatMap(Optional::stream));

        // notify parents
        Stream.of("mother", "father").map(key -> world.getEntity(getBabyNbt(stack).getUUID(key))).filter(Objects::nonNull)
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

        return child;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag flag) {
        Player player = ClientProxy.getClientPlayer();
        int age = getBabyNbt(stack).getInt("age");

        // Name
        if (getBabyNbt(stack).contains("babyName")) {
            final MutableComponent text = Component.literal(getBabyNbt(stack).getString("babyName"));
            tooltip.add(Component.translatable("item.mca.baby.name", text.setStyle(text.getStyle().withColor(gender.getColor()))).withStyle(ChatFormatting.GRAY));

            if (age > 0) {
                tooltip.add(Component.translatable("item.mca.baby.age", StringUtil.formatTickDuration(age)).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("item.mca.baby.give_name").withStyle(ChatFormatting.YELLOW));
        }

        tooltip.add(Component.literal(""));

        // Parents
        Stream.of("mother", "father").forEach(p -> {
                    tooltip.add(Component.translatable("item.mca.baby." + p,
                            player != null && getBabyNbt(stack).getUUID(p).equals(player.getUUID())
                                    ? Component.translatable("item.mca.baby.owner.you")
                                    : getBabyNbt(stack).getString(p + "Name")
                    ).withStyle(ChatFormatting.GRAY));
                }
        );

        // Ready to yeet
        if (getBabyNbt(stack).contains("babyName") && canGrow(age)) {
            tooltip.add(Component.translatable("item.mca.baby.state.ready").withStyle(ChatFormatting.DARK_GREEN));
        }

        // Infected
        if (getBabyNbt(stack).getFloat("infectionProgress") > 0) {
            tooltip.add(Component.translatable("item.mca.baby.state.infected").withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    public static boolean hasBeenInvalidated(ItemStack stack) {
        return stack.getOrCreateTag().contains("invalidated");
    }

    private static boolean canGrow(int age) {
        return age >= Config.getServerConfig().babyItemGrowUpTime;
    }

    private static boolean isReadyToGrowUp(ItemStack stack) {
        return stack.hasTag() && canGrow(getBabyNbt(stack).getInt("age"));
    }
}
