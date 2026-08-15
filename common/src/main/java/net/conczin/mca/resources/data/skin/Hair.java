package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;

public class Hair extends SkinListEntry {
    public Hair(String identifier) {
        super(identifier);
    }

    public Hair(String identifier, JsonObject object) {
        super(identifier, object);
    }

    public Hair(String identifier, Gender gender, float chance) {
        super(identifier, gender, chance);
    }
}
