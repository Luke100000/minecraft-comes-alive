package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public abstract class SkinListEntry {
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
}
