package net.conczin.mca.network;

import net.conczin.mca.client.gui.FamilyTreeSearchScreen;
import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.CommonConfig;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.c2s.BabyNameRequest;
import net.conczin.mca.network.c2s.ChatAIContextUpdateRequest;
import net.conczin.mca.network.c2s.RemoveCustomClothingMessage;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.network.c2s.VillagerNameRequest;
import net.conczin.mca.network.s2c.*;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.resources.data.SerializablePair;
import net.conczin.mca.resources.data.analysis.Analysis;
import net.conczin.mca.resources.data.analysis.ChanceAnalysis;
import net.conczin.mca.resources.data.analysis.FloatAnalysis;
import net.conczin.mca.resources.data.analysis.IntAnalysis;
import net.conczin.mca.resources.data.skin.*;
import net.conczin.mca.resources.data.tasks.*;
import net.conczin.mca.server.world.data.FamilyTreeNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.io.ObjectInputFilter.Status.*;

public final class PacketFilter implements ObjectInputFilter {
    public static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private static final long MAX_DEPTH = 32;
    private static final long MAX_REFERENCES = 100_000;
    private static final long MAX_ARRAY_LENGTH = 65_536;

    private static final Map<Class<? extends Message>, Set<Class<?>>> PAYLOAD_TYPES = Map.ofEntries(
            Map.entry(BabyNameRequest.class, Set.of(Gender.class)),
            Map.entry(ChatAIContextUpdateRequest.class, Set.of(ChatAIContextUpdateRequest.Target.class)),
            Map.entry(RemoveCustomClothingMessage.class, Set.of(RemoveCustomClothingMessage.Type.class)),
            Map.entry(ReportBuildingMessage.class, Set.of(ReportBuildingMessage.Action.class)),
            Map.entry(VillagerNameRequest.class, Set.of(Gender.class)),
            Map.entry(AnalysisResults.class, Set.of(Analysis.class, ChanceAnalysis.class, FloatAnalysis.class,
                    IntAnalysis.class, SerializablePair.class, LinkedList.class, Number.class, Integer.class, Float.class)),
            Map.entry(BuildingPolymorphMessage.class, Set.of(Object[].class)),
            Map.entry(CivilRegistryResponse.class, Set.of(ArrayList.class, Object[].class)),
            Map.entry(ConfigResponse.class, Set.of(CommonConfig.class, ArrayList.class, Object[].class,
                    HashMap.class, LinkedHashMap.class, Map.Entry[].class, Boolean.class)),
            Map.entry(CustomSkinListResponse.class, Set.of(HashMap.class, Map.Entry[].class, SkinListEntry.class,
                    Clothing.class, Hair.class, BodySkin.class, LayeredHair.class, LayeredHair.Category.class,
                    HairStyle.class, Gender.class)),
            Map.entry(FamilyTreeUUIDResponse.class, Set.of(ArrayList.class, Object[].class, FamilyTreeSearchScreen.Entry.class)),
            Map.entry(GetFamilyTreeResponse.class, Set.of(HashMap.class, Map.Entry[].class, FamilyTreeNode.class,
                    HashSet.class, Gender.class, RelationshipState.class)),
            Map.entry(GetInteractDataResponse.class, Set.of(HashSet.class, Map.Entry[].class, Constraint.class, RelationshipState.class)),
            Map.entry(GetVillageResponse.class, Set.of(HashMap.class, Map.Entry[].class, HashSet.class, LinkedList.class,
                    Object[].class, Rank.class, BuildingType.class, Number.class, Integer.class,
                    Task.class, AdvancementTask.class, BlockingTask.class, BuildingTask.class, PopulationTask.class, ReputationTask.class)),
            Map.entry(InteractionDialogueResponse.class, Set.of(LinkedList.class)),
            Map.entry(SkinListResponse.class, Set.of(HashMap.class, Map.Entry[].class, SkinListEntry.class,
                    Clothing.class, Hair.class, Gender.class))
    );

    // List.of and Map.of serialize through a JDK proxy and resolve to these exact implementation classes.
    private static final Set<Class<? extends Message>> IMMUTABLE_COLLECTION_PACKETS =
            Set.of(BuildingPolymorphMessage.class, ConfigResponse.class, GetVillageResponse.class);
    private static final String IMMUTABLE_COLLECTION_PROXY = "java.util.CollSer";
    private static final Set<String> IMMUTABLE_COLLECTION_TYPES = Set.of(
            IMMUTABLE_COLLECTION_PROXY, "java.util.ImmutableCollections$List12", "java.util.ImmutableCollections$ListN",
            "java.util.ImmutableCollections$Map1", "java.util.ImmutableCollections$MapN"
    );

    private static final ClassValue<PacketFilter> FILTERS = new ClassValue<>() {
        @Override
        protected PacketFilter computeValue(Class<?> type) {
            return createFilter(type.asSubclass(Message.class));
        }
    };

