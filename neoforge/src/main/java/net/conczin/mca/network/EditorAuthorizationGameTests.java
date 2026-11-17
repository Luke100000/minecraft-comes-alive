package net.conczin.mca.network;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.Objects;

@PrefixGameTestTemplate(false)
public final class EditorAuthorizationGameTests {
    private EditorAuthorizationGameTests() {
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void senderCannotEditAnotherPlayer(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-sender", new BlockPos(2, 2, 2));
        ServerPlayer target = createPlayer(helper, "editor-target", new BlockPos(4, 2, 2));
        helper.getLevel().addNewPlayer(sender);
        helper.getLevel().addNewPlayer(target);
        try {
            helper.assertTrue(helper.getLevel().getEntity(target.getUUID()) == target,
                    "player target was not registered in the test level");
            CompoundTag original = PlayerSaveData.get(target).getEntityData();
            FamilyTreeNode targetFamily = FamilyTree.get(helper.getLevel()).getOrCreate(target);
            UUID originalPartner = targetFamily.partner();

            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "\"Forged name\"");
            patch.putString("FamilyTreeNewSpouseName", "Forged spouse");
            new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

            helper.assertTrue(PlayerSaveData.get(target).getEntityData().equals(original),
                    "unauthorized sender changed another player's saved editor data");
            helper.assertTrue(Objects.equals(targetFamily.partner(), originalPartner),
                    "unauthorized sender changed another player's partner");
        } finally {
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
            helper.getLevel().removePlayerImmediately(target, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void itemlessSenderCannotEditNearbyVillager(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-itemless", new BlockPos(2, 2, 2));
        VillagerEntityMCA target = spawnVillager(helper, new BlockPos(4, 2, 2));
        target.setCustomName(Component.literal("Original name"));
        CompoundTag patch = new CompoundTag();
        patch.putString("CustomName", "\"Forged name\"");

        new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

        helper.assertTrue("Original name".equals(target.getCustomName().getString()),
                "sender changed a villager without holding the editor item");
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void mixedLimitedPatchDoesNotPartiallyApply(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-limited", new BlockPos(2, 2, 2));
        helper.getLevel().addNewPlayer(sender);
        boolean originalLimited = Config.getInstance().allowLimitedPlayerEditor;
        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        try {
            Config.getInstance().allowLimitedPlayerEditor = true;
            Config.getInstance().allowFullPlayerEditor = false;
            helper.assertTrue(!sender.createCommandSourceStack().hasPermission(2),
                    "limited-editor fixture must be a non-operator");
            CompoundTag original = PlayerSaveData.get(sender).getEntityData();

            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "\"Legal field\"");
            patch.putInt("Gender", original.getInt("Gender") == 1 ? 0 : 1);
            new VillagerEditorSyncRequest("sync", sender.getUUID(), patch).handleServer(sender);

            helper.assertTrue(PlayerSaveData.get(sender).getEntityData().equals(original),
                    "limited editor partially accepted a mixed allowed/forbidden patch");
        } finally {
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            Config.getInstance().allowFullPlayerEditor = originalFull;
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nearbyEditorItemRetainsFamilyEdit(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-held", new BlockPos(2, 2, 2));
        sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.VILLAGER_EDITOR));
        VillagerEntityMCA target = spawnVillager(helper, new BlockPos(4, 2, 2));
        FamilyTreeNode family = target.getRelationships().getFamilyEntry();
        UUID originalPartner = family.partner();

        CompoundTag patch = new CompoundTag();
        patch.putString("FamilyTreeNewSpouseName", "Editor Partner");
        patch.putString("CustomName", "\"Legal edit\"");
        new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

        helper.assertTrue(!Objects.equals(originalPartner, family.partner()),
                "nearby editor-item user could not change villager family relationships");
        helper.assertTrue("Legal edit".equals(target.getCustomName().getString()),
                "nearby editor-item user could not apply another legal full-editor field");
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void limitedSelfEditorRetainsNameEdit(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-self", new BlockPos(2, 2, 2));
        helper.getLevel().addNewPlayer(sender);
        boolean originalLimited = Config.getInstance().allowLimitedPlayerEditor;
        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        try {
            Config.getInstance().allowLimitedPlayerEditor = true;
            Config.getInstance().allowFullPlayerEditor = false;
            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "\"Legal own name\"");
            new VillagerEditorSyncRequest("sync", sender.getUUID(), patch).handleServer(sender);

            helper.assertTrue("\"Legal own name\"".equals(PlayerSaveData.get(sender).getEntityData().getString("CustomName")),
                    "limited self editor was denied legal name field");
        } finally {
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            Config.getInstance().allowFullPlayerEditor = originalFull;
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, String name, BlockPos relativePos) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override
            protected int getPermissionLevel() {
                return 0;
            }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relativePos)));
        return player;
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        return VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(relativePos)))
                .spawn(MobSpawnType.STRUCTURE);
    }
}
