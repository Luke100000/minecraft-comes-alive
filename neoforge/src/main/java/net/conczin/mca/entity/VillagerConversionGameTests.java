package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerConversionGameTests {
    private VillagerConversionGameTests() {
    }

    @GameTest(batch = "mca_villager_conversion", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void infectionAndCureRoundTripPreservesStateAndForcesZombiePersistence(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withGender(Gender.MALE)
                .withAge(AgeState.TEEN.toAge())
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(EntitySpawnReason.COMMAND);

        UUID identity = villager.getUUID();
        VillagerData expectedData = villager.getVillagerData().withLevel(4);
        villager.setVillagerData(expectedData);
        villager.setVillagerXp(37);
        villager.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));

        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BREAD, 5), 8, 3, 0.05F));
        villager.setOffers(offers);

        UUID gossipTarget = UUID.randomUUID();
        villager.getGossips().add(gossipTarget, GossipType.MAJOR_POSITIVE, 20);
        int expectedReputation = villager.getGossips().getReputation(gossipTarget, type -> true);

        helper.assertTrue(!villager.isPersistenceRequired(), "fixture villager unexpectedly requires persistence");

        villager.setInfectionProgress(1.0F);
        villager.tickCount = 20;
        villager.tick();

        var infectedEntity = helper.getLevel().getEntity(identity);
        helper.assertTrue(infectedEntity instanceof ZombieVillagerEntityMCA,
                "infection did not produce an MCA zombie villager under the preserved UUID");
        ZombieVillagerEntityMCA zombie = (ZombieVillagerEntityMCA) infectedEntity;
        helper.assertTrue(villager.isRemoved(), "source villager remained after single conversion");
        helper.assertTrue(zombie.getUUID().equals(identity), "zombie conversion changed MCA identity UUID");
        helper.assertTrue(helper.getLevel().getEntity(identity) == zombie,
                "converted zombie was not registered under the preserved UUID");
        helper.assertTrue(zombie.isPersistenceRequired(),
                "zombie conversion did not force persistence for the preserved MCA villager data");
        helper.assertTrue(zombie.getVillagerData().equals(expectedData), "villager data was not preserved into zombie form");
        helper.assertTrue(zombie.getVillagerXp() == 37, "villager XP was not preserved into zombie form");
        helper.assertTrue(zombie.getAgeState() == AgeState.TEEN, "MCA age state was not preserved into zombie form");
        helper.assertTrue(zombie.tradeOffers != null && zombie.tradeOffers.size() == 1
                        && zombie.tradeOffers.getFirst().getResult().is(Items.BREAD),
                "trade offers were not preserved into zombie form");
        helper.assertTrue(zombie.gossips != null
                        && zombie.gossips.getReputation(gossipTarget, type -> true) == expectedReputation,
                "gossip reputation was not preserved into zombie form");

        VillagerEntityMCA cured = (VillagerEntityMCA) zombie.convertTo(
                EntityType.VILLAGER,
                ConversionParams.single(zombie, false, false),
                converted -> {
                    converted.setVillagerData(zombie.getVillagerData());
                    if (zombie.gossips != null) {
                        converted.setGossips(zombie.gossips.copy());
                    }
                    if (zombie.tradeOffers != null) {
                        converted.setOffers(zombie.tradeOffers.copy());
                    }
                    converted.setVillagerXp(zombie.getVillagerXp());
                    converted.finalizeSpawn(
                            helper.getLevel(),
                            helper.getLevel().getCurrentDifficultyAt(converted.blockPosition()),
                            EntitySpawnReason.CONVERSION,
                            null
                    );
                    converted.refreshBrain(helper.getLevel());
                }
        );

        helper.assertTrue(cured != null, "zombie conversion did not produce an MCA villager");
        helper.assertTrue(zombie.isRemoved(), "source zombie remained after single conversion");
        helper.assertTrue(cured.getUUID().equals(identity), "cure changed MCA identity UUID");
        helper.assertTrue(helper.getLevel().getEntity(identity) == cured,
                "cured villager was not registered under the preserved UUID");
        helper.assertTrue(cured.getVillagerData().equals(expectedData), "villager data was not preserved through round trip");
        helper.assertTrue(cured.getVillagerXp() == 37, "villager XP was not preserved through round trip");
        helper.assertTrue(cured.getOffers().size() == 1 && cured.getOffers().getFirst().getResult().is(Items.BREAD),
                "trade offers were not preserved through round trip");
        helper.assertTrue(cured.getGossips().getReputation(gossipTarget, type -> true) == expectedReputation,
                "gossip reputation was not preserved through round trip");
        helper.assertTrue(cured.getInventory().getItem(0).is(Items.DIAMOND)
                        && cured.getInventory().getItem(0).getCount() == 3,
                "MCA inventory was not preserved through round trip");
        helper.assertTrue(cured.getAgeState() == AgeState.TEEN, "MCA age state was not preserved through round trip");
        helper.assertTrue(!cured.isInfected(), "cured villager remained infected");
        helper.succeed();
    }

    @GameTest(batch = "mca_villager_conversion", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void splitConversionDoesNotDiscardSourceVillager(GameTestHelper helper) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withGender(Gender.MALE)
                .withAge(AgeState.ADULT.toAge())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))))
                .spawn(EntitySpawnReason.COMMAND);

        var converted = villager.convertTo(
                EntityType.WITCH,
                new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, villager.getTeam()),
                mob -> {
                }
        );

        helper.assertTrue(converted != null, "split conversion did not create a target mob");
        helper.assertTrue(!villager.isRemoved(), "MCA discarded the source despite SPLIT_ON_DEATH conversion semantics");
        helper.assertTrue(!converted.getUUID().equals(villager.getUUID()),
                "split conversion reused the live source UUID");
        converted.discard();
        villager.discard();
        helper.succeed();
    }
}
