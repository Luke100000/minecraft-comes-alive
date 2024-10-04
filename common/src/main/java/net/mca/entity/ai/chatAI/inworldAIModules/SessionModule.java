package net.mca.entity.ai.chatAI.inworldAIModules;

import com.google.gson.Gson;

import net.mca.MCA;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Requests;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Session;
import net.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to manage a session for Inworld Fully Managed Integration
 */
public class SessionModule {

    private final static String INWORLD_BASE_URL = "https://api.inworld.ai/v1/";
    private final static long SESSION_MAX_VALID_TIME = 28 * 60 * 1000;
    /** Determines how often status-update triggers are sent with requests. Reduces Interaction cost from (at time of writing) 2/answer to ~(21/20)/answer */
    private final static int STATUS_UPDATE_FREQUENCY = 20;

    private final String characterResourceName;
    private final String workspaceID;
    private final Gson gson;

    private final Map<UUID, OpenSession> openSessionMap = new ConcurrentHashMap<>();

    public SessionModule(String characterResourceName) {
        this.characterResourceName = characterResourceName;
        this.workspaceID = characterResourceName.split("/")[1];
        this.gson = new Gson();
    }

    /**
     * Sends a text message from player to the Inworld character and tries to get a response
     * Opens a new session if necessary
     * Sends the statusUpdate every {@value STATUS_UPDATE_FREQUENCY} messages in the session
     * @param player The player the message is from
     * @param msg The message
     * @param statusUpdate status-update event for the Character.
     * @return {@code Optional.EMPTY} if the request failed, Optional containing Interaction object otherwise
     */
    public Optional<Interaction> getResponse(ServerPlayerEntity player, String msg, TriggerEvent statusUpdate) {
        // Creates a new session if needed
        long currentTime = System.currentTimeMillis();
        if(!openSessionIfNeeded(player, currentTime)) {
            return Optional.empty();
        }

        // Gets current session for player with this character and makes request
        OpenSession openSession = openSessionMap.get(player.getUuid());

        // Sends the status update every x messages
        if (openSession.getInteractionCount() %  STATUS_UPDATE_FREQUENCY == 0) {
            sendTriggerRequest(openSession.getSession(), statusUpdate, player.getUuid().toString());
        }

        Optional<Interaction> interactionOptional = sendTextRequest(openSession.getSession(), msg);
        if (interactionOptional.isPresent()) {
            // Updates lastInteraction in openSessionMap
            openSession.updateSession(currentTime);
        }
        return interactionOptional;
    }

    /**
     * Makes an openSession request.
     * @param playerId Unique ID of player
     * @param playerName Name of player
     * @param playerGender Gender of player
     * @return {@code Optional.empty()} if error on request, Optional containing new Session data otherwise
     */
    private Optional<Session> openSessionRequest(String playerId, String playerName, String playerGender) {
        Requests.OpenSessionRequest.EndUserConfig config = new Requests.OpenSessionRequest.EndUserConfig(playerId, playerName, playerGender, null, null);
        Requests.OpenSessionRequest request = new Requests.OpenSessionRequest(this.characterResourceName, config);
        String requestBody = gson.toJson(request);
        // Make request
        String endpoint = INWORLD_BASE_URL + this.characterResourceName + ":openSession";
        Optional<String> response = Requests.makeRequest(endpoint, requestBody);
        if (response.isEmpty()) {
            return Optional.empty();
        }
        Session session = gson.fromJson(response.get(), Session.class);
        return Optional.of(session);
    }

    /**
     * Makes a sendText request to the Inworld API.
     * @param session Session object for the endpoint
     * @param message Player message used in the request
     * @return {@code Optional.EMPTY} if request failed, else Optional containing Interaction object with response data
     */
    private Optional<Interaction> sendTextRequest(Session session, String message) {
        Requests.SendTextRequest request = new Requests.SendTextRequest(message);
        String requestBody = gson.toJson(request);
        // Create endpoint
        String endpoint = INWORLD_BASE_URL + getSessionCharacterResourceName(session) + ":sendText";
        // Make request
        Optional<String> response = Requests.makeRequest(endpoint, requestBody, session.name());
        if (response.isEmpty()) {
            return Optional.empty();
        }
        // Parse the response
        Interaction data = gson.fromJson(response.get(), Interaction.class);
        return Optional.of(data);
    }

