package net.conczin.mca.integration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

// We don't talk about this file
public final class ImmersiveMelodies {
    private static final String ITEMS_CLASS = "immersive_melodies.Items";
    private static final String SERVER_MELODIES_CLASS = "immersive_melodies.resources.ServerMelodyManager";

    public static Optional<ItemStack> randomInstrument(LivingEntity entity) {
        try {
            Map<?, ?> instruments = (Map<?, ?>) Class.forName(ITEMS_CLASS).getField("items").get(null);
            List<Item> choices = new ArrayList<>();
            for (Object value : instruments.values()) {
                Object item = value instanceof Supplier<?> supplier ? supplier.get() : value;
                if (item instanceof Item instrument) {
                    choices.add(instrument);
                }
            }
            return choices.isEmpty() ? Optional.empty() : Optional.of(new ItemStack(choices.get(entity.getRandom().nextInt(choices.size()))));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    public static Optional<ResourceLocation> randomMelody(LivingEntity entity, String namespace) {
        try {
            Class<?> manager = Class.forName(SERVER_MELODIES_CLASS);
            List<ResourceLocation> melodies = new ArrayList<>();
            addMelodies(melodies, (Map<?, ?>) manager.getMethod("getDatapackMelodies").invoke(null), namespace);

            Object index = manager.getMethod("getIndex").invoke(null);
            if (index != null) {
                addMelodies(melodies, (Map<?, ?>) index.getClass().getMethod("getMelodies").invoke(index), namespace);
            }

            return melodies.isEmpty() ? Optional.empty() : Optional.of(melodies.get(entity.getRandom().nextInt(melodies.size())));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    public static boolean play(ItemStack instrument, ResourceLocation melody, ServerLevel level, LivingEntity entity) {
        return invoke(instrument, "play", 4, melody, level, entity);
    }

    public static boolean play(ItemStack instrument, PlayingMelody melody, ServerLevel level, LivingEntity entity) {
        try {
            for (Method method : instrument.getItem().getClass().getDeclaredMethods()) {
                if (method.getName().equals("play") && method.getParameterCount() == 4 && method.getParameterTypes()[2] == long.class && method.trySetAccessible()) {
                    method.invoke(instrument.getItem(), instrument, melody.melody(), melody.startTime(), entity);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
        }
        return play(instrument, melody.melody(), level, entity);
    }

    public static void stop(ItemStack instrument, ServerLevel level) {
        invoke(instrument, "pause", 2, level);
    }

    public static Optional<PlayingMelody> playingMelody(ItemStack instrument) {
        try {
            Method isPlaying = findMethod(instrument, "isPlaying", 1);
            Method getMelody = findMethod(instrument, "getMelody", 1);
            if (isPlaying == null || getMelody == null || !Boolean.TRUE.equals(isPlaying.invoke(instrument.getItem(), instrument))) {
                return Optional.empty();
            }
            Object melody = getMelody.invoke(null, instrument);
            Object startTime = findItemStackGet().invoke(instrument, instrument.getItem().getClass().getField("START_TIME").get(null));
            return melody instanceof ResourceLocation id && startTime instanceof Long time ? Optional.of(new PlayingMelody(id, time)) : Optional.empty();
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static void addMelodies(Collection<ResourceLocation> target, Map<?, ?> melodies, String namespace) {
        for (Object key : melodies.keySet()) {
            if (key instanceof ResourceLocation id && ("*".equals(namespace) || namespace.equals(id.getNamespace()))) {
                target.add(id);
            }
        }
    }

    private static boolean invoke(ItemStack instrument, String name, int parameterCount, Object... arguments) {
        try {
            Method method = findMethod(instrument, name, parameterCount);
            if (method != null) {
                method.invoke(instrument.getItem(), prepend(instrument, arguments));
                return true;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
        }
        return false;
    }

    private static Method findMethod(ItemStack instrument, String name, int parameterCount) {
        return Arrays.stream(instrument.getItem().getClass().getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
                .findFirst().orElse(null);
    }

    private static Method findItemStackGet() throws NoSuchMethodException {
        return Arrays.stream(ItemStack.class.getMethods())
                .filter(method -> method.getName().equals("get") && method.getParameterCount() == 1)
                .findFirst().orElseThrow(NoSuchMethodException::new);
    }

    private static Object[] prepend(ItemStack instrument, Object[] arguments) {
        Object[] invocation = new Object[arguments.length + 1];
        invocation[0] = instrument;
        System.arraycopy(arguments, 0, invocation, 1, arguments.length);
        return invocation;
    }

    public record PlayingMelody(ResourceLocation melody, long startTime) {
    }
}
