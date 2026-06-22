package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.entity.interaction.Constraint;

import java.io.Serial;
import java.util.Set;

public class GetInteractDataResponse implements Message {
    @Serial
    private static final long serialVersionUID = -4168503424192658779L;

    public final Set<Constraint> constraints;
    public final String father;
    public final String mother;
    public final String spouse;
    public final RelationshipState marriageState;

    public GetInteractDataResponse(Set<Constraint> constraints, String father, String mother, String spouse, RelationshipState marriageState) {
        this.constraints = constraints;
        this.father = father;
        this.mother = mother;
        this.spouse = spouse;
        this.marriageState = marriageState;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleInteractDataResponse(this);
    }
}
