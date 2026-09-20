# Multidimensional Destiny Destinations Design

## Goal

Make Destiny dimension-aware without creating parallel location state.

The server must resolve one canonical immutable list of Destiny choices from the configured locations, automatic discovery rules, blacklist, structure registry, and each loaded dimension's structure placement state. The same resolved destination value must drive the client UI, the client's selection payload, server validation, structure search, and final teleport target.

When the same structure can generate in more than one dimension, Destiny shows it once in each applicable dimension category. The player explicitly chooses the dimension instead of MCA silently choosing one.

Example presentation:

```text
Overworld
  Plains Village
  Desert Village
  CTOV Large Village

Nether
  Nether Village

Some Mod Dimension
  Village
```

Dimensionless story choices such as `somewhere` remain global choices and are displayed once outside the dimension groups. They must not be assigned a fake dimension.

## Scope

This design covers:

- the existing `destinySpawnLocations` manual configuration;
- `autoDiscoverDestinyLocations`;
- `destinySpawnLocationBlacklist` including `*` wildcards;
- direct structure IDs;
- configured structure tags using the existing `#namespace:path` syntax;
- automatic village discovery;
- dimension-aware structure availability;
- synchronization of the resolved destination catalog when Destiny opens;
- dimension-grouped Destiny UI;
- exact server-side validation of the selected location/dimension pair;
- target-dimension structure search;
- target-dimension safe-position calculation and teleport;
- existing story text and translation-map behavior.

This design does not add dimension allowlists/blacklists, per-dimension config syntax, a search box, structure previews, distance estimates, cross-server travel, or a new persistent Destiny session system.

## Current baseline

The current staged Destiny work has one useful central owner already: `DestinyLocationResolver`. It combines manually configured locations with automatically discovered village structures and applies the blacklist.

The current runtime flow still has two problems.

First, `ConfigRequest` resolves a `List<String>` and `ConfigResponse` rewrites the serialized `destinySpawnLocations` field with that derived list. The same config field therefore means two different things:

- on the server it is administrator input;
- on the client it is a derived runtime catalog.

That makes `CommonConfig.destinySpawnLocations` a second source of truth for resolved runtime data.

Second, `DestinyMessage` validates only a location string and then searches with `sp.serverLevel()`. A structure may be discoverable from the global structure registry while having placements only in another dimension. Such an entry can appear in Destiny and then fail because the search is forced into the player's current dimension.

The current safe-position and teleport path also uses `player.level()` / `player.serverLevel()` throughout, so changing only the structure search level would still calculate height, collision, world-border checks, tickets, respawn dimension, and default spawn against the wrong world.

## Minecraft 1.21.1 source authority

The implementation must use the local 1.21.1 Minecraft sources under:

`C:/Users/Mik/Downloads/MCA/mc_source_code/local-source/src/main/java`

Relevant vanilla APIs verified in those sources:

- `MinecraftServer.getAllLevels()` exposes the loaded `ServerLevel` instances.
- `MinecraftServer.createLevels(...)` iterates every registered `LEVEL_STEM` and inserts a `ServerLevel` into that same `levels` map, so `getAllLevels()` is the runtime authority for vanilla and modded dimensions rather than merely dimensions with active players or chunks.
- `MinecraftServer.getLevel(ResourceKey<Level>)` resolves an explicit dimension key.
- `ServerLevel.getChunkSource().getGeneratorState()` returns that dimension's `ChunkGeneratorStructureState`.
- `ChunkGeneratorStructureState.getPlacementsForStructure(Holder<Structure>)` returns the configured placements for a structure in that dimension.
- `ChunkGenerator.findNearestMapStructure(...)` searches using the supplied `ServerLevel` and that level's generator state.
- `DimensionType.getTeleportationScale(source.dimensionType(), target.dimensionType())` is the vanilla coordinate-space conversion used when moving a command source or portal target between dimensions.
- `ServerPlayer.teleportTo(ServerLevel, double, double, double, Set<RelativeMovement>, float, float)` owns the same-dimension/cross-dimension branch and adds the `POST_TELEPORT` region ticket itself.

