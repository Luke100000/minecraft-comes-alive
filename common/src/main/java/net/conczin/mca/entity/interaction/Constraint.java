package net.conczin.mca.entity.interaction;

import net.conczin.mca.MCA;
import net.conczin.mca.ProfessionsMCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Constraint implements BiPredicate<VillagerLike<?>, ServerPlayer> {
    FAMILY("family", Relationship.IS_FAMILY.asConstraint()),
    NOT_FAMILY("!family", Relationship.IS_FAMILY.negate().asConstraint()),

    RELATIVE("relative", Relationship.IS_RELATIVE.asConstraint()),
    NOT_RELATIVE("!relative", Relationship.IS_RELATIVE.negate().asConstraint()),

    BABY("baby", (villager, player) -> villager.getAgeState() == AgeState.BABY),
    NOT_BABY("!baby", (villager, player) -> villager.getAgeState() != AgeState.BABY),

    TODDLER("toddler", (villager, player) -> villager.getAgeState() == AgeState.TODDLER),
    NOT_TODDLER("!toddler", (villager, player) -> villager.getAgeState() != AgeState.TODDLER),

    TEEN("teen", (villager, player) -> villager.getAgeState() == AgeState.TEEN),
    NOT_TEEN("!teen", (villager, player) -> villager.getAgeState() != AgeState.TEEN),

    ADULT("adult", (villager, player) -> villager.getAgeState() == AgeState.ADULT),
    NOT_ADULT("!adult", (villager, player) -> villager.getAgeState() != AgeState.ADULT),

    SPOUSE("spouse", Relationship.IS_MARRIED.asConstraint()),
    NOT_SPOUSE("!spouse", Relationship.IS_MARRIED.negate().asConstraint()),

    ENGAGED("engaged", Relationship.IS_ENGAGED.asConstraint()),
    NOT_ENGAGED("!engaged", Relationship.IS_ENGAGED.negate().asConstraint()),

    PROMISED("promised", Relationship.IS_PROMISED.asConstraint()),
    NOT_PROMISED("!promised", Relationship.IS_PROMISED.negate().asConstraint()),

    KIDS("kids", Relationship.IS_PARENT.asConstraint()),
    NOT_KIDS("!kids", Relationship.IS_PARENT.negate().asConstraint()),

    PARENT("parent", Relationship.IS_KID.asConstraint()),
    NOT_PARENT("!parent", Relationship.IS_KID.negate().asConstraint()),

    CLERIC("cleric", (villager, player) -> villager.getVillagerData().getProfession() == VillagerProfession.CLERIC),
    NOT_CLERIC("!cleric", (villager, player) -> villager.getVillagerData().getProfession() != VillagerProfession.CLERIC),

    ADVENTURER("adventurer", (villager, player) -> villager.getVillagerData().getProfession() == ProfessionsMCA.ADVENTURER.get()),
    NOT_ADVENTURER("!adventurer", (villager, player) -> villager.getVillagerData().getProfession() != ProfessionsMCA.ADVENTURER.get()),

    MERCENARY("mercenary", (villager, player) -> villager.getVillagerData().getProfession() == ProfessionsMCA.MERCENARY.get()),
    NOT_MERCENARY("!mercenary", (villager, player) -> villager.getVillagerData().getProfession() != ProfessionsMCA.MERCENARY.get()),

    OUTLAWED("outlawed", (villager, player) -> villager.getVillagerData().getProfession() == ProfessionsMCA.OUTLAW.get()),
    NOT_OUTLAWED("!outlawed", (villager, player) -> villager.getVillagerData().getProfession() != ProfessionsMCA.OUTLAW.get()),

    TRADER("trader", (villager, player) -> villager.canTradeWithProfession()),
    NOT_TRADER("!trader", (villager, player) -> !villager.canTradeWithProfession()),

    PEASANT("peasant", (villager, player) -> isRankAtLeast(villager, player, Rank.PEASANT)),
    NOT_PEASANT("!peasant", (villager, player) -> !isRankAtLeast(villager, player, Rank.PEASANT)),

    NOBLE("noble", (villager, player) -> isRankAtLeast(villager, player, Rank.NOBLE)),
    NOT_NOBLE("!noble", (villager, player) -> !isRankAtLeast(villager, player, Rank.NOBLE)),

    MAYOR("mayor", (villager, player) -> isRankAtLeast(villager, player, Rank.MAYOR)),
    NOT_MAYOR("!mayor", (villager, player) -> !isRankAtLeast(villager, player, Rank.MAYOR)),

    MONARCH("monarch", (villager, player) -> isRankAtLeast(villager, player, Rank.MONARCH)),
    NOT_MONARCH("!monarch", (villager, player) -> !isRankAtLeast(villager, player, Rank.MONARCH)),

    ORPHAN("orphan", Relationship.IS_ORPHAN.asConstraint()),
    NOT_ORPHAN("!orphan", Relationship.IS_ORPHAN.negate().asConstraint()),

    FOLLOWING("following", (villager, player) -> villager.getVillagerBrain().getMoveState() == MoveState.FOLLOW),
    NOT_FOLLOWING("!following", (villager, player) -> villager.getVillagerBrain().getMoveState() != MoveState.FOLLOW),

    STAYING("staying", (villager, player) -> villager.getVillagerBrain().getMoveState() == MoveState.STAY),
    NOT_STAYING("!staying", (villager, player) -> villager.getVillagerBrain().getMoveState() != MoveState.STAY),

    VILLAGE_HAS_SPACE("village_has_space", (villager, player) -> PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerLevel)player.level())).filter(Village::hasSpace).isPresent()),
    NOT_VILLAGE_HAS_SPACE("!village_has_space", (villager, player) -> PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerLevel)player.level())).filter(Village::hasSpace).isEmpty()),

    HAS_VILLAGE("has_village", (villager, player) -> villager instanceof VillagerEntityMCA mcaVillager && mcaVillager.getResidency().getHomeVillage().isPresent()),
    NOT_HAS_VILLAGE("!has_village", (villager, player) -> villager instanceof VillagerEntityMCA mcaVillager && mcaVillager.getResidency().getHomeVillage().isEmpty()),

    RIDING("riding", (villager, player) -> villager.asEntity().isPassenger()),
    NOT_RIDING("!riding", (villager, player) -> !villager.asEntity().isPassenger()),

    HIT_BY("hit_by", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v) {
            return v.isHitBy(player);
        } else {
            return false;
        }
    }),
    NOT_HIT_BY("!hit_by", (villager, player) -> !HIT_BY.test(villager, player));

    private static boolean isRankAtLeast(VillagerLike<?> villager, ServerPlayer player, Rank rank) {
        return player != null && villager instanceof VillagerEntityMCA && ((VillagerEntityMCA)villager).getResidency().getHomeVillage()
                .filter(village -> Tasks.getRank(village, player).isAtLeast(rank)).isPresent();
    }

    public static final Map<String, Constraint> REGISTRY = Stream.of(values()).collect(Collectors.toMap(a -> a.id, Function.identity()));

    private final String id;
    private final BiPredicate<VillagerLike<?>, ServerPlayer> check;

    Constraint(String id, BiPredicate<VillagerLike<?>, ServerPlayer> check) {
        this.id = id;
        this.check = check;
    }

    @Override
    public boolean test(VillagerLike<?> t, ServerPlayer u) {
        return check.test(t, u);
    }

    public static Set<Constraint> all() {
        return new HashSet<>(REGISTRY.values());
    }

    public static Set<Constraint> allMatching(VillagerLike<?> villager, ServerPlayer player) {
        return Stream.of(values()).filter(c -> c.test(villager, player)).collect(Collectors.toSet());
    }

    public static List<Constraint> fromStringList(String constraints) {
        if (MCA.isBlankString(constraints)) {
            return new ArrayList<>();
        }
        return Stream.of(constraints.split(","))
                .map(REGISTRY::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
