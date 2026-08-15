package net.conczin.mca.item;

import net.conczin.mca.MCA;
import net.conczin.mca.TagsMCA;
import net.conczin.mca.block.BlocksMCA;
import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.CivilRegistryBook;
import net.conczin.mca.client.book.pages.CenteredTextPage;
import net.conczin.mca.client.book.pages.DynamicListPage;
import net.conczin.mca.client.book.pages.ScribbleTextPage;
import net.conczin.mca.client.book.pages.TitlePage;
import net.conczin.mca.entity.CribWoodType;
import net.conczin.mca.entity.EntitiesMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Supporters;
import net.conczin.mca.util.RegistryRef;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface ItemsMCA {
    Map<ResourceLocation, RegistryRef<Item>> ITEMS = new LinkedHashMap<>();

    RegistryRef<Item> MALE_VILLAGER_SPAWN_EGG = register("male_villager_spawn_egg", () -> new SpawnEggItem(EntitiesMCA.MALE_VILLAGER.get(), 0x5e9aff, 0x3366bc, baseProps()));
    RegistryRef<Item> FEMALE_VILLAGER_SPAWN_EGG = register("female_villager_spawn_egg", () -> new SpawnEggItem(EntitiesMCA.FEMALE_VILLAGER.get(), 0xe85ca1, 0xe3368c, baseProps()));

    RegistryRef<Item> MALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("male_zombie_villager_spawn_egg", () -> new SpawnEggItem(EntitiesMCA.MALE_ZOMBIE_VILLAGER.get(), 0x5ebaff, 0x33a6bc, baseProps()));
    RegistryRef<Item> FEMALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("female_zombie_villager_spawn_egg", () -> new SpawnEggItem(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.get(), 0xe8aca1, 0xe3a68c, baseProps()));

    RegistryRef<Item> GRIM_REAPER_SPAWN_EGG = register("grim_reaper_spawn_egg", () -> new SpawnEggItem(EntitiesMCA.GRIM_REAPER.get(), 0x301515, 0x2A1C34, baseProps()));

    RegistryRef<Item> BABY_BOY = register("baby_boy", () -> new BabyItem(Gender.MALE, baseProps().stacksTo(1)));
    RegistryRef<Item> BABY_GIRL = register("baby_girl", () -> new BabyItem(Gender.FEMALE, baseProps().stacksTo(1)));
    RegistryRef<Item> SIRBEN_BABY_BOY = register("sirben_baby_boy", () -> new SirbenBabyItem(Gender.MALE, baseProps().stacksTo(1)));
    RegistryRef<Item> SIRBEN_BABY_GIRL = register("sirben_baby_girl", () -> new SirbenBabyItem(Gender.FEMALE, baseProps().stacksTo(1)));

    RegistryRef<Item> WEDDING_RING = register("wedding_ring", () -> new WeddingRingItem(unstackableProps()));
    RegistryRef<Item> WEDDING_RING_RG = register("wedding_ring_rg", () -> new WeddingRingItem(unstackableProps()));
    RegistryRef<Item> ENGAGEMENT_RING = register("engagement_ring", () -> new EngagementRingItem(unstackableProps()));
    RegistryRef<Item> ENGAGEMENT_RING_RG = register("engagement_ring_rg", () -> new EngagementRingItem(unstackableProps()));
    RegistryRef<Item> MATCHMAKERS_RING = register("matchmakers_ring", () -> new MatchmakersRingItem(baseProps().stacksTo(2)));

    RegistryRef<Item> VILLAGER_EDITOR = register("villager_editor", () -> new VillagerEditorItem(baseProps()));
    RegistryRef<Item> STAFF_OF_LIFE = register("staff_of_life", () -> new StaffOfLifeItem(baseProps().durability(10)));
    RegistryRef<Item> WHISTLE = register("whistle", () -> new WhistleItem(baseProps()));
    RegistryRef<Item> BLUEPRINT = register("blueprint", () -> new BlueprintItem(baseProps()));
    RegistryRef<Item> FAMILY_TREE = register("family_tree", () -> new FamilyTreeItem(baseProps()));
    RegistryRef<Item> VILLAGER_TRACKER = register("villager_tracker", () -> new VillagerTrackerItem(baseProps().stacksTo(1)));

    RegistryRef<Item> SCYTHE = register("scythe", () -> new ScytheItem(baseProps()));

    RegistryRef<Item> BOUQUET = register("bouquet", () -> new BouquetItem(baseProps()));

    RegistryRef<Item> POTION_OF_FEMINITY = register("potion_of_feminity", () -> new PotionOfMetamorphosisItem(baseProps().stacksTo(1), Gender.FEMALE));
    RegistryRef<Item> POTION_OF_MASCULINITY = register("potion_of_masculinity", () -> new PotionOfMetamorphosisItem(baseProps().stacksTo(1), Gender.MALE));

    RegistryRef<Item> NEEDLE_AND_THREAD = register("needle_and_thread", () -> new NeedleAndThreadItem(baseProps().durability(8)));
    RegistryRef<Item> COMB = register("comb", () -> new CombItem(baseProps().durability(8)));

    RegistryRef<Item> BOOK_DEATH = register("book_death", () -> new ExtendedWrittenBookItem(baseProps(), new Book("death")
            .setBackground(MCA.locate("textures/gui/books/death.png"))
            .setTextFormatting(ChatFormatting.WHITE)
            .setTextShadow(true)
            .addPage(new TitlePage("death", ChatFormatting.GRAY))
            .addSimplePages(3, 0)
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/test.png"), "death", 3))
            .addSimplePages(9, 4)
    ));

    RegistryRef<Item> BOOK_ROMANCE = register("book_romance", () -> new ExtendedWrittenBookItem(baseProps(), new Book("romance")
            .setBackground(MCA.locate("textures/gui/books/romance.png"))
            .addPage(new TitlePage("romance"))
            .addSimplePages(10)));

    RegistryRef<Item> BOOK_FAMILY = register("book_family", () -> new ExtendedWrittenBookItem(baseProps(), new Book("family")
            .setBackground(MCA.locate("textures/gui/books/family.png"))
            .addPage(new TitlePage("family"))
            .addSimplePages(6)));

    RegistryRef<Item> BOOK_ROSE_GOLD = register("book_rose_gold", () -> new ExtendedWrittenBookItem(baseProps(), new Book("rose_gold")
            .setBackground(MCA.locate("textures/gui/books/rose_gold.png"))
            .addPage(new TitlePage("rose_gold"))
            .addSimplePages(4)));

    RegistryRef<Item> BOOK_INFECTION = register("book_infection", () -> new ExtendedWrittenBookItem(baseProps(), new Book("infection")
            .setBackground(MCA.locate("textures/gui/books/infection.png"))
            .addPage(new TitlePage("infection"))
            .addSimplePages(6)));

    RegistryRef<Item> BOOK_BLUEPRINT = register("book_blueprint", () -> new ExtendedWrittenBookItem(baseProps(), new Book("blueprint")
            .setBackground(MCA.locate("textures/gui/books/blueprint.png"))
            .setTextFormatting(ChatFormatting.WHITE)
            .setTextShadow(true)
            .addPage(new TitlePage("blueprint", ChatFormatting.WHITE))
            .addSimplePages(6)));

    RegistryRef<Item> BOOK_SUPPORTERS = register("book_supporters", () -> new ExtendedWrittenBookItem(baseProps(), new Book("supporters")
            .setBackground(MCA.locate("textures/gui/books/supporters.png"))
            .addPage(new TitlePage("supporters"))
            .addPage(new DynamicListPage("mca.books.supporters.contributors",
                    page -> Supporters.getSupporterGroup("mca:contributors").stream().map(s -> Component.literal(s).withStyle(ChatFormatting.DARK_GREEN)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.wiki",
                    page -> Supporters.getSupporterGroup("mca:wiki").stream().map(s -> Component.literal(s).withStyle(ChatFormatting.GOLD)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.patrons",
                    page -> Supporters.getSupporterGroup("mca:patrons").stream().map(s -> Component.literal(s).withStyle(ChatFormatting.RED)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.translators",
                    page -> Supporters.getSupporterGroup("mca:translators").stream().map(s -> Component.literal(s).withStyle(ChatFormatting.DARK_BLUE)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.old",
                    page -> Supporters.getSupporterGroup("mca:old").stream().map(s -> Component.literal(s).withStyle(ChatFormatting.BLACK)).collect(Collectors.toList())))
            .addPage(new TitlePage("mca.books.supporters.thanks", ""))));

    RegistryRef<Item> BOOK_CULT_0 = register("book_cult_0", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_0")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_0", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_0", 0))
            .addPage(new CenteredTextPage("cult_0", 1))
            .addPage(new CenteredTextPage("cult_0", 2))
            .addPage(new CenteredTextPage("cult_0", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), ""))));

    RegistryRef<Item> BOOK_CULT_1 = register("book_cult_1", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_1")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_1", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_1", 0))
            .addPage(new CenteredTextPage("cult_1", 1))
            .addPage(new CenteredTextPage("cult_1", 2))
            .addPage(new CenteredTextPage("cult_1", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), ""))));

    RegistryRef<Item> BOOK_CULT_2 = register("book_cult_2", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_2")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_2", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_2", 0))
            .addPage(new CenteredTextPage("cult_2", 1))
            .addPage(new CenteredTextPage("cult_2", 2))
            .addPage(new CenteredTextPage("cult_2", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), ""))));

    RegistryRef<Item> BOOK_CULT_ANCIENT = register("book_cult_ancient", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_ancient")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_ancient", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("We are the universe. We are everything you think isn't you. You are looking at us now, through your skin and your eyes. And why does the universe touch your skin, and throw light on you? To see you, player. To know you. And to be known. I shall tell you a story.")
                    .setStyle(Style.EMPTY.withFont(new ResourceLocation("minecraft", "alt"))))));

    RegistryRef<Item> CIVIL_REGISTRY = register("civil_registry", () -> new CivilRegistry(baseProps().stacksTo(1), new CivilRegistryBook("civil_registry", null)
            .setBackground(MCA.locate("textures/gui/books/supporters.png"))));

    RegistryRef<Item> LETTER = register("letter", () -> new ExtendedWrittenBookItem(baseProps().stacksTo(1), new Book("letter", null)
            .setBackground(MCA.locate("textures/gui/books/paper.png"))));

    RegistryRef<Item> DIVORCE_PAPERS = register("divorce_papers", () -> new TooltippedItem(baseProps()));

    RegistryRef<Item> ROSE_GOLD_DUST = register("rose_gold_dust", () -> new Item(baseProps()));
    RegistryRef<Item> ROSE_GOLD_INGOT = register("rose_gold_ingot", () -> new Item(baseProps()));

    RegistryRef<Item> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new BlockItem(BlocksMCA.ROSE_GOLD_BLOCK.get(), baseProps()));

    RegistryRef<Item> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new BlockItem(BlocksMCA.JEWELER_WORKBENCH.get(), baseProps()));

    RegistryRef<Item> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new BlockItem(BlocksMCA.GRAVELLING_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new BlockItem(BlocksMCA.UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> SLANTED_HEADSTONE = register("slanted_headstone", () -> new BlockItem(BlocksMCA.SLANTED_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> CROSS_HEADSTONE = register("cross_headstone", () -> new BlockItem(BlocksMCA.CROSS_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> WALL_HEADSTONE = register("wall_headstone", () -> new BlockItem(BlocksMCA.WALL_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", () -> new BlockItem(BlocksMCA.COBBLESTONE_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", () -> new BlockItem(BlocksMCA.COBBLESTONE_SLANTED_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", () -> new BlockItem(BlocksMCA.WOODEN_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", () -> new BlockItem(BlocksMCA.WOODEN_SLANTED_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", () -> new BlockItem(BlocksMCA.GOLDEN_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", () -> new BlockItem(BlocksMCA.GOLDEN_SLANTED_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", () -> new BlockItem(BlocksMCA.DEEPSLATE_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistryRef<Item> DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", () -> new BlockItem(BlocksMCA.DEEPSLATE_SLANTED_HEADSTONE.get(), baseProps()));

    List<RegistryRef<Item>> CRIBS = registerAllCribTypes();

    static void bootstrap() {
        TagsMCA.Blocks.bootstrap();
    }

    static List<RegistryRef<Item>> registerAllCribTypes() {
        List<RegistryRef<Item>> cribs = new ArrayList<>();

        for (CribWoodType wood : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                cribs.add(register(color.getName() + "_" + wood.toString().toLowerCase(Locale.ROOT) + "_crib", () -> new CribItem(unstackableProps(), wood, color)));
            }
        }

        return cribs;
    }

    static RegistryRef<Item> register(String name, Supplier<Item> item) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<Item> ref = RegistryRef.of(id, item);
        ITEMS.put(id, ref);
        return ref;
    }

    static Item.Properties baseProps() {
        return new Item.Properties();
    }

    static Item.Properties unstackableProps() {
        return baseProps().stacksTo(1);
    }

    static void registerItems(MCA.RegisterHelper<Item> helper) {
        ITEMS.forEach((id, ref) -> helper.register(id, ref.get()));
    }

    static void registerCreativeModeTab(MCA.RegisterHelper<CreativeModeTab> helper) {
        helper.register(MCA.locate("mca_tab"), CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.mca.mca_tab"))
                .icon(() -> ENGAGEMENT_RING.get().getDefaultInstance())
                .displayItems((parameters, output) -> ITEMS.values().stream()
                        .filter(ref -> ref != SIRBEN_BABY_BOY && ref != SIRBEN_BABY_GIRL)
                        .map(RegistryRef::get)
                        .forEach(output::accept))
                .build());
    }
}