These APIs are the design oracle. MCA should delegate to them instead of maintaining a separate dimension-placement map, custom coordinate-scale table, or custom dimension-change state machine.

## Architectural choice

### Canonical destination value

Introduce one immutable common value type:

```java
public record DestinyDestination(
        String location,
        Optional<ResourceKey<Level>> dimension
) {
}
```

`location` preserves the existing Destiny selector syntax:

- `somewhere` for the existing dimensionless story choice;
- `namespace:structure` for a direct structure;
- `#namespace:tag` for a structure tag.

`dimension` has one precise meaning:

- `Optional.empty()` means this choice does not perform a structure teleport and is not owned by any dimension;
- `Optional.of(key)` means the choice is a real teleport destination and must be searched and teleported in exactly that `ServerLevel`.

Using `ResourceKey<Level>` prevents dimension IDs from becoming ad-hoc strings. `Optional` is justified here because absence is a real domain state, already represented by the `somewhere` choice, rather than an error or unknown value.

The record owns its network codec so every payload serializes the same type:

```java
public static final StreamCodec<FriendlyByteBuf, DestinyDestination> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(128), DestinyDestination::location,
        ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs::optional), DestinyDestination::dimension,
        DestinyDestination::new
);
```

The 128-character selector bound preserves the existing `DestinyMessage` input limit at the shared protocol owner instead of checking it later in one packet handler. The constructor should defensively reject blank locations and normalize the `Optional` reference itself to a non-null value. It must not parse or duplicate resolver policy.

### One resolver owns all derived destination state

`DestinyLocationResolver` remains the only component allowed to turn configuration and Minecraft world state into resolved destinations.

Its main server API becomes conceptually:

```java
public static List<DestinyDestination> resolve(MinecraftServer server, CommonConfig config)
```

The returned list is immutable.

The resolver performs one pipeline:

1. Start with configured `destinySpawnLocations` in administrator order.
2. If `autoDiscoverDestinyLocations` is enabled, add structures in `#minecraft:village` and structure IDs whose path contains `village`.
3. Deduplicate selectors while preserving manual-first ordering and deterministic ordering for discovered additions.
4. Apply `destinySpawnLocationBlacklist` to the selector string.
5. Keep the existing special `somewhere` choice once as `DestinyDestination("somewhere", Optional.empty())`.
6. Resolve each direct structure or tag against the structure registry.
7. For every loaded `ServerLevel`, test whether the selector has at least one configured structure placement in that level.
8. Emit one `DestinyDestination(selector, Optional.of(level.dimension()))` for every valid selector/dimension pair.
9. Omit unknown structure IDs, empty tags, and selector/dimension pairs that have no configured placement, because `findNearestMapStructure(...)` cannot find them in that level either.

No caller builds or patches a second destination list.

The resolver also owns interpretation of the existing selector syntax. Use one small internal parser for `namespace:structure` versus `#namespace:tag`, and reuse that parser both while determining valid dimensions and when dispatching the eventual structure search. `DestinyMessage` must not independently inspect `#`, strip prefixes, or parse resource IDs. This keeps selector meaning in the same owner as destination resolution without adding another public domain type.

### Direct structures

For a direct structure ID, parse with `ResourceLocation.tryParse(...)` and resolve its `Holder<Structure>` once through the verified 1.21.1 `Registry.getHolder(ResourceLocation)` API. Do not construct a second `ResourceKey<Structure>` or perform an ID-to-object-to-ID round trip. For each `ServerLevel`, include the dimension only when:

```java
!level.getChunkSource()
        .getGeneratorState()
        .getPlacementsForStructure(structureHolder)
        .isEmpty()
```

The resolver does not scan chunks and does not run `/locate` during discovery.

### Structure tags

The current teleport code already accepts `#namespace:path`. Preserve that capability.

