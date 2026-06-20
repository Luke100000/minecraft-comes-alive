package net.mca.item;

import net.mca.ClientProxy;
import net.mca.Config;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerFactory;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.FamilyTree;
import net.mca.network.s2c.OpenGuiRequest;
import net.mca.util.WorldUtils;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static net.minecraft.util.Util.NIL_UUID;

public class BabyItem extends Item {
    private final Gender gender;

    public BabyItem(Gender gender, Item.Settings properties) {
        super(properties);
        this.gender = gender;
    }

    public static ItemStack createItem(Entity mother, Entity father, long seed) {
        Gender gender = Gender.getRandom();
        ItemStack stack = (gender.binary() == Gender.MALE ? ItemsMCA.BABY_BOY : ItemsMCA.BABY_GIRL).get().getDefaultStack();

        NbtCompound nbt = getBabyNbt(stack);

        nbt.putUuid("mother", mother.getUuid());
        nbt.putUuid("father", father.getUuid());

        nbt.putString("motherName", mother.getName().getString());
        nbt.putString("fatherName", father.getName().getString());

        VillagerLike<?> motherVillager = VillagerLike.toVillager(mother);
        VillagerLike<?> fatherVillager = VillagerLike.toVillager(father);

        // Create dummy child to generate genes and traits
        VillagerEntityMCA child = VillagerFactory.newVillager(mother.getWorld())
                .withPosition(mother.getPos())
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
        NbtCompound compound = new NbtCompound();
        child.writeCustomDataToNbt(compound);
        nbt.put("child", compound);

        // Make sure family tree entries exist
        FamilyTree tree = FamilyTree.get((ServerWorld)mother.getWorld());
        tree.getOrCreate(mother);
        tree.getOrCreate(father);

        return stack;
    }

    public static NbtCompound getBabyNbt(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (!nbt.contains("baby")) {
            NbtCompound baby = stack.getOrCreateSubNbt("baby");
            baby.putUuid("mother", NIL_UUID);
            baby.putUuid("father", NIL_UUID);
            baby.putString("motherName", "Unknown");
            baby.putString("fatherName", "Unknown");
            baby.putInt("age", 0);
        }
        return stack.getSubNbt("baby");
    }

    public Gender getGender() {
        return gender;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return ActionResult.PASS;
    }

    public boolean onDropped(ItemStack stack, PlayerEntity player) {
        if (!hasBeenInvalidated(stack)) {
            if (!player.getWorld().isClient) {
                int count = 0;
                if (stack.getOrCreateNbt().contains("dropAttempts", NbtElement.INT_TYPE)) {
                    count = stack.getOrCreateNbt().getInt("dropAttempts") + 1;
                }
                stack.getOrCreateNbt().putInt("dropAttempts", count);
                CriterionMCA.BABY_DROPPED_CRITERION.trigger((ServerPlayerEntity)player, count);
                player.sendMessage(Text.translatable("item.mca.baby.no_drop"), true);
            }
            return false;
        }

        return true;
    }

    public static boolean shouldCancelDrop(ItemStack stack, PlayerEntity player) {
        return stack.getItem() instanceof BabyItem baby && !baby.onDropped(stack, player);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) {
            return;
        }

        // use an anvil to rename your baby (in case of typos like I did)
        if (stack.hasCustomName()) {
            getBabyNbt(stack).putString("babyName", stack.getName().getString());
            stack.removeCustomName();

            if (entity instanceof ServerPlayerEntity player) {
                CriterionMCA.GENERIC_EVENT_CRITERION.trigger(player, "rename_baby");
            }
        }

