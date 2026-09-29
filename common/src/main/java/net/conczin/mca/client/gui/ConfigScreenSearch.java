package net.conczin.mca.client.gui;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.CommonColors;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ConfigScreenSearch {
    private static final Style SEARCH_HINT_STYLE = Style.EMPTY.applyFormats(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    private static final Component SEARCH_HINT = Component.translatable("mca.configuration.search").withStyle(SEARCH_HINT_STYLE);
    private static final ResourceLocation CLEAR_SEARCH_SPRITE = ResourceLocation.withDefaultNamespace("widget/cross_button");
    private static final String SECTION = "neoforge.configuration.uitext.section";
    private static final String SECTION_TEXT = "neoforge.configuration.uitext.sectiontext";
    private static final String VILLAGER_AI_REFERENCE_KEY = "_read_this_before_using_villager_ai";
    private static final int COLUMN_GAP = 10;
    private static final int VANILLA_ROW_WIDTH = Button.DEFAULT_WIDTH * 2 + COLUMN_GAP;
    private static final int MAX_ROW_WIDTH = 600;
    private static final int SCREEN_MARGIN = 40;
    private static final Set<String> MAP_LISTS = Set.of(
            "shaderLocationsMap", "playerRendererBlacklist", "destinyLocationsToTranslationMap", "enabledTraits",
            "professionConversionsMap", "guardEquipment", "archerEquipment", "guardsTargetEntities", "taxesMap", "maxTreeTicks");
    private static final Set<String> RESOURCE_ID_LISTS = Set.of(
            "moddedVillagerWhitelist", "moddedZombieVillagerWhitelist", "villagerDimensionBlacklist",
            "villagerInteractionItemBlacklist", "validTreeSources", "unSafeBlocksToTeleportOn");

    private ConfigScreenSearch() {
    }

    public static Screen createSection(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        return new SearchableConfigurationSectionScreen(parent, type, modConfig, title);
    }

    static EntryLayout entryLayout(int screenWidth) {
        int rowWidth = Math.min(MAX_ROW_WIDTH, Math.max(VANILLA_ROW_WIDTH, screenWidth - SCREEN_MARGIN));
        int valueWidth = Button.DEFAULT_WIDTH + (rowWidth - VANILLA_ROW_WIDTH) * 2 / 5;
        int labelWidth = rowWidth - COLUMN_GAP - valueWidth;
        int labelX = (screenWidth - rowWidth) / 2;
        return new EntryLayout(labelX, labelWidth, labelX + labelWidth + COLUMN_GAP, valueWidth);
    }

    record EntryLayout(int labelX, int labelWidth, int valueX, int valueWidth) {
        int rowWidth() {
            return labelWidth + COLUMN_GAP + valueWidth;
        }
    }

    static boolean matchesSearch(String query, String key, Component name, Component tooltip, Object value) {
        String haystack = normalize(key + " " + componentText(name) + " " + componentText(tooltip) + " " + searchableValue(value));
        return haystack.contains(normalize(query));
    }

    static boolean matchesSpecEntry(String query, ModConfigSpec modSpec, List<String> parentPath,
                                    String key, Object value, Object rawValueSpec) {
        List<String> path = new ArrayList<>(parentPath);
        path.add(key);
        ModConfigSpec.ValueSpec valueSpec = rawValueSpec instanceof ModConfigSpec.ValueSpec candidate ? candidate : null;
        String translation = valueSpec == null ? modSpec.getLevelTranslationKey(path) : valueSpec.getTranslationKey();
        if (translation == null) {
            translation = (value == null ? "mca.configuration.section." : "mca.configuration.") + key;
        }
        String comment = valueSpec == null ? modSpec.getLevelComment(path) : valueSpec.getComment();
        Component name = Component.translatable(translation);
        Component tooltip = Component.translatableWithFallback(translation + ".tooltip", comment == null ? "" : comment);
        return matchesSearch(query, key, name, tooltip, value);
    }

    static Component booleanLabel(boolean value) {
        return value
                ? Component.literal("TRUE").withColor(CommonColors.GREEN)
                : Component.literal("FALSE").withColor(CommonColors.SOFT_RED);
    }

    static Component enumDisplayName(Enum<?> value) {
        if (value instanceof TranslatableEnum translated) {
            return translated.getTranslatedName();
        }
        StringBuilder readable = new StringBuilder();
        for (String word : value.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!readable.isEmpty()) {
                readable.append(' ');
            }
            if (!word.isEmpty()) {
                readable.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return Component.translatableWithFallback(
                "mca.configuration.enum." + value.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT)
                        + "." + value.name().toLowerCase(Locale.ROOT), readable.toString());
    }

    static boolean isListDoneEnabled(boolean nativeListValid, boolean allDraftsValid) {
        return nativeListValid && allDraftsValid;
    }

    static boolean isRemoteServerSettings(ModConfig.Type type, boolean connected, boolean integratedServer) {
        return type == ModConfig.Type.SERVER && connected && !integratedServer;
    }

    private static boolean isRemoteServerSettings(ConfigurationScreen.ConfigurationSectionScreen.Context context) {
        if (context.modConfig() == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        return isRemoteServerSettings(context.modConfig().getType(), client.getConnection() != null,
                client.hasSingleplayerServer());
    }

    static ConfigurationScreen.ConfigurationSectionScreen.Element readOnlyValue(
            ConfigurationScreen.ConfigurationSectionScreen.Element element) {
        Component notice = Component.translatableWithFallback("mca.configuration.read_only",
                "Server-owned setting: read-only on multiplayer clients");
        Component tooltip = element.tooltip() == null ? notice
                : Component.empty().append(element.tooltip()).append("\n\n").append(notice);
        if (element.option() != null) {
            Object value = element.option().get();
            Component display = value instanceof Enum<?> enumValue ? enumDisplayName(enumValue)
                    : value instanceof Boolean bool ? booleanLabel(bool) : Component.literal(String.valueOf(value));
            Button widget = Button.builder(display, ignored -> {})
                    .tooltip(Tooltip.create(tooltip)).width(Button.DEFAULT_WIDTH).build();
            widget.active = false;
            return new ConfigurationScreen.ConfigurationSectionScreen.Element(
                    element.name(), tooltip, widget, false);
        }
        if (element.widget() instanceof EditBox box) {
            box.setEditable(false);
        }
        if (element.widget() != null) {
            element.widget().active = false;
            element.widget().setTooltip(Tooltip.create(tooltip));
        }
        return element;
    }

    static Component listEntryError(String key, String value, List<String> drafts, int index,
                                    Predicate<String> nativeValidator, RegistryAccess registries,
                                    Set<ResourceLocation> dimensions) {
        if (!nativeValidator.test(value)) {
            if (MAP_LISTS.contains(key) && value.indexOf('=') <= 0) {
                return Component.translatableWithFallback("mca.configuration.validation.map", "Expected key=value");
            }
            String selector = value.startsWith("#") ? value.substring(1) : value;
            if (RESOURCE_ID_LISTS.contains(key) && ResourceLocation.tryParse(selector) == null) {
                return Component.translatableWithFallback("mca.configuration.validation.id", "Expected namespace:id");
            }
            return Component.translatableWithFallback("mca.configuration.validation.invalid", "Invalid entry");
        }
        if (!isRegistryBackedEntryValid(key, value, registries, dimensions)) {
            return Component.translatableWithFallback("mca.configuration.validation.registry",
                    "Not found in the current registry");
        }
        if (!MAP_LISTS.contains(key) && !RESOURCE_ID_LISTS.contains(key)) {
            return null;
        }
        String candidate = uniqueListKey(key, value);
        for (int other = 0; other < drafts.size(); other++) {
            if (other != index && candidate.equals(uniqueListKey(key, drafts.get(other)))) {
                return MAP_LISTS.contains(key)
                        ? Component.translatableWithFallback("mca.configuration.validation.duplicate_key", "Duplicate key")
                        : Component.translatableWithFallback("mca.configuration.validation.duplicate", "Duplicate entry");
            }
        }
        return null;
    }

    private static String uniqueListKey(String key, String value) {
        String entry = MAP_LISTS.contains(key) && value.contains("=")
                ? value.substring(0, value.indexOf('=')) : value;
        return entry.trim();
    }

    static net.minecraft.network.chat.MutableComponent withRestartBadge(
            Component name,
            ModConfigSpec.RestartType restartType
    ) {
        net.minecraft.network.chat.MutableComponent result = Component.empty().append(name);
        return switch (restartType) {
            case NONE -> result;
            case WORLD -> result.append(Component.literal(" "))
                    .append(Component.translatableWithFallback("mca.configuration.restart.world", "[WORLD]").withStyle(ChatFormatting.GOLD));
            case GAME -> result.append(Component.literal(" "))
                    .append(Component.translatableWithFallback("mca.configuration.restart.game", "[GAME]").withStyle(ChatFormatting.RED));
        };
    }

    static Component restartExplanation(ModConfigSpec.RestartType restartType) {
        return switch (restartType) {
            case NONE -> Component.empty();
            case WORLD -> Component.translatableWithFallback(
                    "mca.configuration.restart.world.tooltip",
                    "Requires leaving and reopening the world/server before the new value takes effect.").withStyle(ChatFormatting.GOLD);
            case GAME -> Component.translatableWithFallback(
                    "mca.configuration.restart.game.tooltip",
                    "Requires restarting Minecraft before the new value takes effect.").withStyle(ChatFormatting.RED);
        };
    }

    private static boolean isRegistryBackedEntryValid(
            String key,
            String value,
            RegistryAccess registries,
            Set<ResourceLocation> dimensions
    ) {
        return switch (key) {
            case "moddedVillagerWhitelist", "moddedZombieVillagerWhitelist" ->
                    exists(BuiltInRegistries.ENTITY_TYPE, value);
            case "villagerDimensionBlacklist" -> dimensions == null
                    || dimensions.contains(ResourceLocation.tryParse(value));
            case "villagerInteractionItemBlacklist" -> exists(BuiltInRegistries.ITEM, value);
            case "maxTreeTicks" -> mapKeyMatches(value,
                    mapKey -> existsStableEntryOrRuntimeTag(BuiltInRegistries.BLOCK, registries, Registries.BLOCK, mapKey));
            case "validTreeSources", "unSafeBlocksToTeleportOn" ->
                    existsStableEntryOrRuntimeTag(BuiltInRegistries.BLOCK, registries, Registries.BLOCK, value);
            case "guardsTargetEntities" -> mapKeyMatches(value,
                    mapKey -> existsStableEntryOrRuntimeTag(BuiltInRegistries.ENTITY_TYPE, registries, Registries.ENTITY_TYPE, mapKey));
            case "professionConversionsMap" -> professionConversionExists(value);
            case "taxesMap" -> mapKeyMatches(value, mapKey -> exists(BuiltInRegistries.ITEM, mapKey));
            default -> true;
        };
    }

    private static boolean professionConversionExists(String value) {
        int separator = value.indexOf('=');
        if (separator <= 0) {
            return false;
        }
        String source = value.substring(0, separator).trim();
        String target = value.substring(separator + 1).trim();
        return ("default".equals(source) || exists(BuiltInRegistries.VILLAGER_PROFESSION, source))
                && exists(BuiltInRegistries.VILLAGER_PROFESSION, target);
    }

    private static boolean mapKeyMatches(String value, Predicate<String> validator) {
        int separator = value.indexOf('=');
        return separator > 0 && validator.test(value.substring(0, separator).trim());
    }

    private static <T> boolean existsStableEntryOrRuntimeTag(
            Registry<T> registry,
            RegistryAccess registries,
            ResourceKey<? extends Registry<T>> registryKey,
            String selector
    ) {
        if (selector.startsWith("#")) {
            return exists(registries, registryKey, selector, true);
        }
        return exists(registry, selector);
    }

    private static <T> boolean exists(Registry<T> registry, String selector) {
        ResourceLocation id = ResourceLocation.tryParse(selector);
        return id != null && registry.containsKey(id);
    }

    private static <T> boolean exists(
            RegistryAccess registries,
            ResourceKey<? extends Registry<T>> registryKey,
            String selector,
            boolean allowTag
    ) {
        if (registries == null) {
            return true;
        }
        Registry<T> registry = registries.registry(registryKey).orElse(null);
        if (registry == null) {
            return true;
        }

        boolean tag = selector.startsWith("#");
        if (tag && !allowTag) {
            return false;
        }
        String rawId = tag ? selector.substring(1) : selector;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return false;
        }
        return tag
                ? registry.getTag(TagKey.create(registryKey, id)).isPresent()
                : registry.containsKey(id);
    }

    private static final class SearchState {
        private String query = "";
        private String normalizedQuery = "";

        private boolean update(String query) {
            this.query = query == null ? "" : query;
            String previousQuery = normalizedQuery;
            normalizedQuery = normalize(this.query);
            return !previousQuery.equals(normalizedQuery);
        }
    }

    private static final class SearchableConfigurationSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {
        private final SearchState searchState;
        private StringWidget searchSummary;
        private Button clearSearchButton;

        private SearchableConfigurationSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
            this(parent, type, modConfig, title, new SearchState());
        }

        private SearchableConfigurationSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title, SearchState searchState) {
            super(parent, type, modConfig, title, new SearchFilter(searchState));
            this.searchState = searchState;
        }

        private SearchableConfigurationSectionScreen(Context context, Component title, SearchState searchState) {
            super(context, title);
            this.searchState = searchState;
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent getTranslationComponent(String key) {
            ModConfigSpec.ValueSpec spec = getValueSpec(key);
            return withRestartBadge(
                    super.getTranslationComponent(key),
                    spec == null ? ModConfigSpec.RestartType.NONE : spec.restartType());
        }

        @Override
        protected Component getTooltipComponent(String key, ModConfigSpec.Range<?> range) {
            Component tooltip = super.getTooltipComponent(key, null);
            if (range != null) {
                tooltip = Component.empty().append(tooltip).append("\n\n")
                        .append(Component.translatableWithFallback("mca.configuration.range", "Allowed range: %s to %s",
                                range.getMin(), range.getMax()));
            }
            ModConfigSpec.ValueSpec spec = getValueSpec(key);
            if (spec == null || spec.restartType() == ModConfigSpec.RestartType.NONE) {
                return tooltip;
            }
            return Component.empty()
                    .append(tooltip)
                    .append("\n\n")
                    .append(restartExplanation(spec.restartType()));
        }

        @Override
        protected void addContents() {
            list = layout.addToContents(new WideOptionsList(minecraft, width, this));
            addOptions();
        }

        @Override
        protected void addTitle() {
            layout.setHeaderHeight(54);

            LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(3));
            header.defaultCellSetting().alignHorizontallyCenter();
            Component displayTitle = isRemoteServerSettings(context)
                    ? Component.empty().append(title).append(" ")
                            .append(Component.translatableWithFallback("mca.configuration.read_only.badge", "[READ-ONLY]"))
                    : title;
            header.addChild(new StringWidget(displayTitle, font));

            int searchLineWidth = Math.min(Math.max(200, font.width(title)), Math.max(200, width - 80));
            LinearLayout searchLine = header.addChild(LinearLayout.horizontal().spacing(4));
            searchLine.addChild(new StringWidget(18, 18, Component.empty(), font));
            EditBox searchBox = searchLine.addChild(new EditBox(font, searchLineWidth, 18, Component.translatable("mca.configuration.search")));
            searchBox.setMaxLength(128);
            searchBox.setValue(searchState.query);
            searchBox.setHint(SEARCH_HINT);
            searchBox.setTooltip(Tooltip.create(Component.translatable("mca.configuration.search.tooltip")));
            searchBox.setResponder(this::updateSearch);

            SpriteIconButton clearButton = SpriteIconButton.builder(
                            Component.translatable("mca.configuration.search.clear"), button -> searchBox.setValue(""), true)
                    .size(18, 18)
                    .sprite(CLEAR_SEARCH_SPRITE, 14, 14)
                    .build();
            clearButton.setTooltip(Tooltip.create(Component.translatable("mca.configuration.search.clear")));
            clearSearchButton = searchLine.addChild(clearButton);
            clearSearchButton.visible = !searchState.query.isBlank();

            int summaryWidth = Math.max(200, width - 80);
            searchSummary = header.addChild(
                    new StringWidget(summaryWidth, 9, createSearchSummary(), font).alignCenter(),
                    settings -> settings.alignHorizontallyCenter().paddingTop(2));
        }

        @Override
        protected Element createStringValue(String key, Predicate<String> tester, Supplier<String> source, Consumer<String> target) {
            if (!VILLAGER_AI_REFERENCE_KEY.equals(key)) {
                return super.createStringValue(key, tester, source, target);
            }

            Component url = Component.literal(source.get());
            Button wikiButton = Button.builder(
                            Component.translatable("mca.configuration.open_wiki"),
                            button -> Util.getPlatform().openUri(URI.create(source.get())))
                    .tooltip(Tooltip.create(url))
                    .width(Button.DEFAULT_WIDTH)
                    .build();
            return new Element(getTranslationComponent(key), getTooltipComponent(key, null), wikiButton, false);
        }

        @Override
        protected Element createBooleanValue(String key, ModConfigSpec.ValueSpec spec, Supplier<Boolean> source, Consumer<Boolean> target) {
            return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                    new OptionInstance<>(getTranslationKey(key), getTooltip(key, null),
                            (caption, value) -> booleanLabel(value),
                            Custom.BOOLEAN_VALUES_NO_PREFIX,
                            source.get(),
                            newValue -> undoManager.add(v -> {
                                target.accept(v);
                                onChanged(key);
                            }, newValue, v -> {
                                target.accept(v);
                                onChanged(key);
                            }, source.get())));
        }

        @Override
        protected <T extends Enum<T>> Element createEnumValue(String key, ModConfigSpec.ValueSpec spec,
                                                                Supplier<T> source, Consumer<T> target) {
            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) spec.getClazz();
            List<T> values = Arrays.stream(type.getEnumConstants()).filter(spec::test).toList();
            return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                    new OptionInstance<>(getTranslationKey(key), getTooltip(key, null),
                            (caption, value) -> enumDisplayName(value), new Custom<>(values), source.get(),
                            newValue -> undoManager.add(v -> {
                                target.accept(v);
                                onChanged(key);
                            }, newValue, v -> {
                                target.accept(v);
                                onChanged(key);
                            }, source.get())));
        }

        @Override
        protected void setUndoButtonstate(boolean state) {
            super.setUndoButtonstate(state && !isRemoteServerSettings(context));
        }

        @Override
        protected void setResetButtonstate(boolean state) {
            super.setResetButtonstate(state && !isRemoteServerSettings(context));
        }

        @Override
        protected <T> Element createList(String key, ModConfigSpec.ListValueSpec spec, ModConfigSpec.ConfigValue<List<T>> valueList) {
            Component tooltip = getTooltipComponent(key, null);
            return new Element(
                    Component.translatable(SECTION, getTranslationComponent(key)),
                    tooltip,
                    Button.builder(Component.translatable(SECTION_TEXT), button ->
                                    minecraft.setScreen(sectionCache.computeIfAbsent(key, ignored ->
                                            new RegistryValidatingConfigurationListScreen<>(
                                                    Context.list(context, this),
                                                    key,
                                                    Component.empty().append(getTitle()).append(" > ").append(getTranslationComponent(key)),
                                                    spec,
                                                    valueList).rebuild())))
                            .tooltip(Tooltip.create(tooltip))
                            .width(Button.DEFAULT_WIDTH)
                            .build(),
                    false);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
            int matchingEntries = countSectionMatches(key, subconfig, subsection);
            if (subconfig.isEmpty() || matchingEntries == 0) {
                return null;
            }

            Component sectionName = getTranslationComponent(key);
            Component sectionLabel = searchState.normalizedQuery.isBlank()
                    ? sectionName
                    : Component.translatable("mca.configuration.search.category_count", sectionName, matchingEntries);
            Component tooltip = getTooltipComponent(key, null);
            return new Element(sectionLabel, tooltip,
                    Button.builder(Component.translatable(SECTION_TEXT),
                                    button -> minecraft.setScreen(new SearchableConfigurationSectionScreen(
                                            Context.section(context, this, subsection.entrySet(), subconfig.valueMap(), key),
                                            Component.empty().append(getTitle()).append(" > ").append(sectionName),
                                            searchState).rebuild()))
                            .tooltip(Tooltip.create(tooltip))
                            .width(Button.DEFAULT_WIDTH)
                            .build(),
                    false);
        }

        private void updateSearch(String query) {
            boolean queryChanged = searchState.update(query);
            updateClearSearchButton();
            if (!queryChanged) {
                return;
            }

            sectionCache.clear();
            rebuild();
            if (searchSummary != null) {
                searchSummary.setMessage(createSearchSummary());
            }
            if (list != null) {
                list.setScrollAmount(0.0);
            }
        }

        private void updateClearSearchButton() {
            if (clearSearchButton != null) {
                clearSearchButton.visible = !searchState.query.isBlank();
            }
        }

        @SuppressWarnings("deprecation")
        private Component createSearchSummary() {
            int matchingEntries = countEntries(context.entries(), context.valueSpecs(), ancestorMatchesSearch());
            if (searchState.normalizedQuery.isBlank()) {
                return context.keylist().isEmpty()
                        ? Component.translatable("mca.configuration.search.entries_total", matchingEntries)
                        : Component.translatable("mca.configuration.search.entries_category", matchingEntries);
            }

            return context.keylist().isEmpty()
                    ? Component.translatable("mca.configuration.search.results_total", matchingEntries)
                    : Component.translatable("mca.configuration.search.results_category", matchingEntries);
        }

        @SuppressWarnings("deprecation")
        private int countSectionMatches(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
            List<String> sectionPath = new ArrayList<>(context.keylist());
            sectionPath.add(key);
            if (searchState.normalizedQuery.isBlank() || ancestorMatchesSearch()) {
                return countEntries(subsection.entrySet(), subconfig.valueMap(), sectionPath, true);
            }

            if (matchesSpecEntry(searchState.normalizedQuery, context.modSpec(), context.keylist(),
                    key, subsection, subconfig)) {
                return countEntries(subsection.entrySet(), subconfig.valueMap(), sectionPath, true);
            }

            return countEntries(subsection.entrySet(), subconfig.valueMap(), sectionPath, false);
        }

        private boolean ancestorMatchesSearch() {
            return context.filter() instanceof SearchFilter filter && filter.matchesAncestor(context);
        }

        private int countEntries(Set<? extends UnmodifiableConfig.Entry> entries, Map<String, Object> valueSpecs, boolean includeAll) {
            return countEntries(entries, valueSpecs, context.keylist(), includeAll);
        }

        private int countEntries(Set<? extends UnmodifiableConfig.Entry> entries, Map<String, Object> valueSpecs,
                                 List<String> parentPath, boolean includeAll) {
            int count = 0;
            for (UnmodifiableConfig.Entry entry : entries) {
                count += countEntry(entry.getKey(), entry.getRawValue(), valueSpecs.get(entry.getKey()), parentPath, includeAll);
            }
            return count;
        }

        @SuppressWarnings("deprecation")
        private int countEntry(String key, Object rawValue, Object valueSpec, List<String> parentPath, boolean includeAll) {
            if (rawValue instanceof UnmodifiableConfig nestedSection && valueSpec instanceof UnmodifiableConfig nestedSpec) {
                List<String> childPath = new ArrayList<>(parentPath);
                childPath.add(key);
                if (includeAll || matchesSpecEntry(searchState.normalizedQuery, context.modSpec(), parentPath,
                        key, rawValue, valueSpec)) {
                    return countEntries(nestedSection.entrySet(), nestedSpec.valueMap(), true);
                }
                return countEntries(nestedSection.entrySet(), nestedSpec.valueMap(), childPath, false);
            }

            return includeAll || matchesSpecEntry(searchState.normalizedQuery, context.modSpec(), parentPath,
                    key, rawValue, valueSpec) ? 1 : 0;
        }
    }

    private static final class RegistryValidatingConfigurationListScreen<T> extends ConfigurationScreen.ConfigurationListScreen<T> {
        private final Map<Integer, String> drafts = new HashMap<>();
        private final Map<Integer, EditBox> editors = new HashMap<>();

        private RegistryValidatingConfigurationListScreen(
                ConfigurationScreen.ConfigurationSectionScreen.Context context,
                String key,
                Component title,
                ModConfigSpec.ListValueSpec spec,
                ModConfigSpec.ConfigValue<List<T>> valueList
        ) {
            super(context, key, title, spec, valueList);
        }

        @Override
        public RegistryValidatingConfigurationListScreen<T> rebuild() {
            drafts.clear();
            editors.clear();
            super.rebuild();
            return this;
        }

        @Override
        protected void addContents() {
            list = layout.addToContents(new WideOptionsList(minecraft, width, this));
            addOptions();
        }

        @Override
        protected void createAddElementButton() {
            if (cfgList.isEmpty()) {
                Component message = Component.translatable("mca.configuration.list.empty");
                StringWidget emptyLabel = new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, message, font).alignLeft();
                emptyLabel.setTooltip(Tooltip.create(message));
                list.addSmall(emptyLabel, null);
            }
            super.createAddElementButton();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Element createStringListValue(int index, String value) {
            drafts.put(index, value);
            Element element = createStringValue(
                    key,
                    candidate -> {
                        drafts.put(index, candidate);
                        return entryError(index, candidate) == null;
                    },
                    () -> value,
                    newValue -> cfgList.set(index, (T) newValue));
            if (element != null && element.widget() instanceof EditBox box) {
                editors.put(index, box);
            }
            return element;
        }

        @Override
        protected void setResetButtonstate(boolean state) {
            List<String> current = currentDrafts();
            boolean allDraftsValid = drafts.size() == cfgList.stream().filter(String.class::isInstance).count();
            for (Map.Entry<Integer, String> draft : drafts.entrySet()) {
                Component error = entryError(draft.getKey(), draft.getValue(), current);
                allDraftsValid &= error == null;
                EditBox editor = editors.get(draft.getKey());
                if (editor != null) {
                    editor.setTextColor(error == null ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF0000);
                    Component tooltip = getTooltipComponent(key, null);
                    if (error != null) {
                        tooltip = Component.empty().append(tooltip).append("\n\n").append(error);
                    }
                    editor.setTooltip(Tooltip.create(tooltip));
                }
            }
            super.setResetButtonstate(state || !allDraftsValid);
            doneButton.active = isListDoneEnabled(doneButton.active, allDraftsValid);
        }

        @Override
        public void onClose() {
            List<String> current = currentDrafts();
            for (Map.Entry<Integer, String> draft : drafts.entrySet()) {
                if (entryError(draft.getKey(), draft.getValue(), current) != null) {
                    // Escape discards an invalid editing session; it never commits the stale backing list.
                    changed = false;
                    break;
                }
            }
            super.onClose();
        }

        private Component entryError(int index, String value) {
            return entryError(index, value, currentDrafts());
        }

        private Component entryError(int index, String value, List<String> current) {
            Predicate<String> nativeValidator = candidate -> spec.test(List.of(candidate));
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return listEntryError(key, value, current, index, nativeValidator, null, null);
            }

            Set<ResourceLocation> dimensions = connection.levels().stream()
                    .map(level -> level.location())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return listEntryError(key, value, current, index, nativeValidator,
                    connection.registryAccess(), dimensions);
        }

        private List<String> currentDrafts() {
            List<String> values = new ArrayList<>(cfgList.size());
            for (int index = 0; index < cfgList.size(); index++) {
                Object value = cfgList.get(index);
                values.add(drafts.getOrDefault(index, value instanceof String text ? text : ""));
            }
            return values;
        }
    }

    private static final class WideOptionsList extends OptionsList {
        private WideOptionsList(Minecraft minecraft, int width, ConfigurationScreen.ConfigurationSectionScreen screen) {
            super(minecraft, width, screen);
        }

        @Override
        public int getRowWidth() {
            return entryLayout(width).rowWidth();
        }

        @Override
        protected void renderItem(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int index,
                                  int rowLeft, int rowTop, int rowWidth, int rowHeight) {
            var entry = getEntry(index);
            var children = entry.children();
            if (children.size() != 2
                    || !(children.get(0) instanceof AbstractWidget label)
                    || !(children.get(1) instanceof AbstractWidget value)) {
                super.renderItem(graphics, mouseX, mouseY, partialTick, index, rowLeft, rowTop, rowWidth, rowHeight);
                return;
            }

            EntryLayout layout = entryLayout(width);
            label.setWidth(layout.labelWidth());
            label.setPosition(layout.labelX(), rowTop);
            value.setWidth(layout.valueWidth());
            value.setPosition(layout.valueX(), rowTop);
            label.render(graphics, mouseX, mouseY, partialTick);
            value.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private record SearchFilter(SearchState searchState) implements ConfigurationScreen.ConfigurationSectionScreen.Filter {
        @Override
        public ConfigurationScreen.ConfigurationSectionScreen.Element filterEntry(
                ConfigurationScreen.ConfigurationSectionScreen.Context context,
                String key,
                ConfigurationScreen.ConfigurationSectionScreen.Element original) {
            if (original == null) {
                return original;
            }
            Object value = searchableValue(original);
            if (value == null) {
                for (UnmodifiableConfig.Entry entry : context.entries()) {
                    if (entry.getKey().equals(key)) {
                        value = entry.getRawValue();
                        break;
                    }
                }
            }
            if (!searchState.normalizedQuery.isBlank() && !matchesAncestor(context)
                    && !matchesSearch(searchState.normalizedQuery, key, original.name(), original.tooltip(),
                            value)) {
                return null;
            }
            return isRemoteServerSettings(context) ? readOnlyValue(original) : original;
        }

        private boolean matchesAncestor(ConfigurationScreen.ConfigurationSectionScreen.Context context) {
            for (int index = 0; index < context.keylist().size(); index++) {
                String ancestor = context.keylist().get(index);
                if (matchesSpecEntry(searchState.normalizedQuery, context.modSpec(),
                        context.keylist().subList(0, index), ancestor, null, null)
                        || matchesSearch(searchState.normalizedQuery, ancestor.replace('_', ' '),
                                Component.translatable("mca.configuration.section." + ancestor), null, null)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Object searchableValue(ConfigurationScreen.ConfigurationSectionScreen.Element element) {
        if (element.option() != null) {
            return element.option().get();
        }
        if (element.widget() instanceof EditBox box) {
            return box.getValue();
        }
        if (element.widget() instanceof StringWidget label) {
            return label.getMessage();
        }
        return null;
    }

    private static String searchableValue(Object value) {
        if (value == null || value instanceof UnmodifiableConfig) {
            return "";
        }
        if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
            return searchableValue(configValue.getRaw());
        }
        if (value instanceof Component component) {
            return component.getString();
        }
        if (value instanceof Iterable<?> entries) {
            StringBuilder searchable = new StringBuilder();
            for (Object entry : entries) {
                searchable.append(searchableValue(entry)).append(' ');
            }
            return searchable.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "true on" : "false off";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name() + " " + enumDisplayName(enumValue).getString();
        }
        if (value instanceof CharSequence || value instanceof Number) {
            return value.toString();
        }
        return "";
    }

    private static String componentText(Component component) {
        return component == null ? "" : component.getString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
