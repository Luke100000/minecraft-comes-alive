package net.mca.network.c2s;

import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.network.NbtDataMessage;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.resources.ClothingList;
import net.mca.resources.HairList;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class VillagerEditorSyncRequest extends NbtDataMessage implements Message {
    @Serial
    private static final long serialVersionUID = -5581564927127176555L;

    private final String command;
    private final UUID uuid;

    public VillagerEditorSyncRequest(String command, UUID uuid, CompoundTag data) {
        super(data);
        this.command = command;
        this.uuid = uuid;
    }

    private void setHair(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        if (villagerData != null) {
            // fetch hair
            String hair;
            if (getData().contains("offset")) {
                hair = HairList.getInstance().getPool(getGender(villagerData)).pickNext(villagerData.getString("hair"), getData().getInt("offset"));
            } else {
                hair = HairList.getInstance().getPool(getGender(villagerData)).pickOne();
            }

            // set
            villagerData.putString("hair", hair);
            saveEntity(player, entity, villagerData);
        }
    }

    private void setClothing(ServerPlayer player, Entity entity) {
        CompoundTag villagerData = GetVillagerRequest.getVillagerData(entity);
        if (villagerData != null) {
            String clothes = "mca:missing";
            if (entity instanceof Player) {
                if (getData().contains("offset")) {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), VillagerProfession.NONE).pickNext(villagerData.getString("clothes"), getData().getInt("offset"));
                } else {
                    clothes = ClothingList.getInstance().getPool(getGender(villagerData), VillagerProfession.NONE).pickOne();
                }
            } else if (entity instanceof VillagerLike<?> villager) {
                if (getData().contains("offset")) {
                    clothes = ClothingList.getInstance().getPool(villager).pickNext(villager.getClothes(), getData().getInt("offset"));
                } else {
                    clothes = ClothingList.getInstance().getPool(villager).pickOne();
                }
            }
            villagerData.putString("clothes", clothes);
            saveEntity(player, entity, villagerData);
        }
    }

    @Override
    public void receive(ServerPlayer player) {
        Entity entity = player.serverLevel().getEntity(uuid);
        switch (command) {
            case "hair":
                setHair(player, entity);
                break;
            case "clothing":
                setClothing(player, entity);
                break;
            case "gender":
                setHair(player, entity);
                setClothing(player, entity);
                break;
            case "sync":
                saveEntity(player, entity, getData());
                break;
            case "profession":
                if (entity instanceof VillagerEntityMCA villager) {
                    VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(new ResourceLocation(getData().getString("profession")));
                    villager.setProfession(profession);
                }
                break;
        }
        getData();
    }

    private void saveEntity(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        if (entity instanceof ServerPlayer serverPlayer) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            data.setEntityData(villagerData);
            data.setEntityDataSet(true);
            syncFamilyTree(player, entity, villagerData);

            //also update players
            serverPlayer.serverLevel().players().forEach(p -> NetworkHandler.sendToPlayer(new PlayerDataMessage(player.getUUID(), villagerData), p));
        } else if (entity instanceof VillagerLike) {
            ((LivingEntity)entity).readAdditionalSaveData(villagerData);
            entity.refreshDimensions();
            syncFamilyTree(player, entity, villagerData);

            if (entity instanceof VillagerEntityMCA villager) {
                villager.getResidency().getHomeVillage().ifPresent(b -> b.updateResident(villager));
            }
        }
    }

    private Gender getGender(CompoundTag villagerData) {
        return Gender.byId(villagerData.getInt("gender"));
    }

    private Optional<FamilyTreeNode> getFamilyNode(ServerPlayer player, FamilyTree tree, String name, Gender gender) {
        try {
            UUID uuid = UUID.fromString(name);
            Optional<FamilyTreeNode> node = tree.getOrEmpty(uuid);
            if (node.isPresent()) {
                player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_known", name, node.get().getName()), true);
                return node;
            } else {
                player.displayClientMessage(Component.translatable("gui.villager_editor.uuid_unknown", name).withStyle(ChatFormatting.RED), true);
                return Optional.empty();
            }
        } catch (IllegalArgumentException exception) {
            List<FamilyTreeNode> nodes = tree.getAllWithName(name).toList();
            if (nodes.isEmpty()) {
                //create a new entry
                player.displayClientMessage(Component.translatable("gui.villager_editor.name_created", name).withStyle(ChatFormatting.YELLOW), true);
                return Optional.of(tree.getOrCreate(UUID.randomUUID(), name, gender));
            } else {
                if (nodes.size() > 1) {
                    player.displayClientMessage(Component.translatable("gui.villager_editor.name_not_unique", name).withStyle(ChatFormatting.RED), true);

                    String uuids = nodes.stream().map(FamilyTreeNode::id).map(UUID::toString).collect(Collectors.joining(", "));
                    player.displayClientMessage(Component.translatable("gui.villager_editor.list_of_ids", uuids), false);
                } else {
                    player.displayClientMessage(Component.translatable("gui.villager_editor.name_unique", name), true);
                }

                return Optional.ofNullable(nodes.get(0));
            }
        }
    }

    private void syncFamilyTree(ServerPlayer player, Entity entity, CompoundTag villagerData) {
        FamilyTree tree = FamilyTree.get((ServerLevel)entity.level());
        FamilyTreeNode entry = tree.getOrCreate(entity);
        entry.setGender(getGender(getData()));
        entry.setName(getData().getString("villagerName"));

        if (villagerData.contains("tree_father_new")) {
            String name = villagerData.getString("tree_father_new");
            if (MCA.isBlankString(name)) {
                entry.removeFather();
            } else {
                getFamilyNode(player, tree, name, Gender.MALE).ifPresent(entry::setFather);
            }
        }

        if (villagerData.contains("tree_mother_new")) {
            String name = villagerData.getString("tree_mother_new");
            if (MCA.isBlankString(name)) {
                entry.removeMother();
            } else {
                getFamilyNode(player, tree, name, Gender.FEMALE).ifPresent(entry::setMother);
            }
        }

        if (villagerData.contains("tree_spouse_new")) {
            String name = villagerData.getString("tree_spouse_new");
            if (MCA.isBlankString(name)) {
                Optional.of(entry.partner()).flatMap(tree::getOrEmpty).ifPresent(node -> node.updatePartner(null, null));
                entry.updatePartner(null, null);
            } else {
                getFamilyNode(player, tree, name, entry.gender().opposite()).ifPresent(node -> {
                    entry.updatePartner(node);
                    node.updatePartner(entry);
                });
            }
        }
    }
}
