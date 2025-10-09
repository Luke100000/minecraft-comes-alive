package net.conczin.mca.network.c2s;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetInteractDataResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.Set;

public record GetInteractDataRequest(int id) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetInteractDataRequest> TYPE = new CustomPacketPayload.Type<>(net.conczin.mca.MCA.locate("get_interact_data_request"));
    public static final StreamCodec<FriendlyByteBuf, GetInteractDataRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GetInteractDataRequest::id,
            GetInteractDataRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.level().getEntity(id);
        if (entity instanceof VillagerLike<?> villager) {
            Set<Constraint> constraints = Constraint.allMatching(villager, player);
            EntityRelationship relationship = ((CompassionateEntity<?>) villager).getRelationships();
            FamilyTreeNode family = relationship.getFamilyEntry();
            Optional<String> fatherName = relationship.getFamilyTree().getOrEmpty(family.father()).map(FamilyTreeNode::getName);
            Optional<String> motherName = relationship.getFamilyTree().getOrEmpty(family.mother()).map(FamilyTreeNode::getName);
            Optional<String> spouseName = relationship.getFamilyTree().getOrEmpty(family.partner()).map(FamilyTreeNode::getName);
            RelationshipState marriageState = relationship.getRelationshipState();
            Network.sendToPlayer(new GetInteractDataResponse(constraints, fatherName, motherName, spouseName, marriageState), player);
        }
    }

    @Override
    public Type<GetInteractDataRequest> type() {
        return TYPE;
    }
}
