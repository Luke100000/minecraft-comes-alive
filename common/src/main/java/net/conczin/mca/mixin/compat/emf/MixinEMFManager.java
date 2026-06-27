package net.conczin.mca.mixin.compat.emf;

import net.conczin.mca.MCA;
import net.conczin.mca.client.model.ModelLayersMCA;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "traben.entity_model_features.EMFManager", remap = false)
public class MixinEMFManager {
    @ModifyVariable(method = "injectIntoModelRootGetter", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ModelLayerLocation mca$useFreshMovesPlayerModelId(ModelLayerLocation layer) {
        if (ModelLayersMCA.isSlimPlayerCompatLayer(layer)) {
            mca$logRemap(layer, ModelLayers.PLAYER_SLIM);
            return ModelLayers.PLAYER_SLIM;
        }
        if (ModelLayersMCA.isPlayerCompatLayer(layer)) {
            mca$logRemap(layer, ModelLayers.PLAYER);
            return ModelLayers.PLAYER;
        }
        return layer;
    }

    private static void mca$logRemap(ModelLayerLocation original, ModelLayerLocation replacement) {
        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("MCA EMF compat remapped model layer {} to {}", original, replacement);
        }
    }
}
