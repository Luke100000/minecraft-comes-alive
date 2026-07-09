package net.mca.client.tts;

import java.util.List;
import java.util.Random;

public final class VoicePreviewSamples {
    private static final List<String> SAMPLES = List.of(
            "dialogue.main/1",
            "dialogue.main/2",
            "dialogue.main/3",
            "dialogue.main/4",
            "dialogue.main/5",
            "dialogue.greet/3",
            "dialogue.greet/4",
            "dialogue.greet/8",
            "dialogue.greet/9",
            "dialogue.greet.success/3"
    );

    private VoicePreviewSamples() {
    }

    public static String random(Random random) {
        return SAMPLES.get(random.nextInt(SAMPLES.size()));
    }
}
