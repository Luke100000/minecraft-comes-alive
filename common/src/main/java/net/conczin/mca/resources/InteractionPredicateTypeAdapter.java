package net.conczin.mca.resources;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.conczin.mca.entity.interaction.InteractionPredicate;

import java.lang.reflect.Type;

public class InteractionPredicateTypeAdapter implements JsonDeserializer<InteractionPredicate> {
    public static final InteractionPredicateTypeAdapter INSTANCE = new InteractionPredicateTypeAdapter();

    @Override
    public InteractionPredicate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return InteractionPredicate.fromJson(json.getAsJsonObject());
    }
}
