package mca.network.client;

import java.util.Optional;

import mca.client.book.Book;
import mca.client.gui.*;
import mca.entity.VillagerLike;
import mca.item.BabyItem;
import mca.item.ExtendedWrittenBookItem;
import mca.server.world.data.BabyTracker;
import mca.server.world.data.Village;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class ClientInteractionManagerImpl implements ClientInteractionManager {

    private final MinecraftClient client = MinecraftClient.getInstance();

    @Override
    public void handleGuiRequest(OpenGuiRequest message) {
        switch (message.gui) {
            case WHISTLE:
                client.setScreen(new WhistleScreen());
                break;
            case BOOK:
                if (client.player != null) {
                    ItemStack item = client.player.getStackInHand(Hand.MAIN_HAND);
                    if (item.getItem() instanceof ExtendedWrittenBookItem) {
                        Book book = ((ExtendedWrittenBookItem)item.getItem()).getBook(item);
                        client.setScreen(new ExtendedBookScreen(book));
                    }
                }
                break;
            case BLUEPRINT:
                client.setScreen(new BlueprintScreen());
                break;
            case INTERACT:
                VillagerLike<?> villager = (VillagerLike<?>)client.world.getEntityById(message.villager);
                client.setScreen(new InteractScreen(villager));
                break;
            case VILLAGER_EDITOR:
                Entity entity = client.world.getEntityById(message.villager);
                client.setScreen(new VillagerEditorScreen(entity.getUuid(), MinecraftClient.getInstance().player.getUuid()));
                break;
            case BABY_NAME:
                if (client.player != null) {
                    ItemStack item = client.player.getStackInHand(Hand.MAIN_HAND);
                    if (item.getItem() instanceof BabyItem) {
                        client.setScreen(new NameBabyScreen(client.player, item));
                    }
                }
                break;
            case FAMILY_TREE:
                client.setScreen(new FamilyTreeSearchScreen());
                break;
            default:
        }
    }

    @Override
    public void handleFamilyTreeResponse(GetFamilyTreeResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof FamilyTreeScreen gui) {
            gui.setFamilyData(message.uuid, message.family);
        }
    }

    @Override
    public void handleInteractDataResponse(GetInteractDataResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof InteractScreen gui) {
            gui.setConstraints(message.constraints);
            gui.setParents(message.father, message.mother);
            gui.setSpouse(message.marriageState, message.spouse);
        }
    }

    @Override
    public void handleVillageDataResponse(GetVillageResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof BlueprintScreen gui) {
            Village village = new Village();
            village.load(message.getData());

            gui.setVillage(village);
            gui.setRank(message.rank, message.reputation, message.ids, message.tasks, message.buildingTypes);
        }
    }

    @Override
    public void handleVillageDataFailedResponse(GetVillageFailedResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof BlueprintScreen gui) {
            gui.setVillage(null);
        }
    }

    @Override
    public void handleFamilyDataResponse(GetFamilyResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof WhistleScreen gui) {
            gui.setVillagerData(message.getData());
        }
    }

    @Override
    public void handleVillagerDataResponse(GetVillagerResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof VillagerEditorScreen gui) {
            gui.setVillagerData(message.getData());
        }
    }

    @Override
    public void handleDialogueResponse(InteractionDialogueResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof InteractScreen gui) {
            gui.setDialogue(message.question, message.answers, message.silent);
        }
    }

    @Override
    public void handleChildData(GetChildDataResponse message) {
        BabyItem.CLIENT_STATE_CACHE.put(message.id, Optional.ofNullable(message.getData()).map(BabyTracker.ChildSaveState::new));
    }

    @Override
    public void handleAnalysisResults(AnalysisResults message) {
        InteractScreen.setAnalysis(message.analysis);
    }

    @Override
    public void handleBabyNameResponse(BabyNameResponse message) {
        Screen screen = client.currentScreen;
        if (screen instanceof NameBabyScreen gui) {
            gui.setBabyName(message.getName());
        }
    }

    @Override
    public void handleToastMessage(ShowToastRequest message) {
        SystemToast.add(client.getToastManager(), SystemToast.Type.TUTORIAL_HINT, message.getTitle(), message.getMessage());
    }

    @Override
    public void handleFamilyTreeUUIDResponse(FamilyTreeUUIDResponse response) {
        Screen screen = client.currentScreen;
        if (screen instanceof FamilyTreeSearchScreen gui) {
            gui.setList(response.getList());
        }
    }
}
