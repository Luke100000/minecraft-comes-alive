package net.conczin.mca.resources;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinCatalogLoaderTest {
    @Test
    void missingHairStyleGenderDefaultsToNeutral() {
        var styles = new HashMap<String, HairStyle>();
        var json = JsonParser.parseString("""
                {
                  "test:style": {"base": "test:base", "chance": 0.4},
                  "test:female": {"gender": "female", "back": "test:back"},
                  "test:male": {"gender": "male"},
                  "mca:skins/hair/female/old": {"base": "test:legacy"}
                }
                """);

        SkinCatalogLoader.addHairStyles(styles, Identifier.parse("mca:skins/hair_styles/skin"), json);

        assertEquals(Gender.NEUTRAL, styles.get("test:style").getGender());
        assertEquals(0.4f, styles.get("test:style").getChance());
        assertEquals("test:base", styles.get("test:style").base());
        assertEquals(Gender.FEMALE, styles.get("test:female").getGender());
        assertEquals("test:back", styles.get("test:female").back());
        assertEquals(Gender.MALE, styles.get("test:male").getGender());
        assertEquals(Gender.FEMALE, styles.get("mca:skins/hair/female/old").getGender());
    }

    @Test
    void invalidGenderIsRejectedByGenderCodecWhileOptionalStyleFieldUsesFallback() {
        assertTrue(SkinListEntry.GENDER_CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("not-a-gender"))
                .result().isEmpty());

        var styles = new HashMap<String, HairStyle>();
        SkinCatalogLoader.addHairStyles(styles, Identifier.parse("mca:skins/hair_styles/neutral"),
                JsonParser.parseString("{\"test:invalid\": {\"gender\": \"not-a-gender\"}}"));

        assertEquals(Gender.NEUTRAL, styles.get("test:invalid").getGender());
    }

    @Test
    void invalidLayeredHairTextureIsSkipped() {
        var hair = new HashMap<String, LayeredHair>();

        SkinCatalogLoader.addLayeredHair(hair, Identifier.parse("mca:hair_layers/front"),
                List.of("bad id !", "mca:wrong/path", "mca:skins/layered_hair/front/0"));

        assertEquals(1, hair.size());
        assertFalse(hair.containsKey("bad id !|neutral|front"));
        assertTrue(hair.containsKey("mca:skins/layered_hair/front/0|neutral|front"));
        assertEquals(LayeredHair.Category.FRONT,
                hair.get("mca:skins/layered_hair/front/0|neutral|front").getCategory());
    }

    @Test
    void layeredHairUsesPathSegmentCategory() {
        var hair = new HashMap<String, LayeredHair>();

        SkinCatalogLoader.addLayeredHair(hair, Identifier.parse("mca:hair_layers/variants/back/summer"),
                JsonParser.parseString("[\"mca:skins/layered_hair/back/0\"]"));
        SkinCatalogLoader.addLayeredHair(hair, Identifier.parse("mca:hair_layers/variants/unknown"),
                List.of("mca:skins/layered_hair/base/0"));

        assertEquals(LayeredHair.Category.BACK,
                hair.get("mca:skins/layered_hair/back/0|neutral|back").getCategory());
        assertEquals(LayeredHair.Category.BASE,
                hair.get("mca:skins/layered_hair/base/0|neutral|base").getCategory());
        assertEquals(2, hair.size());
    }
}
