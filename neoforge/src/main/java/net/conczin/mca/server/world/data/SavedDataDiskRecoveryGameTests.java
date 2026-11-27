package net.conczin.mca.server.world.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.Level;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
public final class SavedDataDiskRecoveryGameTests {
    private SavedDataDiskRecoveryGameTests() {
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingPlayerDataReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-reload-");
        UUID uuid = UUID.randomUUID();
        folder.toFile().deleteOnExit();
        folder.resolve("mca/mca_player_" + uuid + ".dat").toFile().deleteOnExit();
        SavedDataStorage first = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess());
        PlayerSaveData initial = PlayerSaveData.loadSafely(world, uuid, first, folder);
        initial.setChatAIPrompt("persisted-value");
        first.saveAndJoin();

        SavedDataStorage second = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess());
        PlayerSaveData reloaded = PlayerSaveData.loadSafely(world, uuid, second, folder);
        helper.assertTrue("persisted-value".equals(reloaded.getChatAIPrompt()),
                "MCA player data was not loaded from disk with the current loader");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unreadablePlayerDataIsNeverOverwritten(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-corrupt-");
        UUID uuid = UUID.randomUUID();
        Path file = folder.resolve("mca").resolve("mca_player_" + uuid + ".dat");
        byte[] original = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        folder.toFile().deleteOnExit();
        file.toFile().deleteOnExit();
        Files.createDirectories(file.getParent());
        Files.write(file, original);
        SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess());
        PlayerSaveData player = PlayerSaveData.loadSafely(world, uuid, storage, folder);
        player.setChatAIPrompt("do-not-replace-existing-save");
        player.setDirty(true);
        storage.saveAndJoin();
        helper.assertTrue(Arrays.equals(original, Files.readAllBytes(file)),
                "Unreadable MCA player data must retain its original bytes");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void versionedLegacyMailMigratesFromFlatFile(GameTestHelper helper) throws IOException {
        assertLegacyMailReloads(helper, false, true);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unversionedLegacyMailMigratesFromFlatFile(GameTestHelper helper) throws IOException {
        assertLegacyMailReloads(helper, false, false);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void explicitOldVersionOverridesCanonicalMailPath(GameTestHelper helper) throws IOException {
        assertLegacyMailReloads(helper, true, true);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMailPreservesNestedTranslationArgumentEvents(GameTestHelper helper) throws IOException {
        CompoundTag root = mailFixture(List.of("{\"translate\":\"mca.letter.condolence\",\"with\":[{\"text\":\"Ada\","
                + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"Hint\"}},"
                + "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/help\"}}]}"));
        root.putInt("DataVersion", 3955);
        assertMailReloads(helper, root, false, player -> assertNestedTranslationArgumentEvents(helper, player));
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMailPreservesNestedHoverItemNameArgumentEvents(GameTestHelper helper) throws IOException {
        assertLegacyHoverItemMailReloads(helper, false);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMailPreservesNestedHoverItemLoreArgumentEvents(GameTestHelper helper) throws IOException {
        assertLegacyHoverItemMailReloads(helper, true);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMailPreservesHoverWrittenBookPages(GameTestHelper helper) throws IOException {
        JsonArray pages = new JsonArray();
        pages.add(legacyStyledTranslation());
        JsonObject filteredPage = new JsonObject();
        filteredPage.addProperty("raw", "\"Unfiltered page\"");
        filteredPage.addProperty("filtered", legacyStyledTranslation());
        pages.add(filteredPage);
        JsonObject book = new JsonObject();
        book.addProperty("title", "Fixture book");
        book.addProperty("author", "Ada");
        book.addProperty("generation", 1);
        book.addProperty("resolved", true);
        book.add("pages", pages);
        JsonObject components = new JsonObject();
        components.add("minecraft:written_book_content", book);
        JsonObject item = new JsonObject();
        item.addProperty("id", "minecraft:written_book");
        item.addProperty("count", 1);
        item.add("components", components);
        CompoundTag root = mailFixture(List.of("{\"text\":\"Hover me\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":" + item + "}}"));
        root.putInt("DataVersion", 3955);
        assertMailReloads(helper, root, false, player -> {
            Component page = singleMailPage(helper, player);
            helper.assertTrue(page.getString().equals("Hover me") && page.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem,
                    "Legacy mail lost the hovered written book");
            HoverEvent.ShowItem hover = (HoverEvent.ShowItem) page.getStyle().getHoverEvent();
            WrittenBookContent content = hover.item().get(DataComponents.WRITTEN_BOOK_CONTENT);
            helper.assertTrue(hover.item().item().is(Items.WRITTEN_BOOK.builtInRegistryHolder()) && hover.item().count() == 1
                            && content != null && content.title().raw().equals("Fixture book") && content.author().equals("Ada")
                            && content.generation() == 1 && content.resolved() && content.pages().size() == 2,
                    "Legacy mail changed the hovered book metadata or lost its pages");
            assertStyledTranslation(helper, content.pages().getFirst().raw());
            helper.assertTrue(content.pages().get(1).raw().equals(Component.literal("Unfiltered page"))
                            && content.pages().get(1).filtered().isPresent(),
                    "Legacy mail lost the written book's raw or filtered page");
            assertStyledTranslation(helper, content.pages().get(1).filtered().orElseThrow());
        });
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMailPreservesHoverBundleNamedItem(GameTestHelper helper) throws IOException {
        JsonObject nestedComponents = new JsonObject();
        nestedComponents.addProperty("minecraft:custom_name", legacyStyledTranslation());
        nestedComponents.addProperty("minecraft:max_stack_size", 16);
        JsonObject nestedItem = new JsonObject();
        nestedItem.addProperty("id", "minecraft:paper");
        nestedItem.addProperty("count", 3);
        nestedItem.add("components", nestedComponents);
        JsonArray contents = new JsonArray();
        contents.add(nestedItem);
        JsonObject components = new JsonObject();
        components.add("minecraft:bundle_contents", contents);
        JsonObject item = new JsonObject();
        item.addProperty("id", "minecraft:bundle");
        item.addProperty("count", 1);
        item.add("components", components);
        CompoundTag root = mailFixture(List.of("{\"text\":\"Hover me\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":" + item + "}}"));
        root.putInt("DataVersion", 3955);
        assertMailReloads(helper, root, false, player -> {
            Component page = singleMailPage(helper, player);
            helper.assertTrue(page.getString().equals("Hover me") && page.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem,
                    "Legacy mail lost the hovered bundle");
            HoverEvent.ShowItem hover = (HoverEvent.ShowItem) page.getStyle().getHoverEvent();
            BundleContents bundle = hover.item().get(DataComponents.BUNDLE_CONTENTS);
            helper.assertTrue(hover.item().item().is(Items.BUNDLE.builtInRegistryHolder()) && hover.item().count() == 1
                            && bundle != null && bundle.size() == 1,
                    "Legacy mail changed the hovered bundle or lost its contents");
            ItemStackTemplate nested = bundle.items().getFirst();
            helper.assertTrue(nested.item().is(Items.PAPER.builtInRegistryHolder()) && nested.count() == 3
                            && Integer.valueOf(16).equals(nested.get(DataComponents.MAX_STACK_SIZE)),
                    "Legacy mail changed the bundled item identity, count or non-text component");
            assertStyledTranslation(helper, nested.get(DataComponents.CUSTOM_NAME));
        });
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMailPreservesTwoLevelTranslationArgumentEvents(GameTestHelper helper) throws IOException {
        CompoundTag root = mailFixture(List.of("{\"translate\":\"fixture.outer\",\"with\":[" + legacyStyledTranslation() + "]}"));
        root.putInt("DataVersion", 3955);
        assertMailReloads(helper, root, false, player -> {
            Component outer = singleMailPage(helper, player);
            helper.assertTrue(outer.getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals("fixture.outer") && contents.getArgs().length == 1
                            && contents.getArgs()[0] instanceof Component,
                    "Legacy mail lost the outer translation or nested translation component");
            assertStyledTranslation(helper, (Component) ((TranslatableContents) outer.getContents()).getArgs()[0]);
        });
        helper.succeed();
    }

    private static String legacyStyledTranslation() {
        return "{\"translate\":\"fixture.inner\",\"with\":[{\"text\":\"Ada\","
                + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"Hint\"}},"
                + "\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/help\"}},\"plain\",7]}";
    }

    private static void assertLegacyHoverItemMailReloads(GameTestHelper helper, boolean lore) throws IOException {
        // In 1.21.1, hover item stacks use ItemStack.CODEC, but name and lore components use FLAT_CODEC.
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:max_stack_size", 16);
        if (lore) {
            JsonArray lines = new JsonArray();
            lines.add(legacyStyledTranslation());
            lines.add("\"Plain lore\"");
            components.add("minecraft:lore", lines);
        } else {
            components.addProperty("minecraft:custom_name", legacyStyledTranslation());
        }
        JsonObject item = new JsonObject();
        item.addProperty("id", "minecraft:paper");
        item.addProperty("count", 3);
        item.add("components", components);
        CompoundTag root = mailFixture(List.of("{\"text\":\"Hover me\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":" + item + "}}"));
        root.putInt("DataVersion", 3955);
        assertMailReloads(helper, root, false, player -> {
            Component page = singleMailPage(helper, player);
            helper.assertTrue(page.getString().equals("Hover me") && page.getStyle().getHoverEvent() instanceof HoverEvent.ShowItem,
                    "Legacy mail lost the hover item");
            HoverEvent.ShowItem hover = (HoverEvent.ShowItem) page.getStyle().getHoverEvent();
            helper.assertTrue(hover.item().item().is(Items.PAPER.builtInRegistryHolder()) && hover.item().count() == 3
                            && Integer.valueOf(16).equals(hover.item().get(DataComponents.MAX_STACK_SIZE)),
                    "Legacy mail changed the hovered item identity, count or non-text component");
            Component translated;
            if (lore) {
                ItemLore itemLore = hover.item().get(DataComponents.LORE);
                helper.assertTrue(itemLore != null && itemLore.lines().size() == 2
                                && itemLore.lines().get(1).equals(Component.literal("Plain lore")),
                        "Legacy mail lost the hover item's lore or plain control line");
                translated = itemLore.lines().getFirst();
            } else {
                translated = hover.item().get(DataComponents.CUSTOM_NAME);
            }
            assertStyledTranslation(helper, translated);
        });
    }

    private static Component singleMailPage(GameTestHelper helper, PlayerSaveData player) {
        ItemStack letter = player.getMail();
        helper.assertTrue(letter != null, "Player lost the stored letter");
        List<Component> pages = letter.get(DataComponentsMCA.BOOK_PAGES);
        helper.assertTrue(pages != null && pages.size() == 1, "Player lost the mail page");
        return pages.getFirst();
    }

    private static void assertStyledTranslation(GameTestHelper helper, Component translated) {
        helper.assertTrue(translated != null && translated.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("fixture.inner") && contents.getArgs().length == 3
                        && contents.getArgs()[1].equals("plain") && contents.getArgs()[2] instanceof Number number
                        && number.intValue() == 7,
                "Legacy mail lost the inner translation or primitive arguments");
        Object argument = ((TranslatableContents) translated.getContents()).getArgs()[0];
        helper.assertTrue(argument instanceof Component component && component.getString().equals("Ada"),
                "Legacy mail lost the inner styled translation argument");
        Component component = (Component) argument;
        helper.assertTrue(component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover
                        && hover.value().getString().equals("Hint"),
                "Legacy mail lost the inner translation argument's hover text");
        helper.assertTrue(component.getStyle().getClickEvent() instanceof ClickEvent.SuggestCommand click
                        && click.command().equals("/help"),
                "Legacy mail lost the inner translation argument's click event");
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void modernJsonLookingMailRemainsLiteral(GameTestHelper helper) throws IOException {
        assertNativeMailReloads(helper, true, true);
        assertNativeMailReloads(helper, true, false);
        assertNativeMailReloads(helper, false, true);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void malformedLegacyMailDoesNotOverwriteSave(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-mail-malformed-");
        UUID uuid = UUID.randomUUID();
        Path flat = folder.resolve("mca_player_" + uuid + ".dat");
        Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
        try {
            CompoundTag root = mailFixture(List.of("{\"text\":"));
            root.putInt("DataVersion", 3955);
            NbtIo.writeCompressed(root, flat);
            byte[] original = Files.readAllBytes(flat);
            try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData player = PlayerSaveData.loadSafely(world, uuid, storage, folder);
                helper.assertTrue(player.hasUnreadableSavedData(),
                        "Malformed legacy component JSON must not be treated as successfully migrated mail");
                player.setChatAIPrompt("do-not-replace-malformed-mail");
                player.setDirty(true);
                storage.saveAndJoin();
                helper.assertTrue(Arrays.equals(original, Files.readAllBytes(flat)) && !Files.exists(canonical),
                        "Malformed legacy mail must retain the original bytes without writing a replacement");
            }
        } finally {
            deleteMailFixture(folder, flat, canonical);
        }
        helper.succeed();
    }

    private static void assertLegacyMailReloads(GameTestHelper helper, boolean namespaced, boolean versioned) throws IOException {
        // 1.21.1 FLAT_CODEC stored each component as a JSON string, not native component NBT.
        CompoundTag root = mailFixture(List.of("\"Hello from 1.21.1\"",
                "{\"translate\":\"mca.letter.condolence\",\"with\":[\"Ada\"],\"bold\":true,"
                        + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"Hint\"}}}"));
        if (versioned) root.putInt("DataVersion", 3955);
        assertMailReloads(helper, root, namespaced, player -> assertMailPages(helper, player, true));
    }

    private static void assertNativeMailReloads(GameTestHelper helper, boolean namespaced, boolean versioned) throws IOException {
        // Native CODEC stores unstyled literal text directly. Both pages intentionally look like JSON.
        CompoundTag root = mailFixture(List.of("{\"text\":\"not a component\"}", "\"quoted literal\""));
        if (versioned) NbtUtils.addCurrentDataVersion(root);
        assertMailReloads(helper, root, namespaced, player -> assertMailPages(helper, player, false));
    }

    private static CompoundTag mailFixture(List<String> storedPages) {
        ListTag pages = new ListTag();
        storedPages.forEach(page -> pages.add(StringTag.valueOf(page)));
        CompoundTag letter = new CompoundTag();
        letter.putString("title", "Fixture letter");
        letter.put("pages", pages);
        ListTag inbox = new ListTag();
        inbox.add(letter);
        CompoundTag data = new CompoundTag();
        data.put("inbox", inbox);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        return root;
    }

    private static void assertMailReloads(GameTestHelper helper, CompoundTag root, boolean namespaced,
                                          Consumer<PlayerSaveData> assertPages) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-mail-reload-");
        UUID uuid = UUID.randomUUID();
        Path flat = folder.resolve("mca_player_" + uuid + ".dat");
        Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
        Path source = namespaced ? canonical : flat;
        try {
            Files.createDirectories(source.getParent());
            NbtIo.writeCompressed(root, source);
            byte[] original = Files.readAllBytes(source);
            try (SavedDataStorage first = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData player = PlayerSaveData.loadSafely(world, uuid, first, folder);
                helper.assertTrue(!player.hasUnreadableSavedData(), "Valid mail fixture was quarantined");
                // Force ordinary persistence before consuming the first letter.
                player.setChatAIPrompt("mail-round-trip");
                first.saveAndJoin();
                assertPages.accept(player);
            }
            helper.assertTrue(Files.isRegularFile(canonical), "Mail did not persist to canonical storage");
            if (!namespaced) {
                helper.assertTrue(Arrays.equals(original, Files.readAllBytes(flat)),
                        "Migration overwrote the original flat saved-data file");
            }
            try (SavedDataStorage second = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData reloaded = PlayerSaveData.loadSafely(world, uuid, second, folder);
                helper.assertTrue(!reloaded.hasUnreadableSavedData(), "Migrated mail could not be reloaded");
                assertPages.accept(reloaded);
            }
        } finally {
            deleteMailFixture(folder, flat, canonical);
        }
    }

    private static void assertNestedTranslationArgumentEvents(GameTestHelper helper, PlayerSaveData player) {
        ItemStack letter = player.getMail();
        helper.assertTrue(letter != null, "Player lost the stored letter");
        List<Component> pages = letter.get(DataComponentsMCA.BOOK_PAGES);
        helper.assertTrue(pages != null && pages.size() == 1, "Player lost the translated mail page");
        helper.assertTrue(pages.getFirst().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("mca.letter.condolence")
                        && contents.getArgs().length == 1
                        && contents.getArgs()[0] instanceof Component argument
                        && argument.getString().equals("Ada"),
                "Legacy mail lost its styled translation argument");
        TranslatableContents contents = (TranslatableContents) pages.getFirst().getContents();
        Component argument = (Component) contents.getArgs()[0];
        helper.assertTrue(argument.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover
                        && hover.value().getString().equals("Hint"),
                "Legacy mail lost the translation argument's hover text");
        helper.assertTrue(argument.getStyle().getClickEvent() instanceof ClickEvent.SuggestCommand click
                        && click.command().equals("/help"),
                "Legacy mail lost the translation argument's click event");
    }

    private static void assertMailPages(GameTestHelper helper, PlayerSaveData player, boolean legacy) {
        ItemStack letter = player.getMail();
        helper.assertTrue(letter != null, "Player lost the stored letter");
        List<Component> pages = letter.get(DataComponentsMCA.BOOK_PAGES);
        helper.assertTrue(pages != null && pages.size() == 2, "Player lost stored mail pages");
        if (!legacy) {
            helper.assertTrue(pages.get(0).equals(Component.literal("{\"text\":\"not a component\"}"))
                            && pages.get(1).equals(Component.literal("\"quoted literal\"")),
                    "Modern literal mail must not be reparsed as legacy JSON");
            return;
        }
        helper.assertTrue(pages.get(0).equals(Component.literal("Hello from 1.21.1")),
                "Legacy flat-codec mail must decode the JSON string, not display its quotation marks");
        Component translated = pages.get(1);
        helper.assertTrue(translated.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("mca.letter.condolence")
                        && contents.getArgs().length == 1
                        && (contents.getArgs()[0] instanceof Component argument ? argument.getString() : contents.getArgs()[0]).equals("Ada")
                        && translated.getStyle().isBold(),
                "Legacy mail lost its translation key, arguments or style");
        helper.assertTrue(translated.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover
                        && hover.value().getString().equals("Hint"),
                "Legacy mail lost its hover text during component schema migration");
    }

    private static void deleteMailFixture(Path folder, Path flat, Path canonical) throws IOException {
        Files.deleteIfExists(flat);
        Files.deleteIfExists(canonical);
        Files.deleteIfExists(canonical.getParent());
        Files.deleteIfExists(folder);
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void canonicalLegacyMailMigrationMarksOnlyChangedSaveDirty(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-mail-dirty-");
        UUID uuid = UUID.randomUUID();
        Path flat = folder.resolve("mca_player_" + uuid + ".dat");
        Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
        try {
            CompoundTag root = normalizedPlayerFixture(world, uuid, "mail-dirty");
            CompoundTag payload = root.getCompound("data").orElseThrow();
            helper.assertTrue(!new PlayerSaveData(world, uuid, payload).isDirty(),
                    "Fixture entity data must not require unrelated normalization");
            payload.put("inbox", mailFixture(List.of("\"legacy mail\""))
                    .getCompound("data").orElseThrow().getList("inbox").orElseThrow());
            root.putInt("DataVersion", 3955);
            Files.createDirectories(canonical.getParent());
            NbtIo.writeCompressed(root, canonical);
            try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData migrated = PlayerSaveData.loadSafely(world, uuid, storage, folder);
                helper.assertTrue(!migrated.hasUnreadableSavedData() && migrated.isDirty(),
                        "Migrating canonical old mail must schedule persistence without unrelated edits");
                storage.saveAndJoin();
            }
            CompoundTag rewritten = NbtIo.readCompressed(canonical, NbtAccounter.unlimitedHeap());
            helper.assertTrue(NbtUtils.getDataVersion(rewritten) == SharedConstants.getCurrentVersion().dataVersion().version(),
                    "The migrated canonical save must carry the current data version");
            byte[] modernBytes = Files.readAllBytes(canonical);
            try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData nativeData = PlayerSaveData.loadSafely(world, uuid, storage, folder);
                helper.assertTrue(!nativeData.hasUnreadableSavedData() && !nativeData.isDirty(),
                        "An unchanged current canonical save must remain clean");
                storage.saveAndJoin();
                helper.assertTrue(Arrays.equals(modernBytes, Files.readAllBytes(canonical)),
                        "Unchanged current mail must not rewrite its saved-data file");
                assertSingleMailPage(helper, nativeData, "legacy mail");
            }
        } finally {
            deleteMailFixture(folder, flat, canonical);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void optionalColdLookupDoesNotCreateMissingDataAndPrefersCanonical(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-optional-");
        UUID uuid = UUID.randomUUID();
        Path flat = folder.resolve("mca_player_" + uuid + ".dat");
        Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
        try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
            helper.assertTrue(PlayerSaveData.loadIfPresentSafely(world, uuid, storage, folder).isEmpty(),
                    "Optional lookup must not create missing player data");
            storage.saveAndJoin();
            helper.assertTrue(!Files.exists(flat) && !Files.exists(canonical), "Optional miss wrote a player save");

            Files.createDirectories(canonical.getParent());
            NbtIo.writeCompressed(normalizedPlayerFixture(world, uuid, "flat"), flat);
            NbtIo.writeCompressed(normalizedPlayerFixture(world, uuid, "canonical"), canonical);
            byte[] flatBytes = Files.readAllBytes(flat);
            PlayerSaveData found = PlayerSaveData.loadIfPresentSafely(world, uuid, storage, folder).orElse(null);
            helper.assertTrue(found != null && "canonical".equals(found.getChatAIPrompt()),
                    "A prior optional miss must not hide a newly present canonical save or prefer legacy data");
            String expectedPath = "dimensions/minecraft/overworld/data/mca/mca_player_" + uuid + ".dat";
            helper.assertTrue(expectedPath.equals(PlayerSaveData.getRelativeSavePath(uuid)),
                    "Default player save path must be world-relative: " + PlayerSaveData.getRelativeSavePath(uuid));
            helper.assertTrue(expectedPath.equals(found.getRelativeSavePath()),
                    "Healthy canonical player save path must be world-relative: " + found.getRelativeSavePath());
            helper.assertTrue(PlayerSaveData.loadSafely(world, uuid, storage, folder) == found,
                    "Required lookup must reuse the optional cold-loaded record");
            storage.saveAndJoin();
            helper.assertTrue(Arrays.equals(flatBytes, Files.readAllBytes(flat)), "Canonical lookup rewrote legacy data");
        } finally {
            deleteMailFixture(folder, flat, canonical);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void optionalColdLookupMigratesHealthyLegacyMail(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-optional-legacy-");
        UUID uuid = UUID.randomUUID();
        Path flat = folder.resolve("mca_player_" + uuid + ".dat");
        Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
        try {
            CompoundTag root = mailFixture(List.of("\"optional legacy mail\""));
            root.putInt("DataVersion", 3955);
            root.getCompound("data").orElseThrow().putString("chatAIPrompt", "legacy");
            NbtIo.writeCompressed(root, flat);
            byte[] original = Files.readAllBytes(flat);
            try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData player = PlayerSaveData.loadIfPresentSafely(world, uuid, storage, folder).orElse(null);
                helper.assertTrue(player != null && !player.hasUnreadableSavedData() && "legacy".equals(player.getChatAIPrompt()),
                        "Optional lookup must load a healthy legacy record from disk");
                helper.assertTrue(PlayerSaveData.loadSafely(world, uuid, storage, folder) == player,
                        "Required lookup must reuse the optional legacy record");
                storage.saveAndJoin();
                assertSingleMailPage(helper, player, "optional legacy mail");
            }
            helper.assertTrue(Files.isRegularFile(canonical) && Arrays.equals(original, Files.readAllBytes(flat)),
                    "Optional legacy migration must create canonical data without altering the original");
            try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData player = PlayerSaveData.loadIfPresentSafely(world, uuid, storage, folder).orElseThrow();
                assertSingleMailPage(helper, player, "optional legacy mail");
            }
        } finally {
            deleteMailFixture(folder, flat, canonical);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void optionalCorruptOnlyLookupDoesNotCreateReplacement(GameTestHelper helper) throws IOException {
        for (boolean namespaced : new boolean[]{false, true}) {
            ServerLevel world = helper.getLevel().getServer().overworld();
            Path folder = Files.createTempDirectory("mca-player-optional-corrupt-");
            UUID uuid = UUID.randomUUID();
            Path flat = folder.resolve("mca_player_" + uuid + ".dat");
            Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
            Path source = namespaced ? canonical : flat;
            byte[] original = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
            try {
                Files.createDirectories(source.getParent());
                Files.write(source, original);
                try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                    helper.assertTrue(PlayerSaveData.loadIfPresentSafely(world, uuid, storage, folder).isEmpty(),
                            "Corrupt-only optional lookup must return empty, not a replacement record");
                    storage.saveAndJoin();
                    helper.assertTrue(Arrays.equals(original, Files.readAllBytes(source)) && (namespaced || !Files.exists(canonical)),
                            "Optional corrupt lookup changed the original or generated a canonical replacement");
                    PlayerSaveData required = PlayerSaveData.loadSafely(world, uuid, storage, folder);
                    helper.assertTrue(required.hasUnreadableSavedData(), "Required lookup must quarantine the corrupt save after an optional miss");
                    required.setDirty();
                    required.setDirty(true);
                    helper.assertTrue(!required.isDirty(), "Quarantined save accepted a dirty request");
                    storage.saveAndJoin();
                    helper.assertTrue(Arrays.equals(original, Files.readAllBytes(source)) && (namespaced || !Files.exists(canonical)),
                            "Required lookup rewrote the corrupt candidate after an optional miss");
                    String expectedPath = "dimensions/minecraft/overworld/data/" + (namespaced ? "mca/" : "")
                            + "mca_player_" + uuid + ".dat";
                    helper.assertTrue(expectedPath.equals(required.getRelativeSavePath()),
                            "Corrupt player save warning must identify the actual world-relative candidate: " + required.getRelativeSavePath());
                }
            } finally {
                deleteMailFixture(folder, flat, canonical);
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void optionalLegacyFallbackPreservesCorruptCanonical(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-optional-fallback-");
        UUID uuid = UUID.randomUUID();
        Path flat = folder.resolve("mca_player_" + uuid + ".dat");
        Path canonical = folder.resolve("mca/mca_player_" + uuid + ".dat");
        byte[] corrupt = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        try {
            CompoundTag root = mailFixture(List.of("\"quarantined legacy mail\""));
            root.putInt("DataVersion", 3955);
            root.getCompound("data").orElseThrow().putString("chatAIPrompt", "readable-fallback");
            NbtIo.writeCompressed(root, flat);
            byte[] original = Files.readAllBytes(flat);
            Files.createDirectories(canonical.getParent());
            Files.write(canonical, corrupt);
            try (SavedDataStorage storage = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData fallback = PlayerSaveData.loadIfPresentSafely(world, uuid, storage, folder).orElse(null);
                helper.assertTrue(fallback != null && fallback.hasUnreadableSavedData()
                                && "readable-fallback".equals(fallback.getChatAIPrompt()),
                        "Optional lookup must retain healthy legacy data but quarantine the earlier corrupt canonical candidate");
                helper.assertTrue(PlayerSaveData.loadSafely(world, uuid, storage, folder) == fallback,
                        "Required lookup replaced the quarantined fallback record");
                fallback.setDirty();
                fallback.setDirty(true);
                helper.assertTrue(!fallback.isDirty(), "Migration dirtiness must not defeat quarantine");
                storage.saveAndJoin();
                helper.assertTrue(Arrays.equals(original, Files.readAllBytes(flat)) && Arrays.equals(corrupt, Files.readAllBytes(canonical)),
                        "Quarantined fallback changed either saved-data candidate");
                assertSingleMailPage(helper, fallback, "quarantined legacy mail");
                String expectedPath = "dimensions/minecraft/overworld/data/mca/mca_player_" + uuid + ".dat";
                helper.assertTrue(expectedPath.equals(fallback.getRelativeSavePath()),
                        "Fallback warning must identify the corrupt canonical world-relative candidate: " + fallback.getRelativeSavePath());
            }
        } finally {
            deleteMailFixture(folder, flat, canonical);
        }
        helper.succeed();
    }

    private static CompoundTag normalizedPlayerFixture(ServerLevel world, UUID uuid, String prompt) {
        CompoundTag payload = new PlayerSaveData(world, uuid).save(new CompoundTag(), world.registryAccess());
        payload.putString("chatAIPrompt", prompt);
        CompoundTag root = new CompoundTag();
        root.put("data", payload);
        NbtUtils.addCurrentDataVersion(root);
        return root;
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyWorldRootPlayerDataSurvivesLayoutUpgrade(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path root = Files.createTempDirectory("mca-old-player-layout-");
        UUID uuid = UUID.randomUUID();
        String filename = "mca_player_" + uuid + ".dat";
        Path oldFile = root.resolve("data").resolve(filename);
        Path currentFolder = root.resolve("dimensions/minecraft/overworld/data");
        Path canonical = currentFolder.resolve("mca").resolve(filename);
        try {
            CompoundTag oldData = normalizedPlayerFixture(world, uuid, "old world prompt");
            oldData.putInt("DataVersion", 3955);
            byte[] original = writeLayoutFixture(oldFile, oldData);
            Files.createDirectories(currentFolder);
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData loaded = PlayerSaveData.loadIfPresentSafely(world, uuid, storage, currentFolder).orElse(null);
                helper.assertTrue(loaded != null && "old world prompt".equals(loaded.getChatAIPrompt()),
                        "A cold optional lookup must find the old overworld data directory");
                helper.assertTrue(PlayerSaveData.loadSafely(world, uuid, storage, currentFolder) == loaded,
                        "Required lookup must reuse the recovered player data");
                loaded.setChatAIPrompt("new world prompt");
                storage.saveAndJoin();
                helper.assertTrue(Files.isRegularFile(canonical), "Saving recovered player data must use the current namespaced path");
                helper.assertTrue(Arrays.equals(original, Files.readAllBytes(oldFile)), "Saving recovered data changed the old physical file");
            }
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData reloaded = PlayerSaveData.loadSafely(world, uuid, storage, currentFolder);
                helper.assertTrue("new world prompt".equals(reloaded.getChatAIPrompt()), "Reload must prefer the newly saved canonical file");
            }
        } finally {
            deleteLayoutFixture(root);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void currentPlayerDataWinsOverOldWorldRootData(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path root = Files.createTempDirectory("mca-player-layout-priority-");
        UUID uuid = UUID.randomUUID();
        String filename = "mca_player_" + uuid + ".dat";
        Path oldFile = root.resolve("data").resolve(filename);
        Path currentFolder = root.resolve("dimensions/minecraft/overworld/data");
        Path canonical = currentFolder.resolve("mca").resolve(filename);
        try {
            byte[] oldBytes = writeLayoutFixture(oldFile, normalizedPlayerFixture(world, uuid, "old"));
            byte[] currentBytes = writeLayoutFixture(canonical, normalizedPlayerFixture(world, uuid, "current"));
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData loaded = PlayerSaveData.loadSafely(world, uuid, storage, currentFolder);
                helper.assertTrue("current".equals(loaded.getChatAIPrompt()) && !loaded.hasUnreadableSavedData(),
                        "Existing canonical player data must take precedence over old world data");
                storage.saveAndJoin();
                helper.assertTrue(Arrays.equals(oldBytes, Files.readAllBytes(oldFile)) && Arrays.equals(currentBytes, Files.readAllBytes(canonical)),
                        "Reading canonical player data must preserve both physical files");
            }
        } finally {
            deleteLayoutFixture(root);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void corruptCanonicalPlayerDataFallsBackToOldWorldRootWithoutOverwrite(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path root = Files.createTempDirectory("mca-player-layout-quarantine-");
        UUID uuid = UUID.randomUUID();
        String filename = "mca_player_" + uuid + ".dat";
        Path oldFile = root.resolve("data").resolve(filename);
        Path currentFolder = root.resolve("dimensions/minecraft/overworld/data");
        Path canonical = currentFolder.resolve("mca").resolve(filename);
        byte[] corrupt = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        try {
            byte[] original = writeLayoutFixture(oldFile, normalizedPlayerFixture(world, uuid, "recoverable old prompt"));
            Files.createDirectories(canonical.getParent());
            Files.write(canonical, corrupt);
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                PlayerSaveData loaded = PlayerSaveData.loadIfPresentSafely(world, uuid, storage, currentFolder).orElse(null);
                helper.assertTrue(loaded != null && "recoverable old prompt".equals(loaded.getChatAIPrompt()),
                        "An unreadable canonical candidate must not hide healthy data in the old physical directory");
                helper.assertTrue(loaded.hasUnreadableSavedData(), "A recovered player must remain quarantined after canonical corruption");
                helper.assertTrue(("dimensions/minecraft/overworld/data/mca/" + filename).equals(loaded.getRelativeSavePath()),
                        "Recovery must identify the corrupt canonical path: " + loaded.getRelativeSavePath());
                loaded.setChatAIPrompt("must not overwrite");
                loaded.setDirty(true);
                storage.saveAndJoin();
                helper.assertTrue(Arrays.equals(original, Files.readAllBytes(oldFile)) && Arrays.equals(corrupt, Files.readAllBytes(canonical)),
                        "Quarantined recovery must preserve both candidates");
            }
        } finally {
            deleteLayoutFixture(root);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void corruptOldWorldRootPlayerDataDoesNotCreateCanonicalReplacement(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path root = Files.createTempDirectory("mca-player-old-layout-corrupt-");
        UUID uuid = UUID.randomUUID();
        String filename = "mca_player_" + uuid + ".dat";
        Path oldFile = root.resolve("data").resolve(filename);
        Path currentFolder = root.resolve("dimensions/minecraft/overworld/data");
        Path canonical = currentFolder.resolve("mca").resolve(filename);
        byte[] corrupt = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        try {
            Files.createDirectories(oldFile.getParent());
            Files.write(oldFile, corrupt);
            Files.createDirectories(currentFolder);
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                helper.assertTrue(PlayerSaveData.loadIfPresentSafely(world, uuid, storage, currentFolder).isEmpty(),
                        "Optional lookup must not invent a player from corrupt old data");
                PlayerSaveData loaded = PlayerSaveData.loadSafely(world, uuid, storage, currentFolder);
                helper.assertTrue(loaded.hasUnreadableSavedData(), "Corruption in the old physical directory must quarantine required lookup");
                helper.assertTrue(("data/" + filename).equals(loaded.getRelativeSavePath()),
                        "Warning must identify the actual old world-relative file: " + loaded.getRelativeSavePath());
                loaded.setChatAIPrompt("must not create replacement");
                loaded.setDirty(true);
                storage.saveAndJoin();
                helper.assertTrue(!Files.exists(canonical) && Arrays.equals(corrupt, Files.readAllBytes(oldFile)),
                        "Corrupt old player data must not produce a replacement or be modified");
            }
        } finally {
            deleteLayoutFixture(root);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyOverworldVillageDataSurvivesLayoutUpgrade(GameTestHelper helper) throws IOException {
        assertOldVillageLayout(helper, helper.getLevel().getServer().overworld(), "data", "overworld");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyNetherVillageDataSurvivesLayoutUpgrade(GameTestHelper helper) throws IOException {
        assertOldVillageLayout(helper, helper.getLevel().getServer().getLevel(Level.NETHER), "DIM-1/data", "the_nether");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyEndVillageDataSurvivesLayoutUpgrade(GameTestHelper helper) throws IOException {
        assertOldVillageLayout(helper, helper.getLevel().getServer().getLevel(Level.END), "DIM1/data", "the_end");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyWorldRootCustomClothingSurvivesLayoutUpgrade(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path root = Files.createTempDirectory("mca-old-clothing-layout-");
        Path oldFile = root.resolve("data/immersive_library_clothing.dat");
        Path currentFolder = root.resolve("dimensions/minecraft/overworld/data");
        Path canonical = currentFolder.resolve("mca/immersive_library_clothing.dat");
        Clothing clothing = new Clothing("mca:test_old_layout", null, 0, false, Gender.NEUTRAL);
        try {
            CompoundTag payload = new CompoundTag();
            payload.putString(clothing.getIdentifier(), clothing.toJson().toString());
            CompoundTag oldData = new CompoundTag();
            oldData.putInt("DataVersion", 3955);
            oldData.put("data", payload);
            byte[] original = writeLayoutFixture(oldFile, oldData);
            Files.createDirectories(currentFolder);
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                CustomClothingManager.Storage<Clothing> loaded = CustomClothingManager.loadSafely(storage, currentFolder,
                        world.registryAccess(), "immersive_library_clothing", Clothing::new);
                helper.assertTrue(loaded.getEntries().containsKey(clothing.getIdentifier()),
                        "Shared custom clothing lookup must recover data from the old overworld directory");
                loaded.setDirty();
                storage.saveAndJoin();
                helper.assertTrue(Files.isRegularFile(canonical) && Arrays.equals(original, Files.readAllBytes(oldFile)),
                        "Saving recovered clothing must preserve the old physical file");
            }
        } finally {
            deleteLayoutFixture(root);
        }
        helper.succeed();
    }

    private static void assertOldVillageLayout(GameTestHelper helper, ServerLevel world, String oldDirectory, String dimension) throws IOException {
        helper.assertTrue(world != null, "Test world lacks required dimension " + dimension);
        Path root = Files.createTempDirectory("mca-old-village-layout-");
        Path oldFile = root.resolve(oldDirectory).resolve("mca_villages.dat");
        Path currentFolder = root.resolve("dimensions/minecraft").resolve(dimension).resolve("data");
        Path canonical = currentFolder.resolve("mca/mca_villages.dat");
        try {
            CompoundTag payload = new CompoundTag();
            payload.putInt("lastVillageId", 37);
            payload.putInt("lastBuildingId", 42);
            CompoundTag oldData = new CompoundTag();
            oldData.putInt("DataVersion", 3955);
            oldData.put("data", payload);
            byte[] original = writeLayoutFixture(oldFile, oldData);
            Files.createDirectories(currentFolder);
            try (SavedDataStorage storage = new SavedDataStorage(currentFolder, DataFixers.getDataFixer(), world.registryAccess())) {
                VillageManager loaded = VillageManager.loadSafely(world, storage, currentFolder);
                CompoundTag state = loaded.save(new CompoundTag(), world.registryAccess());
                helper.assertTrue(!loaded.hasUnreadableSavedData() && state.getInt("lastVillageId").orElse(-1) == 37
                                && state.getInt("lastBuildingId").orElse(-1) == 42,
                        "Village lookup must recover its own old physical data directory for " + dimension);
                loaded.setDirty();
                storage.saveAndJoin();
                helper.assertTrue(Files.isRegularFile(canonical) && Arrays.equals(original, Files.readAllBytes(oldFile)),
                        "Saving recovered village state must preserve the old physical file for " + dimension);
            }
        } finally {
            deleteLayoutFixture(root);
        }
    }

    private static byte[] writeLayoutFixture(Path path, CompoundTag data) throws IOException {
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(data, path);
        return Files.readAllBytes(path);
    }

    private static void deleteLayoutFixture(Path root) throws IOException {
        List<Path> paths;
        try (var walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static void assertSingleMailPage(GameTestHelper helper, PlayerSaveData player, String expected) {
        ItemStack letter = player.getMail();
        helper.assertTrue(letter != null, "Player lost the stored letter");
        List<Component> pages = letter.get(DataComponentsMCA.BOOK_PAGES);
        helper.assertTrue(pages != null && pages.size() == 1 && Component.literal(expected).equals(pages.getFirst()),
                "Stored literal mail was lost or decoded as JSON source text");
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingVillageDataReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-village-reload-");
        Path savedFile = folder.resolve("mca/mca_villages.dat");
        folder.toFile().deleteOnExit();
        savedFile.toFile().deleteOnExit();
        SavedDataStorage first = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess());
        CompoundTag initialData = new CompoundTag();
        initialData.putInt("lastVillageId", 37);
        initialData.putInt("lastBuildingId", 42);
        CompoundTag root = new CompoundTag();
        root.put("data", initialData);
        NbtUtils.addCurrentDataVersion(root);
        Path legacy = folder.resolve("mca_villages.dat");
        legacy.toFile().deleteOnExit();
        NbtIo.writeCompressed(root, legacy);
        VillageManager initial = VillageManager.loadSafely(world, first, folder);
        initial.setDirty();
        first.saveAndJoin();
        helper.assertTrue(Files.isRegularFile(savedFile), "MCA village data was not written to disk");

        SavedDataStorage second = new SavedDataStorage(folder, DataFixers.getDataFixer(), world.registryAccess());
        VillageManager reloaded = VillageManager.loadSafely(world, second, folder);
        helper.assertTrue(!reloaded.hasUnreadableSavedData(),
                "Saved MCA village data was unreadable with the current loader");
        CompoundTag loadedData = reloaded.save(new CompoundTag(), world.registryAccess());
        helper.assertTrue(loadedData.getInt("lastVillageId").orElse(-1) == 37 && loadedData.getInt("lastBuildingId").orElse(-1) == 42,
                "MCA village state did not round-trip through a fresh disk-backed storage");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villageSaveLocationShownToOperatorsIsWorldRelative(GameTestHelper helper) {
        String path = VillageManager.getRelativeSavePath(helper.getLevel().getServer().overworld());
        helper.assertTrue("dimensions/minecraft/overworld/data/mca/mca_villages.dat".equals(path),
                "The village recovery message must use a world-relative path, not an absolute server path: " + path);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingCustomClothingReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        assertCustomSkinReloads(helper, "immersive_library_clothing",
                new Clothing("mca:test_clothing", null, 0, false, Gender.NEUTRAL), Clothing::new);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingCustomHairReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        assertCustomSkinReloads(helper, "immersive_library_hair", new Hair("mca:test_hair"), Hair::new);
        helper.succeed();
    }

    private static <T extends SkinListEntry> void assertCustomSkinReloads(GameTestHelper helper,
            String dataId, T entry, BiFunction<String, JsonObject, T> parser) throws IOException {
        Path folder = Files.createTempDirectory("mca-skin-reload-");
        folder.toFile().deleteOnExit();
        folder.resolve("mca/" + dataId + ".dat").toFile().deleteOnExit();
        SavedDataStorage first = new SavedDataStorage(folder, DataFixers.getDataFixer(),
                helper.getLevel().registryAccess());
        CustomClothingManager.Storage<T> original = CustomClothingManager.loadSafely(first, folder, helper.getLevel().registryAccess(), dataId, parser);
        original.addEntry(entry.getIdentifier(), entry);
        first.saveAndJoin();

        SavedDataStorage second = new SavedDataStorage(folder, DataFixers.getDataFixer(),
                helper.getLevel().registryAccess());
        CustomClothingManager.Storage<T> reloaded = CustomClothingManager.loadSafely(second, folder, helper.getLevel().registryAccess(), dataId, parser);
        helper.assertTrue(reloaded.getEntries().containsKey(entry.getIdentifier()),
                "MCA custom skin data " + dataId + " was not loaded from disk");
    }
}
