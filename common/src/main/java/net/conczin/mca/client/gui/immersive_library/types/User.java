package net.conczin.mca.client.gui.immersive_library.types;

public record User(int userid, String username, int submission_count, int likes_given, int likes_received,
                   boolean moderator) {
}
