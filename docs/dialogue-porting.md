# DialogueEvent porting notes: Minecraft 26.1.2 and 26.2

These notes compare the shipped 1.21.1 dialogue overhaul with the local patched Minecraft
sources for 26.1.2 and 26.2. They describe API migration seams, **not** a completed
or tested port. Preserve event JSON, namespaced event/choice IDs, history semantics,
server-owned validation, and client-only presentation across versions.

## Datapack reload and identifiers

On 1.21.1, `DialogueEvents` extends `SimpleJsonResourceReloadListener` with a
`Gson, String` constructor and decodes `Map<ResourceLocation, JsonElement>` in
`apply(Map, ResourceManager, ProfilerFiller)`.

On both newer versions, `ResourceLocation` becomes `Identifier` and
`SimpleJsonResourceReloadListener<T>` takes a `Codec<T>` and `FileToIdConverter`
(or registry-aware constructor). Its prepared data is `Map<Identifier, T>`.
Adapt the JSON decoding boundary and resource-lister constructor while retaining
the existing strict per-resource validation, pack replacement behavior and
generation invalidation. Do not change the persisted namespaced event IDs.

The vanilla reload signature also changes from the six-argument
`PreparationBarrier, ResourceManager, ProfilerFiller, ProfilerFiller, Executor, Executor`
form to `SharedState, Executor, PreparationBarrier, Executor`.
The `FabricReloadListener` wrapper currently delegates the former signature;
adapt it to the newer reload lifecycle and use `SharedState.resourceManager()`.
Version-specific Fabric/NeoForge loader registration APIs were **not** verified
from the patched vanilla source trees; check their matching loader sources.

## Network payloads

`CustomPacketPayload.Type` holds `ResourceLocation` on 1.21.1 and `Identifier`
on 26.1.2/26.2. Replace identifier reads/writes and
`ResourceLocation.STREAM_CODEC` with the newer `readIdentifier`,
`writeIdentifier`, and `Identifier.STREAM_CODEC` APIs.

The inspected `StreamCodec.composite`, `ByteBufCodecs.VAR_LONG`,
`ByteBufCodecs.VAR_INT`, `ByteBufCodecs.optional`,
`ByteBufCodecs.collection(constructor, codec, maxSize)`, and
`UUIDUtil.STREAM_CODEC` APIs remain available in both newer trees.
26.2 adds another bounded collection-codec overload, but the current dialogue
packets need no separate architecture for it. Verify Fabric `PayloadTypeRegistry`
and NeoForge `RegisterPayloadHandlersEvent` against the actual target loader
versions before claiming network compatibility.

## World history persistence

1.21.1 stores `DialogueEventHistory` through `SavedData.Factory`,
`DimensionDataStorage.computeIfAbsent(factory, stringId)`, and
`SavedData.save(CompoundTag, HolderLookup.Provider)`.

Both newer versions instead use `SavedDataType<T>(Identifier, Supplier<T>,
Codec<T>, DataFixTypes)` and `SavedDataStorage.computeIfAbsent(type)`.
`SavedDataStorage` decodes/encodes through the supplied codec rather than
calling `SavedData.save(...)`. Port the persistence boundary without changing
the validated per-player/villager history format, completion/choice rules,
cooldown units, or unsupported-future-schema read-only protection.

**Save-file location changed:** 1.21.1 uses `<data-dir>/<string-id>.dat`, while
the newer `SavedDataStorage` resolves `<data-dir>/<namespace>/<path>.dat`.
Plan and test an explicit migration of `mca_dialogue_event_history` before
shipping the newer ports, or existing world history may appear to disappear.
Do not silently discard or overwrite an unreadable newer schema.

The inspected vanilla reload, payload, and SavedData APIs are otherwise the
same across 26.1.2 and 26.2. The newer trees differ in an additional
`ByteBufCodecs.collection` overload and 26.2 identifier path-containment
validation; neither calls for forking `DialogueEvent` or its data format.

Source basis: `minecraft-patched-1.21.1-sources`,
`minecraft-patched-26.1.2-sources`, and `minecraft-patched-26.2-sources`
under the local `mc_source_code` reference tree. Actual target loader builds,
save migrations, dedicated-server networking, and gameplay smoke remain
separate verification requirements.
