package net.mca.server;

import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import net.mca.Config;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.PlayerDimensions;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.item.BabyItem;
import net.mca.network.s2c.OpenDestinyGuiRequest;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.network.s2c.ShowToastRequest;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class ServerInteractionManager {

    private static final ServerInteractionManager INSTANCE = new ServerInteractionManager();

    /**
     * Maps a player's UUID to a list of UUIDs that have proposed to them with /mca propose
     */
    private final Map<UUID, List<UUID>> proposals = new HashMap<>();

    /**
     * List of UUIDs that initiated procreation mapped to the time the request expires.
     */
    private final Object2LongArrayMap<UUID> procreateMap = new Object2LongArrayMap<>();


    private ServerInteractionManager() {
    }

    public static ServerInteractionManager getInstance() {
        return INSTANCE;
    }

    public static void launchDestiny(ServerPlayerEntity player) {
        NetworkHandler.sendToPlayer(new OpenDestinyGuiRequest(player), player);
    }

    public void tick() {
        List<UUID> removals = new ArrayList<>();
        procreateMap.keySet().stream()
                .filter((k) -> procreateMap.getLong(k) < System.currentTimeMillis())
                .forEach(removals::add);
        removals.forEach(procreateMap::removeLong);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        if (!playerData.isEntityDataSet()) {
            if (Config.getInstance().launchIntoDestiny) {
                launchDestiny(player);

                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 3600));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.HEALTH_BOOST, 3600));
            } else if (Config.getInstance().allowDestinyCommandOnce) {
                NetworkHandler.sendToPlayer(new ShowToastRequest(
                        "server.destinyNotSet.title",
                        "server.destinyNotSet.description"
                ), player);
            } else if (Config.getInstance().allowFullPlayerEditor) {
                NetworkHandler.sendToPlayer(new ShowToastRequest(
                        "server.playerNotCustomized.title",
                        "server.playerNotCustomized.description"
                ), player);
            }
        }

        if (playerData.hasMail()) {
            PlayerSaveData.showMailNotification(player);
        }

        if (Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth) {
            PlayerDimensions.debugRefresh(player, "before server join refresh");
            player.calculateDimensions();
            PlayerDimensions.debugRefresh(player, "after server join refresh");
            if (playerData.isEntityDataSet()) {
                player.getServerWorld().getPlayers().forEach(recipient ->
                        NetworkHandler.sendToPlayer(new PlayerDataMessage(player.getUuid(), playerData.getEntityData()), recipient)
                );
            }
        }
    }

    /**
     * Returns true if receiver has a proposal from sender.
     *
     * @param sender   Command sender
     * @param receiver Player whose name was entered by the sender
     *
     * @return boolean
     */
    private boolean hasProposalFrom(ServerPlayerEntity sender, ServerPlayerEntity receiver) {
        return getProposalsFor(receiver).contains(sender.getUuid());
    }

    /**
     * Returns all proposals for the provided player
     *
     * @param player Player whose proposals should be returned.
     *
     * @return List<UUID>
     */
    private List<UUID> getProposalsFor(ServerPlayerEntity player) {
        return proposals.getOrDefault(player.getUuid(), new ArrayList<>());
    }

    /**
     * Removes the provided proposer from the target's list of proposals.
     *
     * @param target   Target player whose proposal list will be modified.
     * @param proposer The proposer to the target player.
     */
    private void removeProposalFor(ServerPlayerEntity target, ServerPlayerEntity proposer) {
        List<UUID> list = getProposalsFor(target);
        list.remove(proposer.getUuid());
        proposals.put(target.getUuid(), list);
    }

    /**
     * Lists all proposals for the given player.
     *
     * @param sender Player whose active proposals will be listed.
     */
    public void listProposals(ServerPlayerEntity sender) {
        List<UUID> proposals = getProposalsFor(sender);

        if (proposals.size() == 0) {
            infoMessage(sender, Text.translatable("server.noProposals"));
        } else {
            infoMessage(sender, Text.translatable("server.proposals"));
        }

        // Send the name of all online players to the command sender.
        proposals.forEach((uuid -> {
            PlayerEntity player = sender.getWorld().getPlayerByUuid(uuid);
            if (player != null) {
                infoMessage(sender, Text.literal("- ").append(Text.literal(player.getEntityName())));
            }
        }));
    }

    /**
     * Sends a proposal from the sender to the receiver.
     *
     * @param sender   The player sending the proposal.
     * @param receiver The player being proposed to.
     */
    public void sendProposal(ServerPlayerEntity sender, ServerPlayerEntity receiver) {
        // Checks if the admin allows this
        if (!Config.getInstance().allowPlayerMarriage) {
            failMessage(sender, Text.translatable("notify.playerMarriage.disabled"));
            return;
        }

        // Ensure the sender isn't already married.
        if (PlayerSaveData.get(sender).isMarried()) {
            failMessage(sender, Text.translatable("server.alreadyMarried"));
            return;
        }

        // Ensure the sender isn't himself.
        if (sender == receiver) {
            failMessage(sender, Text.translatable("server.proposedToYourself"));
            return;
        }

        // Ensure the receiver hasn't already been proposed to by this player.
        if (hasProposalFrom(sender, receiver)) {
            failMessage(sender, Text.translatable("server.sentProposal", receiver.getEntityName()));
        } else {
            // Send the proposal messages.
            successMessage(sender, Text.translatable("server.proposalSent", receiver.getEntityName()));
            infoMessage(receiver, Text.translatable("server.proposedMarriage", sender.getEntityName()));

            // Add the proposal to the receiver's proposal list.
            List<UUID> list = getProposalsFor(receiver);
            list.add(sender.getUuid());
            proposals.put(receiver.getUuid(), list);
        }
    }

    /**
     * Rejects and removes a proposal from the receiver to the sender.
     *
     * @param sender   The person rejecting the proposal.
     * @param receiver The initial proposer.
     */
    public void rejectProposal(ServerPlayerEntity sender, ServerPlayerEntity receiver) {
        // Ensure a proposal existed.
        if (!hasProposalFrom(receiver, sender)) {
            failMessage(sender, Text.translatable("server.noProposal", receiver.getDisplayName()));
        } else {
            // Notify of the proposal failure and remove it.
            successMessage(sender, Text.translatable("server.proposalRejectionSent"));
            failMessage(receiver, Text.translatable("server.proposalRejected", sender.getEntityName()));
            removeProposalFor(sender, receiver);
        }
    }

    /**
     * Accepts and removes a proposal from the receiver to the sender.
     *
     * @param sender   The person accepting the proposal.
     * @param receiver The initial proposer.
     */
    public void acceptProposal(ServerPlayerEntity sender, ServerPlayerEntity receiver) {
        // Ensure a proposal is active.
        if (!hasProposalFrom(receiver, sender)) {
            failMessage(sender, Text.translatable("server.noProposal", receiver.getDisplayName()));
        } else {
            // Notify of acceptance.
            successMessage(receiver, Text.translatable("server.proposalAccepted", sender.getDisplayName()));

            // Set both player data as married.
            PlayerSaveData.get(sender).marry(receiver);
            PlayerSaveData.get(receiver).marry(sender);

            // Send success messages.
            successMessage(sender, Text.translatable("server.married", receiver.getDisplayName()));
            successMessage(receiver, Text.translatable("server.married", sender.getDisplayName()));

            // Remove the proposal.
            removeProposalFor(sender, receiver);
        }
    }

    /**
     * Ends the sender's marriage and notifies their spouse if the spouse is online.
     *
     * @param sender The person ending their marriage.
     */
    public void endMarriage(ServerPlayerEntity sender) {
        // Retrieve all data instances and an instance of the ex-spouse if they are present.
        EntityRelationship.of(sender).ifPresent(senderData -> {
            // Ensure the sender is married
            if (!senderData.isMarried()) {
                failMessage(sender, Text.translatable("server.endMarriageNotMarried"));
                return;
            }

            // Lookup the spouse, if it's a villager, we can't continue
            if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
                failMessage(sender, Text.translatable("server.marriedToVillager"));
                return;
            }

            // Notify the sender of the success and end both marriages.
            senderData.getPartnerName().ifPresent(name ->
                    successMessage(sender, Text.translatable("server.endMarriage", name.getString()))
            );
            senderData.getPartner().ifPresent(spouse -> {
                if (spouse instanceof PlayerEntity player) {
                    // Notify the ex if they are online.
                    failMessage(player, Text.translatable("server.marriageEnded", sender.getEntityName()));
                }
            });
            senderData.endRelationShip(RelationshipState.SINGLE);
            senderData.getPartnerUUID().map(id -> PlayerSaveData.get(sender)).ifPresent(r -> r.endRelationShip(RelationshipState.SINGLE));
        });
    }

    /**
     * Initiates procreation with a married player.
     *
     * @param sender The person requesting procreation.
     */
    public void procreate(ServerPlayerEntity sender) {
        // Ensure the sender is married.
        PlayerSaveData senderData = PlayerSaveData.get(sender);
        if (!senderData.isMarried()) {
            failMessage(sender, Text.translatable("server.notMarried"));
            return;
        }

        // Ensure the spouse is a player
        if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
            failMessage(sender, Text.translatable("server.marriedToVillager"));
            return;
        }

        // Ensure we don't already have a baby
        // todo add cooldown
        if (false) {
            failMessage(sender, Text.translatable("server.babyPresent"));
            return;
        }

        // Ensure the spouse is online.
        senderData.getPartner().filter(e -> e instanceof PlayerEntity).map(PlayerEntity.class::cast).ifPresentOrElse(spouse -> {
            // If the spouse is online and has previously sent a procreation request that hasn't expired, we can continue.
            // Otherwise, we notify the spouse that they must also enter the command.
            if (!procreateMap.containsKey(spouse.getUuid())) {
                procreateMap.put(sender.getUuid(), System.currentTimeMillis() + 10000);
                infoMessage(spouse, Text.translatable("server.procreationRequest", sender.getEntityName()));
            } else {
                // On success, add a randomly generated baby to the original requester.
                successMessage(sender, Text.translatable("server.procreationSuccessful"));
                successMessage(spouse, Text.translatable("server.procreationSuccessful"));

                spouse.giveItemStack(BabyItem.createItem(spouse, sender, spouse.getRandom().nextLong()));
            }
        }, () -> failMessage(sender, Text.translatable("server.spouseNotPresent")));
    }

    private void successMessage(PlayerEntity player, MutableText message) {
        player.sendMessage(message.formatted(Formatting.GREEN));
    }

    private void failMessage(PlayerEntity player, MutableText message) {
        player.sendMessage(message.formatted(Formatting.RED));
    }

    private void infoMessage(PlayerEntity player, MutableText message) {
        player.sendMessage(message.formatted(Formatting.YELLOW));
    }
}
