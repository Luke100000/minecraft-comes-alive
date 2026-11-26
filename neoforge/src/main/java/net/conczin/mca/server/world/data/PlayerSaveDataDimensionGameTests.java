package net.conczin.mca.server.world.data;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class PlayerSaveDataDimensionGameTests {
    private PlayerSaveDataDimensionGameTests() {
    }

    @GameTest(batch = "mca_player_save_data_dimension", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void optionalOfflinePlayerLookupUsesOverworldRecordInAllDimensions(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(nether != null && end != null, "fixture requires Nether and End levels");

        UUID offlinePlayerId = UUID.randomUUID();
        PlayerSaveData canonical = PlayerSaveData.get(overworld, offlinePlayerId);
        canonical.setChatAIPrompt("saved-offline-relative");

        for (ServerLevel level : new ServerLevel[]{overworld, nether, end}) {
            PlayerSaveData found = PlayerSaveData.getIfPresent(level, offlinePlayerId).orElse(null);
            helper.assertTrue(found == canonical,
                    "lookup from " + level.dimension().identifier() + " did not find the canonical saved player record");
            helper.assertTrue("saved-offline-relative".equals(found.getChatAIPrompt()),
                    "offline player record lost its saved data");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_player_save_data_dimension", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void netherPlayerLookupKeepsCanonicalOverworldBinding(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "fixture requires the Nether level");

        UUID playerId = UUID.randomUUID();
        PlayerSaveData canonical = PlayerSaveData.get(overworld, playerId);
        ServerPlayer player = new FakePlayer(nether, new GameProfile(playerId, "dimension-test"));
        helper.assertTrue(PlayerSaveData.get(player) == canonical,
                "Nether player lookup must reuse the canonical overworld record");

        PlayerSaveData found = PlayerSaveData.getIfPresent(overworld, playerId).orElse(null);
        helper.assertTrue(found == canonical, "optional lookup must return the same saved player record");
        helper.assertTrue(found.getWorld() == overworld,
                "Nether player lookup must not change the canonical record's overworld binding");
        helper.succeed();
    }

    @GameTest(batch = "mca_player_save_data_dimension", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void missingOfflinePlayerRemainsAbsentFromEachDimension(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(nether != null && end != null, "fixture requires Nether and End levels");

        UUID absentPlayerId = UUID.randomUUID();
        for (ServerLevel level : new ServerLevel[]{overworld, nether, end}) {
            helper.assertTrue(PlayerSaveData.getIfPresent(level, absentPlayerId).isEmpty(),
                    "optional lookup created a saved player record in " + level.dimension().identifier());
        }
        helper.succeed();
    }
}
