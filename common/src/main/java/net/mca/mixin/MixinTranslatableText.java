package net.mca.mixin;

import net.mca.entity.CommonSpeechManager;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslatableTextContent.class)
public class MixinTranslatableText {
    @Inject(method = "updateTranslations()V", at = @At("TAIL"))
    private void mca$updateTranslations(CallbackInfo ci) {
        if (CommonSpeechManager.INSTANCE.lastResolvedKey != null) {
            CommonSpeechManager.INSTANCE.translations.put((TextContent)this, CommonSpeechManager.INSTANCE.lastResolvedKey);
        }
    }
}
