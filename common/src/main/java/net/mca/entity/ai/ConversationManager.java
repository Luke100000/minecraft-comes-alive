package net.mca.entity.ai;


import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.text.MutableText;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConversationManager {
    private final VillagerEntityMCA entity;

    private final Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();

    public ConversationManager(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public void addMessage(Entity receiver, MutableText message) {
        addMessage(new TextMessage(receiver, message));
    }

    public void addMessage(Message message) {
        pendingMessages.add(message);
        message.entity = entity;
    }

    /**
     * Gets the first valid message from {@link #pendingMessages}. <p>
     * Removes any invalid message it encounters before
     * @return {@code Optional.EMPTY} if no message was found, Optional containing message otherwise
     */
    public Optional<Message> getCurrentMessage() {
        Message message = pendingMessages.peek();
        if (message == null) {
            return Optional.empty();
        }

        if (message.stillValid()) {
            return Optional.of(message);
        }

        // Remove message if not valid
        pendingMessages.remove(message);

        // Try to get valid message
        return getCurrentMessage();
    }

    public abstract static class Message {
        private final Entity receiver;
        VillagerEntityMCA entity;

        public final int validUntil;
        public static final int TIME_VALID = 20 * 60 * 5;
        private boolean delivered = false;

        private Message(Entity receiver) {
            this.receiver = receiver;
            this.validUntil = receiver.age + TIME_VALID;
        }

        public Entity getReceiver() {
            return receiver;
        }

        public void deliver() {
            delivered = true;
        }

        public boolean stillValid() {
            return !delivered && !receiver.isRemoved() && receiver.age < validUntil;
        }
    }

    public static class TextMessage extends Message {
        private final MutableText text;

        public TextMessage(Entity receiver, MutableText text) {
            super(receiver);
            this.text = text;
        }

        @Override
        public void deliver() {
            this.entity.sendChatToAllAround(text);
            super.deliver();
        }
    }

    public static class PhraseText extends Message {
        private final String text;

        public PhraseText(Entity receiver, String text) {
            super(receiver);
            this.text = text;
        }

        @Override
        public void deliver() {
            this.entity.sendChatToAllAround(text);
            super.deliver();
        }
    }
}
