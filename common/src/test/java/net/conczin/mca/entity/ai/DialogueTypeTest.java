package net.conczin.mca.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueTypeTest {
    private Language previousLanguage;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installDialogueLanguage() {
        previousLanguage = Language.getInstance();
        Language.inject(new TestLanguage(previousLanguage, Map.of(
                "flirty.dialogue.greet/1", "Generic flirty greeting",
                "male.adult.dialogue.greet/1", "Ordinary adult greeting",
                "male.adultp.dialogue.greet/1", "Hey dad!"
        )));
    }

    @AfterEach
    void restoreLanguage() {
        Language.inject(previousLanguage);
    }

    @Test
    void flirtyAdultChildUsesParentDialogueInsteadOfGenericFlirtyFallback() {
        String key = "#Gmale.#Emca:flirty.#TADULTP.dialogue.greet/1";

        assertEquals("male.adultp.dialogue.greet/1", DialogueType.applyFallback(key, () -> true));
    }

    @Test
    void flirtyUnrelatedAdultStillUsesPersonalityDialogue() {
        String key = "#Gmale.#Emca:flirty.#TADULT.dialogue.greet/1";

        assertEquals("flirty.dialogue.greet/1", DialogueType.applyFallback(key, () -> true));
    }

    @Test
    void disabledPersonalityTranslationsUseTheOrdinaryGreeting() {
        String key = "#Gmale.#Emca:flirty.#TADULT.dialogue.greet/1";

        assertEquals("male.adult.dialogue.greet/1", DialogueType.applyFallback(key, () -> false));
    }

    private static final class TestLanguage extends Language {
        private final Language delegate;
        private final Map<String, String> overrides;

        private TestLanguage(Language delegate, Map<String, String> overrides) {
            this.delegate = delegate;
            this.overrides = overrides;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return overrides.getOrDefault(key, delegate.getOrDefault(key, fallback));
        }

        @Override
        public boolean has(String key) {
            return overrides.containsKey(key) || delegate.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return delegate.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return delegate.getVisualOrder(text);
        }
    }
}
