package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
public final class HuntingTaskGameTests {
    private HuntingTaskGameTests() {
    }

    @GameTest(batch = "mca_hunting_own_loot", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void hunterCollectsOnlyOwnPreyLoot(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 playerPosition = Vec3.atBottomCenterOf(start.east(2));
        player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
        ItemEntity playerDrop = player.drop(new ItemStack(Items.DIAMOND), false, true);
        helper.assertTrue(playerDrop != null && helper.getLevel().addFreshEntity(playerDrop),
                "could not place the player's dropped item");

        Cow otherPrey = spawnOrdinaryCow(helper.getLevel(), start.south(3));
        ItemEntity unrelated = new ItemEntity(helper.getLevel(), start.getX() + 1.5, start.getY(),
                start.getZ() + 1.5, new ItemStack(Items.EMERALD));
        Cow prey = spawnLootCow(helper.getLevel(), start.east(), () -> {
            // Both actions happen inside the hunter's synchronous prey.hurt call.
            otherPrey.hurt(helper.getLevel().damageSources().playerAttack(player), 100.0F);
            helper.getLevel().addFreshEntity(unrelated);
        });

        try {
            strikeAndProcessDeath(helper, hunter, prey);

            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.IRON_INGOT) >= 3,
                    "hunter did not collect its prey's loot-table iron drops");
            helper.assertTrue(helper.getLevel().getEntity(playerDrop.getId()) == playerDrop
                            && playerDrop.getItem().is(Items.DIAMOND),
                    "hunter consumed the unrelated player's dropped diamond");
            helper.assertTrue(helper.getLevel().getEntity(unrelated.getId()) == unrelated
                            && unrelated.getItem().is(Items.EMERALD),
                    "hunter consumed an unrelated item spawned inside its strike");
            helper.assertTrue(itemCountNear(helper.getLevel(), otherPrey, Items.BEEF) > 0,
                    "hunter consumed another attacker's prey loot");
            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.BEEF) == 0
                            && inventoryCount(hunter.getInventory(), Items.EMERALD) == 0
                            && inventoryCount(hunter.getInventory(), Items.DIAMOND) == 0,
                    "hunter's inventory contains unrelated drops");
        } finally {
            discardItemsNear(helper.getLevel(), prey);
            discardItemsNear(helper.getLevel(), otherPrey);
            playerDrop.discard();
            unrelated.discard();
            prey.discard();
            otherPrey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_hunting_full_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullInventoryPreservesPreyDropEntities(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        fillInventory(hunter.getInventory(), -1);
        Cow prey = spawnLootCow(helper.getLevel(), start.east(), () -> {
        });

        try {
            HuntingTask task = beginHunt(helper, hunter, prey);
            long time = helper.getLevel().getGameTime();
            task.tick(helper.getLevel(), hunter, time);
            List<ItemEntity> originalDrops = ironDropsNear(helper.getLevel(), prey);
            int initialCount = originalDrops.stream().mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(initialCount >= 3, "fixture did not produce iron loot");

            task.tick(helper.getLevel(), hunter, time + 1);
            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.IRON_INGOT) == 0,
                    "full inventory unexpectedly gained iron");
            helper.assertTrue(itemCountNear(helper.getLevel(), prey, Items.IRON_INGOT) == initialCount,
                    "full inventory deleted some or all prey loot");
            helper.assertTrue(originalDrops.stream().allMatch(drop -> helper.getLevel().getEntity(drop.getId()) == drop),
                    "the original prey drop entities were replaced or discarded");
        } finally {
            discardItemsNear(helper.getLevel(), prey);
            prey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_hunting_partial_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void partialInventoryKeepsExactOriginalRemainder(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        fillInventory(hunter.getInventory(), 63);
        Cow prey = spawnLootCow(helper.getLevel(), start.east(), () -> {
        });

        try {
            HuntingTask task = beginHunt(helper, hunter, prey);
            long time = helper.getLevel().getGameTime();
            task.tick(helper.getLevel(), hunter, time);
            List<ItemEntity> originalDrops = ironDropsNear(helper.getLevel(), prey);
            int produced = originalDrops.stream().mapToInt(item -> item.getItem().getCount()).sum()
                    + inventoryCount(hunter.getInventory(), Items.IRON_INGOT) - 63;
            helper.assertTrue(produced >= 3, "fixture did not produce enough iron for overflow");

            task.tick(helper.getLevel(), hunter, time + 1);
            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.IRON_INGOT) == 64,
                    "hunter did not insert exactly one available ingot");
            helper.assertTrue(itemCountNear(helper.getLevel(), prey, Items.IRON_INGOT) == produced - 1,
                    "partial insertion did not preserve the exact loot remainder");
            helper.assertTrue(originalDrops.stream().allMatch(drop -> helper.getLevel().getEntity(drop.getId()) == drop),
                    "partial insertion replaced or discarded a still-needed drop entity");
        } finally {
            discardItemsNear(helper.getLevel(), prey);
            prey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_hunting_cancelled_loot", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void cancelledNeoForgeDropsAreNotCollected(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        Cow prey = spawnLootCow(helper.getLevel(), start.east(), () -> {
        });
        Consumer<LivingDropsEvent> cancellation = event -> {
            if (event.getEntity() == prey) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, cancellation);
        try {
            strikeAndProcessDeath(helper, hunter, prey);
            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.IRON_INGOT) == 0,
                    "hunter collected drops canceled by LivingDropsEvent");
            helper.assertTrue(itemCountNear(helper.getLevel(), prey, Items.IRON_INGOT) == 0,
                    "canceled prey drops appeared in the world");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancellation);
            discardItemsNear(helper.getLevel(), prey);
            prey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_hunting_disabled_loot", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void mobLootGameruleSuppressesHuntingCollection(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        Cow prey = spawnLootCow(helper.getLevel(), start.east(), () -> {
        });
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean previous = rule.get();
        try {
            rule.set(false, helper.getLevel().getServer());
            strikeAndProcessDeath(helper, hunter, prey);
            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.IRON_INGOT) == 0
                            && itemCountNear(helper.getLevel(), prey, Items.IRON_INGOT) == 0,
                    "hunting generated or collected loot despite doMobLoot=false");
        } finally {
            rule.set(previous, helper.getLevel().getServer());
            discardItemsNear(helper.getLevel(), prey);
            prey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_hunting_cooldown_durability", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void huntingRespectsAttackCooldownAndDamagesSwordOnlyOnHits(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        Cow prey = new Cow(EntityType.COW, helper.getLevel());
        Vec3 spawnPos = Vec3.atBottomCenterOf(start.east());
        prey.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        prey.setNoAi(true);
        prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        prey.setHealth(20.0F);
        helper.getLevel().addFreshEntity(prey);

        Cow immunePrey = new Cow(EntityType.COW, helper.getLevel()) {
            @Override
            public boolean hurt(DamageSource source, float amount) {
                return false;
            }
        };
        preparePrey(helper.getLevel(), immunePrey, start.south());

        try {
            HuntingTask task = beginHunt(helper, hunter, prey);
            long time = helper.getLevel().getGameTime();
            task.tick(helper.getLevel(), hunter, time);

            ItemStack sword = hunter.getItemInHand(hunter.getDominantHand());
            float healthAfterFirstHit = prey.getHealth();
            helper.assertTrue(healthAfterFirstHit < 20.0F, "hunter did not damage prey on its initial strike");
            helper.assertTrue(hunter.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN),
                    "hunting strike did not start ATTACK_COOLING_DOWN");
            helper.assertTrue(sword.getDamageValue() == 1,
                    "one successful strike did not consume exactly 1 durability; damage=" + sword.getDamageValue());

            for (int i = 1; i <= 5; i++) {
                task.tick(helper.getLevel(), hunter, time + i);
            }
            helper.assertTrue(prey.getHealth() == healthAfterFirstHit,
                    "hunter bypassed ATTACK_COOLING_DOWN and struck again immediately");
            helper.assertTrue(sword.getDamageValue() == 1,
                    "cooldown ticks drained extra sword durability; damage=" + sword.getDamageValue());

            hunter.getBrain().eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);
            try {
                Field targetField = HuntingTask.class.getDeclaredField("target");
                targetField.setAccessible(true);
                targetField.set(task, immunePrey);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("could not switch hunter target", exception);
            }
            task.tick(helper.getLevel(), hunter, time + 10);
            helper.assertTrue(sword.getDamageValue() == 1,
                    "rejected hit still damaged the hunter's sword; damage=" + sword.getDamageValue());
        } finally {
            discardItemsNear(helper.getLevel(), prey);
            discardItemsNear(helper.getLevel(), immunePrey);
            prey.discard();
            immunePrey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_hunting_fire_aspect", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fireAspectSwordCapturesCookedMeatWithoutTouchingUnrelatedDrops(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        HunterFixture fixture = spawnHunter(helper, start);
        VillagerEntityMCA hunter = fixture.hunter();
        hunter.getTraits().removeTrait(Traits.WEAK);
        hunter.updateAttributes();
        ItemStack enchantedSword = new ItemStack(Items.IRON_SWORD);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        enchantedSword.enchant(enchantments.getOrThrow(Enchantments.FIRE_ASPECT), 2);
        enchantedSword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 5);
        hunter.getInventory().setItem(0, enchantedSword);

        Cow prey = spawnOrdinaryCow(helper.getLevel(), start.east());
        // Full 10 HP (> old 6.0F flat damage) proves doHurtTarget applies weapon attributes + Sharpness V.
        prey.setHealth(prey.getMaxHealth());
        ItemEntity unrelated = new ItemEntity(helper.getLevel(), start.getX() + 1.5, start.getY(),
                start.getZ() + 0.5, new ItemStack(Items.EMERALD));
        helper.getLevel().addFreshEntity(unrelated);

        try {
            HuntingTask task = beginHunt(helper, hunter, prey);
            hunter.tick();
            helper.assertTrue(hunter.getAttributeValue(Attributes.ATTACK_DAMAGE) > 6.0D,
                    "hunter did not sync equipped sword attack damage before striking");
            long time = helper.getLevel().getGameTime();
            task.tick(helper.getLevel(), hunter, time);
            helper.assertTrue(prey.isDeadOrDying(),
                    "Sharpness V iron sword strike did not kill full-health (10 HP) cow; remaining=" + prey.getHealth());
            task.tick(helper.getLevel(), hunter, time + 1);

            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.COOKED_BEEF) >= 1,
                    "Fire Aspect hunting kill did not capture cooked beef into the hunter's inventory");
            helper.assertTrue(inventoryCount(hunter.getInventory(), Items.BEEF) == 0,
                    "Fire Aspect hunting kill left raw beef unsmelted");
            helper.assertTrue(helper.getLevel().getEntity(unrelated.getId()) == unrelated
                            && unrelated.getItem().is(Items.EMERALD)
                            && inventoryCount(hunter.getInventory(), Items.EMERALD) == 0,
                    "Fire Aspect hunting kill collected an unrelated ground item");
        } finally {
            discardItemsNear(helper.getLevel(), prey);
            unrelated.discard();
            prey.discard();
            fixture.close(helper);
        }
        helper.succeed();
    }

    private static HunterFixture spawnHunter(GameTestHelper helper, BlockPos start) {
        helper.getLevel().getChunk(start);
        VillagerEntityMCA hunter = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Hunting Loot Probe")
                .spawn(MobSpawnType.STRUCTURE);
        hunter.refreshBrain(helper.getLevel());
        hunter.setNoAi(true);
        hunter.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        // The assigning player must be discoverable by ServerLevel.getPlayerByUUID.
        // A makeMockPlayer() instance is not registered, so a real tick cancels its job.
        ServerPlayer assigner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "hunting-assigner"), ClientInformation.createDefault());
        helper.getLevel().players().add(assigner);
        hunter.getVillagerBrain().assignJob(Chore.HUNT, assigner);
        helper.assertTrue(hunter.getVillagerBrain().getJobAssigner().orElse(null) == assigner,
                "hunting loot fixture has no registered chore assigner");
        hunter.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        hunter.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return new HunterFixture(hunter, assigner);
    }

    private record HunterFixture(VillagerEntityMCA hunter, ServerPlayer assigner) {
        private void close(GameTestHelper helper) {
            helper.getLevel().players().remove(assigner);
            hunter.discard();
        }
    }

    private static Cow spawnLootCow(ServerLevel world, BlockPos position, Runnable duringDamage) {
        Cow prey = new Cow(EntityType.COW, world) {
            private boolean ranDamageAction;

            @Override
            protected ResourceKey<LootTable> getDefaultLootTable() {
                // Three to five guaranteed iron ingots exercise exact partial insertion.
                return EntityType.IRON_GOLEM.getDefaultLootTable();
            }

            @Override
            public boolean hurt(DamageSource source, float amount) {
                if (!ranDamageAction) {
                    ranDamageAction = true;
                    duringDamage.run();
                }
                return super.hurt(source, amount);
            }
        };
        preparePrey(world, prey, position);
        return prey;
    }

    private static Cow spawnOrdinaryCow(ServerLevel world, BlockPos position) {
        Cow cow = new Cow(EntityType.COW, world);
        preparePrey(world, cow, position);
        return cow;
    }

    private static void preparePrey(ServerLevel world, Cow prey, BlockPos position) {
        Vec3 spawnPosition = Vec3.atBottomCenterOf(position);
        prey.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        prey.setNoAi(true);
        prey.setHealth(1.0F);
        world.addFreshEntity(prey);
    }

    private static HuntingTask beginHunt(GameTestHelper helper, VillagerEntityMCA hunter, Cow prey) {
        HuntingTask task = new HuntingTask();
        long time = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), hunter, time);
        try {
            Field target = HuntingTask.class.getDeclaredField("target");
            target.setAccessible(true);
            target.set(task, prey);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare the hunter's prey target", exception);
        }
        return task;
    }

    private static void strikeAndProcessDeath(GameTestHelper helper, VillagerEntityMCA hunter, Cow prey) {
        HuntingTask task = beginHunt(helper, hunter, prey);
        long time = helper.getLevel().getGameTime();
        task.tick(helper.getLevel(), hunter, time);
        helper.assertTrue(prey.isDeadOrDying(), "hunter did not kill its prepared prey");
        // The legacy implementation did its broad pickup on the subsequent tick.
        task.tick(helper.getLevel(), hunter, time + 1);
    }

    private static void fillInventory(SimpleContainer inventory, int ingotCount) {
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        if (ingotCount > 0) {
            inventory.setItem(inventory.getContainerSize() - 1, new ItemStack(Items.IRON_INGOT, ingotCount));
        }
    }

    private static int inventoryCount(SimpleContainer inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static List<ItemEntity> ironDropsNear(ServerLevel world, Cow prey) {
        return world.getEntitiesOfClass(ItemEntity.class, prey.getBoundingBox().inflate(2.0),
                item -> item.getItem().is(Items.IRON_INGOT));
    }

    private static int itemCountNear(ServerLevel world, Cow prey, Item item) {
        return world.getEntitiesOfClass(ItemEntity.class, prey.getBoundingBox().inflate(1.5),
                        drop -> drop.getItem().is(item)).stream()
                .mapToInt(drop -> drop.getItem().getCount()).sum();
    }

    private static void discardItemsNear(ServerLevel world, Cow prey) {
        world.getEntitiesOfClass(ItemEntity.class, prey.getBoundingBox().inflate(2.0))
                .forEach(ItemEntity::discard);
    }
}
