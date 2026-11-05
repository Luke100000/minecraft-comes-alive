package net.conczin.mca.resources;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void clothingMetadataAndLegacyGenderMatchBothLoaders() {
        Identifier fileId = Identifier.parse("mca:skins/clothing/skin");
        var json = JsonParser.parseString("""
                [
                  {"id": "test:plain", "gender": 2, "profession": "minecraft:farmer", "exclude": true, "temperature": -4},
                  {"id": "test:explicit-male", "gender": 1, "temperature": 3},
                  {"id": "test:skins/clothing/female/legacy"},
                  {"id": "test:invalid", "gender": "not-a-gender"}
                ]
                """);
        var expected = new HashMap<String, Clothing>();
        SkinCatalogLoader.addClothing(expected, fileId, json);
        ClothingList reloader = ClothingList.getInstance() == null ? new ClothingList() : ClothingList.getInstance();
        var old = new HashMap<>(reloader.clothing);
        try {
            reloader.apply(Map.of(fileId, json), null, null);
            assertEquals(expected.keySet(), reloader.clothing.keySet());
            assertEquals(3, reloader.clothing.size());
            assertFalse(reloader.clothing.containsKey("test:invalid"));
            for (String id : expected.keySet()) {
                Clothing bundled = expected.get(id);
                Clothing reloaded = reloader.clothing.get(id);
                assertEquals(bundled.getGender(), reloaded.getGender());
                assertEquals(bundled.profession, reloaded.profession);
                assertEquals(bundled.exclude, reloaded.exclude);
                assertEquals(bundled.temperature, reloaded.temperature);
            }
            Clothing female = reloader.clothing.get("test:plain");
            assertEquals(Gender.FEMALE, female.getGender());
            assertEquals("minecraft:farmer", female.profession);
            assertTrue(female.exclude);
            assertEquals(-4, female.temperature);
            assertEquals(Gender.MALE, reloader.clothing.get("test:explicit-male").getGender());
            assertEquals(Gender.FEMALE, reloader.clothing.get("test:skins/clothing/female/legacy").getGender());
        } finally {
            reloader.clothing.clear();
            reloader.clothing.putAll(old);
        }
    }

    @Test
    void bodySkinsKeepChanceAndLegacyGenderInBothLoaders() {
        Identifier fileId = Identifier.parse("mca:skins/body/skins");
        var json = JsonParser.parseString("""
                [
                  {"id": "test:body/one", "gender": 1, "chance": 0.25},
                  {"id": "test:skins/body/female/old", "chance": 0.7},
                  {"id": "test:skin/neutral", "gender": "neutral"}
                ]
                """);
        var expected = new HashMap<String, BodySkin>();
        SkinCatalogLoader.addBodySkins(expected, fileId, json);
        BodySkinList reloader = BodySkinList.getInstance() == null ? new BodySkinList() : BodySkinList.getInstance();
        var old = new HashMap<>(reloader.skins);
        try {
            reloader.apply(Map.of(fileId, json), null, null);
            assertEquals(expected.keySet(), reloader.skins.keySet());
            assertEquals(3, reloader.skins.size());
            for (String id : expected.keySet()) {
                assertEquals(expected.get(id).getGender(), reloader.skins.get(id).getGender());
                assertEquals(expected.get(id).getChance(), reloader.skins.get(id).getChance());
            }
            assertEquals(Gender.MALE, reloader.get("test:body/one").getGender());
            assertEquals(0.25f, reloader.get("test:body/one").getChance());
            assertEquals(Gender.FEMALE, reloader.get("test:skins/body/female/old").getGender());
            assertEquals(Gender.NEUTRAL, reloader.get("test:skin/neutral").getGender());
        } finally {
            reloader.skins.clear();
            reloader.skins.putAll(old);
        }
    }

    @Test
    void hairStyleReloadMatchesSharedFallbackAndLayers() {
        Identifier fileId = Identifier.parse("mca:skins/hair_styles/skin");
        var json = JsonParser.parseString("""
                {
                  "test:plain": {"base": "test:base", "front": "test:front", "chance": 0.6},
                  "mca:skins/hair/male/old": {"back": "test:back"},
                  "test:female": {"gender": "female", "bangs": "test:bangs"}
                }
                """);
        var expected = new HashMap<String, HairStyle>();
        SkinCatalogLoader.addHairStyles(expected, fileId, json);
        HairStyleList reloader = HairStyleList.getInstance() == null ? new HairStyleList() : HairStyleList.getInstance();
        var old = new HashMap<>(reloader.styles);
        try {
            reloader.apply(Map.of(fileId, json), null, null);
            assertEquals(expected.keySet(), reloader.styles.keySet());
            for (String id : expected.keySet()) {
                HairStyle bundled = expected.get(id);
                HairStyle reloaded = reloader.get(id);
                assertEquals(bundled.getGender(), reloaded.getGender());
                assertEquals(bundled.getChance(), reloaded.getChance());
                for (LayeredHair.Category category : LayeredHair.Category.values()) {
                    assertEquals(bundled.layer(category), reloaded.layer(category));
                }
            }
            assertEquals(Gender.NEUTRAL, reloader.get("test:plain").getGender());
            assertEquals("test:front", reloader.get("test:plain").front());
            assertEquals(Gender.MALE, reloader.get("mca:skins/hair/male/old").getGender());
            assertEquals(Gender.FEMALE, reloader.get("test:female").getGender());
        } finally {
            reloader.styles.clear();
            reloader.styles.putAll(old);
        }
    }

    @Test
    void layeredHairReloadMatchesSharedLoader() {
        Identifier fileId = Identifier.parse("mca:hair_layers/variants/back/seasonal");
        List<String> textures = List.of("mca:skins/layered_hair/back/1", "bad id !", "mca:wrong/path");
        var expected = new HashMap<String, LayeredHair>();
        SkinCatalogLoader.addLayeredHair(expected, fileId, textures);
        LayeredHairList reloader = LayeredHairList.getInstance() == null ? new LayeredHairList() : LayeredHairList.getInstance();
        var old = new HashMap<>(reloader.hair);
        try {
            reloader.apply(Map.of(fileId, textures), null, null);
            assertEquals(expected.keySet(), reloader.hair.keySet());
            assertEquals(1, reloader.hair.size());
            LayeredHair entry = reloader.hair.get("mca:skins/layered_hair/back/1|neutral|back");
            assertEquals("mca:skins/layered_hair/back/1", entry.getIdentifier());
            assertEquals(Gender.NEUTRAL, entry.getGender());
            assertEquals(LayeredHair.Category.BACK, entry.getCategory());
            assertEquals(1.0f, entry.getChance());
        } finally {
            reloader.hair.clear();
            reloader.hair.putAll(old);
        }
    }
}