    /**
     * Makes a sendTrigger request to the Inworld API
     * @param session Session object for the endpoint
     * @param event The triggerEvent of the request
     * @param endUserId ID of the user
     * @return {@code Optional.EMPTY} if request failed, else Optional containing Interaction object with response data
     */
    private Optional<Interaction> sendTriggerRequest(Session session, TriggerEvent event, String endUserId) {
        Requests.SendTriggerRequest request = new Requests.SendTriggerRequest(event, endUserId);
        String requestBody = gson.toJson(request);
        // Create endpoint
        String endpoint = INWORLD_BASE_URL + getSessionCharacterResourceName(session) + ":sendTrigger";
        // Make request
        Optional<String> response = Requests.makeRequest(endpoint, requestBody, session.name());
        if (response.isEmpty()) {
            return Optional.empty();
        }
        // Parse the response
        Interaction data = gson.fromJson(response.get(), Interaction.class);
        return Optional.of(data);
    }

    /**
     * Creates the session character endpoint needed for sendText and sendTrigger requests.
     * Uses workspaceID, session name and session character name
     * @param session The session for the endpoint
     * @return A string representing the resource name for the session character.
     */
    private String getSessionCharacterResourceName(Session session) {
        return "workspaces/%s/sessions/%s/sessionCharacters/%s".formatted(workspaceID, session.name(), session.sessionCharacters()[0].character());
    }

    /**
     * Checks if the session has expired or doesn't exist. Tries to open a new session if it needs to.
     * @param player The player of the interaction
     * @param currentTime The time the session was opened
     * @return {@code true} if a valid session exists, {@code false} if creation of a new session failed
     */
    private boolean openSessionIfNeeded(ServerPlayerEntity player, long currentTime) {
        OpenSession openSession = openSessionMap.getOrDefault(player.getUuid(), new OpenSession(null, 0));
        if (openSession.getSession() == null || currentTime - openSession.getLastInteractionMillis() > SESSION_MAX_VALID_TIME) {
            Optional<Session> sessionOptional = openSessionRequest(player.getUuid().toString(),
                    player.getName().getString(),
                    PlayerSaveData.get(player).getGender().getDataName());

            if (sessionOptional.isEmpty()) {
                MCA.LOGGER.error("Failed to open Inworld session. Consult logs");
                return false;
            } else {
                OpenSession newSession = new OpenSession(sessionOptional.get(), currentTime);
                openSessionMap.put(player.getUuid(), newSession);
            }
        }
        return true;
    }

    /**
     * Object to hold data for open sessions.
     * This is a bit of a duplicate of {@link net.mca.entity.ai.chatAI.ChatAI ChatAI's OpenConversation}
     * but over there it manages what villager will respond if no name is in the message, here it's the actual session
     */
    private static class OpenSession {
        private final Session session;
        private long lastInteractionMillis;
        private int interactionCount;

        public OpenSession(Session session, long lastInteractionMillis) {
            this.session = session;
            this.lastInteractionMillis = lastInteractionMillis;
            this.interactionCount = 0;
        }

        public Session getSession() {
            return session;
        }

        public int getInteractionCount() {
            return this.interactionCount;
        }

        public long getLastInteractionMillis() {
            return  this.lastInteractionMillis;
        }

        /**
         * Update lastInteractionMillis and increase interactionCount by 1
         * @param lastInteractionMillis Timestamp of last interaction of session
         */
        public void updateSession(long lastInteractionMillis) {
            this.lastInteractionMillis = lastInteractionMillis;
            this.interactionCount++;
        }

    }
}
