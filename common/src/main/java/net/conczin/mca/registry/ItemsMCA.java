package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.CivilRegistryBook;
import net.conczin.mca.client.book.pages.CenteredTextPage;
import net.conczin.mca.client.book.pages.DynamicListPage;
import net.conczin.mca.client.book.pages.ScribbleTextPage;
import net.conczin.mca.client.book.pages.TitlePage;
import net.conczin.mca.entity.CribWoodType;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.item.*;
import net.conczin.mca.resources.Supporters;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import java.util.*;
import java.util.stream.Collectors;

public interface ItemsMCA {
    Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();

    Item MALE_VILLAGER_SPAWN_EGG = register("male_villager_spawn_egg", new SpawnEggItem(EntitiesMCA.MALE_VILLAGER, 0x5e9aff, 0x3366bc, baseProps()));
    Item FEMALE_VILLAGER_SPAWN_EGG = register("female_villager_spawn_egg", new SpawnEggItem(EntitiesMCA.FEMALE_VILLAGER, 0xe85ca1, 0xe3368c, baseProps()));

    Item MALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("male_zombie_villager_spawn_egg", new SpawnEggItem(EntitiesMCA.MALE_ZOMBIE_VILLAGER, 0x5ebaff, 0x33a6bc, baseProps()));
    Item FEMALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("female_zombie_villager_spawn_egg", new SpawnEggItem(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, 0xe8aca1, 0xe3a68c, baseProps()));

    Item GRIM_REAPER_SPAWN_EGG = register("grim_reaper_spawn_egg", new SpawnEggItem(EntitiesMCA.GRIM_REAPER, 0x301515, 0x2A1C34, baseProps()));

    Item BABY_BOY = register("baby_boy", new BabyItem(Gender.MALE, baseProps().stacksTo(1)));
    Item BABY_GIRL = register("baby_girl", new BabyItem(Gender.FEMALE, baseProps().stacksTo(1)));
    Item SIRBEN_BABY_BOY = register("sirben_baby_boy", new SirbenBabyItem(Gender.MALE, baseProps().stacksTo(1)));
    Item SIRBEN_BABY_GIRL = register("sirben_baby_girl", new SirbenBabyItem(Gender.FEMALE, baseProps().stacksTo(1)));

    Item WEDDING_RING = register("wedding_ring", new WeddingRingItem(unstackableProps()));
    Item WEDDING_RING_RG = register("wedding_ring_rg", new WeddingRingItem(unstackableProps()));
    Item ENGAGEMENT_RING = register("engagement_ring", new EngagementRingItem(unstackableProps()));
    Item ENGAGEMENT_RING_RG = register("engagement_ring_rg", new EngagementRingItem(unstackableProps()));
    Item MATCHMAKERS_RING = register("matchmakers_ring", new MatchmakersRingItem(baseProps().stacksTo(2)));

    Item VILLAGER_EDITOR = register("villager_editor", new VillagerEditorItem(baseProps()));
    Item STAFF_OF_LIFE = register("staff_of_life", new StaffOfLifeItem(baseProps().durability(10).rarity(Rarity.EPIC)));
    Item WHISTLE = register("whistle", new WhistleItem(baseProps()));
    Item BLUEPRINT = register("blueprint", new BlueprintItem(baseProps()));
    Item FAMILY_TREE = register("family_tree", new FamilyTreeItem(baseProps()));
    Item VILLAGER_TRACKER = register("villager_tracker", new VillagerTrackerItem(baseProps().stacksTo(1)));

    Item SCYTHE = register("scythe", new ScytheItem(baseProps().attributes(SwordItem.createAttributes(Tiers.GOLD, 10, -2.4F))));

    Item BOUQUET = register("bouquet", new BouquetItem(baseProps()));

    Item POTION_OF_FEMININITY = register("potion_of_femininity", new PotionOfMetamorphosisItem(baseProps().stacksTo(1), Gender.FEMALE));
    Item POTION_OF_MASCULINITY = register("potion_of_masculinity", new PotionOfMetamorphosisItem(baseProps().stacksTo(1), Gender.MALE));

