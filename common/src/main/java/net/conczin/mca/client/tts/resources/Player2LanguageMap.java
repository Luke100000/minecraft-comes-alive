package net.conczin.mca.client.tts.resources;

import java.util.HashMap;
import java.util.Map;

public class Player2LanguageMap {
    public static final Map<String, String> LANGUAGE_MAP = new HashMap<>();

    static {
        LANGUAGE_MAP.put("en_us", "american_english");
        LANGUAGE_MAP.put("en_gb", "british_english");
        LANGUAGE_MAP.put("ja_jp", "japanese");
        LANGUAGE_MAP.put("zh_cn", "mandarin_chinese");
        LANGUAGE_MAP.put("es_es", "spanish");
        LANGUAGE_MAP.put("fr_fr", "french");
        LANGUAGE_MAP.put("hi_in", "hindi");
        LANGUAGE_MAP.put("it_it", "italian");
        LANGUAGE_MAP.put("pt_br", "brazilian_portuguese");
    }
}
