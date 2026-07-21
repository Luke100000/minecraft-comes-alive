package net.conczin.mca.client.gui.immersive_library.types;

import java.util.HashSet;
import java.util.Set;

public record LiteContent(int contentid, int userid, String username, int likes, Set<String> tags, String title,
                          int version, boolean is_liked) implements Tagged {
    public LiteContent(int contentid, int userid, String username, int likes, Set<String> tags, String title, int version, boolean is_liked) {
        this.contentid = contentid;
        this.userid = userid;
        this.username = username;
        this.likes = likes;
        this.tags = new HashSet<>(tags);
        this.title = title;
        this.version = version;
        this.is_liked = is_liked;
    }

    public LiteContent withLiked(boolean liked) {
        int updatedLikes = likes + (liked == is_liked ? 0 : liked ? 1 : -1);
        return new LiteContent(contentid, userid, username, Math.max(0, updatedLikes), tags, title, version, liked);
    }
}
