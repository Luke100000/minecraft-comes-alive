package net.conczin.mca.resources.data.skin;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.s2c.CustomSkinListResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinListEntrySerializationTest {
    @Test
    void customSkinListResponseRoundTripsAllSkinTypes() throws Exception {
        HashMap<String, Clothing> clothing = new HashMap<>();
        clothing.put("test:clothing", new Clothing("test:clothing", null, 0, false, Gender.NEUTRAL));

        HashMap<String, BodySkin> bodySkins = new HashMap<>();
        bodySkins.put("test:body", new BodySkin("test:body", Gender.NEUTRAL, 1.0f));

        HashMap<String, LayeredHair> layeredHair = new HashMap<>();
        layeredHair.put("test:layer", new LayeredHair("test:layer", Gender.NEUTRAL, LayeredHair.Category.BASE, 1.0f));

        HashMap<String, HairStyle> hairStyles = new HashMap<>();
        hairStyles.put("test:style", HairStyle.singleLayer("test:style", Gender.NEUTRAL, 1.0f));

        HashMap<String, Hair> hair = new HashMap<>();
        hair.put("test:hair", new Hair("test:hair"));

        CustomSkinListResponse original = new CustomSkinListResponse(clothing, bodySkins, layeredHair, hairStyles, hair);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        CustomSkinListResponse decoded;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            decoded = (CustomSkinListResponse) input.readObject();
        }

        assertEquals("test:clothing", decoded.clothing().get("test:clothing").getIdentifier());
        assertEquals("test:body", decoded.bodySkins().get("test:body").getIdentifier());
        assertEquals("test:layer", decoded.layeredHair().get("test:layer").getIdentifier());
        assertEquals("test:style", decoded.hairStyles().get("test:style").getIdentifier());
        assertEquals("test:hair", decoded.hair().get("test:hair").getIdentifier());
    }
}
