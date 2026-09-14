package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ArcherArrowFriendlyFireGameTests {
    private ArcherArrowFriendlyFireGameTests() {
    }

    @GameTest(batch = "mca_archer_arrow_friendly_fire", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void arrowPassesThroughVillagerAndHitsHostile(GameTestHelper helper) {
        BlockPos archerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos villagerPos = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos targetPos = helper.absolutePos(new BlockPos(8, 2, 4));

        VillagerEntityMCA archer = spawnVillager(helper, archerPos, true);
        VillagerEntityMCA bystander = spawnVillager(helper, villagerPos, false);
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (target == null) {
            throw new IllegalStateException("failed to create zombie target");
        }
        target.absMoveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
        target.setNoAi(true);
        helper.getLevel().addFreshEntity(target);

        float bystanderHealth = bystander.getHealth();
        float targetHealth = target.getHealth();

        ItemStack bow = Items.BOW.getDefaultInstance();
        archer.setItemSlot(EquipmentSlot.MAINHAND, bow);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(archer, Items.ARROW.getDefaultInstance(), 1.0F, bow);
        arrow.setNoGravity(true);
        arrow.setPos(archer.getX(), bystander.getY() + bystander.getBbHeight() * 0.5D, archer.getZ());
        arrow.shoot(1.0D, 0.0D, 0.0D, 1.5F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(bystander.getHealth() == bystanderHealth,
                    "archer arrow damaged the villager in front of its target");
            helper.assertTrue(target.getHealth() < targetHealth,
                    "archer arrow did not continue through the villager to hit the hostile target");
            helper.succeed();
        });
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos pos, boolean archer) {
        helper.getLevel().getChunk(pos);
        var builder = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos));
        if (archer) {
            builder.withProfession(ProfessionsMCA.ARCHER);
        }
        VillagerEntityMCA villager = builder.spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }
}
