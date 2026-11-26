package net.conczin.mca.network;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.c2s.CallToPlayerMessage;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class WhistleAuthorizationGameTests {
    private WhistleAuthorizationGameTests() {
    }

    @GameTest(batch = "mca_whistle_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unrelatedVillagerCannotBeCalledWithWhistle(GameTestHelper helper) {
        ServerPlayer sender = player(helper);
        sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.WHISTLE));
        VillagerEntityMCA unrelated = villager(helper);
        Vec3 previous = unrelated.position();

        new CallToPlayerMessage(unrelated.getUUID()).handleServer(sender);

        helper.assertTrue(unrelated.position().equals(previous),
                "whistle packet teleported an unrelated villager");
        helper.succeed();
    }

    @GameTest(batch = "mca_whistle_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void eligibleRelativeCannotBeCalledAfterWhistleUnequipped(GameTestHelper helper) {
        ServerPlayer sender = player(helper);
        VillagerEntityMCA relative = villager(helper);
        makeChildOf(relative, sender);
        sender.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        sender.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        Vec3 previous = relative.position();

        new CallToPlayerMessage(relative.getUUID()).handleServer(sender);

        helper.assertTrue(relative.position().equals(previous),
                "family member teleported after the sender unequipped the whistle");
        helper.succeed();
    }

    @GameTest(batch = "mca_whistle_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void formerPartnerCannotBeCalled(GameTestHelper helper) {
        ServerPlayer sender = player(helper);
        sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.WHISTLE));
        VillagerEntityMCA formerPartner = villager(helper);
        FamilyTreeNode senderFamily = PlayerSaveData.get(sender).getFamilyEntry();
        senderFamily.updatePartner(FamilyTree.get(helper.getLevel()).getOrCreate(formerPartner));
        senderFamily.updatePartner(null, RelationshipState.SINGLE);
        Vec3 previous = formerPartner.position();

        new CallToPlayerMessage(formerPartner.getUUID()).handleServer(sender);

        helper.assertTrue(formerPartner.position().equals(previous),
                "former partner teleported despite revoked eligibility");
        helper.succeed();
    }

    @GameTest(batch = "mca_whistle_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void relativeCanBeCalledWithOffhandWhistle(GameTestHelper helper) {
        ServerPlayer sender = player(helper);
        VillagerEntityMCA relative = villager(helper);
        makeChildOf(relative, sender);
        sender.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemsMCA.WHISTLE));

        new CallToPlayerMessage(relative.getUUID()).handleServer(sender);

        helper.assertTrue(relative.position().distanceToSqr(sender.position()) < 0.01,
                "eligible family member could not be summoned using an offhand whistle");
        helper.succeed();
    }

    @GameTest(batch = "mca_whistle_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void currentPartnerCanBeCalledWithMainHandWhistle(GameTestHelper helper) {
        ServerPlayer sender = player(helper);
        VillagerEntityMCA partner = villager(helper);
        sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.WHISTLE));
        PlayerSaveData.get(sender).getFamilyEntry().updatePartner(partner, RelationshipState.MARRIED_TO_VILLAGER);

        new CallToPlayerMessage(partner.getUUID()).handleServer(sender);

        helper.assertTrue(partner.position().distanceToSqr(sender.position()) < 0.01,
                "current partner could not be summoned with a main-hand whistle");
        helper.succeed();
    }

    @GameTest(batch = "mca_whistle_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void followSettingControlsAuthorizedCalls(GameTestHelper helper) {
        ServerPlayer sender = player(helper);
        VillagerEntityMCA relative = villager(helper);
        makeChildOf(relative, sender);
        sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.WHISTLE));
        boolean original = Config.getServerConfig().whistleCallMakesVillagersFollow;
        try {
            relative.getVillagerBrain().setMoveState(MoveState.STAY, sender);
            Config.getServerConfig().whistleCallMakesVillagersFollow = false;
            new CallToPlayerMessage(relative.getUUID()).handleServer(sender);
            helper.assertTrue(relative.getVillagerBrain().getMoveState() == MoveState.STAY,
                    "whistle call ignored disabled follow setting");

            Config.getServerConfig().whistleCallMakesVillagersFollow = true;
            new CallToPlayerMessage(relative.getUUID()).handleServer(sender);
            helper.assertTrue(relative.getVillagerBrain().getMoveState() == MoveState.FOLLOW,
                    "whistle call did not follow when enabled");
        } finally {
            Config.getServerConfig().whistleCallMakesVillagersFollow = original;
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "whistle-test"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        return player;
    }

    private static VillagerEntityMCA villager(GameTestHelper helper) {
        return VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 2))))
                .spawn(EntitySpawnReason.STRUCTURE);
    }

    private static void makeChildOf(VillagerEntityMCA child, ServerPlayer parent) {
        child.getRelationships().getFamilyEntry().setFather(PlayerSaveData.get(parent).getFamilyEntry());
    }
}