For a tag, parse the substring after `#` with `ResourceLocation.tryParse(...)`, then obtain the registry's holder set. Malformed configured selectors are omitted rather than throwing while Destiny opens. A dimension is valid when at least one structure holder in the tag has a configured placement in that dimension.

The resolver should not expand a tag into separate visible structure IDs. The user's configured selector and story/translation lookup remain the tag string; only its valid dimensions are expanded.

### Ordering

Configuration order remains authoritative for manual selectors. Automatically discovered selectors are sorted before they are appended, matching the current staged behavior.

For a selector that resolves in several dimensions, emit dimensions in stable `ResourceLocation` order. UI code groups by dimension while retaining the resolver's location order inside each group.

Ordering is presentation policy only. Equality of `DestinyDestination` is the security/identity check.

## Network and client ownership

### Stop rewriting `CommonConfig`

`ConfigResponse` returns to serializing only `CommonConfig`. Remove the Destiny-specific constructor and JSON mutation that replaces `destinySpawnLocations`.

`ConfigRequest` no longer resolves Destiny locations.

This restores one meaning for `CommonConfig.destinySpawnLocations`: administrator configuration input.

### Send the catalog with the Destiny-open request

`OpenDestinyGuiRequest` is the natural server-to-client boundary because every Destiny screen already opens through `ServerInteractionManager.launchDestiny(ServerPlayer)`.

Extend the payload to carry:

```java
boolean allowTeleportation,
List<DestinyDestination> destinations
```

The existing `player` entity ID field is removed because the client handler never reads it; `DestinyManager` opens the screen for the local client player. Keeping that unused field would duplicate identity data without affecting behavior.

Its `ServerPlayer` constructor calls `DestinyLocationResolver.resolve(player.server, Config.getInstance())` exactly once and sends that immutable result.

The client handler passes the list to `DestinyManager.requestOpen(...)`. `DestinyManager` stores `List.copyOf(destinations)` only as the current server-provided view model for the screen. It never derives, edits, blacklists, or rediscover locations.

That client copy is synchronized view data, not an independent source of truth.

`DestinyScreen` reads destinations from `MCAClient.getDestinyManager()`. It no longer reads `Config.getServerConfig().destinySpawnLocations` for runtime destination choices.

## UI design

### Dimension is a category

Dimension is presented as the category/group, not appended to every button label.

The screen derives presentation groups from the server-provided `List<DestinyDestination>`:

- dimensionless choices are displayed once as global choices;
- dimension-bound choices are grouped by `ResourceKey<Level>`;
- a page never mixes two dimension categories;
- groups with more than the existing nine-button capacity are split into additional pages for the same dimension;
- existing previous/next controls navigate the derived pages.

The category heading uses the dimension translation key when available:

```text
dimension.<namespace>.<path>
```

and falls back to a prettified dimension path for modded dimensions without a translation.

Structure button naming remains unchanged:

- `gui.destiny.<path>` translation first;
- prettified structure path fallback;
- non-Minecraft namespace shown with the existing mod-name tooltip.

If the exact same structure generates in Overworld and Nether, both groups contain the same structure button. Selecting either sends a distinct `DestinyDestination` because the dimension key differs.

### Global choices

`somewhere` remains visible once outside dimension groups. It retains the existing story behavior and never triggers structure search or teleport.

Do not create a synthetic `mca:global` dimension and do not duplicate `somewhere` into every dimension.

## Selection and server validation

Replace the location-only client selection with the shared destination value.

`DestinyMessage` should carry an optional destination:

```java
public record DestinyMessage(Optional<DestinyDestination> destination) implements HandleablePayload
```

Semantics:

- `Optional.empty()` means the Destiny flow is closing and performs the existing effect cleanup;
- `Optional.of(destination)` means the player selected exactly that destination.

Provide named factories such as `DestinyMessage.close()` and `DestinyMessage.select(destination)` so call sites do not construct protocol meaning from booleans or empty strings.

