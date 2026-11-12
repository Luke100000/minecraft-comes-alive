package net.conczin.mca.client.gui.immersive_library.types;

import java.util.Set;

public record Content(
        int content_id,
        int user_id,
        String username,
        int likes,
        Set<String> tags,
        String title,
        int version,
        boolean is_liked,
        Set<String> reports,
        String meta,
        String data
) {
    public LiteContent lite() {
        return new LiteContent(content_id, user_id, username, likes, tags, title, version, is_liked, reports);
    }
}
