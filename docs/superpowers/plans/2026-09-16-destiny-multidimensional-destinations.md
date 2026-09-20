# Multidimensional Destiny Destinations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCA Destiny resolve, display, validate, search, and teleport explicit `(location, dimension)` destinations from one server-owned source of truth.

**Architecture:** `CommonConfig` remains raw policy input. `DestinyLocationResolver` becomes the only producer of immutable `DestinyDestination` values by combining configured/auto-discovered selectors with each `ServerLevel`'s `ChunkGeneratorStructureState`; `OpenDestinyGuiRequest` sends that catalog to the client; `DestinyScreen` only groups it for presentation; `DestinyMessage` returns the selected value; the server revalidates exact membership and searches/teleports in the selected `ServerLevel` through vanilla APIs.

**Tech Stack:** Java 21, Minecraft 1.21.1, MCA common/Fabric/NeoForge modules, `ResourceKey<Level>`, structure registry holders/tags, `ChunkGeneratorStructureState`, MCA payload `StreamCodec`s, NeoForge GameTest, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-16-destiny-multidimensional-destinations-design.md`

## Global Constraints

- Preserve the dirty worktree and all unrelated staged/unstaged changes. Do not reset, clean, revert, stash, commit, or push unrelated work.
- Do not commit or push any task in this plan unless the user explicitly asks later.
- Treat `C:/Users/Mik/Downloads/MCA/mc_source_code/local-source/src/main/java` as the Minecraft 1.21.1 behavior/API oracle and never modify it.
- `CommonConfig.destinySpawnLocations`, `autoDiscoverDestinyLocations`, and `destinySpawnLocationBlacklist` are configuration inputs only; never overwrite them with resolved runtime state.
- `DestinyLocationResolver` is the only production owner that derives selectable destinations.
- `DestinyLocationResolver` is also the only owner that interprets Destiny selector syntax (`namespace:structure` versus `#namespace:tag`) and dispatches the matching locate helper.
- `DestinyDestination` is the single identity type for a selectable destination. Do not add a second structure-to-dimension map or client-side discovery model.
- A dimension-bound destination uses `ResourceKey<Level>`, not a string dimension ID.
- `Optional.empty()` dimension is reserved for genuine dimensionless choices such as `somewhere`; never invent a fake global dimension.
- Preserve direct structure IDs, `#structure_tag` selectors, automatic village discovery, wildcard blacklist semantics, existing story lookup, and mod namespace tooltips.
- A structure/tag with no configured placement in a dimension must not produce a destination for that dimension.
- The same structure/tag may produce several `DestinyDestination` values when it has placements in several dimensions.
- Never silently fall back to the player's current dimension or another dimension when a selected target cannot be found.
- Use `DimensionType.getTeleportationScale(...)` for the cross-dimension search origin and `ServerPlayer.teleportTo(ServerLevel, ...)` for the actual transfer.
- Use `targetLevel` consistently for target chunk, heightmap, collision, world-border, respawn, and singleplayer default-spawn operations.
- Apply `coding-standards`: descriptive names, immutable boundaries, guard clauses, KISS, DRY, YAGNI, strong types, and small owner methods.
- Before completion, apply `java-code-review-cleanup` to one fixed Destiny-only diff across reuse, quality, correctness, and efficiency lenses, compare against local Minecraft sources, fix worthwhile findings, and rerun verification.

---

## File Map

### Create

- `common/src/main/java/net/conczin/mca/destiny/DestinyDestination.java`
  - immutable shared destination identity
  - network codec for location plus optional dimension key
- `common/src/test/java/net/conczin/mca/network/DestinyNetworkCodecTest.java`
  - round-trip the shared destination and selection message codecs
- `neoforge/src/main/java/net/conczin/mca/server/DestinyLocationResolverGameTests.java`
  - real server/generator-state placement regressions

### Modify

- `common/src/main/java/net/conczin/mca/server/DestinyLocationResolver.java`
  - keep raw selector merge/blacklist ownership
  - expand selectors into dimension-aware destinations using loaded `ServerLevel` generator states
- `common/src/test/java/net/conczin/mca/server/DestinyLocationResolverTest.java`
  - preserve pure merge/blacklist tests
  - add immutable destination identity/unit coverage that does not fake Minecraft placement state
- `common/src/main/java/net/conczin/mca/network/s2c/ConfigResponse.java`
  - remove Destiny-derived config rewriting
- `common/src/main/java/net/conczin/mca/network/c2s/ConfigRequest.java`
  - return to plain config synchronization
- `common/src/main/java/net/conczin/mca/network/s2c/OpenDestinyGuiRequest.java`
  - transport the canonical resolved destination list with the open request
