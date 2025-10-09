package net.conczin.mca.server;

import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenDestinyGuiRequest;
import net.conczin.mca.network.s2c.ShowToastRequest;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

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

    public static void launchDestiny(ServerPlayer player) {
        Network.sendToPlayer(new OpenDestinyGuiRequest(player), player);
    }

    public void tick() {
        List<UUID> removals = new ArrayList<>();
        procreateMap.keySet().stream()
                .filter((k) -> procreateMap.getLong(k) < System.currentTimeMillis())
                .forEach(removals::add);
        removals.forEach(procreateMap::removeLong);
    }

    public void onPlayerJoin(ServerPlayer player) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        if (!playerData.isEntityDataSet()) {
            if (Config.getInstance().launchIntoDestiny) {
                launchDestiny(player);

                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 3600));
                player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 3600));
            } else if (Config.getInstance().allowDestinyCommandOnce) {
                Network.sendToPlayer(new ShowToastRequest(
                        "server.destinyNotSet.title",
                        "server.destinyNotSet.description"
                ), player);
            } else if (Config.getInstance().allowFullPlayerEditor) {
                Network.sendToPlayer(new ShowToastRequest(
                        "server.playerNotCustomized.title",
                        "server.playerNotCustomized.description"
                ), player);
            }
        }

        if (playerData.hasMail()) {
            PlayerSaveData.showMailNotification(player);
        }
    }

    /**
     * Returns true if receiver has a proposal from sender.
     *
     * @param sender   Command sender
     * @param receiver Player whose name was entered by the sender
     * @return boolean
     */
    private boolean hasProposalFrom(ServerPlayer sender, ServerPlayer receiver) {
        return getProposalsFor(receiver).contains(sender.getUUID());
    }

    /**
     * Returns all proposals for the provided player
     *
     * @param player Player whose proposals should be returned.
     * @return List<UUID>
     */
    private List<UUID> getProposalsFor(ServerPlayer player) {
        return proposals.getOrDefault(player.getUUID(), new ArrayList<>());
    }

    /**
     * Removes the provided proposer from the target's list of proposals.
     *
     * @param target   Target player whose proposal list will be modified.
     * @param proposer The proposer to the target player.
     */
    private void removeProposalFor(ServerPlayer target, ServerPlayer proposer) {
        List<UUID> list = getProposalsFor(target);
        list.remove(proposer.getUUID());
        proposals.put(target.getUUID(), list);
    }

    /**
     * Lists all proposals for the given player.
     *
     * @param sender Player whose active proposals will be listed.
     */
    public void listProposals(ServerPlayer sender) {
        List<UUID> proposals = getProposalsFor(sender);

        if (proposals.isEmpty()) {
            infoMessage(sender, Component.translatable("server.noProposals"));
        } else {
            infoMessage(sender, Component.translatable("server.proposals"));
        }

        // Send the name of all online players to the command sender.
        proposals.forEach((uuid -> {
            Player player = sender.level().getPlayerByUUID(uuid);
            if (player != null) {
                infoMessage(sender, Component.literal("- ").append(Component.literal(player.getScoreboardName())));
            }
        }));
    }

    /**
     * Sends a proposal from the sender to the receiver.
     *
     * @param sender   The player sending the proposal.
     * @param receiver The player being proposed to.
     */
    public void sendProposal(ServerPlayer sender, ServerPlayer receiver) {
        // Checks if the admin allows this
        if (!Config.getInstance().allowPlayerMarriage) {
            failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
            return;
        }

        // Ensure the sender isn't already married.
        if (PlayerSaveData.get(sender).isMarried()) {
            failMessage(sender, Component.translatable("server.alreadyMarried"));
            return;
        }

        // Ensure the sender isn't himself.
        if (sender == receiver) {
            failMessage(sender, Component.translatable("server.proposedToYourself"));
            return;
        }

        // Ensure the receiver hasn't already been proposed to by this player.
        if (hasProposalFrom(sender, receiver)) {
            failMessage(sender, Component.translatable("server.sentProposal", receiver.getScoreboardName()));
        } else {
            // Send the proposal messages.
            successMessage(sender, Component.translatable("server.proposalSent", receiver.getScoreboardName()));
            infoMessage(receiver, Component.translatable("server.proposedMarriage", sender.getScoreboardName()));

            // Add the proposal to the receiver's proposal list.
            List<UUID> list = getProposalsFor(receiver);
            list.add(sender.getUUID());
            proposals.put(receiver.getUUID(), list);
        }
    }

    /**
     * Rejects and removes a proposal from the receiver to the sender.
     *
     * @param sender   The person rejecting the proposal.
     * @param receiver The initial proposer.
     */
    public void rejectProposal(ServerPlayer sender, ServerPlayer receiver) {
        // Ensure a proposal existed.
        if (!hasProposalFrom(receiver, sender)) {
            failMessage(sender, Component.translatable("server.noProposal", receiver.getDisplayName()));
        } else {
            // Notify of the proposal failure and remove it.
            successMessage(sender, Component.translatable("server.proposalRejectionSent"));
            failMessage(receiver, Component.translatable("server.proposalRejected", sender.getScoreboardName()));
            removeProposalFor(sender, receiver);
        }
    }

    /**
     * Accepts and removes a proposal from the receiver to the sender.
     *
     * @param sender   The person accepting the proposal.
     * @param receiver The initial proposer.
     */
    public void acceptProposal(ServerPlayer sender, ServerPlayer receiver) {
        // Ensure a proposal is active.
        if (!hasProposalFrom(receiver, sender)) {
            failMessage(sender, Component.translatable("server.noProposal", receiver.getDisplayName()));
        } else {
            // Notify of acceptance.
            successMessage(receiver, Component.translatable("server.proposalAccepted", sender.getDisplayName()));

            // Set both player data as married.
            PlayerSaveData.get(sender).marry(receiver);
            PlayerSaveData.get(receiver).marry(sender);

            // Send success messages.
            successMessage(sender, Component.translatable("server.married", receiver.getDisplayName()));
            successMessage(receiver, Component.translatable("server.married", sender.getDisplayName()));

            // Remove the proposal.
            removeProposalFor(sender, receiver);
        }
    }

    /**
     * Ends the sender's marriage and notifies their spouse if the spouse is online.
     *
     * @param sender The person ending their marriage.
     */
    public void endMarriage(ServerPlayer sender) {
        // Retrieve all data instances and an instance of the ex-spouse if they are present.
        EntityRelationship.of(sender).ifPresent(senderData -> {
            // Ensure the sender is married
            if (!senderData.isMarried()) {
                failMessage(sender, Component.translatable("server.endMarriageNotMarried"));
                return;
            }

            // Lookup the spouse, if it's a villager, we can't continue
            if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
                failMessage(sender, Component.translatable("server.marriedToVillager"));
                return;
            }

            // Notify the sender of the success and end both marriages.
            senderData.getPartnerName().ifPresent(name ->
                    successMessage(sender, Component.translatable("server.endMarriage", name.getString()))
            );
            senderData.getPartner().ifPresent(spouse -> {
                if (spouse instanceof Player player) {
                    // Notify the ex if they are online.
                    failMessage(player, Component.translatable("server.marriageEnded", sender.getScoreboardName()));
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
    public void procreate(ServerPlayer sender) {
        // Ensure the sender is married.
        PlayerSaveData senderData = PlayerSaveData.get(sender);
        if (!senderData.isMarried()) {
            failMessage(sender, Component.translatable("server.notMarried"));
            return;
        }

        // Ensure the spouse is a player
        if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
            failMessage(sender, Component.translatable("server.marriedToVillager"));
            return;
        }

        // Ensure we don't already have a baby
        // todo add cooldown
        if (false) {
            failMessage(sender, Component.translatable("server.babyPresent"));
            return;
        }

        // Ensure the spouse is online.
        senderData.getPartner().filter(e -> e instanceof Player).map(Player.class::cast).ifPresentOrElse(spouse -> {
            // If the spouse is online and has previously sent a procreation request that hasn't expired, we can continue.
            // Otherwise, we notify the spouse that they must also enter the command.
            if (!procreateMap.containsKey(spouse.getUUID())) {
                procreateMap.put(sender.getUUID(), System.currentTimeMillis() + 10000);
                infoMessage(spouse, Component.translatable("server.procreationRequest", sender.getScoreboardName()));
            } else {
                // On success, add a randomly generated baby to the original requester.
                successMessage(sender, Component.translatable("server.procreationSuccessful"));
                successMessage(spouse, Component.translatable("server.procreationSuccessful"));

                spouse.addItem(BabyItem.createItem(spouse, sender, spouse.getRandom().nextLong()));
            }
        }, () -> failMessage(sender, Component.translatable("server.spouseNotPresent")));
    }

    private void successMessage(Player player, MutableComponent message) {
        player.sendSystemMessage(message.withStyle(ChatFormatting.GREEN));
    }

    private void failMessage(Player player, MutableComponent message) {
        player.sendSystemMessage(message.withStyle(ChatFormatting.RED));
    }

    private void infoMessage(Player player, MutableComponent message) {
        player.sendSystemMessage(message.withStyle(ChatFormatting.YELLOW));
    }
}
