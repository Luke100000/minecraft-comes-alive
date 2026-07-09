package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public abstract class SkinListEntry {
    public static final Codec<Gender> GENDER_CODEC = Codec.STRING.comapFlatMap(name -> {
        Gender gender = Gender.byName(name);
        if (gender == Gender.UNASSIGNED) {
            return DataResult.error(() -> "Invalid gender: " + name);
        }
        return DataResult.success(gender);
    }, Gender::getDataName);
    public static final StreamCodec<ByteBuf, Gender> GENDER_STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Gender::byName, Gender::getDataName);

    protected final String identifier;
    protected final Gender gender;
    protected final float chance;

    public SkinListEntry(String identifier) {
        this(identifier, Gender.NEUTRAL, 1.0f);
    }

    public SkinListEntry(String identifier, Gender gender, float chance) {
        this.identifier = identifier;

        this.gender = gender;
        this.chance = chance;
    }

    public SkinListEntry(String identifier, JsonObject object) {
        this.identifier = identifier;

        this.gender = Gender.byId(GsonHelper.getAsInt(object, "gender", 0));
        this.chance = GsonHelper.getAsFloat(object, "chance", 1.0f);
    }

    public String getPath() {
        return (ResourceLocation.parse(this.identifier)).getPath();
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("gender", gender == null ? Gender.NEUTRAL.getId() : gender.getId());
        j.addProperty("chance", chance);
        return j;
    }

    public String getIdentifier() {
        return identifier;
    }

    public static int compareIdentifiers(String a, String b) {
        int idxA = a.lastIndexOf('/');
        int idxB = b.lastIndexOf('/');
        String strA = idxA >= 0 ? a.substring(idxA) : a;
        String strB = idxB >= 0 ? b.substring(idxB) : b;
        String numA = strA.replaceAll("\\D+", "");
        String numB = strB.replaceAll("\\D+", "");
        if (numA.isEmpty() || numB.isEmpty()) {
            return a.compareTo(b);
        }
        String normalizedA = stripLeadingZeroes(numA);
        String normalizedB = stripLeadingZeroes(numB);
        int lengthCompare = Integer.compare(normalizedA.length(), normalizedB.length());
        if (lengthCompare != 0) {
            return lengthCompare;
        }
        int numberCompare = normalizedA.compareTo(normalizedB);
        return numberCompare != 0 ? numberCompare : a.compareTo(b);
    }

    private static String stripLeadingZeroes(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }

    public Gender getGender() {
        return gender;
    }

    public float getChance() {
        // I messed up and now we have a lot of 0 chances. Let's define 0 as default as they make no sense otherwise anyways.
        if (chance <= 0.0f) {
            return 1.0f;
        }
        return chance;
    }

    protected static Gender resolveGender(Gender gender, Gender fallback) {
        if (gender == null || gender == Gender.UNASSIGNED || gender == Gender.NEUTRAL) {
            return fallback == Gender.UNASSIGNED ? Gender.NEUTRAL : fallback;
        }
        return gender;
    }

    protected ResourceLocation getIdentifierValue() {
        return ResourceLocation.parse(identifier);
    }
}