        // update
        if (world.getTime() % 1200 == 0) {
            getBabyNbt(stack).putInt("age", getBabyNbt(stack).getInt("age") + 1200);
        }
    }

    @Override
    public Text getName(ItemStack stack) {
        if (getBabyNbt(stack).contains("babyName")) {
            return Text.translatable(getTranslationKey(stack) + ".named", getBabyNbt(stack).getString("babyName"));
        } else {
            return super.getName(stack);
        }
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        if (hasBeenInvalidated(stack)) {
            return super.getTranslationKey(stack) + ".blanket";
        }
        return super.getTranslationKey(stack);
    }

    @Override
    public final TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.pass(stack);
        }

        // Right-clicking an unnamed baby allows you to name it
        if (!getBabyNbt(stack).contains("babyName")) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BABY_NAME), serverPlayer);
            }
            return TypedActionResult.pass(stack);
        }

        // Not old enough
        if (!isReadyToGrowUp(stack)) {
            return TypedActionResult.pass(stack);
        }

        // Name is good and we're ready to grow
        if (player instanceof ServerPlayerEntity serverPlayer) {
            birthChild(stack, (ServerWorld)world, serverPlayer);
        }
        stack.decrement(1);

        return TypedActionResult.success(stack);
    }

    protected VillagerEntityMCA birthChild(ItemStack stack, ServerWorld world, ServerPlayerEntity player) {
        VillagerEntityMCA child = VillagerFactory.newVillager(world)
                .withPosition(player.getPos())
                .withGender(gender)
                .withAge(-AgeState.getMaxAge())
                .build();

        if (getBabyNbt(stack).contains("child")) {
            child.readCustomDataFromNbt(getBabyNbt(stack).getCompound("child"));
        }

        child.setName(getBabyNbt(stack).getString("babyName"));

        WorldUtils.spawnEntity(world, child, SpawnReason.BREEDING);

        FamilyTree tree = FamilyTree.get(world);

        // Assign parents
        child.getRelationships().getFamilyEntry().replaceParents(Stream.of("mother", "father")
                .map(key -> getBabyNbt(stack).getUuid(key))
                .map(uuid -> Optional.ofNullable(world.getEntity(uuid))
                        .map(tree::getOrCreate)
                        .or(() -> tree.getOrEmpty(uuid)))
                .flatMap(Optional::stream));

        // notify parents
        Stream.of("mother", "father").map(key -> world.getEntity(getBabyNbt(stack).getUuid(key))).filter(Objects::nonNull)
                .filter(e -> e instanceof ServerPlayerEntity)
                .map(ServerPlayerEntity.class::cast)
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
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext flag) {
        PlayerEntity player = ClientProxy.getClientPlayer();
        int age = getBabyNbt(stack).getInt("age") + (int)(world == null ? 0 : world.getTime() % 1200);

        // Name
        if (getBabyNbt(stack).contains("babyName")) {
            final MutableText text = Text.literal(getBabyNbt(stack).getString("babyName"));
            tooltip.add(Text.translatable("item.mca.baby.name", text.setStyle(text.getStyle().withColor(gender.getColor()))).formatted(Formatting.GRAY));

            if (age > 0) {
                tooltip.add(Text.translatable("item.mca.baby.age", StringHelper.formatTicks(age)).formatted(Formatting.GRAY));
            }
        } else {
            tooltip.add(Text.translatable("item.mca.baby.give_name").formatted(Formatting.YELLOW));
        }

        tooltip.add(Text.literal(""));

        // Parents
        Stream.of("mother", "father").forEach(p -> {
                    tooltip.add(Text.translatable("item.mca.baby." + p,
                            player != null && getBabyNbt(stack).getUuid(p).equals(player.getUuid())
                                    ? Text.translatable("item.mca.baby.owner.you")
                                    : getBabyNbt(stack).getString(p + "Name")
                    ).formatted(Formatting.GRAY));
                }
        );

        // Ready to yeet
        if (getBabyNbt(stack).contains("babyName") && canGrow(age)) {
            tooltip.add(Text.translatable("item.mca.baby.state.ready").formatted(Formatting.DARK_GREEN));
        }

        // Infected
        if (getBabyNbt(stack).getFloat("infectionProgress") > 0) {
            tooltip.add(Text.translatable("item.mca.baby.state.infected").formatted(Formatting.DARK_GREEN));
        }
    }

    public static boolean hasBeenInvalidated(ItemStack stack) {
        return stack.getOrCreateNbt().contains("invalidated");
    }

    private static boolean canGrow(int age) {
        return age >= Config.getServerConfig().babyItemGrowUpTime;
    }

    private static boolean isReadyToGrowUp(ItemStack stack) {
        return stack.hasNbt() && canGrow(getBabyNbt(stack).getInt("age"));
    }
}