On selection, the server re-runs the canonical resolver against current server state and requires exact `DestinyDestination.equals(...)` membership. The client never authorizes its own location or dimension.

This keeps the current server-side whitelist protection while upgrading it from `location` membership to exact `(location, dimension)` membership.

For a dimensionless validated choice such as `somewhere`, the server performs no teleport and returns after the normal Destiny story handling.

## Target-dimension search

For a validated dimension-bound selection:

1. Resolve the target `ServerLevel` with `server.getLevel(destination.dimension().orElseThrow())`.
2. If the level is unavailable, report the existing `destiny.teleport.failed` message and stop.
3. Convert the player's current coordinates into the target dimension's coordinate space with `DimensionType.getTeleportationScale(...)`.
4. Clamp the converted search origin to the target world's border using the vanilla world-border API.
5. Ask `DestinyLocationResolver.findNearest(targetLevel, searchOrigin, destination, 128)` to verify that `targetLevel.dimension()` equals the destination dimension, interpret the selector, and delegate to the existing `WorldUtils.getClosestStructurePosition(...)` overload for a direct structure or tag.

The search center therefore preserves the current "nearest to where the player is" behavior while using Minecraft's own coordinate scaling between dimensions. Same-dimension searches naturally use scale `1.0`.

The search remains on MCA's existing executor. Capture the selected destination, target level, and target-space search origin before dispatch so the asynchronous work does not re-derive them from a player who may have moved dimensions meanwhile.

## Safe position and teleport

The block-position handling method must receive the selected `ServerLevel` explicitly and use it consistently.

All of these operations use `targetLevel`, not `player.level()`:

- chunk access;
- ancient-city Y adjustment context;
- heightmap lookup;
- suffocation checks;
- downward solid-ground search;
- world-height validation;
- world-border validation;
- respawn dimension;
- singleplayer default-spawn level.

Teleport through the vanilla owner:

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

Do not keep MCA's manual `POST_TELEPORT` ticket for this path. `ServerPlayer.teleportTo(ServerLevel, ...)` already adds the ticket and handles same-dimension versus cross-dimension transfer.

After a successful teleport, preserve the current MCA behavior using the target dimension:

```java
player.setRespawnPosition(targetLevel.dimension(), pos, 0.0F, true, false);
```

For the singleplayer owner, update default spawn on `targetLevel` just as the existing code updates the current level.

## Story and translation behavior

Story lookup remains keyed by `destination.location()` only. Dimension is travel identity, not story identity.

That means one village structure used in several dimensions shares its existing `destiny.story.<path>` text and `destinyLocationsToTranslationMap` entry unless a future feature explicitly introduces dimension-specific stories.

This is intentional YAGNI behavior.

## Error handling

Use guard clauses and the existing `destiny.teleport.failed` message.

Fail cleanly when:

- the client sends a destination that is not in the current resolver output;
- the destination dimension is no longer available;
- a structure/tag cannot be located within the existing radius;
- the final position is outside world bounds or the target world border.

Do not silently fall back to the player's current dimension or another valid dimension. The player selected a deterministic destination pair, so failure must not change that meaning.

Do not catch broad exceptions around malformed client data. The shared codec supplies typed dimension keys; resolver membership is the authorization boundary.

## Single-source-of-truth rules

The implementation must preserve these ownership rules:

1. `CommonConfig` owns raw administrator policy only.
2. `DestinyLocationResolver` alone turns that policy plus Minecraft runtime state into destinations.
3. `DestinyDestination` is the one identity type for a selectable destination.
4. `OpenDestinyGuiRequest` transports resolver output; it does not transform it.
5. `DestinyManager` stores the current server-supplied immutable view; it does not derive one.
6. `DestinyScreen` derives only presentation grouping/pagination from that view.
7. `DestinyMessage` sends the selected `DestinyDestination` unchanged.
8. The server validates exact resolver membership before acting.
9. Search and teleport use the dimension contained in the validated destination.
10. `DestinyLocationResolver` is the only owner of Destiny selector syntax (`structure` versus `#tag`); `DestinyMessage` delegates locate dispatch instead of re-parsing it.

