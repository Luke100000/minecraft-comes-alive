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
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void bundledSkinCollectionsRemainAvailable() {
        BuiltInSkinCatalog.Catalog catalog = BuiltInSkinCatalog.get();
        assertFalse(catalog.clothing().isEmpty());
        assertFalse(catalog.bodySkins().isEmpty());
        assertFalse(catalog.hairStyles().isEmpty());
        assertFalse(catalog.layeredHair().isEmpty());

        assertEquals(Gender.FEMALE,
                catalog.clothing().get("mca:skins/clothing/normal/female/none/0.png").getGender());
        assertEquals(Gender.MALE, catalog.bodySkins().get("mca:skins/skin/male/0.png").getGender());
        assertEquals(Gender.FEMALE, catalog.bodySkins().get("mca:skins/skin/female/0.png").getGender());
        assertEquals(Gender.NEUTRAL, catalog.hairStyles().get("mca:hair_style/layered/short_1").getGender());
        assertEquals("mca:skins/layered_hair/base/1.png",
                catalog.hairStyles().get("mca:hair_style/layered/short_1").base());
        assertEquals(LayeredHair.Category.BACK,
                catalog.layeredHair().get("mca:skins/layered_hair/back/1.png|neutral|back").getCategory());

        ClassLoader loader = BuiltInSkinCatalog.class.getClassLoader();
        for (String path : List.of("skins/body/skins.json", "skins/clothing/female.json",
                "skins/clothing/male.json", "skins/clothing/neutral.json",
                "skins/hair_styles/female.json", "skins/hair_styles/male.json",
                "skins/hair_styles/neutral.json", "hair_layers/back.json")) {
            assertNotNull(loader.getResource("data/mca/" + path), path);
        }
    }

    @Test
    void layerCollectionsRespectReplaceDirective() {
        Identifier fileId = Identifier.parse("mca:hair_layers/back.json");
        String base = "[\"mca:skins/layered_hair/back/old\"]";
        String replacement = "{\"replace\":true,\"textures\":[\"mca:skins/layered_hair/back/new\"]}";
        String additive = "{\"replace\":false,\"textures\":[\"mca:skins/layered_hair/back/new\"]}";

        Map<Identifier, List<String>> replaced = SkinListJson.textureCollections(
                twoPackManager(fileId, base, replacement), "hair_layers");
        assertEquals(List.of("mca:skins/layered_hair/back/new"),
                replaced.get(Identifier.parse("mca:back")));

        Map<Identifier, List<String>> appended = SkinListJson.textureCollections(
                twoPackManager(fileId, base, additive), "hair_layers");
        assertEquals(List.of("mca:skins/layered_hair/back/old", "mca:skins/layered_hair/back/new"),
                appended.get(Identifier.parse("mca:back")));

        LayeredHairList reloader = LayeredHairList.getInstance() == null ? new LayeredHairList() : LayeredHairList.getInstance();
        var old = new HashMap<>(reloader.hair);
        try {
            reloader.apply(replaced, null, null);
            assertFalse(reloader.hair.containsKey("mca:skins/layered_hair/back/old|neutral|back"));
            assertTrue(reloader.hair.containsKey("mca:skins/layered_hair/back/new|neutral|back"));
        } finally {
            reloader.hair.clear();
            reloader.hair.putAll(old);
        }
    }

    @Test
    void bundledStylesAndLayersAgreeWithReloaders() throws Exception {
        var builtIn = BuiltInSkinCatalog.get();
        var stylesJson = bundledJson("skins/hair_styles/neutral.json");
        var layersJson = bundledJson("hair_layers/back.json");
        HairStyleList styles = HairStyleList.getInstance() == null ? new HairStyleList() : HairStyleList.getInstance();
        LayeredHairList layers = LayeredHairList.getInstance() == null ? new LayeredHairList() : LayeredHairList.getInstance();
        var oldStyles = new HashMap<>(styles.styles);
        var oldLayers = new HashMap<>(layers.hair);
        try {
            styles.apply(Map.of(Identifier.parse("mca:skins/hair_styles/neutral"), stylesJson), null, null);
            layers.apply(Map.of(Identifier.parse("mca:hair_layers/back"),
                    SkinListJson.textureCollection(Identifier.parse("mca:hair_layers/back"), layersJson)), null, null);
            HairStyle bundledStyle = builtIn.hairStyles().get("mca:hair_style/layered/short_1");
            HairStyle reloadedStyle = styles.get("mca:hair_style/layered/short_1");
            assertEquals(bundledStyle.getGender(), reloadedStyle.getGender());
            assertEquals(bundledStyle.getChance(), reloadedStyle.getChance());
            for (LayeredHair.Category category : LayeredHair.Category.values()) {
                assertEquals(bundledStyle.layer(category), reloadedStyle.layer(category));
            }
            String key = "mca:skins/layered_hair/back/1.png|neutral|back";
            assertEquals(builtIn.layeredHair().get(key).getIdentifier(), layers.hair.get(key).getIdentifier());
            assertEquals(builtIn.layeredHair().get(key).getCategory(), layers.hair.get(key).getCategory());
        } finally {
            styles.styles.clear();
            styles.styles.putAll(oldStyles);
            layers.hair.clear();
            layers.hair.putAll(oldLayers);
        }
    }

    private static com.google.gson.JsonElement bundledJson(String relativePath) throws Exception {
        try (var reader = new InputStreamReader(
                BuiltInSkinCatalog.class.getClassLoader().getResourceAsStream("data/mca/" + relativePath),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static ResourceManager twoPackManager(Identifier fileId, String base, String higherPriority) {
        Resource baseResource = new Resource(pack("base"), () -> new ByteArrayInputStream(base.getBytes(StandardCharsets.UTF_8)));
        Resource overrideResource = new Resource(pack("higher"),
                () -> new ByteArrayInputStream(higherPriority.getBytes(StandardCharsets.UTF_8)));
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listResourceStacks")) {
                        @SuppressWarnings("unchecked")
                        Predicate<Identifier> filter = (Predicate<Identifier>) args[1];
                        return args[0].equals("hair_layers") && filter.test(fileId)
                                ? Map.of(fileId, List.of(baseResource, overrideResource)) : Map.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PackResources pack(String id) {
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
                new Class<?>[]{PackResources.class}, (proxy, method, args) -> {
                    if (method.getName().equals("packId")) {
                        return id;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