- `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
  - pass the server-provided catalog to `DestinyManager`
- `common/src/main/java/net/conczin/mca/DestinyManager.java`
  - hold the immutable catalog for the currently requested Destiny screen
- `common/src/main/java/net/conczin/mca/client/gui/DestinyScreen.java`
  - render global choices once and dimension-bound choices by dimension category
  - send the selected `DestinyDestination` unchanged
- `common/src/main/java/net/conczin/mca/network/c2s/DestinyMessage.java`
  - replace string/boolean protocol meaning with optional `DestinyDestination`
  - exact server validation
  - target-dimension search origin, search, safe position, teleport, respawn/default spawn

### Expected to remain unchanged

- `common/src/main/java/net/conczin/mca/CommonConfig.java`
  - keep the staged auto-discovery and blacklist fields as raw policy inputs
- `common/src/main/java/net/conczin/mca/util/WorldUtils.java`
  - existing structure/tag search helpers already accept an explicit `ServerLevel`
- floor/room scanner files and unrelated carry, fishing, archer, FamilyTree, build-tool, and changelog work

---

### Task 1: Introduce the immutable destination identity and codec

**Files:**
- Create: `common/src/main/java/net/conczin/mca/destiny/DestinyDestination.java`
- Modify: `common/src/test/java/net/conczin/mca/server/DestinyLocationResolverTest.java`

**Interfaces:**
- Produces: `DestinyDestination(String location, Optional<ResourceKey<Level>> dimension)`
- Produces: `DestinyDestination.STREAM_CODEC`
- Consumed by Tasks 2-6: resolver output, open payload, client view, selection payload, validation

- [ ] **Step 1: Add failing identity tests for dimension-bound and dimensionless destinations**

Add tests that prove record equality distinguishes the same structure in two dimensions and preserves the dimensionless `somewhere` state:

```java
@Test
void sameLocationInDifferentDimensionsIsADifferentDestination() {
    DestinyDestination overworld = new DestinyDestination(
            "minecraft:village_plains",
            Optional.of(Level.OVERWORLD)
    );
    DestinyDestination nether = new DestinyDestination(
            "minecraft:village_plains",
            Optional.of(Level.NETHER)
    );

    assertNotEquals(overworld, nether);
}

@Test
void somewhereIsDimensionless() {
    DestinyDestination somewhere = new DestinyDestination("somewhere", Optional.empty());

    assertEquals("somewhere", somewhere.location());
    assertTrue(somewhere.dimension().isEmpty());
}
```

- [ ] **Step 2: Run the focused common test and verify RED**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.server.DestinyLocationResolverTest'
```

Expected: compilation fails because `DestinyDestination` does not exist.

- [ ] **Step 3: Create `DestinyDestination` as a shallow immutable record with one codec**

Create:

```java
package net.conczin.mca.destiny;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public record DestinyDestination(String location, Optional<ResourceKey<Level>> dimension) {
    public static final StreamCodec<FriendlyByteBuf, DestinyDestination> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), DestinyDestination::location,
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs::optional), DestinyDestination::dimension,
            DestinyDestination::new
    );

    public DestinyDestination {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Destiny location must not be blank");
        }
        Objects.requireNonNull(dimension, "dimension");
    }
}
```

The bounded string codec replaces the old handler-local `location.length() > 128` check, keeping the selector limit at the one shared protocol owner. Do not add resolver logic, display names, cached `ServerLevel`, or a second string dimension accessor to this record.

- [ ] **Step 4: Run the focused unit test**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.server.DestinyLocationResolverTest'
```

Expected: PASS.

---

### Task 2: Make `DestinyLocationResolver` the one server-wide dimension-aware catalog owner

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/DestinyLocationResolver.java`
- Modify: `common/src/test/java/net/conczin/mca/server/DestinyLocationResolverTest.java`
- Create: `neoforge/src/main/java/net/conczin/mca/server/DestinyLocationResolverGameTests.java`

**Interfaces:**
- Keeps: one production raw-selector merge stage for manual + discovered + blacklist semantics
- Produces: `public static List<DestinyDestination> resolve(MinecraftServer server, CommonConfig config)`
- Produces: `public static Optional<BlockPos> findNearest(ServerLevel level, BlockPos origin, DestinyDestination destination, int radius)`
- Produces: immutable destination list
- Consumed by Task 3: `OpenDestinyGuiRequest`
- Consumed by Task 5: `DestinyMessage` exact membership validation

- [ ] **Step 1: Preserve existing merge semantics with focused unit tests**

Keep the existing tests for:

```text
autoDiscoveryCanBeDisabledForAnExactManualList
exactBlacklistRemovesManualAndDiscoveredLocations
namespaceWildcardRemovesEveryLocationFromThatNamespace
simpleWildcardsCanMatchAnywhereInAnIdentifier
duplicateDiscoveredLocationsAreNotAddedTwice
```

Rename the pure production helper to make its responsibility explicit:

```java
static List<String> resolveLocationIds(
        Collection<String> configuredLocations,
        boolean autoDiscover,
        Collection<String> discoveredLocations,
        Collection<String> blacklistPatterns
)
```

This helper is allowed because it is the actual first stage of the production resolver, not a test-only placement model.

- [ ] **Step 2: Add a GameTest that proves vanilla village placement is dimension-specific**

Create a NeoForge GameTest that obtains the `minecraft:village_plains` holder and asserts its placement list is non-empty in Overworld and empty in Nether using the exact API the resolver will use:

```java
Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
    Holder.Reference<Structure> plainsVillage = structures.getHolderOrThrow(
        ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse("minecraft:village_plains"))
);

ServerLevel overworld = helper.getLevel().getServer().overworld();
ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);

helper.assertTrue(
        !overworld.getChunkSource().getGeneratorState().getPlacementsForStructure(plainsVillage).isEmpty(),
        "plains village should have Overworld placements"
);
helper.assertTrue(
        nether != null && nether.getChunkSource().getGeneratorState().getPlacementsForStructure(plainsVillage).isEmpty(),
        "plains village should not have Nether placements"
);
helper.succeed();
```

Register the test using the repository's existing NeoForge GameTest conventions.

