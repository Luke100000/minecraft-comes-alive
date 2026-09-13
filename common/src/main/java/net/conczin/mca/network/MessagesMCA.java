package net.conczin.mca.network;

import net.conczin.mca.network.c2s.*;
import net.conczin.mca.network.s2c.*;

public interface MessagesMCA {
    static void register(Network.Registrar c) {
        c.register(AddCustomClothingMessage.TYPE, AddCustomClothingMessage.STREAM_CODEC, true);
        c.register(BabyNameRequest.TYPE, BabyNameRequest.STREAM_CODEC, true);
        c.register(BabyNamingVillagerMessage.TYPE, BabyNamingVillagerMessage.STREAM_CODEC, true);
        c.register(ChatAIContextUpdateRequest.TYPE, ChatAIContextUpdateRequest.STREAM_CODEC, true);
        c.register(CallToPlayerMessage.TYPE, CallToPlayerMessage.STREAM_CODEC, true);
        c.register(CivilRegistryPageRequest.TYPE, CivilRegistryPageRequest.STREAM_CODEC, true);
        c.register(ConfigRequest.TYPE, ConfigRequest.STREAM_CODEC, true);
        c.register(DamageItemMessage.TYPE, DamageItemMessage.STREAM_CODEC, true);
        c.register(DestinyMessage.TYPE, DestinyMessage.STREAM_CODEC, true);
        c.register(FamilyTreeUUIDLookup.TYPE, FamilyTreeUUIDLookup.STREAM_CODEC, true);
        c.register(GetFamilyRequest.TYPE, GetFamilyRequest.STREAM_CODEC, true);
        c.register(GetFamilyTreeRequest.TYPE, GetFamilyTreeRequest.STREAM_CODEC, true);
        c.register(GetInteractDataRequest.TYPE, GetInteractDataRequest.STREAM_CODEC, true);
        c.register(GetVillageRequest.TYPE, GetVillageRequest.STREAM_CODEC, true);
        c.register(GetVillagerRequest.TYPE, GetVillagerRequest.STREAM_CODEC, true);
        c.register(InteractionCloseRequest.TYPE, InteractionCloseRequest.STREAM_CODEC, true);
        c.register(InteractionDialogueInitMessage.TYPE, InteractionDialogueInitMessage.STREAM_CODEC, true);
        c.register(InteractionDialogueMessage.TYPE, InteractionDialogueMessage.STREAM_CODEC, true);
        c.register(InteractionVillagerMessage.TYPE, InteractionVillagerMessage.STREAM_CODEC, true);
        c.register(PlayerDataRequest.TYPE, PlayerDataRequest.STREAM_CODEC, true);
        c.register(RemoveCustomClothingMessage.TYPE, RemoveCustomClothingMessage.STREAM_CODEC, true);
        c.register(RenameVillageMessage.TYPE, RenameVillageMessage.STREAM_CODEC, true);
        c.register(ReportBuildingMessage.TYPE, ReportBuildingMessage.STREAM_CODEC, true);
        c.register(SaveVillageMessage.TYPE, SaveVillageMessage.STREAM_CODEC, true);
        c.register(SetTargetMessage.TYPE, SetTargetMessage.STREAM_CODEC, true);
        c.register(VillagerEditorSyncRequest.TYPE, VillagerEditorSyncRequest.STREAM_CODEC, true);
        c.register(VillagerNameRequest.TYPE, VillagerNameRequest.STREAM_CODEC, true);
        c.register(ConfirmBuildingPolymorphMessage.TYPE, ConfirmBuildingPolymorphMessage.STREAM_CODEC, true);

        c.register(AnalysisResults.TYPE, AnalysisResults.STREAM_CODEC, false);
        c.register(BabyNameResponse.TYPE, BabyNameResponse.STREAM_CODEC, false);
        c.register(ChatAIContextResponse.TYPE, ChatAIContextResponse.STREAM_CODEC, false);
        c.register(CivilRegistryResponse.TYPE, CivilRegistryResponse.STREAM_CODEC, false);
        c.register(ConfigResponse.TYPE, ConfigResponse.STREAM_CODEC, false);
        c.register(FamilyTreeUUIDResponse.TYPE, FamilyTreeUUIDResponse.STREAM_CODEC, false);
        c.register(GetFamilyResponse.TYPE, GetFamilyResponse.STREAM_CODEC, false);
        c.register(GetFamilyTreeResponse.TYPE, GetFamilyTreeResponse.STREAM_CODEC, false);
        c.register(GetInteractDataResponse.TYPE, GetInteractDataResponse.STREAM_CODEC, false);
        c.register(GetVillageFailedResponse.TYPE, GetVillageFailedResponse.STREAM_CODEC, false);
        c.register(GetVillageResponse.TYPE, GetVillageResponse.STREAM_CODEC, false);
        c.register(GetVillagerResponse.TYPE, GetVillagerResponse.STREAM_CODEC, false);
        c.register(InteractionDialogueQuestionResponse.TYPE, InteractionDialogueQuestionResponse.STREAM_CODEC, false);
        c.register(InteractionDialogueResponse.TYPE, InteractionDialogueResponse.STREAM_CODEC, false);
        c.register(OpenDestinyGuiRequest.TYPE, OpenDestinyGuiRequest.STREAM_CODEC, false);
        c.register(OpenGuiRequest.TYPE, OpenGuiRequest.STREAM_CODEC, false);
        c.register(PlayerDataMessage.TYPE, PlayerDataMessage.STREAM_CODEC, false);
        c.register(ShowToastRequest.TYPE, ShowToastRequest.STREAM_CODEC, false);
        c.register(AppearanceCatalogSync.TYPE, AppearanceCatalogSync.STREAM_CODEC, false);
        c.register(VillagerMessage.TYPE, VillagerMessage.STREAM_CODEC, false);
        c.register(VillagerNameResponse.TYPE, VillagerNameResponse.STREAM_CODEC, false);
        c.register(BuildingPolymorphMessage.TYPE, BuildingPolymorphMessage.STREAM_CODEC, false);
    }
}
