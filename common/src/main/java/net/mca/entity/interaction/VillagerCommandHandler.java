package net.mca.entity.interaction;

import net.mca.Config;
import net.mca.MCA;
import net.mca.ProfessionsMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.relationship.RelationshipState;
import net.mca.item.ItemsMCA;
import net.mca.mixin.MixinVillagerEntityInvoker;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.util.WorldUtils;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.ai.FuzzyPositions;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;

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
    public boolean handle(ServerPlayerEntity player, String command) {
        Memories memory = entity.getVillagerBrain().getMemoriesForPlayer(player);

        if (MoveState.byCommand(command).filter(state -> {
            entity.getVillagerBrain().setMoveState(state, player);
            return true;
        }).isPresent()) {
            return true;
        }

        if (Chore.byCommand(command).filter(chore -> {
            entity.getVillagerBrain().assignJob(chore, player);
            CriterionMCA.GENERIC_EVENT_CRITERION.trigger(player, "chores");
            entity.sendChatMessage(Text.translatable("chore.success"), player);
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
                if (player.getPassengerList().size() >= 3) {
                    player.getPassengerList().get(0).stopRiding();
                }
                if (entity.hasVehicle()) {
                    entity.stopRiding();
                } else {
                    entity.startRiding(player, true);
                }
                player.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(player));
                return false;
            }
            case "ridehorse" -> {
                if (entity.hasVehicle()) {
                    entity.stopRiding();
                } else {
                    entity.getWorld().getOtherEntities(player, player.getBoundingBox()
                                    .expand(10), e -> e instanceof Saddleable && ((Saddleable) e).isSaddled())
                            .stream()
                            .filter(horse -> !horse.hasPassengers())
                            .min(Comparator.comparingDouble(a -> a.squaredDistanceTo(entity))).ifPresentOrElse(horse -> {
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
                MixinVillagerEntityInvoker invoker = (MixinVillagerEntityInvoker) this.entity;
                invoker.invokeBeginTradeWith(player);
                return false;
            }
            case "inventory" -> {
                player.openHandledScreen(entity);
                return false;
            }
            case "gift" -> {
                entity.getRelationships().giveGift(player, memory);
                return true;
            }
            case "adopt" -> {
                entity.sendChatMessage(player, "interaction.adopt.success");
                FamilyTreeNode parentNode = FamilyTree.get((ServerWorld) player.getWorld()).getOrCreate(player);
                entity.getRelationships().getFamilyEntry().assignParent(parentNode);
                Optional<FamilyTreeNode> parentSpouse = FamilyTree.get((ServerWorld) player.getWorld()).getOrEmpty(parentNode.partner());
                parentSpouse.ifPresent(p -> entity.getRelationships().getFamilyEntry().assignParent(p));
            }
            case "procreate" -> {
                if (memory.getHearts() < 100) {
                    entity.sendChatMessage(player, "interaction.procreate.fail.lowhearts");
                } else if (entity.getRelationships().mayProcreateAgain(player.getWorld().getTime())) {
                    entity.getRelationships().startProcreating(player.getWorld().getTime());
                } else {
                    entity.sendChatMessage(player, "interaction.procreate.fail.toosoon");
                }
                return true;
            }
            case "divorcePapers" -> {
                player.getInventory().insertStack(new ItemStack(ItemsMCA.DIVORCE_PAPERS.get()));
                return true;
            }
            case "divorceConfirm" -> {
                ItemStack papers = ItemsMCA.DIVORCE_PAPERS.get().getDefaultStack();
                Memories memories = entity.getVillagerBrain().getMemoriesForPlayer(player);
                if (player.getInventory().contains(papers)) {
                    entity.sendChatMessage(player, "divorcePaper");
                    player.getInventory().removeOne(papers);
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
                entity.setProfession(ProfessionsMCA.OUTLAW.get());
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

                        entity.setProfession(ProfessionsMCA.GUARD.get());
                        entity.sendChatMessage(player, "profession.set.guard");
                    }
                    case "archer" -> {
                        entity.setProfession(ProfessionsMCA.ARCHER.get());
                        entity.sendChatMessage(player, "profession.set.archer");
                    }
                }
                return true;
            }
            case "apologize" -> {
                Vec3d pos = entity.getPos();
                entity.getWorld().getNonSpectatingEntities(VillagerEntityMCA.class, new Box(pos, pos).expand(32)).forEach(v -> {
                    if (entity.squaredDistanceTo(v) <= (v.getTarget() == null ? 1024 : 64)) {
                        v.pardonPlayers(99);
                    }
                });
            }
            case "location" -> {
                if (Config.getInstance().structuresInRumors.size() > 0) {
                    //choose a random arg from the default pool
                    if (arg.length() == 0) {
                        arg = Config.getInstance().structuresInRumors.get(entity.getRandom().nextInt(Config.getInstance().structuresInRumors.size()));
                    }

                    //slightly randomly the search center
                    ServerWorld world = (ServerWorld) entity.getWorld();
                    String finalArg = arg;
                    MCA.executorService.execute(() -> {
                        Identifier identifier = new Identifier(finalArg);
                        BlockPos pos = FuzzyPositions.localFuzz(entity.getRandom(), 1024, 0).add(entity.getBlockPos());
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
            case "slap" -> player.damage(player.getWorld().getDamageSources().cramming(), 1.0f);
        }

        return super.handle(player, command);
    }

    private void payEmeralds(ServerPlayerEntity player, int emeralds) {
        PlayerInventory inventory = player.getInventory();
        for (int j = 0; j < inventory.size(); ++j) {
            ItemStack itemStack = inventory.getStack(j);
            if (itemStack.getItem().equals(Items.EMERALD)) {
                int c = Math.min(itemStack.getCount(), emeralds);
                itemStack.decrement(c);
                emeralds -= c;
                if (emeralds <= 0) {
                    return;
                }
            }
        }
    }
}
