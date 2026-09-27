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

import java.net.URI;
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

    private ConfigScreenSearch() {
    }

    public static Screen createSection(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        return new SearchableConfigurationSectionScreen(parent, type, modConfig, title);
    }

    static EntryLayout entryLayout(int screenWidth) {
        int rowWidth = Math.min(MAX_ROW_WIDTH, Math.max(VANILLA_ROW_WIDTH, screenWidth - SCREEN_MARGIN));
        int valueWidth = Button.DEFAULT_WIDTH;
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

    static Component booleanLabel(boolean value) {
        return value
                ? Component.literal("TRUE").withColor(CommonColors.GREEN)
                : Component.literal("FALSE").withColor(CommonColors.SOFT_RED);
    }

    static boolean isListDoneEnabled(boolean nativeListValid, boolean allDraftsValid) {
        return nativeListValid && allDraftsValid;
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

    static boolean isEditorListEntryValid(
            String key,
            String value,
            Predicate<String> nativeValidator,
            RegistryAccess registries,
            Set<ResourceLocation> dimensions
    ) {
        return nativeValidator.test(value)
                && isRegistryBackedEntryValid(key, value, registries, dimensions);
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
            Component tooltip = super.getTooltipComponent(key, range);
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
            header.addChild(new StringWidget(title, font));

            int searchLineWidth = Math.min(Math.max(200, font.width(title)), Math.max(200, width - 80));
            LinearLayout searchLine = header.addChild(LinearLayout.horizontal().spacing(4));
            searchLine.addChild(new StringWidget(18, 18, Component.empty(), font));
            EditBox searchBox = searchLine.addChild(new EditBox(font, searchLineWidth, 18, Component.translatable("mca.configuration.search")));
            searchBox.setMaxLength(128);
            searchBox.setValue(searchState.query);
            searchBox.setHint(SEARCH_HINT);
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
                                                    valueList).prepare())))
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
            int matchingEntries = countEntries(context.entries(), context.valueSpecs(), false);
            if (searchState.normalizedQuery.isBlank()) {
                return context.keylist().isEmpty()
                        ? Component.translatable("mca.configuration.search.entries_total", matchingEntries)
                        : Component.translatable("mca.configuration.search.entries_category", matchingEntries);
            }

            return context.keylist().isEmpty()
                    ? Component.translatable("mca.configuration.search.results_total", matchingEntries)
                    : Component.translatable("mca.configuration.search.results_category", matchingEntries);
        }

        private boolean matchesEntry(String key, Object rawValue, Object valueSpec) {
            net.neoforged.neoforge.common.ModConfigSpec.Range<?> range = valueSpec instanceof net.neoforged.neoforge.common.ModConfigSpec.Range<?> candidate
                    ? candidate
                    : null;
            Component name = rawValue instanceof UnmodifiableConfig
                    ? getTranslationComponent(key)
                    : Component.translatable(getTranslationKey(key));
            Component tooltip = getTooltipComponent(key, range);
            return matchesSearch(searchState.normalizedQuery, key, name, tooltip, rawValue);
        }

        @SuppressWarnings("deprecation")
        private int countSectionMatches(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
            if (searchState.normalizedQuery.isBlank()) {
                return countEntries(subsection.entrySet(), subconfig.valueMap(), false);
            }

            if (matchesEntry(key, subconfig, subsection)) {
                return countEntries(subsection.entrySet(), subconfig.valueMap(), true);
            }

            return countEntries(subsection.entrySet(), subconfig.valueMap(), false);
        }

        private int countEntries(Set<? extends UnmodifiableConfig.Entry> entries, Map<String, Object> valueSpecs, boolean includeAll) {
            int count = 0;
            for (UnmodifiableConfig.Entry entry : entries) {
                count += countEntry(entry.getKey(), entry.getRawValue(), valueSpecs.get(entry.getKey()), includeAll);
            }
            return count;
        }

        @SuppressWarnings("deprecation")
        private int countEntry(String key, Object rawValue, Object valueSpec, boolean includeAll) {
            if (rawValue instanceof UnmodifiableConfig nestedSection && valueSpec instanceof UnmodifiableConfig nestedSpec) {
                if (includeAll || matchesEntry(key, rawValue, valueSpec)) {
                    return countEntries(nestedSection.entrySet(), nestedSpec.valueMap(), true);
                }
                return countEntries(nestedSection.entrySet(), nestedSpec.valueMap(), false);
            }

            return includeAll || matchesEntry(key, rawValue, valueSpec) ? 1 : 0;
        }
    }

    private static final class RegistryValidatingConfigurationListScreen<T> extends ConfigurationScreen.ConfigurationListScreen<T> {
        private final Map<Integer, Boolean> draftValidity = new HashMap<>();

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
        protected RegistryValidatingConfigurationListScreen<T> rebuild() {
            draftValidity.clear();
            super.rebuild();
            return this;
        }

        private RegistryValidatingConfigurationListScreen<T> prepare() {
            rebuild();
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Element createStringListValue(int index, String value) {
            return createStringValue(
                    key,
                    candidate -> {
                        boolean valid = isValidStringEntry(candidate);
                        draftValidity.put(index, valid);
                        return valid;
                    },
                    () -> value,
                    newValue -> cfgList.set(index, (T) newValue));
        }

        @Override
        protected void setResetButtonstate(boolean state) {
            super.setResetButtonstate(state);
            long stringEntries = cfgList.stream().filter(String.class::isInstance).count();
            boolean allDraftsValid = draftValidity.size() == stringEntries
                    && draftValidity.values().stream().allMatch(Boolean::booleanValue);
            doneButton.active = isListDoneEnabled(doneButton.active, allDraftsValid);
        }

        private boolean isValidStringEntry(String value) {
            Predicate<String> nativeValidator = candidate -> spec.test(List.of(candidate));
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return isEditorListEntryValid(key, value, nativeValidator, null, null);
            }

            Set<ResourceLocation> dimensions = connection.levels().stream()
                    .map(level -> level.location())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return isEditorListEntryValid(key, value, nativeValidator, connection.registryAccess(), dimensions);
        }
    }

    private static final class WideOptionsList extends OptionsList {
        private WideOptionsList(Minecraft minecraft, int width, SearchableConfigurationSectionScreen screen) {
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
            if (original == null || searchState.normalizedQuery.isBlank()) {
                return original;
            }

            Object value = original.option() == null ? null : original.option().get();
            return matchesSearch(searchState.normalizedQuery, key, original.name(), original.tooltip(), value) ? original : null;
        }
    }

    private static String searchableValue(Object value) {
        if (value == null || value instanceof UnmodifiableConfig) {
            return "";
        }
        if (value instanceof Component component) {
            return component.getString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "true on" : "false off";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Enum<?>) {
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
