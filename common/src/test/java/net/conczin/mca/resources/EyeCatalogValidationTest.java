package net.conczin.mca.resources;

import com.google.gson.JsonParser;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EyeCatalogValidationTest {
    @Test
    void malformedWeightsDoNotHideValidEyes() {
        var entries = SkinListJson.entries(ResourceLocation.parse("mca:skins/eyes/normal"), JsonParser.parseString("""
                [{"id":"mca:skins/face/normal/1.png","chance":{}},
                 {"id":"mca:skins/face/normal/2.png","chance":1e100},
                 {"id":"mca:skins/face/normal/3.png","chance":2}]
                """));
        var eyes = new HashMap<ResourceLocation, EyeDefinition>();
        AppearanceCatalogLoader.addEyes(eyes, ResourceLocation.parse("mca:skins/eyes/normal"), entries);
        var valid = ResourceLocation.parse("mca:skins/face/normal/3.png");
        assertEquals(List.of(valid), List.copyOf(eyes.keySet()));
        assertEquals(2.0F, eyes.get(valid).chance());
    }

    @Test
    void malformedIdentifierMetadataDoesNotDiscardTheWholeList() {
        var entries = SkinListJson.entries(ResourceLocation.parse("mca:skins/eyes/normal"), JsonParser.parseString("""
                [{"id":{}}, {"id":null}, {"id":"mca:skins/face/normal/3.png"}]
                """));
        assertEquals(List.of("mca:skins/face/normal/3.png"),
                entries.stream().map(SkinListJson.Entry::identifier).toList());
    }

    @Test
    void nonFiniteWeightsAreRejectedAtConstruction() {
        var id = ResourceLocation.parse("mca:skins/face/normal/0.png");
        assertThrows(IllegalArgumentException.class, () -> new EyeDefinition(id, Gender.NEUTRAL, Float.NaN, java.util.Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EyeDefinition(id, Gender.NEUTRAL, Float.POSITIVE_INFINITY, java.util.Map.of()));
    }

    @Test
    void numericAndNamedIdentifiersHaveTransitiveOrder() {
        String two = "mca:skins/face/b/2.png";
        String ten = "mca:skins/face/a/10.png";
        String named = "mca:skins/face/aa/custom.png";
        assertTrue(SkinListEntry.compareIdentifiers(two, ten) < 0);
        assertEquals(Integer.signum(SkinListEntry.compareIdentifiers(two, named)),
                Integer.signum(SkinListEntry.compareIdentifiers(ten, named)));
        assertTrue(SkinListEntry.compareIdentifiers(ten, "mca:skins/face/a/100000000000000000000.png") < 0);
    }

    @Test
    void maleEditorAllFilterOnlyOffersCompatibleEyes() {
        var neutral = ResourceLocation.parse("mca:skins/face/normal/0.png");
        var male = ResourceLocation.parse("mca:skins/face/normal/male/1.png");
        var female = ResourceLocation.parse("mca:skins/face/normal/female/2.png");
        var eyes = Map.of(neutral, new EyeDefinition(neutral, Gender.NEUTRAL, 1, Map.of()),
                male, new EyeDefinition(male, Gender.MALE, 1, Map.of()),
                female, new EyeDefinition(female, Gender.FEMALE, 1, Map.of()));
        assertEquals(List.of(neutral, male), EyeSelection.idsForEditor(eyes, Gender.MALE, Gender.UNASSIGNED));
        assertEquals(List.of(neutral, female), EyeSelection.idsForEditor(eyes, Gender.FEMALE, Gender.UNASSIGNED));
        assertEquals(List.of(), EyeSelection.idsForEditor(eyes, Gender.MALE, Gender.FEMALE));
        assertEquals(List.of(neutral), EyeSelection.idsForEditor(eyes, Gender.MALE, Gender.NEUTRAL));
    }
}
