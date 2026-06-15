package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.IdentifierException;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FaceList extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Codec<Definition> DEFINITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("count", 1).forGetter(Definition::count)
    ).apply(instance, Definition::new));
    private static final Codec<Map<String, Definition>> FILE_CODEC = Codec.unboundedMap(Codec.STRING, DEFINITION_CODEC);
    private static final int FALLBACK_FACE_COUNT = 22;
    public static final Identifier ID = MCA.locate("skins/face");
    private static FaceList INSTANCE;
    private final HashMap<String, List<String>> faces = new HashMap<>();

    public FaceList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/face"));
        INSTANCE = this;
    }

    public static FaceList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        faces.clear();

        data.forEach((id, file) -> FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid face list {}: {}", id, error))
                .ifPresent(entries -> addEntries(id, entries)));
    }

    private void addEntries(Identifier id, Map<String, Definition> entries) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        entries.forEach((key, definition) -> {
            for (int i = 0; i < Math.max(1, definition.count()); i++) {
                String identifier = BodySkinList.formatIdentifier(key, i);
                Identifier parsed;
                try {
                    parsed = Identifier.parse(identifier);
                } catch (IdentifierException exception) {
                    MCA.LOGGER.warn("Invalid face texture identifier {}", identifier, exception);
                    continue;
                }
                String[] parts = parsed.getPath().split("/");
                if (parts.length < 3) {
                    MCA.LOGGER.warn("Invalid face texture path {}", identifier);
                    continue;
                }
                String mapKey = key(parts[2], fileGender);
                faces.computeIfAbsent(mapKey, ignored -> new ArrayList<>()).add(identifier);
            }
        });
    }

    public Identifier pick(String variant, Gender gender, float faceGene, boolean blink) {
        List<String> pool = faces.get(key(variant, gender));
        if (pool == null || pool.isEmpty()) {
            int index = blink ? 2 : (int) Math.min(6, Math.max(0, faceGene * 7));
            return MCA.locate("skins/face/" + variant + "/" + index + ".png");
        }

        int index = blink ? 2 : (int) Math.min(pool.size() - 1, Math.max(0, faceGene * pool.size()));
        return Identifier.parse(pool.get(index));
    }

    private static String key(String variant, Gender gender) {
        return variant.toLowerCase(Locale.ROOT) + "/" + gender.getDataName();
    }

    private record Definition(int count) {
    }
}
