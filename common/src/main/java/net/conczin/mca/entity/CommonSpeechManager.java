package net.conczin.mca.entity;

import net.conczin.mca.util.LimitedLinkedHashMap;
import net.minecraft.network.chat.ComponentContents;

public class CommonSpeechManager {
    public static final CommonSpeechManager INSTANCE = new CommonSpeechManager();

    public String lastResolvedKey;
    public final LimitedLinkedHashMap<ComponentContents, String> translations = new LimitedLinkedHashMap<>(100);
}
