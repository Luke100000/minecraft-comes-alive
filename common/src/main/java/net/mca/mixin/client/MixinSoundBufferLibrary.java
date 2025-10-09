package net.mca.mixin.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import net.mca.client.tts.AudioCache;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class MixinSoundBufferLibrary {
    @Inject(method = "getStream(Lnet/minecraft/resources/ResourceLocation;Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
    void mca$injectLoadStreamed(ResourceLocation id, boolean repeatInstantly, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (id.getPath().startsWith("sounds/tts_cache/")) {
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                String identifier = id.getPath().substring(17, id.getPath().length() - 4);
                if (identifier.endsWith(".ogg")) {
                    // Persistent OGG file
                    try {
                        InputStream inputStream = new FileInputStream("tts_cache/" + identifier);
                        return repeatInstantly ? new LoopingAudioStream(OggAudioStream::new, inputStream) : new OggAudioStream(inputStream);
                    } catch (IOException iOException) {
                        throw new CompletionException(iOException);
                    }
                } else {
                    // PCM audio (Which can be in memory or on disk)
                    return AudioCache.getPCMAudioStream(identifier);
                }
            }, Util.backgroundExecutor()));
        }
    }
}
