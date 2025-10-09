package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TaskRegistry {
    public static Map<String, Function<JsonObject, Task>> DECODER = new HashMap<>();
    public static final StreamCodec<ByteBuf, Task> CODEC = StreamCodec.of(
            (out, value) -> {
                ByteBufCodecs.STRING_UTF8.encode(out, value.serialize().toString());
            },
            (in) -> {
                String json = ByteBufCodecs.STRING_UTF8.decode(in);
                return fromJson(GsonHelper.parse(json));
            }
    );

    static {
        register(AdvancementTask.TYPE, AdvancementTask::new);
        register(BlockingTask.TYPE, BlockingTask::new);
        register(BuildingTask.TYPE, BuildingTask::new);
        register(PopulationTask.TYPE, PopulationTask::new);
        register(ReputationTask.TYPE, ReputationTask::new);
    }

    static void register(String id, Function<JsonObject, Task> decoder) {
        DECODER.put(id, decoder);
    }

    public static Task fromJson(JsonObject json) {
        String id = GsonHelper.getAsString(json, "type");
        Function<JsonObject, Task> decoder = DECODER.get(id);
        if (decoder == null) {
            throw new IllegalArgumentException("Unknown task type: " + id);
        }
        return decoder.apply(json);
    }
}
