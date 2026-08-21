package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.CommonSpeechManager;
import net.conczin.mca.entity.ai.DialogueType;
import net.conczin.mca.util.localization.PooledTranslationStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = ClientLanguage.class, priority = 990)
abstract class MixinTranslationStorage extends Language {
    @Shadow
    private @Final Map<String, String> storage;

    @Shadow
    public abstract String getOrDefault(String key, String fallback);

    @Unique
    private PooledTranslationStorage mca$pool;

    @Unique
    private Map<String, String> mca$poolStorage;

    @Unique
    private PooledTranslationStorage mca$getPool() {
        if (mca$pool == null || mca$poolStorage != storage) {
            mca$poolStorage = storage;
            mca$pool = new PooledTranslationStorage(storage);
            MCA.translations = storage;
            MCA.language = Minecraft.getInstance().options.languageCode;
        }
        return mca$pool;
    }

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void mca$onGet(String key, String fallback, CallbackInfoReturnable<String> info) {
        String modifiedKey = DialogueType.applyFallback(key);

        Tuple<String, String> unpooled = mca$getPool().get(modifiedKey);
        if (unpooled != null) {
            CommonSpeechManager.INSTANCE.lastResolvedKey = unpooled.getA();
            if (storage.containsKey(unpooled.getA()) && !storage.get(unpooled.getA()).equals(unpooled.getB())) {
                // In this case, the text has been dynamically created and we need to return directly
                info.setReturnValue(unpooled.getB());
            } else {
                info.setReturnValue(getOrDefault(unpooled.getA(), fallback));
            }
        } else if (!key.equals(modifiedKey)) {
            info.setReturnValue(getOrDefault(modifiedKey, fallback));
        }
    }

    @ModifyReturnValue(method = "has(Ljava/lang/String;)Z", at = @At("RETURN"))
    public boolean mca$includePooledTranslations(boolean original, String key) {
        return original || mca$getPool().contains(key);
    }
}