- [ ] **Step 3: Run the focused resolver unit test plus GameTest compilation and verify the new GameTest fails until registered/implemented correctly**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.server.DestinyLocationResolverTest' :neoforge:compileJava
```

Expected before implementation: the unit tests remain green; the new resolver/GameTest references fail to compile where the new API is not present yet.

- [ ] **Step 4: Refactor resolver discovery into one readable pipeline with one selector parser**

Change the public resolver to accept the server:

```java
public static List<DestinyDestination> resolve(MinecraftServer server, CommonConfig config) {
    Registry<Structure> structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
    List<String> locationIds = resolveLocationIds(
            config.destinySpawnLocations,
            config.autoDiscoverDestinyLocations,
            discoverVillageLocations(structures, config.autoDiscoverDestinyLocations),
            config.destinySpawnLocationBlacklist
    );

    return resolveDestinations(server, structures, locationIds);
}
```

Add one private selector parser inside `DestinyLocationResolver` and keep its result internal to the class:

```java
private record Selector(ResourceLocation id, boolean tag) {
}

private static Optional<Selector> parseSelector(String location) {
    boolean tag = location.startsWith("#");
    String rawId = tag ? location.substring(1) : location;
    return Optional.ofNullable(ResourceLocation.tryParse(rawId))
            .map(id -> new Selector(id, tag));
}
```

`somewhere` remains the explicit dimensionless special case before this parser. Keep `discoverVillageLocations(...)`, blacklist matching, selector expansion, and placement checks as small named methods. Avoid one dense stream chain.

- [ ] **Step 5: Expand direct structures through each level's real generator state**

For direct IDs, resolve the holder once and use the level-owned placement state:

```java
private static void addStructureDestinations(
        MinecraftServer server,
        Registry<Structure> structures,
        String location,
        List<DestinyDestination> destinations
) {
    Optional<Selector> selector = parseSelector(location);
    if (selector.isEmpty() || selector.get().tag()) {
        return;
    }

    Optional<Holder.Reference<Structure>> structure = structures.getHolder(selector.get().id());
    if (structure.isEmpty()) {
        return;
    }

    sortedLevels(server).stream()
            .filter(level -> !level.getChunkSource()
                    .getGeneratorState()
                    .getPlacementsForStructure(structure.get())
                    .isEmpty())
            .map(level -> new DestinyDestination(location, Optional.of(level.dimension())))
            .forEach(destinations::add);
}
```

This uses the verified 1.21.1 `Registry.getHolder(ResourceLocation)` overload directly. Do not construct an unnecessary `ResourceKey<Structure>` or introduce an ID-to-object-to-ID round trip.

- [ ] **Step 6: Expand structure tags without flattening their visible selector**

For a `#namespace:path` selector:

```java
Optional<Selector> selector = parseSelector(location);
if (selector.isEmpty() || !selector.get().tag()) {
    return;
}

TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, selector.get().id());
Optional<HolderSet.Named<Structure>> taggedStructures = structures.getTag(tag);
if (taggedStructures.isEmpty()) {
    return;
}

for (ServerLevel level : sortedLevels(server)) {
    boolean hasPlacement = taggedStructures.get().stream().anyMatch(structure ->
            !level.getChunkSource().getGeneratorState().getPlacementsForStructure(structure).isEmpty()
    );
    if (hasPlacement) {
        destinations.add(new DestinyDestination(location, Optional.of(level.dimension())));
    }
}
```

Do not replace the tag selector with member structure IDs.

- [ ] **Step 7: Preserve `somewhere` as the only current dimensionless special choice**

Handle it explicitly before structure parsing:

```java
if ("somewhere".equals(location)) {
    destinations.add(new DestinyDestination(location, Optional.empty()));
    continue;
}
```

Unknown or unplaced structure/tag selectors produce no runtime destination.

- [ ] **Step 8: Add one resolver-owned locate dispatcher using the same selector parser**

Add:

```java
public static Optional<BlockPos> findNearest(
        ServerLevel level,
        BlockPos origin,
        DestinyDestination destination,
        int radius
) {
    if (destination.dimension().isEmpty() || !destination.dimension().get().equals(level.dimension())) {
        return Optional.empty();
    }

    Optional<Selector> selector = parseSelector(destination.location());
    if (selector.isEmpty()) {
        return Optional.empty();
    }

    Selector parsed = selector.get();
    return parsed.tag()
            ? WorldUtils.getClosestStructurePosition(
                    level,
                    origin,
                    TagKey.create(Registries.STRUCTURE, parsed.id()),
                    radius
            )
            : WorldUtils.getClosestStructurePosition(level, origin, parsed.id(), radius);
}
```

This is the only production code that interprets `#` after catalog construction. It also enforces that the supplied search level matches the canonical destination dimension. It delegates actual structure search to the existing `WorldUtils` helpers and introduces no second search algorithm.

- [ ] **Step 9: Keep output deterministic and immutable**

Implement `sortedLevels(server)` by sorting `server.getAllLevels()` on `level.dimension().location().toString()` and finish with:

```java
return List.copyOf(destinations);
```

Do not cache the map across calls; resolver execution happens at Destiny-open/selection boundaries and Minecraft already owns placement state.

- [ ] **Step 10: Add a resolver GameTest assertion against the public API**

Using a `CommonConfig` whose manual list is exactly `List.of("somewhere", "minecraft:village_plains")` and auto-discovery is disabled, assert:

