package net.mca.entity.ai.relationship;

import net.mca.advancement.criterion.CriterionMCA;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface EntityRelationship {

    default Gender getGender() {
        return Gender.MALE;
    }

    default FamilyTree getFamilyTree() {
        return FamilyTree.get(getWorld());
    }

    ServerWorld getWorld();

    UUID getUUID();

    @NotNull
    FamilyTreeNode getFamilyEntry();

    default Stream<Entity> getFamily(int parents, int children) {
        return getFamilyEntry()
                .getRelatives(parents, children)
                .map(getWorld()::getEntity)
                .filter(Objects::nonNull)
                .filter(e -> !e.getUuid().equals(getUUID()));
    }

    default Stream<Entity> getParents() {
        return getFamilyEntry().streamParents().map(getWorld()::getEntity).filter(Objects::nonNull);
    }

    default Optional<Entity> getPartner() {
        UUID partner = getFamilyEntry().partner();
        Entity entity = getWorld().getEntity(partner);
        if (entity != null) {
            return Optional.of(entity);
        }
        return getWorld().getServer().getPlayerManager().getPlayerList().stream()
                .filter(player -> player.getUuid().equals(partner))
                .map(player -> (Entity)player)
                .findFirst();
    }

    //try to load a PlayerSaveData before loading the entity
    //that way, offline players are also considered
    default Stream<EntityRelationship> getRelationshipStream(Stream<UUID> uuids) {
        return uuids.map(uuid -> PlayerSaveData.getIfPresent(getWorld(), uuid)
                        .map(p -> (EntityRelationship)p)
                        .or(() -> EntityRelationship.of(getWorld().getEntity(uuid))))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    default void onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity victim) {
        if (type == RelationshipType.STRANGER) {
            return;
        }

        // notify family
        if (type == RelationshipType.SELF) {
            getRelationshipStream(getFamilyEntry().streamParents())
                    .forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.CHILD, victim));

            getRelationshipStream(getFamilyEntry().siblings().stream())
                    .forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.SIBLING, victim));

            getRelationshipStream(Stream.of(getFamilyEntry().partner()))
                    .forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.SPOUSE, victim));
        }

        // end the marriage for both the deceased one and the spouse
        if (type == RelationshipType.SPOUSE || type == RelationshipType.SELF) {
            if (getRelationshipState().isMarried()) {
                endRelationShip(RelationshipState.WIDOW);
            } else {
                endRelationShip(RelationshipState.SINGLE);
            }
        }
    }

    default void marry(Entity spouse) {
        RelationshipState state = spouse instanceof PlayerEntity ? RelationshipState.MARRIED_TO_PLAYER : RelationshipState.MARRIED_TO_VILLAGER;
        if (spouse instanceof ServerPlayerEntity spouseEntity) {
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(spouseEntity, "marriage");
        }
        getFamilyEntry().updatePartner(spouse, state);
    }

    default void engage(Entity spouse) {
        if (spouse instanceof ServerPlayerEntity spouseEntity) {
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(spouseEntity, "engage");
        }
        getFamilyEntry().updatePartner(spouse, RelationshipState.ENGAGED);
    }

    default void promise(Entity spouse) {
        if (spouse instanceof ServerPlayerEntity spouseEntity) {
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(spouseEntity, "promise");
        }
        getFamilyEntry().updatePartner(spouse, RelationshipState.PROMISED);
    }

    default void endRelationShip(RelationshipState newState) {
        getFamilyEntry().updatePartner(null, newState);
    }

    default RelationshipState getRelationshipState() {
        return getFamilyEntry().getRelationshipState();
    }

    default Optional<UUID> getPartnerUUID() {
        UUID spouse = getFamilyEntry().partner();
        if (spouse.equals(Util.NIL_UUID)) {
            return Optional.empty();
        } else {
            return Optional.of(spouse);
        }
    }

    default Optional<Text> getPartnerName() {
        return getFamilyTree().getOrEmpty(getFamilyEntry().partner()).map(FamilyTreeNode::getName).map(Text::literal);
    }

    default boolean isMarried() {
        return getRelationshipState() == RelationshipState.MARRIED_TO_PLAYER || getRelationshipState() == RelationshipState.MARRIED_TO_VILLAGER;
    }

    default boolean isEngaged() {
        return getRelationshipState() == RelationshipState.ENGAGED;
    }

    default boolean isPromised() {
        return getRelationshipState() == RelationshipState.PROMISED;
    }

    default boolean isPromisedTo(UUID uuid) {
        return getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid) && isPromised();
    }

    default boolean isMarriedTo(UUID uuid) {
        return getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid) && isMarried();
    }

    default boolean isEngagedWith(UUID uuid) {
        return getPartnerUUID().orElse(Util.NIL_UUID).equals(uuid) && isEngaged();
    }

    static Optional<EntityRelationship> of(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) {
            return Optional.ofNullable(PlayerSaveData.get(player));
        }

        if (entity instanceof CompassionateEntity<?> compassionateEntity) {
            return Optional.ofNullable(compassionateEntity.getRelationships());
        }

        return Optional.empty();
    }
}
