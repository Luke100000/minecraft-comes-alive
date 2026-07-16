package net.conczin.mca.client.model;

import net.conczin.mca.mixin.client.MixinModelPartAccessor;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import java.util.Map;

/**
 * Bakes MCA's animation source through Minecraft's normal model layer path.
 * Compatibility mods such as EMF can intercept that bake without becoming a hard dependency.
 */
public final class McaModelLayerBaker {
    private McaModelLayerBaker() {
    }

    public static ModelPart bakeAnimationRoot(EntityRendererProvider.Context context, MeshDefinition mcaMesh) {
        // Visible MCA models use the wide player arm layout, so copied pivots must match it.
        ModelPart animationRoot = context.bakeLayer(ModelLayers.PLAYER);
        ModelPart mcaRoot = LayerDefinition.create(mcaMesh, 64, 64).bakeRoot();

        // Keep the externally baked hierarchy intact and add only MCA-specific root parts.
        Map<String, ModelPart> children = ((MixinModelPartAccessor) (Object) animationRoot).mca$getChildren();
        children.putIfAbsent(VillagerEntityBaseModelMCA.BREASTS, mcaRoot.getChild(VillagerEntityBaseModelMCA.BREASTS));
        children.putIfAbsent(VillagerEntityModelMCA.BREASTPLATE, mcaRoot.getChild(VillagerEntityModelMCA.BREASTPLATE));

        return animationRoot;
    }
}
