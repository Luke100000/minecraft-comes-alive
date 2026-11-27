package net.conczin.mca.network;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.gametest.GameTestTerrain;
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
import net.minecraft.world.level.ChunkPos;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    public static void operatorCanEditAnotherPlayer(GameTestHelper helper) {
        ServerPlayer sender = createPlayer(helper, "editor-operator", new BlockPos(2, 2, 2), 2);
        ServerPlayer target = createPlayer(helper, "editor-op-target", new BlockPos(4, 2, 2));
        helper.getLevel().addNewPlayer(sender);
        helper.getLevel().addNewPlayer(target);
        try {
            CompoundTag patch = new CompoundTag();
            patch.putString("CustomName", "\"Edited by operator\"");
            new VillagerEditorSyncRequest("sync", target.getUUID(), patch).handleServer(sender);

            helper.assertTrue("\"Edited by operator\"".equals(PlayerSaveData.get(target).getEntityData().getString("CustomName")),
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
            patch.putString("CustomName", "\"Forged name\"");

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
        List<ChunkPos> ownedChunks = new ArrayList<>();
        ChunkPos distantChunk = new ChunkPos(helper.absolutePos(new BlockPos(22, 2, 2)));
        GameTestTerrain.ensureEntityTickingChunks(helper, distantChunk, distantChunk, ownedChunks);
        VillagerEntityMCA distantTarget = spawnVillager(helper, new BlockPos(22, 2, 2));
        try {
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

            helper.assertTrue(sender.distanceToSqr(distantTarget) > 64.0D,
                    "distant villager fixture must be > 8 blocks away");
            helper.assertTrue(helper.getLevel().getEntity(distantTarget.getUUID()) == distantTarget,
                    "distant editor target must be registered in the level's entity lookup");
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(distantTarget.blockPosition()),
                    "distant editor target must be inside an entity-ticking chunk");
            CompoundTag distantPatch = new CompoundTag();
            distantPatch.putString("CustomName", "\"Distant editor edit\"");
            new VillagerEditorSyncRequest("sync", distantTarget.getUUID(), distantPatch).handleServer(sender);
            helper.assertTrue("Distant editor edit".equals(distantTarget.getCustomName().getString()),
                    "holding ItemsMCA.VILLAGER_EDITOR must authorize editing villagers > 8 blocks away");
        } finally {
            target.discard();
            distantTarget.discard();
            ownedChunks.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
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

            Gender nextGender = Gender.byId(seeded.getInt("Gender")) == Gender.FEMALE ? Gender.MALE : Gender.FEMALE;
            String nextHairBase = pickDifferentHairBase(nextGender, seeded.getString("HairBase"));
            String nextClothes = pickDifferentClothes(nextGender, seeded.getString("Clothes"));
            String nextSkin = pickBodySkin(nextGender, seeded.getString("Skin"));

            CompoundTag genderPatch = VillagerEditorSyncRequest.createEditorPatch(saveData.getEntityData());
            genderPatch.putInt("Gender", nextGender.getId());
            if (!nextSkin.isEmpty()) {
                genderPatch.putString("Skin", nextSkin);
            }
            genderPatch.putString("Clothes", nextClothes);
            genderPatch.putString("FamilyTreeNewSpouseName", bystanderName);
            new VillagerEditorSyncRequest("gender", sender.getUUID(), genderPatch).handleServer(sender);

            CompoundTag syncPatch = VillagerEditorSyncRequest.createEditorPatch(saveData.getEntityData());
            syncPatch.putString("HairBase", nextHairBase);
            syncPatch.putString("Clothes", nextClothes);
            syncPatch.putString("FamilyTreeNewSpouseName", bystanderName);
            new VillagerEditorSyncRequest("sync", sender.getUUID(), syncPatch).handleServer(sender);

            CompoundTag updated = saveData.getEntityData();
            helper.assertTrue(saveData.isEntityDataSet(),
                    "active Destiny session sync must mark entityDataSet=true");
            helper.assertTrue(updated.getInt("Gender") == nextGender.getId(),
                    "Destiny session should update Gender");
            helper.assertTrue(nextHairBase.equals(updated.getString("HairBase")),
                    "Destiny session should update HairBase");
            helper.assertTrue(nextClothes.equals(updated.getString("Clothes")),
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
        List<ChunkPos> ownedChunks = new ArrayList<>();
        ChunkPos at25 = new ChunkPos(BlockPos.containing(sender.position().add(25, 0, 0)));
        ChunkPos at26 = new ChunkPos(BlockPos.containing(sender.position().add(26, 0, 0)));
        GameTestTerrain.ensureEntityTickingChunks(helper, at25, at26, ownedChunks);
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

            Gender selfGender = Gender.byId(selfSave.getEntityData().getInt("Gender"));
            String selfNewHair = pickDifferentHairBase(selfGender, selfSave.getEntityData().getString("HairBase"));
            CompoundTag selfCombPatch = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            selfCombPatch.putString("HairBase", selfNewHair);
            new VillagerEditorSyncRequest("sync", sender.getUUID(), selfCombPatch).handleServer(sender);
            helper.assertTrue(selfNewHair.equals(selfSave.getEntityData().getString("HairBase")),
                    "Comb should allow changing HairBase on self");

            String selfOriginalClothes = selfSave.getEntityData().getString("Clothes");
            CompoundTag selfForbiddenClothesPatch = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            selfForbiddenClothesPatch.putString("Clothes", pickDifferentClothes(selfGender, selfOriginalClothes));
            new VillagerEditorSyncRequest("sync", sender.getUUID(), selfForbiddenClothesPatch).handleServer(sender);
            helper.assertTrue(selfOriginalClothes.equals(selfSave.getEntityData().getString("Clothes")),
                    "Comb must reject Clothes changes");

            Gender nearbyGender = nearbyVillager.getGenetics().getGender();
            String nearbyNewHair = pickDifferentHairBase(nearbyGender, nearbyVillager.getLayeredHair(LayeredHair.Category.BASE));
            CompoundTag nearbyCombPatch = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(nearbyVillager)));
            nearbyCombPatch.putString("HairBase", nearbyNewHair);
            new VillagerEditorSyncRequest("sync", nearbyVillager.getUUID(), nearbyCombPatch).handleServer(sender);
            helper.assertTrue(nearbyNewHair.equals(nearbyVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb should allow changing HairBase on villager 3 blocks away");

            farVillager.setPos(sender.position().add(25, 0, 0));
            helper.assertTrue(helper.getLevel().getEntity(farVillager.getUUID()) == farVillager,
                    "25-block comb target must be registered in the level's entity lookup");
            helper.assertTrue(helper.getLevel().isPositionEntityTicking(farVillager.blockPosition()),
                    "25-block comb target must be inside an entity-ticking chunk");
            String farOriginalHair = farVillager.getLayeredHair(LayeredHair.Category.BASE);
            String farNewHair = pickDifferentHairBase(farVillager.getGenetics().getGender(), farOriginalHair);
            CompoundTag farCombPatch = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(farVillager)));
            farCombPatch.putString("HairBase", farNewHair);
            new VillagerEditorSyncRequest("sync", farVillager.getUUID(), farCombPatch).handleServer(sender);
            helper.assertTrue(farNewHair.equals(farVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb should allow changing hair exactly 25 blocks away");

            farVillager.setPos(sender.position().add(26, 0, 0));
            farCombPatch.putString("HairBase", pickDifferentHairBase(farVillager.getGenetics().getGender(), farNewHair));
            new VillagerEditorSyncRequest("sync", farVillager.getUUID(), farCombPatch).handleServer(sender);
            helper.assertTrue(farNewHair.equals(farVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb must reject a villager beyond 25 blocks");

            // 2. Holding NEEDLE_AND_THREAD: Clothes allowed; HairBase & Gender rejected.
            sender.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.NEEDLE_AND_THREAD));

            String nearbyNewClothes = pickDifferentClothes(nearbyGender, nearbyVillager.getClothes());
            CompoundTag nearbyNeedlePatch = VillagerEditorSyncRequest.createEditorPatch(
                    Objects.requireNonNull(GetVillagerRequest.getVillagerData(nearbyVillager)));
            nearbyNeedlePatch.putString("Clothes", nearbyNewClothes);
            new VillagerEditorSyncRequest("sync", nearbyVillager.getUUID(), nearbyNeedlePatch).handleServer(sender);
            helper.assertTrue(nearbyNewClothes.equals(nearbyVillager.getClothes()),
                    "Needle and Thread should allow changing Clothes on nearby villager");

            CompoundTag needleHairReject = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            needleHairReject.putString("HairBase", pickDifferentHairBase(selfGender, selfSave.getEntityData().getString("HairBase")));
            new VillagerEditorSyncRequest("sync", sender.getUUID(), needleHairReject).handleServer(sender);
            helper.assertTrue(selfNewHair.equals(selfSave.getEntityData().getString("HairBase")),
                    "Needle and Thread must reject HairBase changes");

            int originalGenderId = selfSave.getEntityData().getInt("Gender");
            CompoundTag needleGenderReject = VillagerEditorSyncRequest.createEditorPatch(selfSave.getEntityData());
            needleGenderReject.putInt("Gender", selfGender == Gender.FEMALE ? Gender.MALE.getId() : Gender.FEMALE.getId());
            new VillagerEditorSyncRequest("sync", sender.getUUID(), needleGenderReject).handleServer(sender);
            helper.assertTrue(selfSave.getEntityData().getInt("Gender") == originalGenderId,
                    "Needle and Thread must reject Gender changes");
        } finally {
            Config.getInstance().allowFullPlayerEditor = originalFull;
            Config.getInstance().allowLimitedPlayerEditor = originalLimited;
            nearbyVillager.discard();
            farVillager.discard();
            helper.getLevel().removePlayerImmediately(sender, Entity.RemovalReason.DISCARDED);
            ownedChunks.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
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
            limitedPatch.putString("CustomName", "\"Echoed Finalized Name\"");
            new VillagerEditorSyncRequest("sync", sender.getUUID(), limitedPatch).handleServer(sender);

            helper.assertTrue("\"Echoed Finalized Name\"".equals(saveData.getEntityData().getString("CustomName")),
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
            combPatch.putString("HairBase", nextHair);
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
            clothesTamper.putString("HairBase", pickDifferentHairBase(gender, nextHair));
            clothesTamper.putString("Clothes", pickDifferentClothes(gender, originalClothes));
            new VillagerEditorSyncRequest("sync", childVillager.getUUID(), clothesTamper).handleServer(sender);
            helper.assertTrue(originalClothes.equals(childVillager.getClothes()) && nextHair.equals(childVillager.getLayeredHair(LayeredHair.Category.BASE)),
                    "Comb packet tampering with Clothes must be rejected completely");

            boolean hadRainbow = childVillager.getTraits().hasTrait(Traits.RAINBOW);
            CompoundTag traitsTamper = clientSnapshot.copy();
            CompoundTag forgedTraits = traitsTamper.getCompound("Traits").copy();
            if (hadRainbow) {
                forgedTraits.remove(Traits.RAINBOW.getId().toString());
            } else {
                forgedTraits.putBoolean(Traits.RAINBOW.getId().toString(), true);
            }
            traitsTamper.put("Traits", forgedTraits);
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
            int initialAge = initial.getInt("Age");
            int initialMood = initial.getInt("Mood");
            float initialInfection = initial.getFloat("InfectionProgress");
            String currentPersonality = initial.getString("Personality");
            String nextPersonality = Personality.PLAYFUL.getId().toString().equals(currentPersonality)
                    ? Personality.FRIENDLY.getId().toString()
                    : Personality.PLAYFUL.getId().toString();
            Gender gender = Gender.byId(initial.getInt("Gender"));
            String nextHair = pickDifferentHairBase(gender, initial.getString("HairBase"));

            ServerInteractionManager.launchDestiny(sender);

            CompoundTag presetPatch = VillagerEditorSyncRequest.createEditorPatch(initial.copy());
            presetPatch.putString("Personality", nextPersonality);
            presetPatch.putInt("Age", -24000);
            presetPatch.putString("HairBase", nextHair);
            presetPatch.putInt("Mood", initialMood + 25);
            presetPatch.putFloat("InfectionProgress", 0.75f);
            presetPatch.putString("FamilyTreeNewSpouseName", "PresetSpouse");

            new VillagerEditorSyncRequest("sync", sender.getUUID(), presetPatch).handleServer(sender);

            CompoundTag updated = saveData.getEntityData();
            helper.assertTrue(saveData.isEntityDataSet(),
                    "Destiny preset sync should mark entityDataSet=true");
            helper.assertTrue(nextPersonality.equals(updated.getString("Personality")),
                    "Destiny preset sync should apply Personality");
            helper.assertTrue(nextHair.equals(updated.getString("HairBase")),
                    "Destiny preset sync should apply HairBase");
            helper.assertTrue(updated.getInt("Age") == initialAge,
                    "Destiny preset sync must strip Age on ServerPlayer");
            helper.assertTrue(updated.getInt("Mood") == initialMood,
                    "Destiny preset sync must not mutate Mood");
            helper.assertTrue(Float.compare(updated.getFloat("InfectionProgress"), initialInfection) == 0,
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
            protected int getPermissionLevel() {
                return permissionLevel;
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
