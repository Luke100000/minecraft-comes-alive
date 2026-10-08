package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RecentVillagerEvents {
    public static final String NBT_KEY = "recentVillagerEvents";

    public static final ResourceLocation RELATIVE_DEATH = MCA.locate("relative_death");
    public static final ResourceLocation ATTACKED = MCA.locate("attacked");
    public static final ResourceLocation ZOMBIFIED = MCA.locate("zombified");
    public static final ResourceLocation CURED = MCA.locate("cured");
    public static final ResourceLocation REVIVED = MCA.locate("revived");
    public static final ResourceLocation RAID_SURVIVED = MCA.locate("raid_survived");

    private static final Set<ResourceLocation> REGISTERED = Set.of(
            RELATIVE_DEATH,
            ATTACKED,
            ZOMBIFIED,
            CURED,
            REVIVED,
            RAID_SURVIVED
    );

    private final Map<ResourceLocation, Long> occurrences = new HashMap<>();

    public void record(ResourceLocation event, long gameTime) {
        Objects.requireNonNull(event, "event");
        if (!REGISTERED.contains(event)) {
            return;
        }
        occurrences.merge(event, gameTime, Math::max);
    }

    public boolean occurredWithin(ResourceLocation event, long gameTime, long withinTicks) {
        Objects.requireNonNull(event, "event");
        if (withinTicks < 0 || !REGISTERED.contains(event)) {
            return false;
        }
        Long occurrence = occurrences.get(event);
        if (occurrence == null) {
            return false;
        }
        long elapsed = gameTime - occurrence;
        return elapsed >= 0 && elapsed <= withinTicks;
    }

    public void writeToNbt(CompoundTag tag) {
        CompoundTag events = new CompoundTag();
        occurrences.forEach((event, gameTime) -> {
            if (REGISTERED.contains(event)) {
                events.putLong(event.toString(), gameTime);
            }
        });
        tag.put(NBT_KEY, events);
    }

    public void readFromNbt(CompoundTag tag) {
        occurrences.clear();
        CompoundTag events = tag.getCompound(NBT_KEY);
        for (String key : events.getAllKeys()) {
            ResourceLocation event = ResourceLocation.tryParse(key);
            if (event != null && REGISTERED.contains(event)) {
                occurrences.put(event, events.getLong(key));
            }
        }
    }

    public static boolean isRegistered(ResourceLocation event) {
        return REGISTERED.contains(event);
    }

    public static long gameTime(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().overworld().getGameTime();
        }
        return level.getGameTime();
    }
}
