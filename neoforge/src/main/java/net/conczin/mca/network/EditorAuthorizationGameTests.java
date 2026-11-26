package net.conczin.mca.network;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.network.c2s.CallToPlayerMessage;
import net.conczin.mca.network.c2s.GetVillagerRequest;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.resources.SkinVisualIds;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.UUID;

@GameTestHolder("minecraft")
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
            patch.putString("CustomName", "Forged name");
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
    public static void operatorCanEditAnotherPlayer(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-operator", new BlockPos(2, 2, 2), 2);
        ServerPlayer target = createPlayer(helper, "editor-op-target", new BlockPos(4, 2, 2));
        helper.getLevel().addNewPlayer(sender);
        helper.getLevel().addNewPlayer(target);
        try {
            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "Edited by operator");
            new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

            helper.assertTrue("Edited by operator".equals(PlayerSaveData.get(target).getEntityData().getString("CustomName").orElse("")),
                    "an operator should be able to edit another player's name without an editor item");
        } finally {
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
            helper.getLevel().removePlayerImmediately(target, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerMenuActionsStopBeyondTwentyFiveBlocks(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "menu-range", new BlockPos(2, 2, 2));
        VillagerEntityMCA villager = spawnVillager(helper, new BlockPos(5, 2, 2));
        helper.getLevel().addNewPlayer(sender);
        try {
            villager.setPos(sender.position().add(25, 0, 0));
            new InteractionVillagerMessage("stay", villager.getUUID()).handleServer(sender);
            helper.assertTrue(villager.getVillagerBrain().getMoveState() == MoveState.STAY,
                    "a villager menu action should work exactly 25 blocks away");

            villager.setPos(sender.position().add(26, 0, 0));
            new InteractionVillagerMessage("move", villager.getUUID()).handleServer(sender);
            helper.assertTrue(villager.getVillagerBrain().getMoveState() == MoveState.STAY,
                    "a villager menu action should be rejected beyond 25 blocks");
        } finally {
            villager.discard();
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void itemlessSenderCannotEditNearbyVillager(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-itemless", new BlockPos(2, 2, 2));
        VillagerEntityMCA target = spawnVillager(helper, new BlockPos(4, 2, 2));
        try {
            target.setCustomName(Component.literal("Original name"));
            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "Forged name");

            new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

            helper.assertTrue("Original name".equals(target.getCustomName().getString()),
                    "sender changed a villager without holding the editor item");
        } finally {
            target.discard();
        }
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
            helper.assertTrue(!sender.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
                    "limited-editor fixture must be a non-operator");
            CompoundTag original = PlayerSaveData.get(sender).getEntityData();

            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "Legal field");
            mca(patch).putInt("Gender", mca(original).getInt("Gender").orElse(0) == 1 ? 0 : 1);
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
        helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(22, 2, 2)));
        VillagerEntityMCA distantTarget = spawnVillager(helper, new BlockPos(22, 2, 2));
        try {
            FamilyTreeNode family = target.getRelationships().getFamilyEntry();
            UUID originalPartner = family.partner();

            CompoundTag patch = new CompoundTag();
            patch.putString("FamilyTreeNewSpouseName", "Editor Partner");
            patch.putString("CustomName", "Legal edit");
            new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

            helper.assertTrue(!Objects.equals(originalPartner, family.partner()),
                    "nearby editor-item user could not change villager family relationships");
            helper.assertTrue("Legal edit".equals(target.getCustomName().getString()),
                    "nearby editor-item user could not apply another legal full-editor field");

            helper.assertTrue(sender.distanceToSqr(distantTarget) > 64.0D,
                    "distant villager fixture must be > 8 blocks away");
            CompoundTag distantPatch = new CompoundTag();
            distantPatch.putString("CustomName", "Distant editor edit");
            new VillagerEditorSyncRequest("sync", distantTarget.getUUID(), distantPatch).handleServer(sender);
            helper.assertTrue("Distant editor edit".equals(distantTarget.getCustomName().getString()),
                    "holding ItemsMCA.VILLAGER_EDITOR must authorize editing villagers > 8 blocks away");
        } finally {
            target.discard();
            distantTarget.discard();
        }
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
            patch.putString("CustomName", "Legal own name");
            new VillagerEditorSyncRequest("sync", sender.getUUID(), patch).handleServer(sender);

            helper.assertTrue("Legal own name".equals(PlayerSaveData.get(sender).getEntityData().getString("CustomName").orElse("")),
                    "limited self editor was denied legal name field");
        } finally {
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            Config.getInstance().allowFullPlayerEditor = originalFull;
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nonOpDestinySessionCanCustomizeAndMarkEntityDataSet(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "destiny-self", new BlockPos(2, 2, 2));
        VillagerEntityMCA bystander = spawnVillager(helper, new BlockPos(5, 2, 2));
        String bystanderName = "DestinyBystander-" + UUID.randomUUID();
        bystander.setCustomName(Component.literal(bystanderName));
        bystander.getRelationships().getFamilyEntry().setName(bystanderName);
        helper.getLevel().addNewPlayer(sender);

        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        boolean originalLimited = Config.getInstance().allowLimitedPlayerEditor;
        boolean originalBody = Config.getInstance().allowBodyCustomizationInDestiny;
        boolean originalTraits = Config.getInstance().allowTraitCustomizationInDestiny;
        try {
            Config.getInstance().allowFullPlayerEditor = false;
            Config.getInstance().allowLimitedPlayerEditor = true;
            Config.getInstance().allowBodyCustomizationInDestiny = true;
            Config.getInstance().allowTraitCustomizationInDestiny = true;

            PlayerSaveData saveData = PlayerSaveData.get(sender);
            CompoundTag seeded = saveData.getEntityData();
            seeded.putBoolean("VillagerDataFinalized", true);
            saveData.setEntityData(seeded);
            saveData.setEntityDataSet(false);

            ServerInteractionManager.launchDestiny(sender);
            helper.assertTrue(ServerInteractionManager.getInstance().hasActiveDestinySession(sender),
                    "launchDestiny must register an active Destiny session");

            Gender nextGender = Gender.byId(mca(seeded).getInt("Gender").orElse(0)) == Gender.FEMALE ? Gender.MALE : Gender.FEMALE;
            String nextHairBase = pickDifferentHairBase(nextGender, mca(seeded).getString("HairBase").orElse(""));
            String nextClothes = pickDifferentClothes(nextGender, mca(seeded).getString("Clothes").orElse(""));
            String nextSkin = pickBodySkin(nextGender, mca(seeded).getString("Skin").orElse(""));

            CompoundTag genderPatch = VillagerEditorSyncRequest.createEditorPatch(saveData.getEntityData());
            mca(genderPatch).putInt("Gender", nextGender.getId());
            if (!nextSkin.isEmpty()) {
                mca(genderPatch).putString("Skin", nextSkin);
            }
            mca(genderPatch).putString("Clothes", nextClothes);
            genderPatch.putString("FamilyTreeNewSpouseName", bystanderName);
            new VillagerEditorSyncRequest("gender", sender.getUUID(), genderPatch).handleServer(sender);

            CompoundTag syncPatch = VillagerEditorSyncRequest.createEditorPatch(saveData.getEntityData());
            mca(syncPatch).putString("HairBase", nextHairBase);
            mca(syncPatch).putString("Clothes", nextClothes);
            syncPatch.putString("FamilyTreeNewSpouseName", bystanderName);
            new VillagerEditorSyncRequest("sync", sender.getUUID(), syncPatch).handleServer(sender);

            CompoundTag updated = saveData.getEntityData();
            helper.assertTrue(saveData.isEntityDataSet(),
                    "active Destiny session sync must mark entityDataSet=true");
            helper.assertTrue(mca(updated).getInt("Gender").orElse(0) == nextGender.getId(),
                    "Destiny session should update Gender");
            helper.assertTrue(nextHairBase.equals(mca(updated).getString("HairBase").orElse("")),
                    "Destiny session should update HairBase");
            helper.assertTrue(nextClothes.equals(mca(updated).getString("Clothes").orElse("")),
                    "Destiny session should update Clothes");
            helper.assertTrue(!FamilyTreeNode.isValid(FamilyTree.get(helper.getLevel()).getOrCreate(sender).partner()),
                    "Destiny session must ignore injected FamilyTreeNewSpouseName");
            helper.assertTrue(!FamilyTreeNode.isValid(bystander.getRelationships().getFamilyEntry().partner()),
                    "Destiny session must not marry bystander villager");
        } finally {
            ServerInteractionManager.getInstance().clearDestinySession(sender.getUUID());
            Config.getInstance().allowFullPlayerEditor = originalFull;
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            Config.getInstance().allowBodyCustomizationInDestiny = originalBody;
            Config.getInstance().allowTraitCustomizationInDestiny = originalTraits;
            bystander.discard();
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nonOpCombAndNeedleWorkOnSelfAndNearbyVillagerOnlyForOwnedKeys(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "comb-needle-user", new BlockPos(2, 2, 2));
        VillagerEntityMCA nearbyVillager = spawnVillager(helper, new BlockPos(5, 2, 2));
        VillagerEntityMCA farVillager = spawnVillager(helper, new BlockPos(14, 2, 2));
        helper.getLevel().addNewPlayer(sender);

        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        boolean originalLimited = Config.getInstance().allowLimitedPlayerEditor;
        try {
            Config.getInstance().allowFullPlayerEditor = false;
            Config.getInstance().allowLimitedPlayerEditor = true;

            PlayerSaveData selfSave = PlayerSaveData.get(sender);
            CompoundTag selfSeeded = selfSave.getEntityData();
            selfSeeded.putBoolean("VillagerDataFinalized", true);
            selfSave.setEntityData(selfSeeded);

            // A comb can change hair on self and on villagers within range.
            sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.COMB));

            Gender selfGender = Gender.byId(mca(selfSave.getEntityData()).getInt("Gender").orElse(0));
            String selfNewHair = pickDifferentHairBase(selfGender, mca(selfSave.getEntityData()).getString("HairBase").orElse(""));
            CompoundTag selfCombPatch = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            mca(selfCombPatch).putString("HairBase", selfNewHair);
            new VillagerEditorSyncRequest("sync", sender.getUUID(), selfCombPatch).handleServer(sender);
            helper.assertTrue(selfNewHair.equals(mca(selfSave.getEntityData()).getString("HairBase").orElse("")),
                    "Comb should allow changing HairBase on self");

            String selfOriginalClothes = mca(selfSave.getEntityData()).getString("Clothes").orElse("");
            CompoundTag selfForbiddenClothesPatch = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            mca(selfForbiddenClothesPatch).putString("Clothes", pickDifferentClothes(selfGender, selfOriginalClothes));
            new VillagerEditorSyncRequest("sync", sender.getUUID(), selfForbiddenClothesPatch).handleServer(sender);
            helper.assertTrue(selfOriginalClothes.equals(mca(selfSave.getEntityData()).getString("Clothes").orElse("")),
                    "Comb must reject Clothes changes");

            Gender nearbyGender = nearbyVillager.getGenetics().getGender();
            String nearbyNewHair = pickDifferentHairBase(nearbyGender, nearbyVillager.getLayeredHair(LayeredHair.Category.BASE));
            CompoundTag nearbyCombPatch = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(nearbyVillager)));
            mca(nearbyCombPatch).putString("HairBase", nearbyNewHair);
            new VillagerEditorSyncRequest("sync", nearbyVillager.getUUID(), nearbyCombPatch).handleServer(sender);
            helper.assertTrue(nearbyNewHair.equals(nearbyVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb should allow changing HairBase on villager 3 blocks away");

            farVillager.setPos(sender.position().add(25, 0, 0));
            String farOriginalHair = farVillager.getLayeredHair(LayeredHair.Category.BASE);
            String farNewHair = pickDifferentHairBase(farVillager.getGenetics().getGender(), farOriginalHair);
            CompoundTag farCombPatch = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(farVillager)));
            mca(farCombPatch).putString("HairBase", farNewHair);
            new VillagerEditorSyncRequest("sync", farVillager.getUUID(), farCombPatch).handleServer(sender);
            helper.assertTrue(farNewHair.equals(farVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb should allow changing hair exactly 25 blocks away");

            farVillager.setPos(sender.position().add(26, 0, 0));
            mca(farCombPatch).putString("HairBase", pickDifferentHairBase(farVillager.getGenetics().getGender(), farNewHair));
            new VillagerEditorSyncRequest("sync", farVillager.getUUID(), farCombPatch).handleServer(sender);
            helper.assertTrue(farNewHair.equals(farVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb must reject a villager beyond 25 blocks");

            // 2. Holding NEEDLE_AND_THREAD: Clothes allowed; HairBase & Gender rejected.
            sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.NEEDLE_AND_THREAD));

            String nearbyNewClothes = pickDifferentClothes(nearbyGender, nearbyVillager.getClothes());
            CompoundTag nearbyNeedlePatch = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(nearbyVillager)));
            mca(nearbyNeedlePatch).putString("Clothes", nearbyNewClothes);
            new VillagerEditorSyncRequest("sync", nearbyVillager.getUUID(), nearbyNeedlePatch).handleServer(sender);
            helper.assertTrue(nearbyNewClothes.equals(nearbyVillager.getClothes()),
                    "Needle and Thread should allow changing Clothes on nearby villager");

            CompoundTag needleHairReject = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            mca(needleHairReject).putString("HairBase", pickDifferentHairBase(selfGender, mca(selfSave.getEntityData()).getString("HairBase").orElse("")));
            new VillagerEditorSyncRequest("sync", sender.getUUID(), needleHairReject).handleServer(sender);
            helper.assertTrue(selfNewHair.equals(mca(selfSave.getEntityData()).getString("HairBase").orElse("")),
                    "Needle and Thread must reject HairBase changes");

            int originalGenderId = mca(selfSave.getEntityData()).getInt("Gender").orElse(0);
            CompoundTag needleGenderReject = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            mca(needleGenderReject).putInt("Gender", selfGender == Gender.FEMALE ? Gender.MALE.getId() : Gender.FEMALE.getId());
            new VillagerEditorSyncRequest("sync", sender.getUUID(), needleGenderReject).handleServer(sender);
            helper.assertTrue(mca(selfSave.getEntityData()).getInt("Gender").orElse(0) == originalGenderId,
                    "Needle and Thread must reject Gender changes");
        } finally {
            Config.getInstance().allowFullPlayerEditor = originalFull;
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            nearbyVillager.discard();
            farVillager.discard();
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void limitedSelfEditorAllowsEchoedVillagerDataFinalizedAndBlocksFamilyHijack(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "limited-echo-self", new BlockPos(2, 2, 2));
        VillagerEntityMCA targetVillager = spawnVillager(helper, new BlockPos(12, 2, 2));
        String villagerName = "UnrelatedVillager-" + UUID.randomUUID();
        targetVillager.setCustomName(Component.literal(villagerName));
        targetVillager.getRelationships().getFamilyEntry().setName(villagerName);
        Vec3 villagerInitialPos = targetVillager.position();
        helper.getLevel().addNewPlayer(sender);

        boolean originalLimited = Config.getInstance().allowLimitedPlayerEditor;
        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        try {
            PlayerSaveData saveData = PlayerSaveData.get(sender);
            CompoundTag seeded = saveData.getEntityData();
            seeded.putBoolean("VillagerDataFinalized", true);
            saveData.setEntityData(seeded);

            // 1. Limited self-editor allows echoed VillagerDataFinalized: true with CustomName edit.
            Config.getInstance().allowLimitedPlayerEditor = true;
            Config.getInstance().allowFullPlayerEditor = false;

            CompoundTag limitedPatch = VillagerEditorSyncRequest.createEditorPatch(saveData.getEntityData().copy());
            limitedPatch.putBoolean("VillagerDataFinalized", true);
            limitedPatch.putString("CustomName", "Echoed Finalized Name");
            new VillagerEditorSyncRequest("sync", sender.getUUID(), limitedPatch).handleServer(sender);

            helper.assertTrue("Echoed Finalized Name".equals(saveData.getEntityData().getString("CustomName").orElse("")),
                    "limited self editor should accept echoed VillagerDataFinalized when CustomName changes");

            // 2. Full player self-editor cannot link spouse via FamilyTreeNewSpouseName or authorize CallToPlayerMessage.
            Config.getInstance().allowFullPlayerEditor = true;
            CompoundTag hijackPatch = VillagerEditorSyncRequest.createEditorPatch(saveData.getEntityData().copy());
            hijackPatch.putString("FamilyTreeNewSpouseName", villagerName);
            new VillagerEditorSyncRequest("sync", sender.getUUID(), hijackPatch).handleServer(sender);

            helper.assertTrue(!FamilyTreeNode.isValid(FamilyTree.get(helper.getLevel()).getOrCreate(sender).partner()),
                    "allowFullPlayerEditor self-edit must not link villager via FamilyTreeNewSpouseName");
            helper.assertTrue(!saveData.getCallableFamilyIds().contains(targetVillager.getUUID()),
                    "hijacked villager must not appear in callable family IDs");

            sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.WHISTLE));
            new CallToPlayerMessage(targetVillager.getUUID()).handleServer(sender);
            helper.assertTrue(targetVillager.position().equals(villagerInitialPos),
                    "unlinked villager must not be teleported by CallToPlayerMessage");
        } finally {
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            Config.getInstance().allowFullPlayerEditor = originalFull;
            targetVillager.discard();
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void combSucceedsAfterVillagerAgeTicksWithoutRollingBackAgeAndRejectsNonHairKeys(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "comb-drift-user", new BlockPos(2, 2, 2));
        VillagerEntityMCA childVillager = spawnVillager(helper, new BlockPos(5, 2, 2));
        helper.getLevel().addNewPlayer(sender);

        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        try {
            Config.getInstance().allowFullPlayerEditor = false;
            sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.COMB));

            // Capture t=0 client snapshot while villager is a growing child.
            childVillager.setAge(-12000);
            childVillager.getVillagerBrain().modifyMoodValue(2);
            CompoundTag clientSnapshot = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(childVillager))
            );

            // Simulate server ticks advancing Age and Mood while CombScreen is open on the client.
            childVillager.setAge(-11920);
            childVillager.getVillagerBrain().modifyMoodValue(5);
            int tickedMood = childVillager.getVillagerBrain().getMoodValue();

            Gender gender = childVillager.getGenetics().getGender();
            String nextHair = pickDifferentHairBase(gender, childVillager.getLayeredHair(LayeredHair.Category.BASE));
            CompoundTag combPatch = clientSnapshot.copy();
            mca(combPatch).putString("HairBase", nextHair);
            new VillagerEditorSyncRequest("sync", childVillager.getUUID(), combPatch).handleServer(sender);

            helper.assertTrue(nextHair.equals(childVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb edit must succeed even when villager Age/Mood ticked after client snapshot; expected="
                            + nextHair + " actual=" + childVillager.getLayeredHair(LayeredHair.Category.BASE));
            helper.assertTrue(childVillager.getAge() == -11920,
                    "Comb edit must not roll back live server Age to stale client snapshot, got " + childVillager.getAge());
            helper.assertTrue(childVillager.getVillagerBrain().getMoodValue() == tickedMood,
                    "Comb edit must not roll back live server Mood to stale client snapshot");

            // Comb packet attempting to mutate Clothes or Traits must still be rejected.
            String originalClothes = childVillager.getClothes();
            CompoundTag clothesTamper = clientSnapshot.copy();
            mca(clothesTamper).putString("HairBase", pickDifferentHairBase(gender, nextHair));
            mca(clothesTamper).putString("Clothes", pickDifferentClothes(gender, originalClothes));
            new VillagerEditorSyncRequest("sync", childVillager.getUUID(), clothesTamper).handleServer(sender);
            helper.assertTrue(originalClothes.equals(childVillager.getClothes()) && nextHair.equals(childVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb packet tampering with Clothes must be rejected completely");

            boolean hadRainbow = childVillager.getTraits().hasTrait(Traits.RAINBOW);
            CompoundTag traitsTamper = clientSnapshot.copy();
            CompoundTag forgedTraits = mca(traitsTamper).getCompound("Traits").orElseGet(CompoundTag::new).copy();
            if (hadRainbow) {
                forgedTraits.remove(Traits.RAINBOW.getId().toString());
            } else {
                forgedTraits.putBoolean(Traits.RAINBOW.getId().toString(), true);
            }
            mca(traitsTamper).put("Traits", forgedTraits);
            new VillagerEditorSyncRequest("sync", childVillager.getUUID(), traitsTamper).handleServer(sender);
            helper.assertTrue(childVillager.getTraits().hasTrait(Traits.RAINBOW) == hadRainbow,
                    "Comb packet tampering with Traits must be rejected");
        } finally {
            Config.getInstance().allowFullPlayerEditor = originalFull;
            childVillager.discard();
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_editor_authorization", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void destinySessionAcceptsPresetWithPersonalityAndAgeWithoutMutatingAdminKeys(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "destiny-preset-self", new BlockPos(2, 2, 2));
        helper.getLevel().addNewPlayer(sender);

        boolean originalFull = Config.getInstance().allowFullPlayerEditor;
        boolean originalBody = Config.getInstance().allowBodyCustomizationInDestiny;
        boolean originalTraits = Config.getInstance().allowTraitCustomizationInDestiny;
        try {
            Config.getInstance().allowFullPlayerEditor = false;
            Config.getInstance().allowBodyCustomizationInDestiny = true;
            Config.getInstance().allowTraitCustomizationInDestiny = true;

            PlayerSaveData saveData = PlayerSaveData.get(sender);
            saveData.setEntityDataSet(false);
            CompoundTag initial = saveData.getEntityData();
            int initialAge = initial.getInt("Age").orElse(0);
            int initialMood = mca(initial).getInt("Mood").orElse(0);
            float initialInfection = mca(initial).getFloat("InfectionProgress").orElse(0.0F);
            String currentPersonality = mca(initial).getString("Personality").orElse("");
            String nextPersonality = Personality.PLAYFUL.getId().toString().equals(currentPersonality)
                    ? Personality.FRIENDLY.getId().toString()
                    : Personality.PLAYFUL.getId().toString();
            Gender gender = Gender.byId(mca(initial).getInt("Gender").orElse(0));
            String nextHair = pickDifferentHairBase(gender, mca(initial).getString("HairBase").orElse(""));

            ServerInteractionManager.launchDestiny(sender);

            CompoundTag presetPatch = VillagerEditorSyncRequest.createEditorPatch(initial.copy());
            mca(presetPatch).putString("Personality", nextPersonality);
            presetPatch.putInt("Age", -24000);
            mca(presetPatch).putString("HairBase", nextHair);
            mca(presetPatch).putInt("Mood", initialMood + 25);
            mca(presetPatch).putFloat("InfectionProgress", 0.75f);
            presetPatch.putString("FamilyTreeNewSpouseName", "PresetSpouse");

            new VillagerEditorSyncRequest("sync", sender.getUUID(), presetPatch).handleServer(sender);

            CompoundTag updated = saveData.getEntityData();
            helper.assertTrue(saveData.isEntityDataSet(),
                    "Destiny preset sync should mark entityDataSet=true");
            helper.assertTrue(nextPersonality.equals(mca(updated).getString("Personality").orElse("")),
                    "Destiny preset sync should apply Personality");
            helper.assertTrue(nextHair.equals(mca(updated).getString("HairBase").orElse("")),
                    "Destiny preset sync should apply HairBase");
            helper.assertTrue(updated.getInt("Age").orElse(0) == initialAge,
                    "Destiny preset sync must strip Age on ServerPlayer");
            helper.assertTrue(mca(updated).getInt("Mood").orElse(0) == initialMood,
                    "Destiny preset sync must not mutate Mood");
            helper.assertTrue(Float.compare(mca(updated).getFloat("InfectionProgress").orElse(0.0F), initialInfection) == 0,
                    "Destiny preset sync must not mutate InfectionProgress");
            helper.assertTrue(!FamilyTreeNode.isValid(FamilyTree.get(helper.getLevel()).getOrCreate(sender).partner()),
                    "Destiny preset sync must ignore FamilyTreeNewSpouseName");
        } finally {
            ServerInteractionManager.getInstance().clearDestinySession(sender.getUUID());
            Config.getInstance().allowFullPlayerEditor = originalFull;
            Config.getInstance().allowBodyCustomizationInDestiny = originalBody;
            Config.getInstance().allowTraitCustomizationInDestiny = originalTraits;
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    private static CompoundTag mca(CompoundTag data) {
        return NbtHelper.getOrCreateCompound(data, VillagerEntityMCA.MCA_DATA_KEY);
    }

    private static String pickDifferentHairBase(Gender gender, String current) {
        LayeredHairList list = LayeredHairList.getInstance();
        if (list != null) {
            for (LayeredHair entry : list.hair.values()) {
                if (entry.getCategory() == LayeredHair.Category.BASE
                        && SkinVisualIds.isHairLayer(entry.getIdentifier(), LayeredHair.Category.BASE, gender)
                        && !entry.getIdentifier().equals(current)) {
                    return entry.getIdentifier();
                }
            }
        }
        return "immersive_library:test_hair_base";
    }

    private static String pickDifferentClothes(Gender gender, String current) {
        ClothingList list = ClothingList.getInstance();
        if (list != null) {
            for (Clothing entry : list.clothing.values()) {
                if (SkinVisualIds.isClothing(entry.getIdentifier(), gender)
                        && !entry.getIdentifier().equals(current)) {
                    return entry.getIdentifier();
                }
            }
        }
        return "immersive_library:test_clothes";
    }

    private static String pickBodySkin(Gender gender, String current) {
        BodySkinList list = BodySkinList.getInstance();
        if (list != null) {
            for (BodySkin entry : list.skins.values()) {
                if (SkinVisualIds.isBodySkin(entry.getIdentifier(), gender)
                        && !entry.getIdentifier().equals(current)) {
                    return entry.getIdentifier();
                }
            }
        }
        return "";
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, String name, BlockPos relativePos) {
        return createPlayer(helper, name, relativePos, 0);
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, String name, BlockPos relativePos, int permissionLevel) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public PermissionSet permissions() {
                return permissionLevel >= 2 ? PermissionSet.ALL_PERMISSIONS : PermissionSet.NO_PERMISSIONS;
            }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relativePos)));
        return player;
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        return VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(relativePos)))
                .spawn(EntitySpawnReason.STRUCTURE);
    }
}
