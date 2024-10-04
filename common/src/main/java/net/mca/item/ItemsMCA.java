package net.mca.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.TagsMCA;
import net.mca.block.BlocksMCA;
import net.mca.client.book.Book;
import net.mca.client.book.CivilRegistryBook;
import net.mca.client.book.pages.CenteredTextPage;
import net.mca.client.book.pages.DynamicListPage;
import net.mca.client.book.pages.ScribbleTextPage;
import net.mca.client.book.pages.TitlePage;
import net.mca.entity.CribWoodType;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.Supporters;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public interface ItemsMCA {
    DeferredRegister<Item> ITEMS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.ITEM);
    DeferredRegister<ItemGroup> ITEM_GROUPS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.ITEM_GROUP);

    RegistrySupplier<ItemGroup> MCA_GROUP = registerTab("mca_tab", () -> CreativeTabRegistry.create(
            Text.translatable("itemGroup.mca.mca_tab"),
            () -> ItemsMCA.ENGAGEMENT_RING.get().getDefaultStack()
    ));

    RegistrySupplier<Item> MALE_VILLAGER_SPAWN_EGG = register("male_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.MALE_VILLAGER, 0x5e9aff, 0x3366bc, baseProps()));
    RegistrySupplier<Item> FEMALE_VILLAGER_SPAWN_EGG = register("female_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.FEMALE_VILLAGER, 0xe85ca1, 0xe3368c, baseProps()));

    RegistrySupplier<Item> MALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("male_zombie_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.MALE_ZOMBIE_VILLAGER, 0x5ebaff, 0x33a6bc, baseProps()));
    RegistrySupplier<Item> FEMALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("female_zombie_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, 0xe8aca1, 0xe3a68c, baseProps()));

    RegistrySupplier<Item> GRIM_REAPER_SPAWN_EGG = register("grim_reaper_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.GRIM_REAPER, 0x301515, 0x2A1C34, baseProps()));

    RegistrySupplier<Item> BABY_BOY = register("baby_boy", () -> new BabyItem(Gender.MALE, baseProps().maxCount(1)));
    RegistrySupplier<Item> BABY_GIRL = register("baby_girl", () -> new BabyItem(Gender.FEMALE, baseProps().maxCount(1)));
    RegistrySupplier<Item> SIRBEN_BABY_BOY = register("sirben_baby_boy", () -> new SirbenBabyItem(Gender.MALE, baseProps().arch$tab((ItemGroup) null).maxCount(1)));
    RegistrySupplier<Item> SIRBEN_BABY_GIRL = register("sirben_baby_girl", () -> new SirbenBabyItem(Gender.FEMALE, baseProps().arch$tab((ItemGroup) null).maxCount(1)));

    RegistrySupplier<Item> WEDDING_RING = register("wedding_ring", () -> new WeddingRingItem(unstackableProps()));
    RegistrySupplier<Item> WEDDING_RING_RG = register("wedding_ring_rg", () -> new WeddingRingItem(unstackableProps()));
    RegistrySupplier<Item> ENGAGEMENT_RING = register("engagement_ring", () -> new EngagementRingItem(unstackableProps()));
    RegistrySupplier<Item> ENGAGEMENT_RING_RG = register("engagement_ring_rg", () -> new EngagementRingItem(unstackableProps()));
    RegistrySupplier<Item> MATCHMAKERS_RING = register("matchmakers_ring", () -> new MatchmakersRingItem(baseProps().maxCount(2)));

    RegistrySupplier<Item> VILLAGER_EDITOR = register("villager_editor", () -> new VillagerEditorItem(baseProps()));
    RegistrySupplier<Item> STAFF_OF_LIFE = register("staff_of_life", () -> new StaffOfLifeItem(baseProps().maxDamage(10)));
    RegistrySupplier<Item> WHISTLE = register("whistle", () -> new WhistleItem(baseProps()));
    RegistrySupplier<Item> BLUEPRINT = register("blueprint", () -> new BlueprintItem(baseProps()));
    RegistrySupplier<Item> FAMILY_TREE = register("family_tree", () -> new FamilyTreeItem(baseProps()));

    RegistrySupplier<Item> BOUQUET = register("bouquet", () -> new BouquetItem(baseProps()));
    
    List<RegistrySupplier<Item>> CRIBS = registerAllCribTypes();

    RegistrySupplier<Item> POTION_OF_FEMINITY = register("potion_of_feminity", () -> new PotionOfMetamorphosisItem(baseProps().maxCount(1), Gender.FEMALE));
    RegistrySupplier<Item> POTION_OF_MASCULINITY = register("potion_of_masculinity", () -> new PotionOfMetamorphosisItem(baseProps().maxCount(1), Gender.MALE));

    RegistrySupplier<Item> NEEDLE_AND_THREAD = register("needle_and_thread", () -> new NeedleAndThreadItem(baseProps().maxDamage(8)));
    RegistrySupplier<Item> COMB = register("comb", () -> new CombItem(baseProps().maxDamage(8)));

    RegistrySupplier<Item> BOOK_DEATH = register("book_death", () -> new ExtendedWrittenBookItem(baseProps(), new Book("death")
            .setBackground(MCA.locate("textures/gui/books/death.png"))
            .setTextFormatting(Formatting.WHITE)
            .setTextShadow(true)
            .addPage(new TitlePage("death", Formatting.GRAY))
            .addSimplePages(3, 0)
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/test.png"), "death", 3))
            .addSimplePages(9, 4)
    ));

    RegistrySupplier<Item> BOOK_ROMANCE = register("book_romance", () -> new ExtendedWrittenBookItem(baseProps(), new Book("romance")
            .setBackground(MCA.locate("textures/gui/books/romance.png"))
            .addPage(new TitlePage("romance"))
            .addSimplePages(10)));

    RegistrySupplier<Item> BOOK_FAMILY = register("book_family", () -> new ExtendedWrittenBookItem(baseProps(), new Book("family")
            .setBackground(MCA.locate("textures/gui/books/family.png"))
            .addPage(new TitlePage("family"))
            .addSimplePages(6)));

    RegistrySupplier<Item> BOOK_ROSE_GOLD = register("book_rose_gold", () -> new ExtendedWrittenBookItem(baseProps(), new Book("rose_gold")
            .setBackground(MCA.locate("textures/gui/books/rose_gold.png"))
            .addPage(new TitlePage("rose_gold"))
            .addSimplePages(4)));

    RegistrySupplier<Item> BOOK_INFECTION = register("book_infection", () -> new ExtendedWrittenBookItem(baseProps(), new Book("infection")
            .setBackground(MCA.locate("textures/gui/books/infection.png"))
            .addPage(new TitlePage("infection"))
            .addSimplePages(6)));

    RegistrySupplier<Item> BOOK_BLUEPRINT = register("book_blueprint", () -> new ExtendedWrittenBookItem(baseProps(), new Book("blueprint")
            .setBackground(MCA.locate("textures/gui/books/blueprint.png"))
            .setTextFormatting(Formatting.WHITE)
            .setTextShadow(true)
            .addPage(new TitlePage("blueprint", Formatting.WHITE))
            .addSimplePages(6)));

    RegistrySupplier<Item> BOOK_SUPPORTERS = register("book_supporters", () -> new ExtendedWrittenBookItem(baseProps(), new Book("supporters")
            .setBackground(MCA.locate("textures/gui/books/supporters.png"))
            .addPage(new TitlePage("supporters"))
            .addPage(new DynamicListPage("mca.books.supporters.contributors",
                    page -> Supporters.getSupporterGroup("mca:contributors").stream().map(s -> Text.literal(s).formatted(Formatting.DARK_GREEN)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.wiki",
                    page -> Supporters.getSupporterGroup("mca:wiki").stream().map(s -> Text.literal(s).formatted(Formatting.GOLD)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.patrons",
                    page -> Supporters.getSupporterGroup("mca:patrons").stream().map(s -> Text.literal(s).formatted(Formatting.RED)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.translators",
                    page -> Supporters.getSupporterGroup("mca:translators").stream().map(s -> Text.literal(s).formatted(Formatting.DARK_BLUE)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.old",
                    page -> Supporters.getSupporterGroup("mca:old").stream().map(s -> Text.literal(s).formatted(Formatting.BLACK)).collect(Collectors.toList())))
            .addPage(new TitlePage("mca.books.supporters.thanks", ""))));

    RegistrySupplier<Item> BOOK_CULT_0 = register("book_cult_0", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_0")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(Formatting.DARK_RED)
            .addPage(new TitlePage("cult_0", Formatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_0", 0))
            .addPage(new CenteredTextPage("cult_0", 1))
            .addPage(new CenteredTextPage("cult_0", 2))
            .addPage(new CenteredTextPage("cult_0", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), ""))));

    RegistrySupplier<Item> BOOK_CULT_1 = register("book_cult_1", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_1")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(Formatting.DARK_RED)
            .addPage(new TitlePage("cult_1", Formatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_1", 0))
            .addPage(new CenteredTextPage("cult_1", 1))
            .addPage(new CenteredTextPage("cult_1", 2))
            .addPage(new CenteredTextPage("cult_1", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), ""))));

    RegistrySupplier<Item> BOOK_CULT_2 = register("book_cult_2", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_2")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(Formatting.DARK_RED)
            .addPage(new TitlePage("cult_2", Formatting.DARK_RED))
            .addPage(new CenteredTextPage("cult_2", 0))
            .addPage(new CenteredTextPage("cult_2", 1))
            .addPage(new CenteredTextPage("cult_2", 2))
            .addPage(new CenteredTextPage("cult_2", 3))
            .addPage(new ScribbleTextPage(MCA.locate("textures/gui/scribbles/goat.png"), ""))));

    RegistrySupplier<Item> BOOK_CULT_ANCIENT = register("book_cult_ancient", () -> new ExtendedWrittenBookItem(baseProps(), new Book("cult_ancient")
            .setBackground(MCA.locate("textures/gui/books/cult.png"))
            .setTextFormatting(Formatting.DARK_RED)
            .addPage(new TitlePage("cult_ancient", Formatting.DARK_RED))
            .addPage(new CenteredTextPage("We are the universe. We are everything you think isn't you. You are looking at us now, through your skin and your eyes. And why does the universe touch your skin, and throw light on you? To see you, player. To know you. And to be known. I shall tell you a story.")
                    .setStyle(Style.EMPTY.withFont(new Identifier("minecraft", "alt"))))));

    RegistrySupplier<Item> LETTER = register("letter", () -> new ExtendedWrittenBookItem(baseProps().maxCount(1), new Book("letter", null)
            .setBackground(MCA.locate("textures/gui/books/paper.png"))));

    RegistrySupplier<Item> CIVIL_REGISTRY = register("civil_registry", () -> new CivilRegistry(baseProps().maxCount(1), new CivilRegistryBook("civil_registry", null)
            .setBackground(MCA.locate("textures/gui/books/supporters.png"))));

    RegistrySupplier<Item> VILLAGER_TRACKER = register("villager_tracker", () -> new VillagerTrackerItem(baseProps().maxCount(1)));

    RegistrySupplier<Item> ROSE_GOLD_DUST = register("rose_gold_dust", () -> new Item(baseProps()));
    RegistrySupplier<Item> ROSE_GOLD_INGOT = register("rose_gold_ingot", () -> new Item(baseProps()));

    RegistrySupplier<Item> DIVORCE_PAPERS = register("divorce_papers", () -> new TooltippedItem(baseProps()));

    RegistrySupplier<Item> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new BlockItem(BlocksMCA.ROSE_GOLD_BLOCK.get(), baseProps()));

    RegistrySupplier<Item> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new BlockItem(BlocksMCA.JEWELER_WORKBENCH.get(), baseProps()));

    RegistrySupplier<Item> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new BlockItem(BlocksMCA.GRAVELLING_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new BlockItem(BlocksMCA.UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> SLANTED_HEADSTONE = register("slanted_headstone", () -> new BlockItem(BlocksMCA.SLANTED_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> CROSS_HEADSTONE = register("cross_headstone", () -> new BlockItem(BlocksMCA.CROSS_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> WALL_HEADSTONE = register("wall_headstone", () -> new BlockItem(BlocksMCA.WALL_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> COBBLESTONE_UPRIGHT_HEADSTONE = register("cobblestone_upright_headstone", () -> new BlockItem(BlocksMCA.COBBLESTONE_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> COBBLESTONE_SLANTED_HEADSTONE = register("cobblestone_slanted_headstone", () -> new BlockItem(BlocksMCA.COBBLESTONE_SLANTED_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> WOODEN_UPRIGHT_HEADSTONE = register("wooden_upright_headstone", () -> new BlockItem(BlocksMCA.WOODEN_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> WOODEN_SLANTED_HEADSTONE = register("wooden_slanted_headstone", () -> new BlockItem(BlocksMCA.WOODEN_SLANTED_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> GOLDEN_UPRIGHT_HEADSTONE = register("golden_upright_headstone", () -> new BlockItem(BlocksMCA.GOLDEN_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> GOLDEN_SLANTED_HEADSTONE = register("golden_slanted_headstone", () -> new BlockItem(BlocksMCA.GOLDEN_SLANTED_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> DEEPSLATE_UPRIGHT_HEADSTONE = register("deepslate_upright_headstone", () -> new BlockItem(BlocksMCA.DEEPSLATE_UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> DEEPSLATE_SLANTED_HEADSTONE = register("deepslate_slanted_headstone", () -> new BlockItem(BlocksMCA.DEEPSLATE_SLANTED_HEADSTONE.get(), baseProps()));

    RegistrySupplier<Item> SCYTHE = register("scythe", () -> new ScytheItem(baseProps()));

    static void bootstrap() {
        ITEM_GROUPS.register();
        ITEMS.register();
        TagsMCA.Blocks.bootstrap();
    }
    
    static List<RegistrySupplier<Item>> registerAllCribTypes()
    {
    	List<RegistrySupplier<Item>> cribs = new ArrayList<>();
    	
		for(CribWoodType wood : CribWoodType.values())
		{
			for(DyeColor color : DyeColor.values())
			{
				cribs.add(register(color.getName() + "_" + wood.toString().toLowerCase() + "_crib", () -> new CribItem(unstackableProps(), wood, color)));
			}
		}
		
		return cribs;
    }

    static RegistrySupplier<Item> register(String name, Supplier<Item> item) {
        return ITEMS.register(new Identifier(MCA.MOD_ID, name), item);
    }

    static RegistrySupplier<ItemGroup> registerTab(String name, Supplier<ItemGroup> group) {
        return ITEM_GROUPS.register(new Identifier(MCA.MOD_ID, name), group);
    }

    static Item.Settings baseProps() {
        return new Item.Settings().arch$tab(MCA_GROUP);
    }

    static Item.Settings unstackableProps() {
        return baseProps().maxCount(1);
    }
}
