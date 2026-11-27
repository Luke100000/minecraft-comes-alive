package net.conczin.mca.client.resources;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.EyeGenetics;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class EyeTextureLayersTest {
    @BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    private static VillagerLike<?> preview(CompoundTag data) {
        return (VillagerLike<?>) Proxy.newProxyInstance(VillagerLike.class.getClassLoader(), new Class<?>[]{VillagerLike.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getTrackedValue" -> ((CParameter<?, ?>) args[0]).load(data, RegistryAccess.EMPTY);
                case "getGenetics" -> new Genetics((VillagerLike<?>) proxy);
                case "getTraits" -> new Traits((VillagerLike<?>) proxy);
                case "getEyeDye" -> data.contains("EyeColor") ? data.getInt("EyeColor") : -1;
                case "getEyeLeftDye" -> data.contains("EyeColorLeft") ? data.getInt("EyeColorLeft") : -1;
                default -> throw new AssertionError("read-only appearance tried " + method.getName());
            };
        });
    }
    @Test void clientWithoutInitialSnapshotDoesNotInitializeGenetics() {
        CompoundTag snapshot = new CompoundTag(); snapshot.putFloat("GeneEyeColor", 0.6F);
        var villager = preview(snapshot); var before = snapshot.copy();
        CompoundTag legacy = new CompoundTag(); legacy.putFloat("Scalar", 0.6F);
        assertEquals(EyeGenetics.resolveLegacyNatural(legacy, false, false, false), EyeTextureLayers.getStaticEyeColor(villager, false));
        assertEquals(before, snapshot);
    }
    @Test void dyesAlbinismAndLocalExpressionKeepExistingPrecedence() {
        var genotype = new EyeGenetics.Genotype(new EyeGenetics.Locus(0, 0), new EyeGenetics.Locus(1, 1));
        var local = new EyeGenetics.LocalExpression(0.4F, 0.1F);
        CompoundTag snapshot = new CompoundTag(); snapshot.put(EyeGenetics.KEY, EyeGenetics.encode(genotype, local, new CompoundTag()));
        var villager = preview(snapshot);
        assertEquals(EyeGenetics.resolveNatural(genotype, null, false), EyeTextureLayers.getStaticEyeColor(villager, false));
        CompoundTag traits = new CompoundTag(); traits.putBoolean(Traits.HETEROCHROMIA.getId().toString(), true); snapshot.put("Traits", traits);
        assertEquals(EyeGenetics.resolveNatural(genotype, local, true), EyeTextureLayers.getStaticEyeColor(villager, true));
        traits.putBoolean(Traits.ALBINISM.getId().toString(), true);
        assertEquals(0xFFE8A0A0, EyeTextureLayers.getStaticEyeColor(villager, false));
        snapshot.putInt("EyeColorLeft", 0xFF112233); snapshot.putInt("EyeColor", 0xFF445566);
        assertEquals(0xFF112233, EyeTextureLayers.getStaticEyeColor(villager, true));
        assertEquals(0xFF445566, EyeTextureLayers.getStaticEyeColor(villager, false));
        snapshot.putInt("EyeColor", -1);
        assertEquals(0xFFE8A0A0, EyeTextureLayers.getStaticEyeColor(villager, false));
    }
}