```java
List<DestinyDestination> destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), config);

helper.assertTrue(
        destinations.contains(new DestinyDestination("somewhere", Optional.empty())),
        "somewhere should stay dimensionless"
);
helper.assertTrue(
        destinations.contains(new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD))),
        "plains village should resolve to Overworld"
);
helper.assertTrue(
        !destinations.contains(new DestinyDestination("minecraft:village_plains", Optional.of(Level.NETHER))),
        "plains village must not be offered in Nether"
);
helper.succeed();
```

- [ ] **Step 11: Run focused resolver verification**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.server.DestinyLocationResolverTest' :neoforge:compileJava
```

Then run the repository's NeoForge GameTest server with the new resolver batch and require the named tests to pass.

---

### Task 3: Move resolved destinations out of config synchronization and into the Destiny-open payload

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/network/s2c/ConfigResponse.java`
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/ConfigRequest.java`
- Modify: `common/src/main/java/net/conczin/mca/network/s2c/OpenDestinyGuiRequest.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- Modify: `common/src/main/java/net/conczin/mca/DestinyManager.java`
- Create: `common/src/test/java/net/conczin/mca/network/DestinyNetworkCodecTest.java`

**Interfaces:**
- Removes: `ConfigResponse(Config, List<String>)`
- Removes: unused `OpenDestinyGuiRequest.player` entity ID field
- Produces: `OpenDestinyGuiRequest(boolean allowTeleportation, List<DestinyDestination> destinations)`
- Produces: `DestinyManager.requestOpen(boolean, List<DestinyDestination>)`
- Produces: `DestinyManager.getDestinations()` returning the immutable current view
- Consumed by Task 4: `DestinyScreen`

- [ ] **Step 1: Add codec/manager tests before changing the payload**

Create `DestinyNetworkCodecTest` and round-trip `DestinyDestination.STREAM_CODEC` through a `FriendlyByteBuf` backed by `Unpooled.buffer()`. Cover exactly these two values:

```java
new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD))
new DestinyDestination("somewhere", Optional.empty())
```

Assert exact equality after decode.

- [ ] **Step 2: Run the focused codec test as a baseline**

Run:

```powershell
./gradlew :common:test --tests '*Destiny*'
```

Expected: PASS. Task 1 already introduced the shared codec; this establishes the network round-trip baseline before changing payload ownership.

- [ ] **Step 3: Restore `ConfigResponse` to raw config only**

Remove:

```java
public ConfigResponse(Config config, List<String> destinySpawnLocations)
private static String createJson(Config config, List<String> destinySpawnLocations)
```

Remove the now-unused `JsonObject` and `List` imports. Keep:

```java
public ConfigResponse(Config config) {
    this(GSON.toJson(config, CommonConfig.class));
}
```

- [ ] **Step 4: Restore `ConfigRequest` to plain config synchronization**

Its server handler becomes:

```java
@Override
public void handleServer(ServerPlayer player) {
    Network.sendToPlayer(new ConfigResponse(Config.getInstance()), player);
}
```

Remove Destiny resolver and structure-registry imports from this packet.

- [ ] **Step 5: Extend `OpenDestinyGuiRequest` with the canonical destination list**

Use the shared codec directly:

```java
public record OpenDestinyGuiRequest(
        boolean allowTeleportation,
        List<DestinyDestination> destinations
) implements HandleablePayload {
    public static final StreamCodec<FriendlyByteBuf, OpenDestinyGuiRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenDestinyGuiRequest::allowTeleportation,
            DestinyDestination.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenDestinyGuiRequest::destinations,
            OpenDestinyGuiRequest::new
    );

    public OpenDestinyGuiRequest(ServerPlayer player) {
        this(
                Config.getInstance().allowDestinyTeleportation,
                DestinyLocationResolver.resolve(player.server, Config.getInstance())
        );
    }
}
```

Local 1.21.1 source confirms `ServerPlayer.server` is public final; use it directly. The removed `player` entity ID had no reader anywhere in MCA, while `DestinyManager` already opens the screen for the local client player.

- [ ] **Step 6: Make `DestinyManager` the immutable client view holder only**

Add:

```java
private List<DestinyDestination> destinations = List.of();

public void requestOpen(boolean allowTeleportation, List<DestinyDestination> destinations) {
    this.openDestiny = true;
    this.allowTeleportation = allowTeleportation;
    this.destinations = List.copyOf(destinations);
}

public List<DestinyDestination> getDestinations() {
    return destinations;
}
```

Do not sort, filter, discover, or blacklist in `DestinyManager`.

Leave `allowClosing()` responsible only for `openDestiny = false`. The next `OpenDestinyGuiRequest` replaces the immutable destination view before another Destiny screen opens.

- [ ] **Step 7: Pass the payload list unchanged in `ClientHandlerImpl`**

Change:

```java
MCAClient.getDestinyManager().requestOpen(message.allowTeleportation(), message.destinations());
```

Keep `handleConfigResponse` responsible only for `Config.setServerConfig(message.getConfig())` and the existing player-dimension refresh.

- [ ] **Step 8: Verify config/network compilation and tests**

Run:

```powershell
./gradlew :common:test --tests '*Destiny*' :fabric:compileJava :neoforge:compileJava
```

Expected: PASS.

Search and require no remaining derived-config rewrite:

```powershell
rg -n 'ConfigResponse\(Config\.getInstance\(\),|json\.add\("destinySpawnLocations"|DestinyLocationResolver.*ConfigRequest|OpenDestinyGuiRequest::player' common/src/main/java
```

