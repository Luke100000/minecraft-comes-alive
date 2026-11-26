package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.server.world.data.villageComponents.VillageInnManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PrefixGameTestTemplate(false)
public final class VillageInnGameTests {
    private VillageInnGameTests() {
    }

    @GameTest(batch = "mca_inn_tagged_visitors", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void innSpawnsTheDatapackSelectedVisitor(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertTrue(level.isPositionEntityTicking(pos), "inn position must be entity ticking");
        helper.assertTrue(level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty(), "inn visitor requires open space");

        var registry = BuiltInRegistries.ENTITY_TYPE;
        TagKey<EntityType<?>> visitorTag = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("mca:spawn_at_inn"));
        Map<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>> originalTags = new HashMap<>();
        registry.listTags().forEach(tag -> originalTags.put(tag.key(), tag.stream().toList()));
        var visitorTags = new HashMap<>(originalTags);
        visitorTags.put(visitorTag, List.of(EntityType.COW.builtInRegistryHolder()));

        Config config = Config.getInstance();
        boolean traders = config.innSpawnsWanderingTraders;
        boolean cultists = config.innSpawnsCultists;
        boolean adventurers = config.innSpawnsAdventurers;
        boolean notifications = config.innArrivalNotification;
        AABB bounds = new AABB(pos).inflate(2);
        Set<Entity> existing = new HashSet<>(level.getEntitiesOfClass(Entity.class, bounds));
        var manager = new VillageInnManager(new Village(-1, level));
        var spawn = VillageInnManager.class.getDeclaredMethod("trySpawnAdventurer", ServerLevel.class, BlockPos.class);
        spawn.setAccessible(true);
        try {
            registry.prepareTagReload(new TagLoader.LoadResult<>(Registries.ENTITY_TYPE, visitorTags)).apply();
            config.innSpawnsWanderingTraders = true;
            config.innSpawnsCultists = false;
            config.innSpawnsAdventurers = false;
            config.innArrivalNotification = false;

            boolean spawned = false;
            for (int attempt = 0; attempt < 512 && !spawned; attempt++) {
                spawned = (boolean) spawn.invoke(manager, level, pos);
            }
            helper.assertTrue(spawned, "inn never selected the configured visitor branch");
            List<Entity> visitors = level.getEntitiesOfClass(Entity.class, bounds).stream()
                    .filter(entity -> !existing.contains(entity)).toList();
            helper.assertTrue(visitors.size() == 1 && visitors.getFirst().getType() == EntityType.COW,
                    "inn did not spawn the sole entity selected by the datapack tag");
        } finally {
            config.innSpawnsWanderingTraders = traders;
            config.innSpawnsCultists = cultists;
            config.innSpawnsAdventurers = adventurers;
            config.innArrivalNotification = notifications;
            registry.prepareTagReload(new TagLoader.LoadResult<>(Registries.ENTITY_TYPE, originalTags)).apply();
            level.getEntitiesOfClass(Entity.class, bounds).stream()
                    .filter(entity -> !existing.contains(entity)).forEach(Entity::discard);
        }
        helper.succeed();
    }
}
