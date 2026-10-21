package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.Config;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuardEnemiesSensorPriorityOrderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void overlappingTagsFollowConfigurationInsertionOrderEvenWhenEntriesAreReversed() throws ReflectiveOperationException {
        Map<String, Integer> undeadFirst = new LinkedHashMap<>();
        undeadFirst.put("#minecraft:undead", -1);
        undeadFirst.put("#minecraft:skeletons", 7);

        Map<String, Integer> skeletonsFirst = new LinkedHashMap<>();
        skeletonsFirst.put("#minecraft:skeletons", 7);
        skeletonsFirst.put("#minecraft:undead", -1);

        assertEquals(List.of("minecraft:undead", "minecraft:skeletons"), compiledTagOrder(undeadFirst));
        assertEquals(List.of("minecraft:skeletons", "minecraft:undead"), compiledTagOrder(skeletonsFirst));
    }

    @Test
    void reorderingUnchangedTagPrioritiesRefreshesCachedLookup() throws ReflectiveOperationException {
        Map<String, Integer> previous = Config.getInstance().guardsTargetEntities;
        Map<String, Integer> undeadFirst = new LinkedHashMap<>();
        undeadFirst.put("#minecraft:undead", -1);
        undeadFirst.put("#minecraft:skeletons", 7);
        Map<String, Integer> skeletonsFirst = new LinkedHashMap<>();
        skeletonsFirst.put("#minecraft:skeletons", 7);
        skeletonsFirst.put("#minecraft:undead", -1);

        try {
            Config.getInstance().guardsTargetEntities = undeadFirst;
            assertEquals(List.of("minecraft:undead", "minecraft:skeletons"), cachedTagOrder());
            Config.getInstance().guardsTargetEntities = skeletonsFirst;
            assertEquals(List.of("minecraft:skeletons", "minecraft:undead"), cachedTagOrder());
        } finally {
            Config.getInstance().guardsTargetEntities = previous;
        }
    }

    private static List<String> compiledTagOrder(Map<String, Integer> priorities) throws ReflectiveOperationException {
        Class<?> lookupClass = Class.forName(GuardEnemiesSensor.class.getName() + "$ConfiguredPriorityLookup");
        Method compile = lookupClass.getDeclaredMethod("compile", Map.class);
        compile.setAccessible(true);
        Object lookup = compile.invoke(null, priorities);

        return tagOrder(lookupClass, lookup);
    }

    private static List<String> cachedTagOrder() throws ReflectiveOperationException {
        Class<?> lookupClass = Class.forName(GuardEnemiesSensor.class.getName() + "$ConfiguredPriorityLookup");
        Method get = lookupClass.getDeclaredMethod("get");
        get.setAccessible(true);
        return tagOrder(lookupClass, get.invoke(null));
    }

    private static List<String> tagOrder(Class<?> lookupClass, Object lookup) throws ReflectiveOperationException {
        Method tagPriorities = lookupClass.getDeclaredMethod("tagPriorities");
        tagPriorities.setAccessible(true);
        List<?> tags = (List<?>) tagPriorities.invoke(lookup);
        if (tags.isEmpty()) {
            return List.of();
        }
        Method tag = tags.getFirst().getClass().getDeclaredMethod("tag");
        tag.setAccessible(true);
        return tags.stream().map(entry -> {
            try {
                return ((TagKey<?>) tag.invoke(entry)).location().toString();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }).toList();
    }
}