Expected: no matches.

---

### Task 4: Render dimensions as Destiny categories without creating a second destination model

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/DestinyScreen.java`

**Interfaces:**
- Consumes: `MCAClient.getDestinyManager().getDestinations()`
- Keeps: existing location name/story/mod-tooltip logic keyed by `destination.location()`
- Produces: presentation-only dimension pages derived from the canonical list
- Consumed by Task 5: selected `DestinyDestination` sent unchanged

- [ ] **Step 1: Change screen selection state from `String` to `DestinyDestination`**

Replace:

```java
private String location;
```

with:

```java
private DestinyDestination destination;
```

Change location-name helpers to accept `destination.location()` or keep them as string helpers and pass the location explicitly. Do not duplicate destination fields into `selectedLocation` plus `selectedDimension`.

- [ ] **Step 2: Read the runtime catalog only from `DestinyManager`**

Replace the current config-backed getter with:

```java
private List<DestinyDestination> getDestinyDestinations() {
    return MCAClient.getDestinyManager().getDestinations();
}
```

`DestinyScreen` must have no remaining read of `Config.getServerConfig().destinySpawnLocations`.

- [ ] **Step 3: Derive global choices and dimension groups from that list**

Keep derivation local and immutable:

```java
private List<DestinyDestination> getGlobalDestinations(List<DestinyDestination> destinations) {
    return destinations.stream()
            .filter(destination -> destination.dimension().isEmpty())
            .toList();
}

private Map<ResourceKey<Level>, List<DestinyDestination>> groupDestinationsByDimension(
        List<DestinyDestination> destinations
) {
    return destinations.stream()
            .filter(destination -> destination.dimension().isPresent())
            .collect(Collectors.groupingBy(
                    destination -> destination.dimension().orElseThrow(),
                    LinkedHashMap::new,
                    Collectors.toList()
            ));
}
```

Because resolver output is already ordered, use an insertion-order map. Do not sort structure IDs a second time in the client.

- [ ] **Step 4: Build presentation pages where one page belongs to one dimension**

Use a private screen-only record if it makes the pagination clearer:

```java
private record DestinyPage(ResourceKey<Level> dimension, List<DestinyDestination> destinations) {
    private DestinyPage {
        destinations = List.copyOf(destinations);
    }
}
```

Split each grouped destination list into chunks of `DESTINY_LOCATIONS_PER_PAGE`. This is presentation state only and must be recomputed from the canonical catalog rather than stored as a second mutable catalog.

- [ ] **Step 5: Render dimensionless choices once and dimension-bound choices under a category heading**

Keep the existing journey title. Render the global `somewhere` choice once outside the dimension category area.

For a `DestinyPage`, render a centered heading using:

```java
private Component getDimensionName(ResourceKey<Level> dimension) {
    ResourceLocation id = dimension.location();
    return Component.translatableWithFallback(
            "dimension." + id.getNamespace() + "." + id.getPath(),
            prettifyIdentifier(id.getPath())
    );
}
```

Then render only that page's destination buttons in the current three-column grid.

The previous/next controls navigate the flattened `List<DestinyPage>`. If one dimension has more than nine entries, consecutive pages retain the same category heading.

- [ ] **Step 6: Preserve existing structure labels and stories**

Button name:

```java
getLocationName(destination.location())
```

Mod tooltip:

```java
getLocationModName(destination.location())
```

Story translation map:

```java
Map<String, String> map = Config.getServerConfig().destinyLocationsToTranslationMap;
String location = destination.location();
story.add(Component.translatable(map.getOrDefault(location, map.getOrDefault("default", "missing_default"))));
story.add(Component.translatableWithFallback("destiny.story." + getPath(location), getLocationName(location).getString()));
```

Dimension must not alter story-key lookup in this scope.

- [ ] **Step 7: Remove the single-string fast path or make it destination-aware**

The current `destinyLocations.size() == 1` branch may only auto-select when the entire canonical catalog has exactly one visible choice. It must not accidentally auto-select the first destination of the first dimension group while hiding global or other dimension choices.

Use:

```java
if (destinations.size() == 1) {
    selectStory(destinations.getFirst());
    return;
}
```

- [ ] **Step 8: Compile the client code**

Run:

```powershell
./gradlew :fabric:compileJava :neoforge:compileJava
```

Expected: PASS.

- [ ] **Step 9: Perform live UI verification before calling this task complete**

Open Destiny on a server with at least Overworld and Nether available. Confirm:

```text
- Somewhere appears once.
- Overworld has its own heading.
- Nether has its own heading when the resolver supplies a Nether destination.
- No page mixes two dimension headings.
- Existing 3-column button layout and mod tooltip remain readable.
- Previous/next pagination remains usable with more than nine entries.
```

If the layout needs a small spacing adjustment, keep it inside `DestinyScreen`; do not introduce a new generic GUI framework.

---

### Task 5: Send and validate the exact destination instead of a location string

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/DestinyMessage.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/DestinyScreen.java`
- Modify: `common/src/test/java/net/conczin/mca/network/DestinyNetworkCodecTest.java`

**Interfaces:**
- Produces: `DestinyMessage(Optional<DestinyDestination> destination)`
- Produces: `DestinyMessage.select(DestinyDestination)`
- Produces: `DestinyMessage.close()`
- Consumes: `DestinyLocationResolver.resolve(server, Config.getInstance())`

- [ ] **Step 1: Add focused message factory/codec tests**

