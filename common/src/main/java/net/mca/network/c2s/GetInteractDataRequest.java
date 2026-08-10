package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.CompassionateEntity;
import net.mca.entity.ai.relationship.EntityRelationship;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.interaction.Constraint;
import net.mca.network.s2c.GetInteractDataResponse;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.io.Serial;
import java.util.Set;
import java.util.UUID;

public class GetInteractDataRequest implements Message {
    @Serial
    private static final long serialVersionUID = -4363277735373237564L;

    final UUID uuid;

    public GetInteractDataRequest(UUID villager) {
        this.uuid = villager;
    }

    @Override
    public void receive(ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(uuid);

        if (entity instanceof VillagerLike<?> villager) {
            //get constraints
            Set<Constraint> constraints = Constraint.allMatching(villager, player);

            EntityRelationship relationship = ((CompassionateEntity<?>)villager).getRelationships();
            FamilyTreeNode family = relationship.getFamilyEntry();

            String fatherName = relationship.getFamilyTree().getOrEmpty(family.father()).map(FamilyTreeNode::getName).orElse(null);
            String motherName = relationship.getFamilyTree().getOrEmpty(family.mother()).map(FamilyTreeNode::getName).orElse(null);
            String spouseName = relationship.getFamilyTree().getOrEmpty(family.partner()).map(FamilyTreeNode::getName).orElse(null);
            RelationshipState marriageState = relationship.getRelationshipState();

            NetworkHandler.sendToPlayer(new GetInteractDataResponse(constraints, fatherName, motherName, spouseName, marriageState), player);
        }
    }
}