No second map of `structure -> dimensions`, cached placement registry, client discovery pass, config-field rewrite, inferred dimension lookup, or duplicate selector parser is allowed.

## Coding standards

Apply the requested `coding-standards` rules throughout:

- prefer descriptive names such as `targetLevel`, `searchOrigin`, `destination`, and `resolvedDestinations`;
- use immutable records and `List.copyOf(...)` at ownership boundaries;
- use `ResourceKey<Level>` rather than dimension strings;
- keep resolver stages small and named instead of building one dense stream pipeline;
- use guard clauses in `DestinyMessage` instead of nested validation;
- reuse Minecraft registry, holder, generator-state, coordinate-scale, world-border, and teleport APIs;
- do not add speculative caches, compatibility fallbacks, or configurable dimension rules;
- comments explain why a vanilla API or special case is necessary, not what obvious code does.

## Verification requirements

### Resolver unit tests

Keep the current tests for manual order, auto-discovery toggle, exact blacklist, wildcard blacklist, and deduplication. Refactor them around the resolver's raw-selector merge helper only if that helper remains a real production stage.

Add coverage that proves:

- `somewhere` produces one dimensionless destination;
- a direct structure emits only dimensions where its holder has placements;
- the same structure can emit more than one distinct destination when valid in more than one dimension;
- a tag is valid in a dimension when any member structure has placements there;
- invalid/unplaced selectors are omitted;
- exact `(location, dimension)` equality distinguishes duplicate structure IDs across dimensions.

Use real Minecraft server/generator state in GameTests for placement behavior rather than maintaining a test-only parallel placement model.

### Network/model tests

Verify `DestinyDestination.STREAM_CODEC` round-trips both:

- a dimension-bound destination;
- `somewhere` with `Optional.empty()`.

Verify `DestinyMessage.close()` and `DestinyMessage.select(...)` produce distinct codec values.

### Runtime/GameTest coverage

Add server tests where feasible for:

- current-dimension destination selection still working;
- forged location/dimension pairs being rejected;
- the target `ServerLevel` being used by safe-position logic;
- successful cross-dimension transfer through `ServerPlayer.teleportTo(ServerLevel, ...)` without an MCA-owned duplicate ticket path.

### Manual client verification

Client UI behavior requires live verification because common unit tests and GameTests cannot prove screen layout.

Verify with at least two available dimensions and, if possible, one structure mod/custom dimension:

- each dimension has a visible category heading;
- a structure that is valid in two dimensions appears in both categories;
- `somewhere` appears once outside dimension groups;
- categories with more than nine structures paginate without mixing dimensions;
- selecting an Overworld entry while standing in Nether searches/teleports in Overworld;
- selecting a Nether/custom-dimension entry while standing in Overworld searches/teleports in that chosen level;
- mod namespace tooltips and existing story translations remain intact.

## Java cleanup gate

Before calling the implementation complete, apply the requested `java-code-review-cleanup` process to one fixed Destiny-only diff.

The review must cover all four lenses:

- **Reuse:** verify Minecraft's registry holders, generator state, coordinate scaling, world border, `ServerPlayer.teleportTo`, existing `WorldUtils` search helpers, and existing network codecs are reused instead of copied.
- **Quality:** remove duplicated location/dimension derivation, boolean protocol flags that can be replaced by named message factories, mutable destination lists, stringly typed dimensions, and unnecessary wrappers.
- **Correctness:** check exact server validation, tag handling, optional-dimension semantics, target-level use in every safe-position operation, async captured state, respawn dimension, and cross-dimension teleport ownership.
- **Efficiency:** ensure structure holders and level placement checks are done only during resolver execution, no chunk scans are added, no repeated resolver call is made merely to render one screen, and no avoidable intermediate catalog copies are introduced.

If the review finds another source of destination truth, collapse it into the resolver/domain model before final verification.
