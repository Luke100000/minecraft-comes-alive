package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;
import java.util.Locale;

public class LayeredHair extends SkinListEntry {
    public static final Codec<Category> CATEGORY_CODEC = Codec.STRING.comapFlatMap(name -> {
        Category category = Category.byNameOrNull(name);
        if (category == null) {
            return DataResult.error(() -> "Invalid layered hair category: " + name);
        }
        return DataResult.success(category);
    }, Category::getId);
    public static final StreamCodec<ByteBuf, Category> CATEGORY_STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Category::byName, Category::getId);
    public static final Codec<LayeredHair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(LayeredHair::getIdentifierValue),
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(LayeredHair::getGender),
            CATEGORY_CODEC.fieldOf("category").forGetter(LayeredHair::getCategory),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(LayeredHair::getChance)
    ).apply(instance, (id, gender, category, chance) -> new LayeredHair(id.toString(), gender, category, chance)));
    public static final StreamCodec<ByteBuf, LayeredHair> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                String json = CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value)
                        .getOrThrow()
                        .toString();
                ByteBufCodecs.STRING_UTF8.encode(out, json);
            },
            in -> {
                String json = ByteBufCodecs.STRING_UTF8.decode(in);
                return CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, GsonHelper.parse(json)).getOrThrow();
            }
    );

    private final Category category;

    public LayeredHair(String identifier, JsonObject object) {
        super(identifier, object);
        this.category = Category.byName(GsonHelper.getAsString(object, "category", ""));
    }

    public LayeredHair(String identifier, Gender gender, Category category, float chance) {
        super(identifier, gender, chance);
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = super.toJson();
        json.addProperty("category", category.getId());
        return json;
    }

    public enum Category {
        BASE("base", true, "HairBase"),
        BANGS("bangs", true, "HairBangs"),
        BACK("back", false, "HairBack"),
        FRONT("front", false, "HairFront"),
        EXTRA("extra", false, "HairExtra");

        public static final List<Category> RENDER_ORDER = List.of(BACK, BASE, BANGS, FRONT, EXTRA);

        private final String id;
        private final boolean required;
        private final String dataKey;

        Category(String id, boolean required, String dataKey) {
            this.id = id;
            this.required = required;
            this.dataKey = dataKey;
        }

        public String getId() {
            return id;
        }

        public boolean isRequired() {
            return required;
        }

        public String getDataKey() {
            return dataKey;
        }

        public static Category byName(String name) {
            Category category = byNameOrNull(name);
            return category == null ? BASE : category;
        }

        public static Category byNameOrNull(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (Category category : values()) {
                if (category.id.equals(normalized)) {
                    return category;
                }
            }
            return null;
        }
    }

}
