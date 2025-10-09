package net.mca.network;

import net.mca.Config;
import net.mca.MCAClient;
import net.mca.client.tts.SpeechManager;
import net.mca.client.book.Book;
import net.mca.client.book.CivilRegistryBook;
import net.mca.client.gui.*;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.item.BabyItem;
import net.mca.item.ExtendedWrittenBookItem;
import net.mca.network.s2c.*;
import net.mca.resources.BuildingTypes;
import net.mca.server.world.data.Village;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class ClientInteractionManagerImpl implements ClientInteractionManager {
    private final Minecraft client = Minecraft.getInstance();

    @Override
    public void handleGuiRequest(OpenGuiRequest message) {
        Entity entity;
        assert client.level != null;
        assert Minecraft.getInstance().player != null;
        switch (message.getGui()) {
            case WHISTLE:
                client.setScreen(new WhistleScreen());
                break;
            case BOOK:
                if (client.player != null) {
                    ItemStack item = client.player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (item.getItem() instanceof ExtendedWrittenBookItem bookItem) {
                        Book book = bookItem.getBook(item);
                        client.setScreen(new ExtendedBookScreen(book));
                    }
                }
                break;
            case BLUEPRINT:
                client.setScreen(new BlueprintScreen());
                break;
            case INTERACT:
                if (client.player != null) {
                    ItemStack item = client.player.getItemInHand(InteractionHand.MAIN_HAND);
                    boolean isOnBlacklist = Config.getInstance().villagerInteractionItemBlacklist.contains(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                    if (!isOnBlacklist) {
                        VillagerLike<?> villager = (VillagerLike<?>) client.level.getEntity(message.villager);
                        client.setScreen(new InteractScreen(villager));
                    }
                }
                break;
            case VILLAGER_EDITOR:
                entity = client.level.getEntity(message.villager);
                assert entity != null;
                client.setScreen(new VillagerEditorScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                break;
            case LIMITED_VILLAGER_EDITOR:
                entity = client.level.getEntity(message.villager);
                assert entity != null;
                client.setScreen(new LimitedVillagerEditorScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                break;
            case NEEDLE_AND_THREAD:
                entity = client.level.getEntity(message.villager);
                if (entity == null) {
                    client.setScreen(new NeedleScreen(Minecraft.getInstance().player.getUUID()));
                } else {
                    client.setScreen(new NeedleScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                }
                break;
            case COMB:
                entity = client.level.getEntity(message.villager);
                if (entity == null) {
                    client.setScreen(new CombScreen(Minecraft.getInstance().player.getUUID()));
                } else {
                    client.setScreen(new CombScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                }
                break;
            case BABY_NAME:
                if (client.player != null) {
                    ItemStack item = client.player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (item.getItem() instanceof BabyItem) {
                        client.setScreen(new NameBabyScreen(client.player, item));
                    }
                }
                break;
            case FAMILY_TREE:
                client.setScreen(new FamilyTreeSearchScreen());
                break;
            case VILLAGER_TRACKER:
                client.setScreen(new VillagerTrackerSearchScreen());
                break;
            default:
        }
    }

    @Override
    public void handleFamilyTreeResponse(GetFamilyTreeResponse message) {
        Screen screen = client.screen;
        if (screen instanceof FamilyTreeScreen gui) {
            gui.setFamilyData(message.uuid, message.family);
        }
    }

    @Override
    public void handleInteractDataResponse(GetInteractDataResponse message) {
        Screen screen = client.screen;
        if (screen instanceof InteractScreen gui) {
            gui.setConstraints(message.constraints);
            gui.setParents(message.father, message.mother);
            gui.setSpouse(message.marriageState, message.spouse);
        }
    }

    @Override
    public void handleVillageDataResponse(GetVillageResponse message) {
        Screen screen = client.screen;
        if (screen instanceof BlueprintScreen gui) {
            BuildingTypes.getInstance().setBuildingTypes(message.buildingTypes);

            Village village = new Village(message.getData(), null);
            gui.setVillage(village);
            gui.setVillageData(message.rank, message.reputation, message.isVillage, message.ids, message.tasks);
        }
    }

    @Override
    public void handleVillageDataFailedResponse(GetVillageFailedResponse message) {
        Screen screen = client.screen;
        if (screen instanceof BlueprintScreen gui) {
            gui.setVillage(null);
        }
    }

    @Override
    public void handleFamilyDataResponse(GetFamilyResponse message) {
        Screen screen = client.screen;
        if (screen instanceof WhistleScreen gui) {
            gui.setVillagerData(message.getData());
        }
    }

    @Override
    public void handleVillagerDataResponse(GetVillagerResponse message) {
        Screen screen = client.screen;
        if (screen instanceof VillagerEditorScreen gui) {
            gui.setVillagerData(message.getData());
        }
    }

    @Override
    public void handleDialogueResponse(InteractionDialogueResponse message) {
        Screen screen = client.screen;
        if (screen instanceof InteractScreen gui) {
            gui.setDialogue(message.question, message.answers);
        }
    }

    @Override
    public void handleDialogueQuestionResponse(InteractionDialogueQuestionResponse message) {
        Screen screen = client.screen;
        if (screen instanceof InteractScreen gui) {
            gui.setLastPhrase(message.getQuestionText(), message.silent);
        }
    }

    @Override
    public void handleSkinListResponse(AnalysisResults message) {
        InteractScreen.setAnalysis(message.analysis);
    }

    @Override
    public void handleBabyNameResponse(BabyNameResponse message) {
        Screen screen = client.screen;
        if (screen instanceof NameBabyScreen gui) {
            gui.setBabyName(message.getName());
        }
    }

    @Override
    public void handleVillagerNameResponse(VillagerNameResponse message) {
        Screen screen = client.screen;
        if (screen instanceof VillagerEditorScreen gui) {
            gui.setVillagerName(message.getName());
        }
    }

    @Override
    public void handleToastMessage(ShowToastRequest message) {
        SystemToast.add(client.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT, message.getTitle(), message.getMessage());
    }

    @Override
    public void handleFamilyTreeUUIDResponse(FamilyTreeUUIDResponse response) {
        Screen screen = client.screen;
        if (screen instanceof FamilyTreeSearchScreen gui) {
            gui.setList(response.getList());
        }
    }

    @Override
    public void handlePlayerDataMessage(PlayerDataMessage response) {
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.get().create(Minecraft.getInstance().level);
        assert villager != null;
        villager.readAdditionalSaveData(response.getData());
        MCAClient.addPlayerData(response.uuid, villager);
    }

    @Override
    public void handleSkinListResponse(SkinListResponse message) {
        Screen screen = client.screen;
        VillagerEditorScreen.setSkinList(message.getClothing(), message.getHair());
        if (screen instanceof SkinListUpdateListener gui) {
            gui.skinListUpdatedCallback();
        }
    }

    @Override
    public void handleDestinyGuiRequest(OpenDestinyGuiRequest message) {
        MCAClient.getDestinyManager().requestOpen(message.allowTeleportation);
    }

    @Override
    public void handleConfigResponse(ConfigResponse message) {
        Config.setServerConfig(message.getConfig());
    }

    @Override
    public void handleVillagerMessage(VillagerMessage message) {
        client.getChatListener().handleSystemMessage(message.getMessage(), false);
        SpeechManager.INSTANCE.onChatMessage(message.getContent(), message.getUuid());
    }

    @Override
    public void handleCustomSkinsChangedMessage(CustomSkinsChangedMessage message) {
        VillagerEditorScreen.setSkinListOutdated();
    }

    @Override
    public void handleCivilRegistryResponse(CivilRegistryResponse response) {
        Screen screen = client.screen;
        if (screen instanceof ExtendedBookScreen extendedBookScreen && (extendedBookScreen.getBook() instanceof CivilRegistryBook civilRegistryBook)) {
            civilRegistryBook.receive(response.getIndex(), response.getLines());
        }
    }
}
