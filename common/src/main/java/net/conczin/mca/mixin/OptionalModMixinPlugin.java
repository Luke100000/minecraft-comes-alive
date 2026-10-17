package net.conczin.mca.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects optional integrations without loading either loader's API during Mixin startup. */
public final class OptionalModMixinPlugin implements IMixinConfigPlugin {
    // Match each config's mixin package to a class provided by its optional dependency.
    private static final Map<String, OptionalMixins> INTEGRATIONS = Map.of(
            "net.conczin.mca.mixin.compat",
            new OptionalMixins("fuzs.opentogether.common.world.level.block.DoubleFenceGateLogic",
                    List.of("MixinOpenTogetherFenceGate"), false),
            "net.conczin.mca.mixin.client.journeymap",
            new OptionalMixins("journeymap.client.render.draw.DrawEntityStep",
                    List.of("MixinDrawEntityStep", "MixinEntityDTO"), true)
    );

    private OptionalMixins integration;

    @Override
    public void onLoad(String mixinPackage) {
        integration = INTEGRATIONS.get(mixinPackage);
        if (integration == null) {
            throw new IllegalArgumentException("Unknown optional mixin package: " + mixinPackage);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        if (integration.clientOnly()
                && MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) {
            return List.of();
        }

        try {
            MixinService.getService().getBytecodeProvider().getClassNode(integration.targetClass());
            return integration.mixins();
        } catch (ClassNotFoundException e) {
            return List.of();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to check optional mixin target " + integration.targetClass(), e);
        }
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private record OptionalMixins(String targetClass, List<String> mixins, boolean clientOnly) {
    }
}
