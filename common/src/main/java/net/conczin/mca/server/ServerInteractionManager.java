package net.conczin.mca.server;

import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.EngagementRingItem;
import net.conczin.mca.item.RelationshipItem;
import net.conczin.mca.item.WeddingRingItem;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenDestinyGuiRequest;
import net.conczin.mca.network.s2c.PlayerInteractionAnimationMessage;
import net.conczin.mca.network.s2c.ShowToastRequest;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ServerInteractionManager {
    private static final long SOCIAL_INTERACTION_COOLDOWN_MS = 2000L;
    private static final int KISS_BASE_DURATION_TICKS = 14;
    private static final int KISS_BASE_REGEN_TICKS = 60;
    private static final int KISS_BASE_XP = 2;
    private static final ServerInteractionManager INSTANCE = new ServerInteractionManager();

    /**
     * Maps a player's UUID to a list of UUIDs that have proposed to them with /mca propose
     */
    private final Map<UUID, List<UUID>> proposals = new HashMap<>();

    /**
     * List of UUIDs that initiated procreation mapped to the time the request expires.
     */
    private final Object2LongArrayMap<UUID> procreateMap = new Object2LongArrayMap<>();
    private final Object2LongArrayMap<UUID> socialCooldowns = new Object2LongArrayMap<>();


    private ServerInteractionManager() {
    }

    public static ServerInteractionManager getInstance() {
        return INSTANCE;
    }

    public static void launchDestiny(ServerPlayer player) {
        Network.sendToPlayer(new OpenDestinyGuiRequest(player), player);
    }

    public void tick() {
        pruneExpired(procreateMap);
        pruneExpired(socialCooldowns);

        MCA.getServer().ifPresent(server ->
                server.getPlayerList().getPlayers().forEach(this::applyNearbySpouseBenefits)
        );
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

    private void pruneExpired(Object2LongArrayMap<UUID> timedMap) {
        List<UUID> removals = new ArrayList<>();
        timedMap.keySet().stream()
                .filter(uuid -> timedMap.getLong(uuid) < System.currentTimeMillis())
                .forEach(removals::add);
        removals.forEach(timedMap::removeLong);
    }

    private void applyNearbySpouseBenefits(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }

        PlayerSaveData data = PlayerSaveData.get(player);
        if (data.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
            return;
        }

        Player spousePlayer = data.getPartnerUUID()
                .map(((ServerLevel) player.level())::getPlayerByUUID)
                .orElse(null);
        ServerPlayer spouse = spousePlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (spouse == null || spouse.level() != player.level() || player.distanceToSqr(spouse) > 36.0D) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false, true));
    }

    private boolean hasUsableRing(ServerPlayer player, Class<? extends Item> ringType) {
        if (ringType.isInstance(player.getMainHandItem().getItem())) {
            return true;
        }

        ItemStack equippedRing = PlayerSaveData.get(player).getEquippedRing();
        return !equippedRing.isEmpty() && ringType.isInstance(equippedRing.getItem());
    }

    private void autoEquipHeldRing(ServerPlayer player, Class<? extends Item> ringType) {
        ItemStack held = player.getMainHandItem();
        if (ringType.isInstance(held.getItem())) {
            RelationshipItem.equipRing(player, held);
        } else {
            PlayerSaveData.sync(player);
        }
    }

    private boolean isOnSocialCooldown(ServerPlayer player) {
        return socialCooldowns.getLong(player.getUUID()) > System.currentTimeMillis();
    }

    private void playAffectionAnimation(ServerPlayer sender, ServerPlayer receiver, String action, int durationTicks) {
        playAffectionAnimation(sender, receiver, action, durationTicks, 1.0F);
    }

    private void playAffectionAnimation(ServerPlayer sender, ServerPlayer receiver, String action, int durationTicks, float strength) {
        long expiresAt = System.currentTimeMillis() + SOCIAL_INTERACTION_COOLDOWN_MS;
        socialCooldowns.put(sender.getUUID(), expiresAt);
        socialCooldowns.put(receiver.getUUID(), expiresAt);

        sender.swing(InteractionHand.MAIN_HAND);
        receiver.swing(InteractionHand.MAIN_HAND);

        if (sender.level() instanceof ServerLevel serverLevel) {
            PlayerInteractionAnimationMessage message = new PlayerInteractionAnimationMessage(sender.getUUID(), receiver.getUUID(), action, durationTicks, strength);
            serverLevel.players().forEach(player -> Network.sendToPlayer(message, player));

            double x = (sender.getX() + receiver.getX()) * 0.5D;
            double y = (sender.getY(0.75D) + receiver.getY(0.75D)) * 0.5D;
            double z = (sender.getZ() + receiver.getZ()) * 0.5D;
            if ("kiss".equals(action)) {
                int hearts = 6 + Mth.ceil(Math.max(0.0F, strength - 1.0F) * 4.0F);
                serverLevel.sendParticles(ParticleTypes.HEART, x, y, z, hearts, 0.2D + strength * 0.05D, 0.15D + strength * 0.03D, 0.2D + strength * 0.05D, 0.01D);
            } else {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 8, 0.3D, 0.15D, 0.3D, 0.02D);
            }
        }
    }

    private FoodShare shareHeldFoodForKiss(ServerPlayer sender, ServerPlayer receiver) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sender.getItemInHand(hand);
            if (stack.isEmpty()) {
                continue;
            }

            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null || food.nutrition() <= 0) {
                continue;
            }

            receiver.getFoodData().eat(food);

            if (!sender.getAbilities().instabuild) {
                stack.shrink(1);
                sender.setItemInHand(hand, stack);
                sender.containerMenu.broadcastChanges();
            }

            return new FoodShare(food.nutrition(), food.saturation());
        }

        return FoodShare.NONE;
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
        if (!Config.getInstance().allowPlayerMarriage) {
            failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
            return;
        }

        PlayerSaveData senderData = PlayerSaveData.get(sender);
        PlayerSaveData receiverData = PlayerSaveData.get(receiver);

        if (senderData.isMarried()) {
            failMessage(sender, Component.translatable("server.alreadyMarried"));
            return;
        }

        if (senderData.isEngaged()) {
            failMessage(sender, Component.translatable("server.alreadyEngaged"));
            return;
        }

        if (receiverData.isMarried()) {
            failMessage(sender, Component.translatable("server.targetAlreadyMarried", receiver.getScoreboardName()));
            return;
        }

        if (receiverData.isEngaged()) {
            failMessage(sender, Component.translatable("server.targetAlreadyEngaged", receiver.getScoreboardName()));
            return;
        }

        if (sender == receiver) {
            failMessage(sender, Component.translatable("server.proposedToYourself"));
            return;
        }

        if (hasProposalFrom(sender, receiver)) {
            failMessage(sender, Component.translatable("server.sentProposal", receiver.getScoreboardName()));
            return;
        }

        successMessage(sender, Component.translatable("server.proposalSent", receiver.getScoreboardName()));
        infoMessage(receiver, Component.translatable("server.proposedMarriage", sender.getScoreboardName()));

        List<UUID> list = getProposalsFor(receiver);
        list.add(sender.getUUID());
        proposals.put(receiver.getUUID(), list);
    }

    public void engage(ServerPlayer sender, ServerPlayer receiver) {
        if (!Config.getInstance().allowPlayerMarriage) {
            failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
            return;
        }

        if (sender == receiver) {
            failMessage(sender, Component.translatable("server.cannotTargetYourself"));
            return;
        }

        PlayerSaveData senderData = PlayerSaveData.get(sender);
        PlayerSaveData receiverData = PlayerSaveData.get(receiver);

        if (senderData.isMarried()) {
            failMessage(sender, Component.translatable("server.alreadyMarried"));
            return;
        }

        if (receiverData.isMarried()) {
            failMessage(sender, Component.translatable("server.targetAlreadyMarried", receiver.getScoreboardName()));
            return;
        }

        if (senderData.isEngaged()) {
            failMessage(sender, Component.translatable("server.alreadyEngaged"));
            return;
        }

        if (receiverData.isEngaged()) {
            failMessage(sender, Component.translatable("server.targetAlreadyEngaged", receiver.getScoreboardName()));
            return;
        }

        if (!hasUsableRing(sender, EngagementRingItem.class)) {
            failMessage(sender, Component.translatable("server.needEngagementRing"));
            return;
        }

        senderData.engage(receiver);
        receiverData.engage(sender);
        removeProposalFor(receiver, sender);
        removeProposalFor(sender, receiver);

        autoEquipHeldRing(sender, EngagementRingItem.class);

        successMessage(sender, Component.translatable("server.engaged", receiver.getDisplayName()));
        successMessage(receiver, Component.translatable("server.engaged", sender.getDisplayName()));
    }

    public void marry(ServerPlayer sender, ServerPlayer receiver) {
        if (!Config.getInstance().allowPlayerMarriage) {
            failMessage(sender, Component.translatable("notify.playerMarriage.disabled"));
            return;
        }

        if (sender == receiver) {
            failMessage(sender, Component.translatable("server.cannotTargetYourself"));
            return;
        }

        PlayerSaveData senderData = PlayerSaveData.get(sender);
        PlayerSaveData receiverData = PlayerSaveData.get(receiver);

        if (senderData.isMarried()) {
            failMessage(sender, Component.translatable("server.alreadyMarried"));
            return;
        }

        if (receiverData.isMarried()) {
            failMessage(sender, Component.translatable("server.targetAlreadyMarried", receiver.getScoreboardName()));
            return;
        }

        if (!senderData.isEngagedWith(receiver.getUUID()) || !receiverData.isEngagedWith(sender.getUUID())) {
            failMessage(sender, Component.translatable("server.notEngaged"));
            return;
        }

        if (!hasUsableRing(sender, WeddingRingItem.class)) {
            failMessage(sender, Component.translatable("server.needWeddingRing"));
            return;
        }

        senderData.marry(receiver);
        receiverData.marry(sender);
        removeProposalFor(receiver, sender);
        removeProposalFor(sender, receiver);

        autoEquipHeldRing(sender, WeddingRingItem.class);

        successMessage(sender, Component.translatable("server.married", receiver.getDisplayName()));
        successMessage(receiver, Component.translatable("server.married", sender.getDisplayName()));
    }

    public void hug(ServerPlayer sender, ServerPlayer receiver) {
        if (sender == receiver) {
            failMessage(sender, Component.translatable("server.cannotTargetYourself"));
            return;
        }

        if (isOnSocialCooldown(sender)) {
            failMessage(sender, Component.translatable("server.socialInteractionCooldown"));
            return;
        }

        PlayerSaveData senderData = PlayerSaveData.get(sender);
        PlayerSaveData receiverData = PlayerSaveData.get(receiver);
        boolean engaged = senderData.isEngagedWith(receiver.getUUID()) && receiverData.isEngagedWith(sender.getUUID());
        boolean married = senderData.isMarriedTo(receiver.getUUID()) && receiverData.isMarriedTo(sender.getUUID());
        if (!engaged && !married) {
            failMessage(sender, Component.translatable("server.hug.requiresPartner"));
            return;
        }

        sender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
        receiver.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
        sender.giveExperiencePoints(2);
        receiver.giveExperiencePoints(2);
        playAffectionAnimation(sender, receiver, "hug", 18);

        successMessage(sender, Component.translatable("server.hug.success", receiver.getDisplayName()));
        successMessage(receiver, Component.translatable("server.hug.received", sender.getDisplayName()));
    }

    public void kiss(ServerPlayer sender, ServerPlayer receiver) {
        if (sender == receiver) {
            failMessage(sender, Component.translatable("server.cannotTargetYourself"));
            return;
        }

        if (isOnSocialCooldown(sender)) {
            failMessage(sender, Component.translatable("server.socialInteractionCooldown"));
            return;
        }

        PlayerSaveData senderData = PlayerSaveData.get(sender);
        PlayerSaveData receiverData = PlayerSaveData.get(receiver);
        if (!senderData.isMarriedTo(receiver.getUUID()) || !receiverData.isMarriedTo(sender.getUUID())) {
            failMessage(sender, Component.translatable("server.kiss.requiresSpouse"));
            return;
        }

        FoodShare sharedFood = shareHeldFoodForKiss(sender, receiver);
        int strengthBonus = sharedFood.strengthBonus();
        int regenTicks = KISS_BASE_REGEN_TICKS + strengthBonus * 20;
        int sharedXp = KISS_BASE_XP + strengthBonus;
        int durationTicks = KISS_BASE_DURATION_TICKS + strengthBonus * 2;
        float animationStrength = 1.0F + strengthBonus * 0.18F;

        sender.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenTicks, 0, true, false, true));
        receiver.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenTicks, 0, true, false, true));
        sender.giveExperiencePoints(sharedXp);
        receiver.giveExperiencePoints(sharedXp);

        playAffectionAnimation(sender, receiver, "kiss", durationTicks, animationStrength);
        successMessage(sender, Component.translatable("server.kiss.success", receiver.getDisplayName()));
        successMessage(receiver, Component.translatable("server.kiss.received", sender.getDisplayName()));
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
        PlayerSaveData senderData = PlayerSaveData.get(sender);
        if (!senderData.isMarried()) {
            failMessage(sender, Component.translatable("server.endMarriageNotMarried"));
            return;
        }

        if (senderData.getRelationshipState() != RelationshipState.MARRIED_TO_PLAYER) {
            failMessage(sender, Component.translatable("server.marriedToVillager"));
            return;
        }

        UUID partnerId = senderData.getPartnerUUID().orElse(null);
        senderData.getPartnerName().ifPresent(name ->
                successMessage(sender, Component.translatable("server.endMarriage", name.getString()))
        );

        Player spousePlayer = partnerId == null ? null : ((ServerLevel) sender.level()).getPlayerByUUID(partnerId);
        ServerPlayer spouse = spousePlayer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (spouse != null) {
            failMessage(spouse, Component.translatable("server.marriageEnded", sender.getScoreboardName()));
            PlayerSaveData.get(spouse).endRelationShip(RelationshipState.SINGLE);
            PlayerSaveData.sync(spouse);
        } else if (partnerId != null && sender.level() instanceof ServerLevel serverLevel) {
            PlayerSaveData.getIfPresent(serverLevel, partnerId).ifPresent(partnerData -> partnerData.endRelationShip(RelationshipState.SINGLE));
        }

        senderData.endRelationShip(RelationshipState.SINGLE);
        PlayerSaveData.sync(sender);
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

    public void procreate(ServerPlayer sender, ServerPlayer receiver) {
        PlayerSaveData senderData = PlayerSaveData.get(sender);
        if (senderData.getPartnerUUID().filter(receiver.getUUID()::equals).isEmpty()) {
            failMessage(sender, Component.translatable("server.spouseNotPresent"));
            return;
        }

        procreate(sender);
    }

    private void successMessage(Player player, MutableComponent message) {
        net.conczin.mca.util.PlayerMessageHelper.displayClientMessage(player, message.withStyle(ChatFormatting.GREEN), false);
    }

    private void failMessage(Player player, MutableComponent message) {
        net.conczin.mca.util.PlayerMessageHelper.displayClientMessage(player, message.withStyle(ChatFormatting.RED), false);
    }

    private void infoMessage(Player player, MutableComponent message) {
        net.conczin.mca.util.PlayerMessageHelper.displayClientMessage(player, message.withStyle(ChatFormatting.YELLOW), false);
    }

    private record FoodShare(int nutrition, float saturationModifier) {
        private static final FoodShare NONE = new FoodShare(0, 0.0F);

        private int strengthBonus() {
            if (nutrition <= 0) {
                return 0;
            }
            return Math.min(6, Math.max(1, nutrition / 2 + Mth.ceil(saturationModifier * 2.0F)));
        }
    }
}
