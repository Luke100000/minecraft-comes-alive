package net.conczin.mca.client.gui;

import com.mojang.serialization.Lifecycle;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.conczin.mca.Config;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.CommonColors;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigScreenSearchTest {
    @Test
    void wideConfigRowsGiveBothLabelsAndValuesExtraWidth() {
        ConfigScreenSearch.EntryLayout layout = ConfigScreenSearch.entryLayout(918);

        assertEquals(159, layout.labelX());
        assertEquals(324, layout.labelWidth());
        assertEquals(493, layout.valueX());
        assertEquals(266, layout.valueWidth());
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

    private enum ExampleMode {
        SOME_ENUM_VALUE
    }

    @Test
    void searchMatchesEncodedMapKeysValuesAndReadableEnumNames() {
        List<String> encodedMap = List.of("minecraft:zombie=4", "minecraft:creeper=-1");
        assertTrue(ConfigScreenSearch.matchesSearch("minecraft:zombie", "targets", Component.empty(), Component.empty(), encodedMap));
        assertTrue(ConfigScreenSearch.matchesSearch("-1", "targets", Component.empty(), Component.empty(), encodedMap));
        assertTrue(ConfigScreenSearch.matchesSearch("Some Enum Value", "mode", Component.empty(), Component.empty(), ExampleMode.SOME_ENUM_VALUE));
    }

    @Test
    void searchUsesAncestorCategoryCommentAsWellAsItsName() throws Exception {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Advanced multiplayer rules").translation("mca.configuration.section.special").push("special");
        builder.define("enabled", true);
        builder.pop();
        ModConfigSpec spec = builder.build();

        ConfigurationScreen.ConfigurationSectionScreen.Filter filter = searchFilter("multiplayer rules");
        EditBox editor = new EditBox(null, 150, 20, Component.empty());
        editor.setValue("not related to category name");
        ConfigurationScreen.ConfigurationSectionScreen.Element element =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(Component.literal("Enabled"), Component.empty(), editor, false);

        assertSame(element, filter.filterEntry(searchContext(filter, List.of("special"), spec), "enabled", element));
    }

    @Test
    void categorySearchCountsNativeConfigValuesByTheirCurrentValue() {
        assertTrue(ConfigScreenSearch.matchesSearch(
                "api.conczin.net",
                "villagerChatAIEndpoint",
                Component.literal("Endpoint"),
                Component.literal("Chat AI endpoint"),
                Config.COMMON.villagerChatAIEndpoint));
    }

    @Test
    void searchFilterMatchesTheCurrentStringEditorValue() throws Exception {
        ConfigurationScreen.ConfigurationSectionScreen.Filter filter = searchFilter("secret-endpoint");
        EditBox editor = new EditBox(null, 150, 20, Component.empty());
        editor.setMaxLength(128);
        editor.setValue("https://example.test/secret-endpoint");
        ConfigurationScreen.ConfigurationSectionScreen.Element element =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(
                        Component.literal("Endpoint"),
                        Component.literal("Chat AI endpoint"),
                        editor,
                        false);

        assertSame(element, filter.filterEntry(searchContext(filter, List.of()), "villagerChatAIEndpoint", element));
    }

    @Test
    void searchFilterKeepsChildrenVisibleWhenTheirAncestorCategoryMatches() throws Exception {
        ConfigurationScreen.ConfigurationSectionScreen.Filter filter = searchFilter("Player Customization");
        EditBox editor = new EditBox(null, 150, 20, Component.empty());
        editor.setValue("https://example.test/library");
        ConfigurationScreen.ConfigurationSectionScreen.Element element =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(
                        Component.literal("Immersive Library URL"),
                        Component.empty(),
                        editor,
                        false);

        assertSame(element, filter.filterEntry(
                searchContext(filter, List.of("player_customization")),
                "immersiveLibraryUrl",
                element));
    }

    @Test
    void searchFilterKeepsListButtonWhenItsEncodedEntriesMatch() throws Exception {
        ConfigurationScreen.ConfigurationSectionScreen.Filter filter = searchFilter("minecraft:zombie");
        Button listButton = Button.builder(Component.literal("Edit list"), ignored -> {}).build();
        ConfigurationScreen.ConfigurationSectionScreen.Element listElement =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(
                        Component.literal("Guard attack priorities"), Component.empty(), listButton, false);

        UnmodifiableConfig section = Config.SERVER_SPEC.getValues().get("village_behavior");
        ConfigurationScreen.ConfigurationSectionScreen.Context context =
                new ConfigurationScreen.ConfigurationSectionScreen.Context("mca", null, null, Config.SERVER_SPEC,
                        section.entrySet(), Map.of(), List.of("village_behavior"), filter);
        assertSame(listElement, filter.filterEntry(context, "guardsTargetEntities", listElement));
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

        assertNull(listError(
                "villagerInteractionItemBlacklist", "minecraft:bucket", nativeValidator, registries, dimensions));
        assertNotNull(listError(
                "villagerInteractionItemBlacklist", "not an id", nativeValidator, registries, dimensions));
        assertNotNull(listError(
                "villagerInteractionItemBlacklist", "example:missing_item", nativeValidator, registries, dimensions));
    }

    @Test
    void editorValidationChecksStableRegistriesWithoutAWorldButDefersWorldOwnedEntries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Predicate<String> itemValidator = value -> Config.SERVER.villagerInteractionItemBlacklist.getSpec().test(List.of(value));
        Predicate<String> dimensionValidator = value -> Config.SERVER.villagerDimensionBlacklist.getSpec().test(List.of(value));

        assertNotNull(listError(
                "villagerInteractionItemBlacklist", "example:missing_item", itemValidator, null, null));
        assertNull(listError(
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

        assertNull(listError(
                "destinySpawnLocations", "example:server_structure", destinyValidator, registries, Set.of()));
        assertNull(listError(
                "structuresInRumors", "example:server_structure", rumorValidator, registries, Set.of()));
    }

    @Test
    void listDoneRequiresBothTheBackingListAndCurrentDraftsToBeValid() {
        assertTrue(ConfigScreenSearch.isListDoneEnabled(true, true));
        assertFalse(ConfigScreenSearch.isListDoneEnabled(false, true));
        assertFalse(ConfigScreenSearch.isListDoneEnabled(true, false));
    }

    @Test
    void draftValidationRejectsDuplicateIdsAndMapKeysWithoutRejectingValidExistingRows() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Predicate<String> itemValidator = value -> Config.SERVER.villagerInteractionItemBlacklist.getSpec().test(List.of(value));
        assertNull(ConfigScreenSearch.listEntryError("villagerInteractionItemBlacklist", "minecraft:bucket",
                List.of("minecraft:bucket"), 0, itemValidator, null, null));
        assertEquals("Duplicate entry", ConfigScreenSearch.listEntryError("villagerInteractionItemBlacklist", "minecraft:bucket",
                List.of("minecraft:bucket", "minecraft:bucket"), 1, itemValidator, null, null).getString());

        Predicate<String> mapValidator = value -> Config.SERVER.guardsTargetEntities.getSpec().test(List.of(value));
        assertEquals("Duplicate key", ConfigScreenSearch.listEntryError("guardsTargetEntities", "minecraft:zombie=1",
                List.of("minecraft:zombie=4", "minecraft:zombie=1"), 1, mapValidator, null, null).getString());
        assertNull(ConfigScreenSearch.listEntryError("elevenlabsMaleVoices", "repeatable voice",
                List.of("repeatable voice", "repeatable voice"), 1, ignored -> true, null, null));
    }

    @Test
    void nestedSettingsCanBeFoundByTheirOwnSpecComments() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Group description").push("group");
        builder.comment("Unique child description").define("entry", "value");
        builder.pop();
        ModConfigSpec spec = builder.build();

        UnmodifiableConfig sectionSpec = spec.getSpec().get("group");
        assertTrue(ConfigScreenSearch.matchesSpecEntry("unique child description", spec,
                List.of("group"), "entry", "value", sectionSpec.get("entry")));
    }

    @Test
    void invalidDraftsExplainSyntaxAndMissingRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Predicate<String> validator = value -> Config.SERVER.villagerInteractionItemBlacklist.getSpec().test(List.of(value));
        assertEquals("Expected namespace:id", ConfigScreenSearch.listEntryError("villagerInteractionItemBlacklist",
                "not an id", List.of("not an id"), 0, validator, null, null).getString());
        assertEquals("Not found in the current registry", ConfigScreenSearch.listEntryError("villagerInteractionItemBlacklist",
                "example:missing_item", List.of("example:missing_item"), 0, validator, null, null).getString());
    }

    @Test
    void remoteServerValuesAreVisiblyReadOnlyButOtherConfigTypesRemainEditable() {
        assertTrue(ConfigScreenSearch.isRemoteServerSettings(ModConfig.Type.SERVER, true, false));
        assertFalse(ConfigScreenSearch.isRemoteServerSettings(ModConfig.Type.SERVER, true, true));
        assertFalse(ConfigScreenSearch.isRemoteServerSettings(ModConfig.Type.COMMON, true, false));

        EditBox editor = new EditBox(null, 150, 20, Component.empty());
        ConfigurationScreen.ConfigurationSectionScreen.Element textValue =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(Component.literal("Name"), Component.empty(), editor, false);
        assertSame(textValue, ConfigScreenSearch.readOnlyValue(textValue));
        assertFalse(editor.active);

        Button button = Button.builder(Component.literal("Open list"), ignored -> {}).build();
        ConfigurationScreen.ConfigurationSectionScreen.Element listValue =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(Component.literal("List"), Component.empty(), button, false);
        assertSame(listValue, ConfigScreenSearch.readOnlyValue(listValue));
        assertFalse(button.active);

        Button browseList = Button.builder(Component.literal("Browse list"), ignored -> {}).build();
        ConfigurationScreen.ConfigurationSectionScreen.Element navigation =
                new ConfigurationScreen.ConfigurationSectionScreen.Element(Component.literal("List"),
                        Component.empty(), browseList, false);
        assertSame(browseList, ConfigScreenSearch.readOnlyListNavigation(navigation).widget());
        assertTrue(browseList.active, "Read-only lists must remain browsable");
    }

    private static ConfigurationScreen.ConfigurationSectionScreen.Filter searchFilter(String query) throws Exception {
        Class<?> searchStateClass = Class.forName(ConfigScreenSearch.class.getName() + "$SearchState");
        Constructor<?> stateConstructor = searchStateClass.getDeclaredConstructor();
        stateConstructor.setAccessible(true);
        Object searchState = stateConstructor.newInstance();

        Method update = searchStateClass.getDeclaredMethod("update", String.class);
        update.setAccessible(true);
        update.invoke(searchState, query);

        Class<?> filterClass = Class.forName(ConfigScreenSearch.class.getName() + "$SearchFilter");
        Constructor<?> filterConstructor = filterClass.getDeclaredConstructor(searchStateClass);
        filterConstructor.setAccessible(true);
        return (ConfigurationScreen.ConfigurationSectionScreen.Filter) filterConstructor.newInstance(searchState);
    }

    private static Component listError(String key, String value, Predicate<String> validator,
                                       RegistryAccess registries, Set<ResourceLocation> dimensions) {
        return ConfigScreenSearch.listEntryError(key, value, List.of(value), 0, validator, registries, dimensions);
    }

    private static ConfigurationScreen.ConfigurationSectionScreen.Context searchContext(
            ConfigurationScreen.ConfigurationSectionScreen.Filter filter,
            List<String> keyList
    ) {
        return searchContext(filter, keyList, Config.SERVER_SPEC);
    }

    private static ConfigurationScreen.ConfigurationSectionScreen.Context searchContext(
            ConfigurationScreen.ConfigurationSectionScreen.Filter filter,
            List<String> keyList,
            ModConfigSpec spec
    ) {
        return new ConfigurationScreen.ConfigurationSectionScreen.Context(
                "mca",
                null,
                null,
                spec,
                Set.of(),
                Map.of(),
                keyList,
                filter);
    }
}
