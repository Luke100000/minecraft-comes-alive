package net.conczin.mca.client.resources;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinExporterTest {
    @BeforeAll
    static void bootstrap() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void malformedStoredSkinFallsBackToGenetics() {
        assertEquals(ResourceLocation.parse("mca:skins/skin/female/2.png"),
                SkinExporter.getSkin(villagerWithSkin("not a resource identifier")));
    }

    @Test
    void validCustomSkinIsPreserved() {
        assertEquals(ResourceLocation.parse("example:skins/custom.png"),
                SkinExporter.getSkin(villagerWithSkin("example:skins/custom.png")));
    }

    private static VillagerLike<?> villagerWithSkin(String skin) {
        return (VillagerLike<?>) Proxy.newProxyInstance(VillagerLike.class.getClassLoader(),
                new Class<?>[]{VillagerLike.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getSkin" -> skin;
                    case "getGenetics" -> new Genetics((VillagerLike<?>) proxy);
                    case "getTrackedValue" -> args[0] == Genetics.SKIN.getParam() ? 0.5f : Gender.FEMALE;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
