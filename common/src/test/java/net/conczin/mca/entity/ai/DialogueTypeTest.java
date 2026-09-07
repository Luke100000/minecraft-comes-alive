package net.conczin.mca.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueTypeTest {
    private Language previousLanguage;
    private Minecraft previousMinecraft;
    private ReloadableResourceManager resourceManager;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installDialogueLanguage() throws Exception {
        previousMinecraft = Minecraft.getInstance();
        Minecraft minecraft = allocate(Minecraft.class);
        Options options = allocate(Options.class);
        options.languageCode = "en_us";
        resourceManager = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        setField(minecraft, "options", options);
        setField(minecraft, "resourceManager", resourceManager);
        setStaticField(Minecraft.class, "instance", minecraft);

        previousLanguage = Language.getInstance();
        Language.inject(new TestLanguage(previousLanguage, Map.of(
                "flirty.dialogue.greet/1", "Generic flirty greeting",
                "male.adultp.dialogue.greet/1", "Hey dad!"
        )));
    }

    @AfterEach
    void restoreLanguage() throws Exception {
        Language.inject(previousLanguage);
        resourceManager.close();
        setStaticField(Minecraft.class, "instance", previousMinecraft);
    }

    @Test
    void flirtyAdultChildUsesParentDialogueInsteadOfGenericFlirtyFallback() {
        String key = "#Gmale.#Emca:flirty.#TADULTP.dialogue.greet/1";

        assertEquals("male.adultp.dialogue.greet/1", DialogueType.applyFallback(key));
    }

    @Test
    void flirtyUnrelatedAdultStillUsesPersonalityDialogue() {
        String key = "#Gmale.#Emca:flirty.#TADULT.dialogue.greet/1";

        assertEquals("flirty.dialogue.greet/1", DialogueType.applyFallback(key));
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStaticField(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
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