Assert:

```java
DestinyDestination village = new DestinyDestination(
        "minecraft:village_plains",
        Optional.of(Level.OVERWORLD)
);

assertEquals(Optional.of(village), DestinyMessage.select(village).destination());
assertTrue(DestinyMessage.close().destination().isEmpty());
```

Round-trip both values through `DestinyMessage.STREAM_CODEC` using the same codec-test utility from Task 3.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
./gradlew :common:test --tests '*Destiny*'
```

Expected: compilation fails because the message still has `(String location, boolean isClosing)`.

- [ ] **Step 3: Replace the boolean/empty-string protocol with optional destination semantics**

Refactor to:

```java
public record DestinyMessage(Optional<DestinyDestination> destination) implements HandleablePayload {
    public static final StreamCodec<FriendlyByteBuf, DestinyMessage> STREAM_CODEC = StreamCodec.composite(
            DestinyDestination.STREAM_CODEC.apply(ByteBufCodecs::optional), DestinyMessage::destination,
            DestinyMessage::new
    );

    public static DestinyMessage select(DestinyDestination destination) {
        return new DestinyMessage(Optional.of(destination));
    }

    public static DestinyMessage close() {
        return new DestinyMessage(Optional.empty());
    }
}
```

Add a compact constructor with `Objects.requireNonNull(destination, "destination")`; do not restore an `isClosing` field.

- [ ] **Step 4: Update screen call sites to send the shared value unchanged**

Closing paths use:

```java
Network.sendToServer(DestinyMessage.close());
```

The first story advance uses:

```java
Network.sendToServer(DestinyMessage.select(destination));
```

- [ ] **Step 5: Validate exact canonical membership with guard clauses**

Server handling begins:

```java
if (!(player instanceof ServerPlayer serverPlayer)) {
    return;
}

if (destination.isEmpty()) {
    serverPlayer.removeEffect(MobEffects.INVISIBILITY);
    serverPlayer.removeEffect(MobEffects.HEALTH_BOOST);
    return;
}

DestinyDestination selectedDestination = destination.get();
List<DestinyDestination> allowedDestinations = DestinyLocationResolver.resolve(
        serverPlayer.serverLevel().getServer(),
        Config.getInstance()
);
if (!allowedDestinations.contains(selectedDestination)) {
    notifyDestinationNotFound(serverPlayer);
    return;
}

if (!Config.getInstance().allowDestinyTeleportation || selectedDestination.dimension().isEmpty()) {
    return;
}
```

Remove the old 128-character location guard because `DestinyDestination.STREAM_CODEC` now enforces that same bound at the shared protocol owner before handler logic runs.

- [ ] **Step 6: Resolve the exact target level and fail rather than falling back**

```java
ResourceKey<Level> targetDimension = selectedDestination.dimension().orElseThrow();
ServerLevel targetLevel = serverPlayer.serverLevel().getServer().getLevel(targetDimension);
if (targetLevel == null) {
    notifyDestinationNotFound(serverPlayer);
    return;
}
```

Do not use `sp.serverLevel()` as a fallback.

- [ ] **Step 7: Run message tests and both loader compiles**

Run:

```powershell
./gradlew :common:test --tests '*Destiny*' :fabric:compileJava :neoforge:compileJava
```

Expected: PASS before proceeding to cross-dimension search/teleport behavior.

---

### Task 6: Search and teleport entirely in the selected dimension using vanilla owners

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/DestinyMessage.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/DestinyLocationResolverGameTests.java`

**Interfaces:**
- Consumes: validated `DestinyDestination`
- Consumes: target `ServerLevel`
- Keeps: `WorldUtils.getClosestStructurePosition(ServerLevel, ...)`
- Produces: target-space search origin and target-level-safe teleport path

- [ ] **Step 1: Reconfirm the vanilla cross-dimension teleport owner before editing MCA**

Run against the local 1.21.1 source:

```powershell
$serverPlayerSource = 'C:\Users\Mik\Downloads\MCA\mc_source_code\local-source\src\main\java\net\minecraft\server\level\ServerPlayer.java'
rg -n -C 12 'public boolean teleportTo\(ServerLevel' $serverPlayerSource
```

Expected: the method adds `TicketType.POST_TELEPORT`, branches on same versus different level, and delegates the cross-dimension path to `teleportTo(ServerLevel, double, double, double, float, float)` / `changeDimension(...)`.

The repository's GameTests use `helper.makeMockPlayer(...)` as `Player` and do not provide a connected `ServerPlayer` harness. Do not introduce a fake-player subsystem solely to mirror vanilla's already-verified dimension-transfer owner. Actual cross-dimension player transfer is covered by the live acceptance step below.

- [ ] **Step 2: Convert the search center with vanilla dimension scaling before dispatch**

Add a small owner method:

```java
private static BlockPos getSearchOrigin(ServerPlayer player, ServerLevel targetLevel) {
    double scale = DimensionType.getTeleportationScale(
            player.serverLevel().dimensionType(),
            targetLevel.dimensionType()
    );
    return targetLevel.getWorldBorder().clampToBounds(
            player.getX() * scale,
            player.getY(),
            player.getZ() * scale
    );
}
```

This follows vanilla `CommandSourceStack.withLevel(...)` and `NetherPortalBlock` behavior. Do not hardcode the Nether `8:1` scale.

- [ ] **Step 3: Capture destination, target level, and target-space origin before the async locate**

Use:

