package net.conczin.mca.client.gui;

import com.mojang.serialization.Lifecycle;
import net.conczin.mca.Config;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.CommonColors;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigScreenSearchTest {
    @Test
    void wideConfigRowsGiveLabelsTheExtraWidthAndKeepNativeSizedValueControls() {
        ConfigScreenSearch.EntryLayout layout = ConfigScreenSearch.entryLayout(918);

        assertEquals(159, layout.labelX());
        assertEquals(440, layout.labelWidth());
        assertEquals(609, layout.valueX());
        assertEquals(150, layout.valueWidth());
    }

    @Test
    void narrowConfigRowsKeepTheVanillaTwoColumnLayout() {
        ConfigScreenSearch.EntryLayout layout = ConfigScreenSearch.entryLayout(320);

        assertEquals(5, layout.labelX());
        assertEquals(150, layout.labelWidth());
        assertEquals(165, layout.valueX());
        assertEquals(150, layout.valueWidth());
    }

    @Test
    void matchesKeysLabelsTooltipsAndValuesCaseInsensitively() {
        Component name = Component.literal("Enable Mourning");
        Component tooltip = Component.literal("Controls village grief behaviour");

        assertTrue(ConfigScreenSearch.matchesSearch("mourning", "enableMourning", name, tooltip, true));
        assertTrue(ConfigScreenSearch.matchesSearch("GRIEF", "enableMourning", name, tooltip, true));
        assertTrue(ConfigScreenSearch.matchesSearch("on", "enableMourning", name, tooltip, true));
        assertFalse(ConfigScreenSearch.matchesSearch("archer", "enableMourning", name, tooltip, true));
    }

    @Test
    void booleanLabelsUseRequestedWordsAndColours() {
        Component trueLabel = ConfigScreenSearch.booleanLabel(true);
        Component falseLabel = ConfigScreenSearch.booleanLabel(false);

        assertEquals("TRUE", trueLabel.getString());
        assertEquals(CommonColors.GREEN & 0xFFFFFF, trueLabel.getStyle().getColor().getValue());
        assertEquals("FALSE", falseLabel.getString());
        assertEquals(CommonColors.SOFT_RED & 0xFFFFFF, falseLabel.getStyle().getColor().getValue());
    }

    @Test
    void restartBadgesReflectNativeRestartMetadata() {
        Component none = ConfigScreenSearch.withRestartBadge(Component.literal("Setting"), ModConfigSpec.RestartType.NONE);
        Component world = ConfigScreenSearch.withRestartBadge(Component.literal("Setting"), ModConfigSpec.RestartType.WORLD);
        Component game = ConfigScreenSearch.withRestartBadge(Component.literal("Setting"), ModConfigSpec.RestartType.GAME);

        assertEquals("Setting", none.getString());
        assertEquals("Setting [WORLD]", world.getString());
        assertEquals("Setting [GAME]", game.getString());
        assertEquals(ChatFormatting.GOLD.getColor(), world.getSiblings().getLast().getStyle().getColor().getValue());
        assertEquals(ChatFormatting.RED.getColor(), game.getSiblings().getLast().getStyle().getColor().getValue());
        assertEquals("", ConfigScreenSearch.restartExplanation(ModConfigSpec.RestartType.NONE).getString());
    }

    @Test
    void editorListValidationCombinesNativePredicateWithLiveRegistryExistence() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(List.of());
        Set<ResourceLocation> dimensions = Set.of(ResourceLocation.parse("minecraft:overworld"));
        Predicate<String> nativeValidator = value -> Config.SERVER.villagerInteractionItemBlacklist.getSpec().test(List.of(value));

        assertTrue(ConfigScreenSearch.isEditorListEntryValid(
                "villagerInteractionItemBlacklist", "minecraft:bucket", nativeValidator, registries, dimensions));
        assertFalse(ConfigScreenSearch.isEditorListEntryValid(
                "villagerInteractionItemBlacklist", "not an id", nativeValidator, registries, dimensions));
        assertFalse(ConfigScreenSearch.isEditorListEntryValid(
                "villagerInteractionItemBlacklist", "example:missing_item", nativeValidator, registries, dimensions));
    }

    @Test
    void editorValidationChecksStableRegistriesWithoutAWorldButDefersWorldOwnedEntries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Predicate<String> itemValidator = value -> Config.SERVER.villagerInteractionItemBlacklist.getSpec().test(List.of(value));
        Predicate<String> dimensionValidator = value -> Config.SERVER.villagerDimensionBlacklist.getSpec().test(List.of(value));

        assertFalse(ConfigScreenSearch.isEditorListEntryValid(
                "villagerInteractionItemBlacklist", "example:missing_item", itemValidator, null, null));
        assertTrue(ConfigScreenSearch.isEditorListEntryValid(
                "villagerDimensionBlacklist", "example:future_dimension", dimensionValidator, null, null));
    }

    @Test
    void editorValidationDoesNotUseUnsynchronizedStructureRegistryContents() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        RegistryAccess registries = new RegistryAccess.ImmutableRegistryAccess(List.of(
                new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable())));
        Predicate<String> destinyValidator = value -> Config.SERVER.destinySpawnLocations.getSpec().test(List.of(value));
        Predicate<String> rumorValidator = value -> Config.SERVER.structuresInRumors.getSpec().test(List.of(value));

        assertTrue(ConfigScreenSearch.isEditorListEntryValid(
                "destinySpawnLocations", "example:server_structure", destinyValidator, registries, Set.of()));
        assertTrue(ConfigScreenSearch.isEditorListEntryValid(
                "structuresInRumors", "example:server_structure", rumorValidator, registries, Set.of()));
    }

    @Test
    void listDoneRequiresBothTheBackingListAndCurrentDraftsToBeValid() {
        assertTrue(ConfigScreenSearch.isListDoneEnabled(true, true));
        assertFalse(ConfigScreenSearch.isListDoneEnabled(false, true));
        assertFalse(ConfigScreenSearch.isListDoneEnabled(true, false));
    }
}
