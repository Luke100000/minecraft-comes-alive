package net.conczin.mca.client.gui.immersive_library.types;

import java.util.HashSet;
import java.util.Set;

public record LiteContent(
        int content_id,
        int user_id,
        String username,
        int likes,
        Set<String> tags,
        String title,
        int version,
        boolean is_liked,
        Set<String> reports
) implements Tagged {
    public LiteContent(int content_id, int user_id, String username, int likes, Set<String> tags, String title, int version, boolean is_liked, Set<String> reports) {
        this.content_id = content_id;
        this.user_id = user_id;
        this.username = username;
        this.likes = likes;
        this.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
        this.title = title;
        this.version = version;
        this.is_liked = is_liked;
        this.reports = reports == null ? new HashSet<>() : new HashSet<>(reports);
    }
}
