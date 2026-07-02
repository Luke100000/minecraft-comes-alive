package net.conczin.mca.entity.interaction;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.mixin.MixinVillagerInvoker;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

public class VillagerCommandHandler extends EntityCommandHandler<VillagerEntityMCA> {
    public VillagerCommandHandler(VillagerEntityMCA entity) {
        super(entity);
    }

    /**
     * Called on the server to respond to button events.
     */
    @Override
    public boolean handle(ServerPlayer player, String command) {
        Memories memory = entity.getVillagerBrain().getMemoriesForPlayer(player);

        if (MoveState.byCommand(command).filter(state -> {
            entity.getVillagerBrain().setMoveState(state, player);
            return true;
        }).isPresent()) {
            return true;
        }

        if (Chore.byCommand(command).filter(chore -> {
            entity.getVillagerBrain().assignJob(chore, player);
            CriterionMCA.GENERIC_EVENT.trigger(player, "chores");
            entity.sendChatMessage(Component.translatable("chore.success"), player);
            return true;
        }).isPresent()) {
            return true;
        }

        //an optional argument is stored separated using a dot
        String arg = "";
        String[] split = command.split("\\.");
        if (split.length > 1) {
            command = split[0];
            arg = split[1];
        }

        switch (command) {
            case "pick_up" -> {
                if (player.getPassengers().size() >= 3) {
                    player.getPassengers().getFirst().stopRiding();
                }
                if (entity.isPassenger()) {
                    entity.stopRiding();
                } else {
                    entity.startRiding(player, true);
                }
                player.connection.send(new ClientboundSetPassengersPacket(player));
                return false;
            }
            case "ridehorse" -> {
                if (entity.isPassenger()) {
                    entity.stopRiding();
                } else {
                    entity.level().getEntities(player, player.getBoundingBox()
                                    .inflate(10), e -> e instanceof Saddleable && ((Saddleable) e).isSaddled())
                            .stream()
                            .filter(horse -> !horse.isVehicle())
                            .min(Comparator.comparingDouble(a -> a.distanceToSqr(entity))).ifPresentOrElse(horse -> {
                                entity.startRiding(horse, false);
                                entity.sendChatMessage(player, "interaction.ridehorse.success");
                            }, () -> entity.sendChatMessage(player, "interaction.ridehorse.fail.notnearby"));
                }
                return true;
            }
            case "sethome" -> {
                entity.getResidency().setHome(player);
                return true;
            }
            case "gohome" -> {
                entity.getResidency().goHome(player);
                stopInteracting();
                return false;
            }
            case "setworkplace" -> {
                entity.getResidency().setWorkplace(player);
                return true;
            }
            case "trade" -> {
                entity.getInteractions().stopInteracting();
                MixinVillagerInvoker invoker = (MixinVillagerInvoker) this.entity;
                invoker.invokeStartTrading(player);
                return false;
            }
            case "inventory" -> {
                player.openMenu(entity);
                return false;
            }
            case "gift" -> {
                entity.getRelationships().giveGift(player, memory);
                return true;
            }
            case "adopt" -> {
                entity.sendChatMessage(player, "interaction.adopt.success");
                FamilyTreeNode parentNode = FamilyTree.get((ServerLevel) player.level()).getOrCreate(player);
                Optional<FamilyTreeNode> parentSpouse = FamilyTree.get((ServerLevel) player.level()).getOrEmpty(parentNode.partner());
                entity.getRelationships().getFamilyEntry().replaceParents(parentNode, parentSpouse);
            }
            case "procreate" -> {
                if (memory.getHearts() < 100) {
                    entity.sendChatMessage(player, "interaction.procreate.fail.lowhearts");
                } else if (entity.getTraits().hasTrait(Traits.INFERTILE)) {
                    entity.sendChatMessage(player, "interaction.procreate.fail.infertile");
                } else if (entity.getRelationships().mayProcreateAgain(player.level().getGameTime())) {
                    entity.getRelationships().startProcreating(player.level().getGameTime());
                } else {
                    entity.sendChatMessage(player, "interaction.procreate.fail.toosoon");
                }
                return true;
            }
            case "divorcePapers" -> {
                player.getInventory().add(new ItemStack(ItemsMCA.DIVORCE_PAPERS));
                return true;
            }
            case "divorceConfirm" -> {
                ItemStack papers = ItemsMCA.DIVORCE_PAPERS.getDefaultInstance();
                Memories memories = entity.getVillagerBrain().getMemoriesForPlayer(player);
                if (player.getInventory().contains(papers)) {
                    entity.sendChatMessage(player, "divorcePaper");
                    player.getInventory().removeItem(papers);
                    memories.modHearts(-20);
                } else {
                    entity.sendChatMessage(player, "divorce");
                    memories.modHearts(-200);
                }
                entity.getVillagerBrain().modifyMoodValue(-5);
                entity.getRelationships().endRelationShip(RelationshipState.SINGLE);
                PlayerSaveData playerData = PlayerSaveData.get(player);
                playerData.endRelationShip(RelationshipState.SINGLE);
                return true;
            }
            case "execute" -> {
                entity.setProfession(ProfessionsMCA.OUTLAW);
                return true;
            }
            case "pardon" -> {
                entity.setProfession(VillagerProfession.NONE);
                return true;
            }
            case "stay_in_village" -> {
                entity.setProfession(VillagerProfession.NONE);
                entity.setDespawnDelay(0);
                return true;
            }
            case "hire_short" -> {
                payEmeralds(player, 5);
                entity.makeMercenary();
                entity.setDespawnDelay(24000 * 3);
                return true;
            }
            case "hire_long" -> {
                payEmeralds(player, 10);
                entity.makeMercenary();
                entity.setDespawnDelay(24000 * 7);
                return true;
            }
            case "infected" -> {
                entity.setInfected(!entity.isInfected());
                return true;
            }
            case "stopworking" -> {
                entity.getVillagerBrain().abandonJob();
                return true;
            }
            case "armor" -> {
                entity.getVillagerBrain().setArmorWear(!entity.getVillagerBrain().getArmorWear());
                if (entity.getVillagerBrain().getArmorWear()) {
                    entity.sendChatMessage(player, "armor.enabled");
                } else {
                    entity.sendChatMessage(player, "armor.disabled");
                }
                return true;
            }
            case "profession" -> {
                switch (arg) {
                    case "none" -> {
                        entity.setProfession(VillagerProfession.NONE);
                        entity.sendChatMessage(player, "profession.set.none");
                    }
                    case "guard" -> {

                        entity.setProfession(ProfessionsMCA.GUARD);
                        entity.sendChatMessage(player, "profession.set.guard");
                    }
                    case "archer" -> {
                        entity.setProfession(ProfessionsMCA.ARCHER);
                        entity.sendChatMessage(player, "profession.set.archer");
                    }
                }
                return true;
            }
            case "apologize" -> {
                Vec3 pos = entity.position();
                entity.level().getEntitiesOfClass(VillagerEntityMCA.class, new AABB(pos, pos).inflate(32)).forEach(v -> {
                    if (entity.distanceToSqr(v) <= (v.getTarget() == null ? 1024 : 64)) {
                        v.pardonPlayers(99);
                    }
                });
            }
            case "location" -> {
                if (!Config.getInstance().structuresInRumors.isEmpty()) {
                    //choose a random arg from the default pool
                    if (arg.isEmpty()) {
                        arg = Config.getInstance().structuresInRumors.get(entity.getRandom().nextInt(Config.getInstance().structuresInRumors.size()));
                    }

                    //slightly randomly the search center
                    ServerLevel world = (ServerLevel) entity.level();
                    String finalArg = arg;
                    MCA.executorService.execute(() -> {
                        ResourceLocation identifier = ResourceLocation.parse(finalArg);
                        BlockPos pos = RandomPos.generateRandomDirection(entity.getRandom(), 1024, 0).offset(entity.blockPosition());
                        Optional<BlockPos> position = WorldUtils.getClosestStructurePosition(world, pos, identifier, 64);
                        if (position.isPresent()) {
                            String posString = position.get().getX() + "," + position.get().getY() + "," + position.get().getZ();
                            entity.sendChatMessage(player, "dialogue.location." + identifier.getPath(), posString);
                        } else {
                            entity.sendChatMessage(player, "dialogue.location.forgot");
                        }
                    });
                } else {
                    entity.sendChatMessage(player, "dialogue.location.forgot");
                }
            }
            case "slap" -> player.hurt(player.level().damageSources().cramming(), 1.0f);
        }

        return super.handle(player, command);
    }

    private void payEmeralds(ServerPlayer player, int emeralds) {
        Inventory inventory = player.getInventory();
        for (int j = 0; j < inventory.getContainerSize(); ++j) {
            ItemStack itemStack = inventory.getItem(j);
            if (itemStack.getItem().equals(Items.EMERALD)) {
                int c = Math.min(itemStack.getCount(), emeralds);
                itemStack.shrink(c);
                emeralds -= c;
                if (emeralds <= 0) {
                    return;
                }
            }
        }
    }
}