```java
BlockPos searchOrigin = getSearchOrigin(serverPlayer, targetLevel);

MCA.executorService.execute(() -> {
    Optional<BlockPos> result = DestinyLocationResolver.findNearest(
            targetLevel,
            searchOrigin,
            selectedDestination,
            128
    );

    result.ifPresentOrElse(
            pos -> serverPlayer.server.execute(() -> handleBlockPos(
                    serverPlayer,
                    targetLevel,
                    selectedDestination.location(),
                    pos
            )),
            () -> notifyDestinationNotFound(serverPlayer)
    );
});
```

`DestinyMessage` does not inspect `#`, strip selector prefixes, or parse `ResourceLocation`s. Do not re-resolve dimension inside the executor.

- [ ] **Step 4: Make safe-position calculation explicitly target-level-owned**

Change the helper signature to:

```java
private void handleBlockPos(
        ServerPlayer player,
        ServerLevel targetLevel,
        String location,
        BlockPos pos
)
```

Then replace every world access in that method with `targetLevel`:

```java
targetLevel.getChunkAt(pos);

if (location.equals("minecraft:ancient_city")) {
    pos = new BlockPos(pos.getX(), -50, pos.getZ());
} else {
    pos = targetLevel.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos);
}

pos = RandomPos.moveUpOutOfSolid(
        pos,
        targetLevel.getHeight(),
        candidate -> targetLevel.getBlockState(candidate).isSuffocating(targetLevel, candidate)
);
pos = ExtendedFuzzyPositions.downWhile(
        pos,
        1,
        candidate -> !targetLevel.getBlockState(candidate.below()).isCollisionShapeFullBlock(targetLevel, candidate)
);

if (!targetLevel.isInWorldBounds(pos) || !targetLevel.getWorldBorder().isWithinBounds(pos)) {
    notifyDestinationNotFound(player);
    return;
}
```

- [ ] **Step 5: Delegate ticketing and dimension change to `ServerPlayer.teleportTo(ServerLevel, ...)`**

Remove the MCA-owned manual `ChunkPos`, `TicketType.POST_TELEPORT`, and `player.connection.teleport(...)` code.

Use:

```java
player.teleportTo(
        targetLevel,
        pos.getX(),
        pos.getY(),
        pos.getZ(),
        Set.of(),
        player.getYRot(),
        player.getXRot()
);
```

Local Minecraft 1.21.1 source proves this method adds its own `POST_TELEPORT` region ticket and delegates cross-dimension transfer through `changeDimension(...)`.

- [ ] **Step 6: Preserve respawn/default-spawn behavior on the target level**

After teleport:

```java
player.setRespawnPosition(targetLevel.dimension(), pos, 0.0F, true, false);

if (player.server.isSingleplayerOwner(player.getGameProfile())) {
    targetLevel.setDefaultSpawnPos(pos, 0.0F);
}
```

Use the accessible server getter if `player.server` visibility differs in current mappings. Do not derive the dimension from `player.level()` after teleport when `targetLevel` is already the authoritative selected level.

- [ ] **Step 7: Add a forged-pair resolver regression**

Construct a destination pair that the resolver does not return, for example:

```java
new DestinyDestination("minecraft:village_plains", Optional.of(Level.NETHER))
```

In `DestinyLocationResolverGameTests`, resolve a config that contains `minecraft:village_plains` and assert the returned list contains the Overworld pair but not the forged Nether pair. `DestinyMessage` uses `List.contains(selectedDestination)` against this same resolver output, so the test proves the authorization data excludes the forged pair without adding a second validation helper.

- [ ] **Step 8: Run focused tests, GameTests, and loader compilation**

Run:

```powershell
./gradlew :common:test --tests '*Destiny*' :fabric:compileJava :neoforge:compileJava
```

Run the resolver/teleport NeoForge GameTest batches and require their named tests to pass.

- [ ] **Step 9: Live-test actual cross-dimension Destiny travel**

Verify at minimum:

```text
1. Open Destiny while standing in Overworld.
2. Select a resolver-provided Nether/custom-dimension entry.
3. Confirm locate runs against that target dimension and the player transfers there.
4. Confirm height/collision/world-border handling is correct in the target level.
5. Confirm respawn dimension matches the selected target.
6. Return to another dimension and select an Overworld entry.
7. Confirm the reverse path works and no current-dimension fallback occurs.
```

Also test a same-dimension destination to ensure the existing behavior did not regress.

---

### Task 7: Run the required Java cleanup pass against one frozen Destiny diff

**Files:**
- Review only the Destiny files changed by Tasks 1-6 plus nearby Minecraft source owners needed for comparison
- Fix only worthwhile findings inside that scope

**Interfaces:**
- No new production interface is expected from this task
- Deliverable: one-source architecture remains intact after cleanup

- [ ] **Step 1: Freeze the exact Destiny-only diff for all four review lenses**

Run:

```powershell
$destinyFiles = @(
    'common/src/main/java/net/conczin/mca/destiny/DestinyDestination.java',
    'common/src/main/java/net/conczin/mca/server/DestinyLocationResolver.java',
    'common/src/main/java/net/conczin/mca/network/s2c/ConfigResponse.java',
    'common/src/main/java/net/conczin/mca/network/c2s/ConfigRequest.java',
    'common/src/main/java/net/conczin/mca/network/s2c/OpenDestinyGuiRequest.java',
    'common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java',
    'common/src/main/java/net/conczin/mca/DestinyManager.java',
    'common/src/main/java/net/conczin/mca/client/gui/DestinyScreen.java',
    'common/src/main/java/net/conczin/mca/network/c2s/DestinyMessage.java',
    'common/src/test/java/net/conczin/mca/network/DestinyNetworkCodecTest.java',
    'common/src/test/java/net/conczin/mca/server/DestinyLocationResolverTest.java',
    'neoforge/src/main/java/net/conczin/mca/server/DestinyLocationResolverGameTests.java'
)
git diff HEAD -- $destinyFiles
```

