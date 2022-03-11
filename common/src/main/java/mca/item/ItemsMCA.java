package mca.item;

import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import mca.TagsMCA;
import mca.block.BlocksMCA;
import mca.client.book.Book;
import mca.client.book.pages.DynamicListPage;
import mca.client.book.pages.ScribbleTextPage;
import mca.client.book.pages.TitlePage;
import mca.crafting.recipe.RecipesMCA;
import mca.entity.EntitiesMCA;
import mca.entity.ai.relationship.Gender;
import mca.resources.Supporters;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface ItemsMCA {
    
    DeferredRegister<Item> ITEMS = DeferredRegister.create(MCA.MOD_ID, Registry.ITEM_KEY);
    
    RegistrySupplier<Item> MALE_VILLAGER_SPAWN_EGG = register("male_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.MALE_VILLAGER, 0x5e9aff, 0x3366bc, baseProps()));
    RegistrySupplier<Item> FEMALE_VILLAGER_SPAWN_EGG = register("female_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.FEMALE_VILLAGER, 0xe85ca1, 0xe3368c, baseProps()));

    RegistrySupplier<Item> MALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("male_zombie_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.MALE_ZOMBIE_VILLAGER, 0x5ebaff, 0x33a6bc, baseProps()));
    RegistrySupplier<Item> FEMALE_ZOMBIE_VILLAGER_SPAWN_EGG = register("female_zombie_villager_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, 0xe8aca1, 0xe3a68c, baseProps()));

    RegistrySupplier<Item> GRIM_REAPER_SPAWN_EGG = register("grim_reaper_spawn_egg", () -> new ArchitecturySpawnEggItem(EntitiesMCA.GRIM_REAPER, 0x301515, 0x2A1C34, baseProps()));

    RegistrySupplier<Item> BABY_BOY = register("baby_boy", () -> new BabyItem(Gender.MALE, baseProps().maxCount(1)));
    RegistrySupplier<Item> BABY_GIRL = register("baby_girl", () -> new BabyItem(Gender.FEMALE, baseProps().maxCount(1)));

    RegistrySupplier<Item> WEDDING_RING = register("wedding_ring", () -> new WeddingRingItem(unstackableProps()));
    RegistrySupplier<Item> WEDDING_RING_RG = register("wedding_ring_rg", () -> new WeddingRingItem(unstackableProps()));
    RegistrySupplier<Item> ENGAGEMENT_RING = register("engagement_ring", () -> new WeddingRingItem(unstackableProps(), 0.5F));
    RegistrySupplier<Item> ENGAGEMENT_RING_RG = register("engagement_ring_rg", () -> new WeddingRingItem(unstackableProps(), 0.5F));
    RegistrySupplier<Item> MATCHMAKERS_RING = register("matchmakers_ring", () -> new MatchmakersRingItem(baseProps().maxCount(2)));

    RegistrySupplier<Item> VILLAGER_EDITOR = register("villager_editor", () -> new VillagerEditorItem(baseProps()));
    RegistrySupplier<Item> STAFF_OF_LIFE = register("staff_of_life", () -> new StaffOfLifeItem(baseProps().maxDamage(5)));
    RegistrySupplier<Item> WHISTLE = register("whistle", () -> new WhistleItem(baseProps()));
    RegistrySupplier<Item> BLUEPRINT = register("blueprint", () -> new BlueprintItem(baseProps()));
    RegistrySupplier<Item> FAMILY_TREE = register("family_tree", () -> new FamilyTreeItem(baseProps()));

    RegistrySupplier<Item> BOOK_DEATH = register("book_death", () -> new ExtendedWrittenBookItem(baseProps(), new Book("death")
            .setBackground(new Identifier("mca:textures/gui/books/death.png"))
            .setTextFormatting(Formatting.WHITE)
            .addPage(new TitlePage("death", Formatting.GRAY))
            .addSimplePages(3, 0)
            .addPage(new ScribbleTextPage(new Identifier("mca:textures/gui/scribbles/test.png"), "death", 3))
            .addSimplePages(9, 4)
    ));

    RegistrySupplier<Item> BOOK_ROMANCE = register("book_romance", () -> new ExtendedWrittenBookItem(baseProps(), new Book("romance")
            .setBackground(new Identifier("mca:textures/gui/books/romance.png"))
            .addPage(new TitlePage("romance"))
            .addSimplePages(10)));

    RegistrySupplier<Item> BOOK_FAMILY = register("book_family", () -> new ExtendedWrittenBookItem(baseProps(), new Book("family")
            .addPage(new TitlePage("family"))
            .addSimplePages(8)));

    RegistrySupplier<Item> BOOK_ROSE_GOLD = register("book_rose_gold", () -> new ExtendedWrittenBookItem(baseProps(), new Book("rose_gold")
            .setBackground(new Identifier("mca:textures/gui/books/rose_gold.png"))
            .addPage(new TitlePage("rose_gold"))
            .addSimplePages(5)));

    RegistrySupplier<Item> BOOK_INFECTION = register("book_infection", () -> new ExtendedWrittenBookItem(baseProps(), new Book("infection")
            .setBackground(new Identifier("mca:textures/gui/books/infection.png"))
            .addPage(new TitlePage("infection"))
            .addSimplePages(6)));

    RegistrySupplier<Item> BOOK_BLUEPRINT = register("book_blueprint", () -> new ExtendedWrittenBookItem(baseProps(), new Book("blueprint")
            .setBackground(new Identifier("mca:textures/gui/books/blueprint.png"))
            .setTextFormatting(Formatting.WHITE)
            .addPage(new TitlePage("blueprint", Formatting.WHITE))
            .addSimplePages(6)));

    RegistrySupplier<Item> BOOK_SUPPORTERS = register("book_supporters", () -> new ExtendedWrittenBookItem(baseProps(), new Book("supporters")
            .setBackground(new Identifier("mca:textures/gui/books/supporters.png"))
            .addPage(new TitlePage("supporters"))
            .addPage(new DynamicListPage("mca.books.supporters.patrons",
                    page -> Supporters.getSupporterGroup("mca:patrons").stream().map(s -> new LiteralText(s).formatted(Formatting.RED)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.wiki",
                    page -> Supporters.getSupporterGroup("mca:wiki").stream().map(s -> new LiteralText(s).formatted(Formatting.GOLD)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.contributors",
                    page -> Supporters.getSupporterGroup("mca:contributors").stream().map(s -> new LiteralText(s).formatted(Formatting.DARK_GREEN)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.translators",
                    page -> Supporters.getSupporterGroup("mca:translators").stream().map(s -> new LiteralText(s).formatted(Formatting.DARK_BLUE)).collect(Collectors.toList())))
            .addPage(new DynamicListPage("mca.books.supporters.old",
                    page -> Supporters.getSupporterGroup("mca:old").stream().map(s -> new LiteralText(s).formatted(Formatting.BLACK)).collect(Collectors.toList())))
            .addPage(new TitlePage("mca.books.supporters.thanks", ""))));

    RegistrySupplier<Item> LETTER = register("letter", () -> new ExtendedWrittenBookItem(baseProps().maxCount(1), new Book("letter", null)
            .setBackground(new Identifier("mca:textures/gui/books/paper.png"))));

    RegistrySupplier<Item> GOLD_DUST = register("gold_dust", () -> new Item(baseProps()));
    RegistrySupplier<Item> ROSE_GOLD_DUST = register("rose_gold_dust", () -> new Item(baseProps()));
    RegistrySupplier<Item> ROSE_GOLD_INGOT = register("rose_gold_ingot", () -> new Item(baseProps()));

    RegistrySupplier<Item> DIVORCE_PAPERS = register("divorce_papers", () -> new TooltippedItem(baseProps()));

    RegistrySupplier<Item> ROSE_GOLD_BLOCK = register("rose_gold_block", () -> new BlockItem(BlocksMCA.ROSE_GOLD_BLOCK.get(), baseProps()));
    RegistrySupplier<Item> ROSE_GOLD_ORE = register("rose_gold_ore", () -> new BlockItem(BlocksMCA.ROSE_GOLD_ORE.get(), baseProps()));

    RegistrySupplier<Item> JEWELER_WORKBENCH = register("jeweler_workbench", () -> new BlockItem(BlocksMCA.JEWELER_WORKBENCH.get(), baseProps()));

    RegistrySupplier<Item> GRAVELLING_HEADSTONE = register("gravelling_headstone", () -> new BlockItem(BlocksMCA.GRAVELLING_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> UPRIGHT_HEADSTONE = register("upright_headstone", () -> new BlockItem(BlocksMCA.UPRIGHT_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> SLANTED_HEADSTONE = register("slanted_headstone", () -> new BlockItem(BlocksMCA.SLANTED_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> CROSS_HEADSTONE = register("cross_headstone", () -> new BlockItem(BlocksMCA.CROSS_HEADSTONE.get(), baseProps()));
    RegistrySupplier<Item> WALL_HEADSTONE = register("wall_headstone", () -> new BlockItem(BlocksMCA.WALL_HEADSTONE.get(), baseProps()));

    RegistrySupplier<Item> SCYTHE = register("scythe", () -> new ScytheItem(baseProps()));

    static void bootstrap() {
        ITEMS.register();
        TagsMCA.Blocks.bootstrap();
        RecipesMCA.bootstrap();
    }

    static RegistrySupplier<Item> register(String name, Supplier<Item> item) {
        return ITEMS.register(new Identifier(MCA.MOD_ID, name), item);
    }

    static Item.Settings baseProps() {
        return new Item.Settings().group(ItemGroupMCA.MCA_GROUP);
    }

    static Item.Settings unstackableProps() {
        return baseProps().maxCount(1);
    }
}
