package net.mca.mixin;

import net.mca.entity.CommonSpeechManager;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslatableContents.class)
public class MixinTranslatableText {
    @Inject(method = "decompose()V", at = @At("TAIL"))
    private void mca$updateTranslations(CallbackInfo ci) {
        if (CommonSpeechManager.INSTANCE.lastResolvedKey != null) {
            CommonSpeechManager.INSTANCE.translations.put((ComponentContents)this, CommonSpeechManager.INSTANCE.lastResolvedKey);
        }
    }
}