    Item NEEDLE_AND_THREAD = register("needle_and_thread", new NeedleAndThreadItem(baseProps().durability(8)));
    Item COMB = register("comb", new CombItem(baseProps().durability(8)));

    Item BOOK_DEATH = register("book_death", new ExtendedWrittenBookItem(baseProps(), new Book("death")
            .setBackground(MCA.locate("textures/gui/books/death.png"))
            .setTextFormatting(ChatFormatting.WHITE)
            .setTextShadow(true)
            .addPage(new TitlePage("death", ChatFormatting.GRAY))
            .addSimplePages(3, 0)
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/test.png"), "death", 3))
            .addSimplePages(9, 4)
    ));

    Item BOOK_ROMANCE = register("book_romance", new ExtendedWrittenBookItem(baseProps(), new Book("romance")
            .setBackground(MCA.locate("textures/gui/books/romance.png"))
            .addPage(new TitlePage("romance"))
            .addSimplePages(10)));

    Item BOOK_FAMILY = register("book_family", new ExtendedWrittenBookItem(baseProps(), new Book("family")
            .setBackground(MCA.locate("textures/gui/books/family.png"))
            .addPage(new TitlePage("family"))
            .addSimplePages(6)));

    Item BOOK_ROSE_GOLD = register("book_rose_gold", new ExtendedWrittenBookItem(baseProps(), new Book("rose_gold")
            .setBackground(MCA.locate("textures/gui/books/rose_gold.png"))
            .addPage(new TitlePage("rose_gold"))
            .addSimplePages(4)));

    Item BOOK_INFECTION = register("book_infection", new ExtendedWrittenBookItem(baseProps(), new Book("infection")
            .setBackground(MCA.locate("textures/gui/books/infection.png"))
            .addPage(new TitlePage("infection"))
            .addSimplePages(6)));

    Item BOOK_BLUEPRINT = register("book_blueprint", new ExtendedWrittenBookItem(baseProps(), new Book("blueprint")
            .setBackground(MCA.locate("textures/gui/books/blueprint.png"))
            .setTextFormatting(ChatFormatting.WHITE)
            .setTextShadow(true)
            .addPage(new TitlePage("blueprint", ChatFormatting.WHITE))
            .addSimplePages(6)));

