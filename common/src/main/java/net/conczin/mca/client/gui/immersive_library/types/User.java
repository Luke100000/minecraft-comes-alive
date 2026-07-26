package net.conczin.mca.client.gui.immersive_library.types;

import java.util.LinkedList;
import java.util.List;

public record User(int userid, String username, int likes_received, List<LiteContent> likes,
                   List<LiteContent> submissions, boolean moderator) {
    public User(int userid, String username, int likes_received, List<LiteContent> likes, List<LiteContent> submissions, boolean moderator) {
        this.userid = userid;
        this.username = username;
        this.likes_received = likes_received;
        this.likes = new LinkedList<>(likes);
        this.submissions = new LinkedList<>(submissions);
        this.moderator = moderator;
    }
}
