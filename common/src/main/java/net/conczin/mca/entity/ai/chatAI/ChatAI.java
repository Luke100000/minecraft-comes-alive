package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.server.level.ServerPlayer;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ChatAI {
    /** Max range to find a villager in */
    private static final int VILLAGER_SEARCH_RANGE = 32;

    /** Max time until a conversation is considered invalid */
    private static final int CONVERSATION_TIME = 20 * 120;

    /** Max distance until a conversation is considered invalid */
    private static final int CONVERSATION_DISTANCE = 16;

    /** Map of villager UUIDs to strategies (i.e. managed by InworldAI or GPT3) */
    private static final Map<UUID, ChatAIStrategy> strategies = new ConcurrentHashMap<>();

    /**
     * Current conversation of player. <p>
     * A player can max. have 1 conversation at all times.
     */
    private static final Map<UUID, OpenConversation> currentConversations = new ConcurrentHashMap<>();


    /**
     * Gets an answer for a specific message for a villager from a player with the villager-specific chat strategy
     * @param player ServerPlayerEntity of the player
     * @param villager VillagerEntityMCA of the villager
     * @param msg Message in question
     * @return {@code Optional.EMPTY} if answer couldn't be generated, Optional containing answer String otherwise.
     */
    public static CompletableFuture<Optional<String>> answerAsync(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        // Get villager-specific strategy
        ChatAIStrategy strategy = computeStrategyIfAbsent(villager.getUUID());

        // Get answer
        return strategy.answerAsync(player, villager, msg);
    }

    /**
     * Selects the exact villager a player is currently talking to.
     */
    public static void selectVillagerForConversation(ServerPlayer player, VillagerEntityMCA villager) {
        currentConversations.put(player.getUUID(), new OpenConversation(villager.getUUID(), villager.level().getGameTime()));
    }

    /**
     * Searches Config for a map entry for UUID, uses Inworld with said entry if found, else GPT3 (default)
     * @param villagerID UUID of villager
     * @return Object implementing the ChatAIStrategy interface
     */
    private static ChatAIStrategy computeStrategyIfAbsent(UUID villagerID) {
        return strategies.computeIfAbsent(villagerID, v -> {
            String inworldResourceName = Config.getInstance().inworldAIResourceNames.getOrDefault(v, "");
            return inworldResourceName.isEmpty() ? new OpenAIChatAI() : new InworldAI(inworldResourceName);
        });
    }

    /**
     * Clears the strategy for a specific villager
     * @param villagerID UUID of the villager
     */
    public static void clearStrategy(UUID villagerID) {
        strategies.remove(villagerID);
    }

    private static String getName(VillagerEntityMCA villager) {
        return normalizeString(villager.getName().getString());
    }

    /**
     * Resolves a nearby villager for chat. An explicit full name or nickname may switch the target;
     * otherwise the current conversation UUID is preferred. A unique partial name is only used to
     * bootstrap a conversation when no valid UUID target exists.
     * @param player The player in the conversation
     * @param msg The message
     * @return {@code Optional.Empty} if no valid villager was found, Optional containing the VillagerEntityMCA object otherwise
     */
    public static Optional<VillagerEntityMCA> getVillagerForConversation(ServerPlayer player, String msg) {
        UUID playerUUID = player.getUUID();
        // Get nearby villagers
        List<VillagerEntityMCA> nearbyVillagers = WorldUtils.getCloseEntities(player.level(), player, VILLAGER_SEARCH_RANGE, VillagerEntityMCA.class);

        // An explicit full name or nickname can switch the current conversation.
        String normalizedMsg = normalizeString(msg);
        Optional<VillagerEntityMCA> explicitlyMentionedVillager = findUniqueVillager(nearbyVillagers, villager -> {
            String nickname = villager.getNickname(playerUUID);
            return (!nickname.isEmpty() && containsWholeWord(normalizedMsg, normalizeString(nickname)))
                    || containsWholeWord(normalizedMsg, getName(villager));
        });
        if (explicitlyMentionedVillager.isPresent()) {
            return explicitlyMentionedVillager;
        }

        // Prefer the already-selected villager by UUID. This is set by direct villager interaction
        // and refreshed whenever a ChatAI request starts.
        OpenConversation conv = currentConversations.getOrDefault(playerUUID, new OpenConversation(playerUUID, 0L));
        Optional<VillagerEntityMCA> optionalVillager = nearbyVillagers.stream().filter(v -> conv.villagerUUID.equals(v.getUUID())).findFirst();
        if (optionalVillager.isPresent() && isInConversationWith(player, optionalVillager.get())) {
            return optionalVillager;
        }

        // With no selected villager, a unique partial name can bootstrap a conversation. If a
        // message happens to contain parts of multiple nearby names, do not guess based on entity
        // iteration order.
        return findUniqueVillager(nearbyVillagers, villager -> Arrays.stream(getName(villager).split(" "))
                .anyMatch(part -> containsWholeWord(normalizedMsg, part)));
    }

    private static Optional<VillagerEntityMCA> findUniqueVillager(List<VillagerEntityMCA> villagers, Predicate<VillagerEntityMCA> predicate) {
        VillagerEntityMCA match = null;
        for (VillagerEntityMCA villager : villagers) {
            if (!predicate.test(villager)) {
                continue;
            }
            if (match != null && match != villager) {
                return Optional.empty();
            }
            match = villager;
        }
        return Optional.ofNullable(match);
    }

    private static boolean containsWholeWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b")
                .matcher(text)
                .find();
    }

    /**
     * Checks if a player is in a conversation with a villager
     * @param player ServerPlayerEntity of the player to be checked
     * @param villager VillagerEntityMCA entity of the villager to be checked
     * @return {@code true} if all the following conditions are met: <p>
     *  1. Villager is within {@value CONVERSATION_DISTANCE} blocks of the player<p>
     *  2. Last conversation interaction with this villager wasn't longer than {@value CONVERSATION_TIME} ago
     */
    private static boolean isInConversationWith(ServerPlayer player, VillagerEntityMCA villager) {
        OpenConversation conversation = currentConversations.getOrDefault(player.getUUID(), new OpenConversation(villager.getUUID(), 0L));
        return villager.distanceTo(player) < CONVERSATION_DISTANCE
                && villager.level().getGameTime() < conversation.lastInteractionTime + CONVERSATION_TIME;
    }

    /**
     * Scans the local area in a {@value #VILLAGER_SEARCH_RANGE} block range of the player for a villager with searchName. <p>
     * searchName is {@link #normalizeString normalized}.
     * @param player ServerPlayerEntity object of the reference player
     * @param searchName Name of the villager
     * @return Optional containing the VillagerEntityMCA of the first villager with the matching name, empty Optional otherwise
     */
    public static Optional<VillagerEntityMCA> findVillagerInArea(ServerPlayer player, String searchName) {
        List<VillagerEntityMCA> entities = WorldUtils.getCloseEntities(player.level(), player, VILLAGER_SEARCH_RANGE, VillagerEntityMCA.class);

        // Get specific villager
        String normalizedSearchName = normalizeString(searchName);

        // Go through list, look for first match for name
        for (VillagerEntityMCA villager : entities) {
            String villagerName = getName(villager);
            if (normalizedSearchName.equals(villagerName)) {
                return Optional.of(villager);
            }
        }
        return Optional.empty();
    }

    /** Finds the nearest MCA villager available to the player for context editing. */
    public static Optional<VillagerEntityMCA> findClosestVillager(ServerPlayer player) {
        return WorldUtils.getCloseEntities(player.level(), player, VILLAGER_SEARCH_RANGE, VillagerEntityMCA.class).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    /**
     * Normalizes the String according to NFD and removes any accents, umlauts, etc.
     * @param string The String to be normalized
     * @see <a href="https://unicode.org/reports/tr15/#Examples">Unicode Normalization Forms</a>
     */
    private static String normalizeString(String string) {
        return Normalizer.normalize(string, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Information needed to manage an open conversation.
     * @param villagerUUID UUID of the villager the conversation is with
     * @param lastInteractionTime Timestamp of the last interaction with the villager
     */
    private record OpenConversation(UUID villagerUUID, Long lastInteractionTime) {}

}