    private static volatile PacketFilter villageResponseFilter = createFilter(GetVillageResponse.class);

    private final Class<? extends Message> expectedType;
    private final Map<String, Class<?>> payloadTypes;
    private final boolean usesImmutableCollections;

    private PacketFilter(Class<? extends Message> expectedType, Map<String, Class<?>> payloadTypes) {
        this.expectedType = expectedType;
        this.payloadTypes = Map.copyOf(payloadTypes);
        this.usesImmutableCollections = IMMUTABLE_COLLECTION_PACKETS.contains(expectedType);
    }

    private static PacketFilter createFilter(Class<? extends Message> expectedType) {
        Map<String, Class<?>> types = new HashMap<>();
        types.put(UUID.class.getName(), UUID.class);
        types.put(Enum.class.getName(), Enum.class);
        if (NbtDataMessage.class.isAssignableFrom(expectedType)) {
            types.put(NbtDataMessage.class.getName(), NbtDataMessage.class);
            types.put(NbtDataMessage.Data.class.getName(), NbtDataMessage.Data.class);
        }
        for (Class<?> type : PAYLOAD_TYPES.getOrDefault(expectedType, Set.of())) {
            types.put(type.getName(), type);
        }
        return new PacketFilter(expectedType, types);
    }

    /**
     * Allows an addon task and its exact payload types in clientbound Blueprint responses.
     * Call this from local addon initialization on the receiving client, alongside the
     * addon's normal task factory registration in {@link net.conczin.mca.resources.Tasks#TASK_TYPES}.
     * Include any extra serializable superclasses, field types, and array classes used on the wire.
     * Register only types that are safe to deserialize from untrusted servers.
     *
     * @param taskType the concrete addon task class
     * @param serializedTypes additional classes used by that task's serialized state
     */
    public static synchronized void registerVillageTaskTypes(Class<? extends Task> taskType, Class<?>... serializedTypes) {
        Map<String, Class<?>> types = new HashMap<>(villageResponseFilter.payloadTypes);
        addVillageTaskType(types, taskType.asSubclass(Task.class));
        for (Class<?> type : serializedTypes) {
            addVillageTaskType(types, type);
        }
        villageResponseFilter = new PacketFilter(GetVillageResponse.class, types);
    }

    private static void addVillageTaskType(Map<String, Class<?>> types, Class<?> type) {
        if (!Serializable.class.isAssignableFrom(type) || Message.class.isAssignableFrom(type) || Proxy.isProxyClass(type)) {
            throw new IllegalArgumentException("Unsupported village task payload class: " + type.getName());
        }
        Class<?> previous = types.putIfAbsent(type.getName(), type);
        if (previous != null && previous != type) {
            throw new IllegalArgumentException("Conflicting village task payload class: " + type.getName());
        }
    }

    public static ObjectInputStream openStream(InputStream input, Class<? extends Message> expectedType) throws IOException {
        PacketFilter filter = expectedType == GetVillageResponse.class ? villageResponseFilter : FILTERS.get(expectedType);
        return new PacketInputStream(input, filter);
    }

    @Override
    public Status checkInput(FilterInfo info) {
        if (info.depth() > MAX_DEPTH || info.references() > MAX_REFERENCES || info.arrayLength() > MAX_ARRAY_LENGTH) {
            return REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) {
            return UNDECIDED;
        }
        if (info.depth() == 1) {
            return type == expectedType ? ALLOWED : REJECTED;
        }
        if (payloadTypes.get(type.getName()) == type
                || usesImmutableCollections && type.getClassLoader() == null && IMMUTABLE_COLLECTION_TYPES.contains(type.getName())) {
            return ALLOWED;
        }
        return REJECTED;
    }

    private Class<?> resolveClass(String name) throws InvalidClassException, ClassNotFoundException {
        if (expectedType.getName().equals(name)) {
            return expectedType;
        }
        Class<?> type = payloadTypes.get(name);
        if (type != null) {
            return type;
        }
        if (usesImmutableCollections && IMMUTABLE_COLLECTION_PROXY.equals(name)) {
            return Class.forName(IMMUTABLE_COLLECTION_PROXY, false, null);
        }
        throw new InvalidClassException("Class is not allowed in this MCA packet");
    }

    private static final class PacketInputStream extends ObjectInputStream {
        private final PacketFilter filter;

        private PacketInputStream(InputStream input, PacketFilter filter) throws IOException {
            super(input);
            this.filter = filter;
            ObjectInputFilter existing = getObjectInputFilter();
            setObjectInputFilter(existing == null ? filter : ObjectInputFilter.merge(filter, existing));
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            // ObjectInputStream resolves descriptors before it invokes the object filter.
            return filter.resolveClass(descriptor.getName());
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
            throw new InvalidClassException("Dynamic proxies are not allowed in MCA packets");
        }
    }
}
