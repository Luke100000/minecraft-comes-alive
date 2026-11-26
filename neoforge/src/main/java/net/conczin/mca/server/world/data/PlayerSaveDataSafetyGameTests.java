package net.conczin.mca.server.world.data;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.ShowToastRequest;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.server.ServerInteractionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
public final class PlayerSaveDataSafetyGameTests {
    private PlayerSaveDataSafetyGameTests() {
    }

    @GameTest(batch = "mca_player_save_safety", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void netherFirstLoadBindsOverworldAndFindsCrossDimensionPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel nether = Objects.requireNonNull(server.getLevel(Level.NETHER), "Nether level must exist");

        UUID playerUuid = UUID.randomUUID();
        String profileName = "NetherBound-" + playerUuid.toString().substring(0, 8);
        AtomicInteger refreshCount = new AtomicInteger();
        List<HandleablePayload> sentPayloads = new ArrayList<>();

        ServerPlayer netherPlayer = new FakePlayer(nether, new GameProfile(playerUuid, profileName)) {
            @Override
            public void refreshDimensions() {
                super.refreshDimensions();
                refreshCount.incrementAndGet();
            }
        };
        nether.players().add(netherPlayer);

        Network.registerSender((target, payload) -> {
            if (target == netherPlayer) {
                sentPayloads.add(payload);
            }
            PacketDistributor.sendToPlayer(target, payload);
        });

        try {
            PlayerSaveData data = PlayerSaveData.get(nether, playerUuid);
            helper.assertTrue(data.getWorld() == overworld,
                    "PlayerSaveData loaded from Nether must still bind server.overworld()");

            helper.assertTrue(profileName.equals(data.getFamilyEntry().getName()),
                    "cross-dimension family entry lookup must resolve the online Nether player");

            refreshCount.set(0);
            data.setEntityDataSet(true);
            helper.assertTrue(refreshCount.get() > 0,
                    "refreshPlayerDimensions must locate online player in Nether");

            sentPayloads.clear();
            data.sendLetter(Component.literal("Cross-dimension letter"));
            helper.assertTrue(data.hasMail(), "sendLetter should enqueue mail");
            helper.assertTrue(sentPayloads.stream().anyMatch(ShowToastRequest.class::isInstance),
                    "sendLetter should notify online player in Nether");
        } finally {
            Network.registerSender(PacketDistributor::sendToPlayer);
            nether.players().remove(netherPlayer);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_player_save_safety", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unreadablePlayerSaveSkipsDestinyAndClearsDirtyOnSave(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        UUID playerUuid = UUID.randomUUID();
        Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path saveFile = dataDir.resolve("mca_player_" + playerUuid + ".dat");
        byte[] corruptBytes = new byte[]{0x13, 0x37, 0x00, 0x7F};

        List<Component> systemMessages = new ArrayList<>();
        ServerPlayer player = new FakePlayer(overworld, new GameProfile(playerUuid, "corrupt-save")) {
            @Override
            public PermissionSet permissions() {
                return PermissionSet.NO_PERMISSIONS;
            }

            @Override
            public void sendSystemMessage(Component component) {
                systemMessages.add(component);
            }
        };

        boolean originalLaunchDestiny = Config.getInstance().launchIntoDestiny;
        try {
            Files.createDirectories(dataDir);
            Files.write(saveFile, corruptBytes);
            Config.getInstance().launchIntoDestiny = true;

            PlayerSaveData data = PlayerSaveData.get(player);
            helper.assertTrue(data.hasUnreadableSavedData(),
                    "corrupt mca_player_<uuid>.dat on disk must mark hasUnreadableSavedData=true");

            ServerInteractionManager.getInstance().onPlayerJoin(player);

            boolean warnedUnreadable = systemMessages.stream().anyMatch(msg ->
                    msg.getContents() instanceof TranslatableContents tc
                            && "mca.error.player_data_unreadable".equals(tc.getKey())
                            || msg.getString().contains("could not load your player data")
            );
            helper.assertTrue(warnedUnreadable,
                    "onPlayerJoin must warn the player when PlayerSaveData is unreadable");
            helper.assertTrue(!ServerInteractionManager.getInstance().hasActiveDestinySession(player),
                    "unreadable PlayerSaveData must not launch an active Destiny session");
            helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY) && !player.hasEffect(MobEffects.HEALTH_BOOST),
                    "unreadable PlayerSaveData must not apply Destiny onboarding effects");

            data.setDirty();
            data.setDirty(true);
            helper.assertTrue(!data.isDirty(), "Unreadable data must remain clean before vanilla storage serializes it");
            overworld.getDataStorage().saveAndJoin();
            helper.assertTrue(!data.isDirty(),
                    "save() on unreadable PlayerSaveData must clear isDirty() so subsequent autosaves do not re-log");
            helper.assertTrue(Arrays.equals(corruptBytes, Files.readAllBytes(saveFile)),
                    "save() must preserve the original corrupt save file on disk");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Config.getInstance().launchIntoDestiny = originalLaunchDestiny;
            ServerInteractionManager.getInstance().clearDestinySession(playerUuid);
            try {
                Files.deleteIfExists(saveFile);
            } catch (Exception ignored) {
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_player_save_safety", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void tombstoneLoadAdditionalAfterSetLevelRegistersGlobalGrave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gravePos = helper.absolutePos(new BlockPos(2, 2, 2));
        GlobalPos expectedGrave = GlobalPos.of(level.dimension(), gravePos);
        UUID deceasedId = UUID.randomUUID();

        TombstoneBlock.Data tombstone = new TombstoneBlock.Data(gravePos, BlocksMCA.CROSS_HEADSTONE.defaultBlockState());
        try {
            tombstone.setLevel(level);
            helper.assertTrue(GraveyardManager.getGlobal(level).getOccupiedGrave(deceasedId).isEmpty(),
                    "empty tombstone must not pre-register deceased UUID");

            CompoundTag entityNbt = new CompoundTag();
            entityNbt.store("UUID", UUIDUtil.CODEC, deceasedId);
            entityNbt.putString("id", "mca:female_villager");

            CompoundTag savedTag = new CompoundTag();
            savedTag.put("EntityData", entityNbt);
            savedTag.putString("EntityName", "Late Villager");
            savedTag.putInt("EntityGender", Gender.FEMALE.ordinal());

            tombstone.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedTag));

            helper.assertTrue(
                    GraveyardManager.getGlobal(level).getOccupiedGrave(deceasedId).equals(Optional.of(expectedGrave)),
                    "loadAdditional after setLevel must register the grave in GraveyardManager.getGlobal"
            );
            assertTombstoneRoundTrip(helper, tombstone, entityNbt, deceasedId, expectedGrave);
        } finally {
            GraveyardManager.getGlobal(level).clearOccupiedGrave(deceasedId, expectedGrave);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_player_save_safety", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nestedTombstoneDataPreservesGraveAndMetadata(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gravePos = helper.absolutePos(new BlockPos(2, 2, 2));
        GlobalPos expectedGrave = GlobalPos.of(level.dimension(), gravePos);
        UUID deceasedId = UUID.randomUUID();
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.store("UUID", UUIDUtil.CODEC, deceasedId);
        entityNbt.putString("id", "mca:female_villager");
        CompoundTag envelope = new CompoundTag();
        envelope.put("EntityData", entityNbt);
        envelope.putString("EntityName", "Late Villager");
        envelope.putInt("EntityGender", Gender.FEMALE.ordinal());
        CompoundTag savedTag = new CompoundTag();
        savedTag.put("EntityData", envelope);

        TombstoneBlock.Data tombstone = new TombstoneBlock.Data(gravePos, BlocksMCA.CROSS_HEADSTONE.defaultBlockState());
        try {
            tombstone.setLevel(level);
            tombstone.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), savedTag));
            assertTombstoneRoundTrip(helper, tombstone, entityNbt, deceasedId, expectedGrave);
        } finally {
            GraveyardManager.getGlobal(level).clearOccupiedGrave(deceasedId, expectedGrave);
        }
        helper.succeed();
    }

    private static void assertTombstoneRoundTrip(GameTestHelper helper, TombstoneBlock.Data tombstone,
                                                 CompoundTag entityNbt, UUID deceasedId, GlobalPos expectedGrave) {
        ServerLevel level = helper.getLevel();
        GraveyardManager graves = GraveyardManager.getGlobal(level);
        helper.assertTrue(tombstone.getEntityUuid().equals(Optional.of(deceasedId)), "loaded grave lost its deceased UUID");
        helper.assertTrue(tombstone.getEntityName().equals(Optional.of("Late Villager")), "loaded grave lost its name");
        helper.assertTrue(tombstone.getGender() == Gender.FEMALE, "loaded grave lost its gender");
        helper.assertTrue(graves.getOccupiedGrave(deceasedId).equals(Optional.of(expectedGrave)),
                "loaded grave was not registered in the global graveyard");

        CompoundTag saved = tombstone.saveCustomOnly(level.registryAccess());
        CompoundTag expectedEnvelope = new CompoundTag();
        expectedEnvelope.put("EntityData", entityNbt);
        expectedEnvelope.putString("EntityName", "Late Villager");
        expectedEnvelope.putInt("EntityGender", Gender.FEMALE.ordinal());
        helper.assertTrue(saved.getCompound("EntityData").equals(Optional.of(expectedEnvelope)),
                "saving a loaded grave must retain its raw entity payload and metadata in the target envelope");

        graves.clearOccupiedGrave(deceasedId, expectedGrave);
        TombstoneBlock.Data reloaded = new TombstoneBlock.Data(expectedGrave.pos(), BlocksMCA.CROSS_HEADSTONE.defaultBlockState());
        reloaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        reloaded.setLevel(level);
        helper.assertTrue(reloaded.getEntityUuid().equals(Optional.of(deceasedId))
                        && reloaded.getEntityName().equals(Optional.of("Late Villager"))
                        && reloaded.getGender() == Gender.FEMALE,
                "target writer round trip must preserve grave identity and metadata");
        helper.assertTrue(graves.getOccupiedGrave(deceasedId).equals(Optional.of(expectedGrave)),
                "setting the level after reload must register the grave again");
    }
}