- [ ] **Step 2: Reuse review**

Compare against these local vanilla owners:

```text
net.minecraft.server.MinecraftServer
net.minecraft.server.level.ServerChunkCache
net.minecraft.world.level.chunk.ChunkGeneratorStructureState
net.minecraft.world.level.dimension.DimensionType
net.minecraft.server.level.ServerPlayer
net.minecraft.world.level.portal.DimensionTransition
```

Require:

```text
- no custom dimension registry/cache
- no hand-written coordinate scale table
- no duplicate POST_TELEPORT ticket
- no copied cross-dimension transfer state machine
- no duplicate structure/tag search implementation where WorldUtils already owns it
- no duplicate Destiny selector parser outside DestinyLocationResolver
```

- [ ] **Step 3: Quality review**

Require:

```text
- `DestinyDestination` is the only selected destination identity
- no separate selectedLocation + selectedDimension fields
- no mutable catalog outside screen-local presentation state
- no `CommonConfig.destinySpawnLocations` runtime overwrite
- no boolean `isClosing` plus empty-string protocol convention
- no raw dimension strings where `ResourceKey<Level>` is available
- resolver methods have one clear responsibility and readable names
```

- [ ] **Step 4: Correctness review**

Require:

```text
- direct structures check holder placements per target level
- tags are valid when any member has a placement in that level
- `somewhere` is dimensionless and non-teleporting
- exact `(location, dimension)` resolver membership is checked before lookup
- target level is used for heightmap, collision, border, respawn and default spawn
- async locate captures targetLevel/searchOrigin before dispatch
- same structure in two dimensions remains two distinct destinations
- failed target lookup never falls back to another dimension
```

- [ ] **Step 5: Efficiency review**

Require:

```text
- no chunk scanning during discovery
- registry holders are resolved once per selector, not once per level when avoidable
- no resolver execution inside per-button rendering
- no repeated client sorting of already ordered structure entries
- no broad server state copies or persistent placement caches
```

- [ ] **Step 6: Fix worthwhile findings only and rerun the same focused verification**

Do not broaden into floor/room scanner, FamilyTree, fishing, archer, carry, dependency, or changelog cleanup.

Run:

```powershell
./gradlew :common:test --tests '*Destiny*' :fabric:compileJava :neoforge:compileJava
```

Then rerun the named Destiny GameTest batches.

---

### Task 8: Final verification and stale-source scan

**Files:**
- No planned production edits unless verification finds a Destiny regression

**Interfaces:**
- Deliverable: documented proof that the one-source architecture is complete

- [ ] **Step 1: Search for stale current-dimension and config-backed Destiny paths**

Run:

```powershell
rg -n 'destinySpawnLocations|DestinyLocationResolver|new DestinyMessage|sp\.serverLevel\(\)|player\.level\(\)|player\.serverLevel\(\)' common/src/main/java/net/conczin/mca/network common/src/main/java/net/conczin/mca/client/gui/DestinyScreen.java common/src/main/java/net/conczin/mca/DestinyManager.java common/src/main/java/net/conczin/mca/server/DestinyLocationResolver.java
```

Inspect every match. Expected intentional matches:

```text
- resolver reads raw config inputs
- OpenDestinyGuiRequest calls the resolver
- DestinyMessage calls the resolver for security validation
- same-dimension/current-player APIs may appear only when calculating the source side of coordinate scaling or obtaining the MinecraftServer
```

There must be no client screen read of raw `destinySpawnLocations` and no structure search using the player's current `ServerLevel` after target validation.

- [ ] **Step 2: Run the focused automated suite**

Run:

```powershell
./gradlew :common:test --tests '*Destiny*' :fabric:compileJava :neoforge:compileJava
```

Expected: all selected tests and both loader compiles pass.

- [ ] **Step 3: Run the named Destiny GameTests**

Require all resolver/teleport tests introduced by this plan to pass. Do not claim the unrelated full GameTest suite passed unless it is actually run and reports all required tests passing.

- [ ] **Step 4: Run formatting/diff checks without touching unrelated work**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors in the current diff. If unrelated pre-existing dirty work causes a failure, isolate and report the exact unrelated path rather than modifying it.

- [ ] **Step 5: Repeat the live acceptance path**

Require:

```text
- global `somewhere` choice appears once
- explicit dimension category heading is visible
- same structure in multiple dimensions is a separate choice per category
- same-dimension teleport still works
- Overworld -> Nether/custom dimension works
- Nether/custom dimension -> Overworld works
- story text and mod tooltip behavior remain correct
- invalid/removed destination fails with the existing failure message instead of falling back
```

- [ ] **Step 6: Report scope accurately**

Report:

```text
- exact Destiny files changed
- unit tests run and results
- named GameTests run and results
- Fabric/NeoForge compile results
- live client scenarios verified
- any unrelated dirty-suite failures separately
- no commit/push performed unless the user explicitly requested one after this plan was written
```
