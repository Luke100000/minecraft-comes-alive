package net.conczin.mca.client.gui.immersive_library.types;

import java.util.HashSet;
import java.util.Set;

public record Content(int contentid, int userid, String username, int likes, Set<String> tags, String title,
                      int version, String meta, String data) implements Tagged {
    public Content(int contentid, int userid, String username, int likes, Set<String> tags, String title, int version, String meta, String data) {
        this.contentid = contentid;
        this.userid = userid;
        this.username = username;
        this.likes = likes;
        this.tags = new HashSet<>(tags);
        this.title = title;
        this.version = version;
        this.meta = meta;
        this.data = data;
    }
}
