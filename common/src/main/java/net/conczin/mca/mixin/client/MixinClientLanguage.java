package net.conczin.mca.mixin.client;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.CommonSpeechManager;
import net.conczin.mca.entity.ai.DialogueType;
import net.conczin.mca.resources.data.SerializablePair;
import net.conczin.mca.util.localization.PooledTranslationStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = ClientLanguage.class, priority = 990)
abstract class MixinClientLanguage extends Language {
    @Shadow
    @Final
    private Map<String, String> storage;
    @Unique
    private PooledTranslationStorage mca$pool;

    @Shadow
    public abstract @NotNull String getOrDefault(String key, String fallback);

    @Unique
    private PooledTranslationStorage mca$getPool() {
        if (mca$pool == null) {
            mca$pool = new PooledTranslationStorage(storage);
            MCA.storage = storage;
            MCA.language = Minecraft.getInstance().options.languageCode;
        }
        return mca$pool;
    }

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void mca$injectGetOrDefault(String key, String fallback, CallbackInfoReturnable<String> info) {
        String modifiedKey = DialogueType.applyFallback(key);

        SerializablePair<String, String> unpooled = mca$getPool().get(modifiedKey);
        if (unpooled != null) {
            CommonSpeechManager.INSTANCE.lastResolvedKey = unpooled.left();
            if (storage.containsKey(unpooled.left()) && !storage.get(unpooled.left()).equals(unpooled.right())) {
                // Dynamically generated lines bypass vanilla lookup so template substitutions survive.
                info.setReturnValue(unpooled.right());
            } else {
                info.setReturnValue(getOrDefault(unpooled.left(), fallback));
            }
        } else if (!key.equals(modifiedKey)) {
            info.setReturnValue(getOrDefault(modifiedKey, fallback));
        }
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    public void mca$injectHas(String key, CallbackInfoReturnable<Boolean> info) {
        if (mca$getPool().contains(key)) {
            info.setReturnValue(true);
        }
    }
}