    Item BOOK_SUPPORTERS = register("book_supporters", new ExtendedWrittenBookItem(baseProps(), new Book("supporters")
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

    Item BOOK_CULT_0 = register("book_cult_0", new ExtendedWrittenBookItem(baseProps(), new Book("cult_0")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_0", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_0", 0))
            .addPage(new CenteredTextPage("cult_0", 1))
            .addPage(new CenteredTextPage("cult_0", 2))
            .addPage(new CenteredTextPage("cult_0", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), Component.literal("")))));

    Item BOOK_CULT_1 = register("book_cult_1", new ExtendedWrittenBookItem(baseProps(), new Book("cult_1")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_1", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_1", 0))
            .addPage(new CenteredTextPage("cult_1", 1))
            .addPage(new CenteredTextPage("cult_1", 2))
            .addPage(new CenteredTextPage("cult_1", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), Component.literal("")))));

    Item BOOK_CULT_2 = register("book_cult_2", new ExtendedWrittenBookItem(baseProps(), new Book("cult_2")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_2", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_2", 0))
            .addPage(new CenteredTextPage("cult_2", 1))
            .addPage(new CenteredTextPage("cult_2", 2))
            .addPage(new CenteredTextPage("cult_2", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), Component.literal("")))));

    Item BOOK_CULT_ANCIENT = register("book_cult_ancient", new ExtendedWrittenBookItem(baseProps(), new Book("cult_ancient")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(ChatFormatting.DARK_RED)
            .addPage(new TitlePage("cult_ancient", ChatFormatting.DARK_RED))
            .addPage(new CenteredTextPage(Component.literal("We are the universe. We are everything you think isn't you. You are looking at us now, through your skin and your eyes. And why does the universe touch your skin, and throw light on you? To see you, player. To know you. And to be known. I shall tell you a story."))
                    .setStyle(Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"))))));

    Item CIVIL_REGISTRY = register("civil_registry", new CivilRegistry(baseProps().stacksTo(1), new CivilRegistryBook("civil_registry", null)
            .setBackground(MCA.locate("textures/gui/books/supporters.png"))));

    Item LETTER = register("letter", new ExtendedWrittenBookItem(baseProps().stacksTo(1), new Book("letter", null)
            .setBackground(MCA.locate("textures/gui/books/paper.png"))));

    Item DIVORCE_PAPERS = register("divorce_papers", new TooltippedItem(baseProps()));

    Item ROSE_GOLD_DUST = register("rose_gold_dust", new Item(baseProps()));
    Item ROSE_GOLD_INGOT = register("rose_gold_ingot", new Item(baseProps()));

    Item ROSE_GOLD_BLOCK = register("rose_gold_block", new BlockItem(BlocksMCA.ROSE_GOLD_BLOCK, baseProps()));

    Item GRAVELLING_HEADSTONE = register("gravelling_headstone", new BlockItem(BlocksMCA.GRAVELLING_HEADSTONE, baseProps()));
    Item UPRIGHT_HEADSTONE = register("upright_headstone", new BlockItem(BlocksMCA.UPRIGHT_HEADSTONE, baseProps()));
    Item SLANTED_HEADSTONE = register("slanted_headstone", new BlockItem(BlocksMCA.SLANTED_HEADSTONE, baseProps()));
    Item CROSS_HEADSTONE = register("cross_headstone", new BlockItem(BlocksMCA.CROSS_HEADSTONE, baseProps()));
    Item WALL_HEADSTONE = register("wall_headstone", new BlockItem(BlocksMCA.WALL_HEADSTONE, baseProps()));
    Item COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", new BlockItem(BlocksMCA.COBBLESTONE_UPRIGHT_HEADSTONE, baseProps()));
    Item COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", new BlockItem(BlocksMCA.COBBLESTONE_SLANTED_HEADSTONE, baseProps()));
    Item WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", new BlockItem(BlocksMCA.WOODEN_UPRIGHT_HEADSTONE, baseProps()));
    Item WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", new BlockItem(BlocksMCA.WOODEN_SLANTED_HEADSTONE, baseProps()));
    Item GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", new BlockItem(BlocksMCA.GOLDEN_UPRIGHT_HEADSTONE, baseProps()));
    Item GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", new BlockItem(BlocksMCA.GOLDEN_SLANTED_HEADSTONE, baseProps()));
    Item DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", new BlockItem(BlocksMCA.DEEPSLATE_UPRIGHT_HEADSTONE, baseProps()));
    Item DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", new BlockItem(BlocksMCA.DEEPSLATE_SLANTED_HEADSTONE, baseProps()));

    List<CribItem> CRIBS = registerAllCribTypes();

    static List<CribItem> registerAllCribTypes() {
        List<CribItem> cribs = new ArrayList<>();

        for (CribWoodType wood : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                cribs.add((CribItem) register(color.getName() + "_" + wood.toString().toLowerCase(Locale.ROOT) + "_crib", new CribItem(unstackableProps(), wood, color)));
            }
        }

        return cribs;
    }

    static Item register(String name, Item item) {
        ITEMS.put(MCA.locate(name), item);
        return item;
    }

    static Item.Properties baseProps() {
        return new Item.Properties();
    }

    static Item.Properties unstackableProps() {
        return baseProps().stacksTo(1);
    }

    static void registerItems(MCA.RegisterHelper<Item> helper) {
        ITEMS.forEach(helper::register);
    }
}
