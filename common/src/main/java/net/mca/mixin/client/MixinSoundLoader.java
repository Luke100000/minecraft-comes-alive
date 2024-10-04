package net.mca.mixin.client;

import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.RepeatingAudioStream;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundLoader.class)
public class MixinSoundLoader {
    @Inject(method = "loadStreamed(Lnet/minecraft/util/Identifier;Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
    void mca$injectLoadStreamed(Identifier id, boolean repeatInstantly, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (id.getPath().startsWith("sounds/tts_cache/")) {
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream inputStream = new FileInputStream(id.getPath().replace("sounds/", ""));
                    return repeatInstantly ? new RepeatingAudioStream(OggAudioStream::new, inputStream) : new OggAudioStream(inputStream);
                } catch (IOException iOException) {
                    throw new CompletionException(iOException);
                }
            }, Util.getMainWorkerExecutor()));
        }
    }
}
