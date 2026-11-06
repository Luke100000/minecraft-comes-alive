# Server-Authoritative Appearance Catalog: Incremental Implementation and Simplification Plan

> **Status (2026-10-10):** The original incremental implementation below is already present as uncommitted work and its automated checks passed. A **proposed, not yet approved or implemented** simplification supersedes its design recommendation. Follow the new tasks below only after approval; retain the original tasks as implementation history, not steps to repeat. Use the existing inline workflow, no subagents.

## Proposed simplification: one server-derived full snapshot (not yet implemented)

**Goal:** Preserve server-defined appearance choices and multiplayer/editor consistency with one established six-map `AppearanceCatalogSync` protocol. Avoid a second per-entry synchronization mechanism unless actual packet or rendering measurements justify it.

**Evidence:** MCA's clothing/hair render layers use texture identifiers and `VillagerLike` already tracks selected IDs; eye rendering uses client catalog definitions. Patched Minecraft 1.21.1 synchronizes changing entity fields with `SynchedEntityData` but sends complete resource data on join/reload. This supports server authority without requiring the two new custom-edit payloads. The current incremental code may be preferable only if full snapshots on rare global edits prove costly; do not make that assumption in either direction.

**Retain:** `AppearanceCatalogSync.current/send/sendToAll`, both loader join/reload hooks, server-backed catalogs and client full replacement; custom-over-datapack clothing precedence; explicit hairstyle precedence; `Gender.NEUTRAL`; server permissions and ID/JSON validation; no-op change detection; login/logout reset; existing Immersive Library texture loading. The server decides valid effective definitions, the entity-data pipeline conveys a villager's chosen IDs, and the client renders from its own textures.

**Change:** For each *actual authorized* `CustomClothingManager.Storage.addEntry/removeEntry` mutation, the existing C2S handler broadcasts exactly one full `AppearanceCatalogSync` assembled **after** the change. Do not reintroduce broadcast in `Storage#setDirty`, which can be invoked for unrelated persistence work; audit all storage writers before moving the notification. Remove `AppearanceClothingUpdate` and `AppearanceHairUpdate`, their codec registration and client handlers, `ClientAppearanceCatalog.applyClothingUpdate/applyHairUpdate`, and tests made obsolete solely by those packets. Preserve targeted image-cache invalidation in `SkinCache`; evaluate full-snapshot cache churn separately, particularly generated eyes, without adding a second protocol or speculative revision state.

### Simplification acceptance checklist

- [ ] **S0: Baseline and size measurement before deleting packets.** Inspect `git status` and the dirty diff; record a representative serialized `AppearanceCatalogSync.current(server)` byte size with normal and larger datapacks and compare with the NeoForge 1.21.1 1 MiB clientbound limit. Capture any meaningful cache rebuild/latency impact on global edits. If the snapshot cannot fit supported packs, stop and reconsider bootstrap; per-edit deltas alone cannot solve it.
- [ ] **S1: Tests first.** Adjust tests to assert full snapshots are sent only after successful authorized additions, meaningful replacements and existing-key removals, not identical replacements, missing removals or rejected IDs. Cover server-derived custom-over-datapack clothing fallback, explicit-over-generated hair style behavior and `Gender.NEUTRAL`. Preserve session reset/full-replacement tests and verify a non-editor client receives current definitions. Prove relevant tests fail before changing the dispatch path where feasible.
- [ ] **S2: Narrow implementation.** Switch only the validated C2S edit handlers to `AppearanceCatalogSync.sendToAll(player.serverLevel().getServer())` after a `true` storage mutation. Retain boolean/no-op dirty semantics; remove the two incremental packets and their registrations, receive handlers and client patch methods. Keep login/reload snapshots unchanged and avoid unnecessary changes to entity tracking, render layers, resource reload listeners, and texture download handling.
- [ ] **S3: Cache and lifecycle validation.** Check whether a clothing/hair full snapshot needlessly clears generated eye textures or all stitched skins. If demonstrated problematic, add only a narrow condition in the existing full-snapshot handling path without compromising genuine eye/data/resource reload invalidation. Verify Fabric/NeoForge two-client add/change/delete, editor refresh and non-editor convergence, join/edit races, server switching, `/reload`, custom-override removal, and real texture refresh. Do not claim runtime testing from compilation alone.
- [ ] **S4: Finish.** Run focused JUnit, `:common:test`, `:fabric:build`, `:neoforge:build`, `git diff --check` and a dirty-worktree audit. Report new test counts, measured bytes, remaining multiplayer gaps and net production LOC. Preserve unrelated eye-genetics/editor work; no commits or pushes.

**Approval boundary:** This is a proposed amendment. Do not remove existing incremental code until approval and the S0 gate; if measurements justify keeping it, document that decision instead. The original plan and its verification log below are retained as historical evidence.

---

## Original incremental implementation plan (historical; do not re-execute)

**Goal:** Keep every client's server-authoritative appearance catalog current while sending only the affected server-resolved clothing/hair metadata on edits.

**Architecture:** Preserve six-map `AppearanceCatalogSync` for join/datapack reloads. Make the existing global custom-content handlers broadcast type-safe per-key authoritative updates after real mutations; apply the updated keys with copy-on-write client maps and targeted skin-cache invalidation. Follow the vanilla/MineColonies snapshot-plus-changed-entry pattern without adding subscription tracking, bundled-baseline merging, or a general diff/versioning subsystem.

**Tech Stack:** Minecraft 1.21.1; Java 21 source; existing common networking `StreamCodec`/`HandleablePayload`, Fabric + NeoForge, Gradle wrapper/JUnit, client multiplayer runtime acceptance.

**Spec:** [Server-authoritative appearance catalog design](../specs/2026-10-09-authoritative-appearance-catalog-incremental-sync-design.md).

## Global Constraints

- Work **only** in the existing `feature/1.21.1-eye-system` worktree. No WSL, new checkout/worktree, subagent, commit, push, or unrelated cleanup.
- Preserve the existing dirty editor/FaceLayer/test files; inspect current status/diff before edits and do not overwrite other work.
- Keep Fabric/NeoForge datapack full-snapshot behavior and the current six-map codec unchanged. No client-side built-in baseline merge; no changes to intentional neutral gender behavior.
- Only authorized, materially changed custom edits emit packets. The server derives final metadata after the mutation; empty Optionals mean removal of an effective key.
- Always resolve storage and broadcast against the handling player's server, not the static dummy storage. Apply the existing canonical library-ID check to both additions and removals.
- Preserve client main-thread access and dedicated-server classloading boundaries. No new locks, executors, persistent caches, client requests, debouncing, or revisions without failure evidence.
- Datapack reload and joining retain whole-catalog snapshots; login/logout clear stale pre-sync catalog state. Cache invalidation for metadata edits must not clear generated eye textures.
- Treat full snapshot packet size as a separate measured limit. Halt release and seek a new design if a supported large catalog cannot fit in the version's clientbound payload limit.
- Apply custom-over-datapack clothing precedence consistently to server clothing selection/editor pools, server ID lookup and full catalog, without rewriting unrelated spawn selection or gender policy.

## Review Focus

1. **Datapack entry reappears after removing a custom override:** verify the server's effective clothing entry is applied, not the client's bundled entry (Task 3, runtime acceptance).
2. **Custom hair ID conflicts with an explicit `HairStyle`:** verify explicit server style wins before and after add/delete (Tasks 2-3).
3. **No-op or invalid edit/removal:** verify no network notification, no dirty-state change, and shared canonical ID checks (Tasks 1, 3 and 4).
4. **Joining/disconnecting or reload while editing:** verify per-connection readiness, ordered snapshot/edit delivery and final convergence without stale values (Tasks 2 and 4).
5. **Large pack and non-editor viewers:** verify packet sizing and immediate update without an open UI (Task 4).

---

### Task 0: Make clothing precedence consistent across server consumers

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/resources/ClothingList.java`
- Modify: `common/src/main/java/net/conczin/mca/resources/SkinVisualIds.java`
- Modify: `common/src/main/java/net/conczin/mca/network/s2c/AppearanceCatalogSync.java` only if needed to use the same effective map
- Create: `common/src/test/java/net/conczin/mca/resources/ClothingOverridePrecedenceTest.java`

**Interfaces:** Resolve a fresh effective clothing map by overlaying custom IDs onto datapack IDs. A single custom override wins for matching ID in the authoritative snapshot, weighted selection, and known-clothing lookup; removing it restores the original. Keep client built-in fallback, selection filters and neutral-gender treatment.

- [ ] **Step 1: Write failing precedence tests.** Two definitions with the same ID but differing chance/gender/exclude/profession must yield exactly one effective custom definition. Without the override, the datapack version returns. Preserve unrelated and neutral entries.
- [ ] **Step 2: Observe RED.** Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.resources.ClothingOverridePrecedenceTest' --no-parallel --no-daemon --console=plain`.
- [ ] **Step 3: Implement narrow consistent precedence.** Replace `ClothingList.allClothing()` concatenation with an effective map overlay; make `SkinVisualIds` return custom metadata first on colliding ID. Reuse the same helper for the full snapshot if appropriate; do not mutate the loaded datapack map.
- [ ] **Step 4: Observe GREEN.** Rerun the focused precedence test and verify it ran, then include it in the final common suite and two-loader acceptance.

### Task 1: Make global-content changes observable, without `setDirty` broadcasts

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/CustomClothingManager.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/CustomClothingManagerStorageTest.java`

**Interfaces:**
- Produce: `Storage<T>.addEntry(String identifier, T entry): boolean` and `Storage<T>.removeEntry(String identifier): boolean`. `true` means storage actually changed and `SavedData.setDirty()` was called.
- Comparison: an existing value is unchanged iff `old.toJson().equals(newEntry.toJson())` for the same ID. Do not rely on `SkinListEntry.equals` (identity-based).

- [ ] **Step 1: Write the failing storage tests.** Assert fresh add returns true, identical-value replacement with a distinct `Clothing`/`Hair` instance returns false, changed metadata returns true, absent removal returns false, and removing an existing key returns true. Use the existing `Storage` constructor and inspect `SavedData` dirty state where the available API permits; false operations must not call `setDirty`. Cover metadata-only changes (gender/chance and clothing exclusion/profession/temperature), not just new IDs.
- [ ] **Step 2: Observe RED.** Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.CustomClothingManagerStorageTest' --no-parallel --no-daemon --console=plain` (expected failure because the mutation methods do not yet return booleans).
- [ ] **Step 3: Implement the two boolean mutation methods.** Only call inherited `setDirty()` for true mutations. Remove the `Storage#setDirty()` override and its snapshot-broadcast import. Preserve existing save/load IDs, authorization, and dummy storage behavior.
- [ ] **Step 4: Observe GREEN.** Rerun the same focused test and verify executed test count and exit code.

### Task 2: Add type-safe per-key client updates with exact snapshot semantics

**Files:**
- Create: `common/src/main/java/net/conczin/mca/network/s2c/AppearanceClothingUpdate.java`
- Create: `common/src/main/java/net/conczin/mca/network/s2c/AppearanceHairUpdate.java`
- Modify: `common/src/main/java/net/conczin/mca/network/MessagesMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandler.java`
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- Modify: `common/src/main/java/net/conczin/mca/client/resources/ClientAppearanceCatalog.java`
- Modify: `common/src/main/java/net/conczin/mca/MCAClient.java`
- Create: `common/src/test/java/net/conczin/mca/client/resources/ClientAppearanceCatalogUpdatesTest.java`
- Create: `common/src/test/java/net/conczin/mca/network/AppearanceCatalogUpdateCodecTest.java`

**Interfaces:**
- Produce `record AppearanceClothingUpdate(String identifier, Optional<Clothing> effectiveClothing) implements HandleablePayload`, clientbound `TYPE` and `STREAM_CODEC`.
- Produce `record AppearanceHairUpdate(String identifier, Optional<Hair> customHair, Optional<HairStyle> effectiveStyle) implements HandleablePayload`, clientbound `TYPE` and `STREAM_CODEC`.
- Produce `ClientAppearanceCatalog.applyClothingUpdate(String identifier, Optional<Clothing> value): boolean` and `applyHairUpdate(String identifier, Optional<Hair> hair, Optional<HairStyle> style): boolean`; `false` on invalid identifier, entry-ID mismatch or before first full snapshot; `true` after a validated, copy-on-write application. Reject an invalid hair/style pair **before** changing either map. A null/empty Optionals check belongs at decode/application boundary, not a client fallback.
- Produce readiness managed by `ClientAppearanceCatalog.installServerSnapshot(...)` and `ClientAppearanceCatalog.clear()`; call `clear()` from both `MCAClient.onLogin()` and the existing `onLogout()`. No new global state owner or separate login callback.
- Add `ClientHandler.handleAppearanceClothingUpdate(AppearanceClothingUpdate)` and `handleAppearanceHairUpdate(AppearanceHairUpdate)`. Route through the established `ClientProxy` handler pattern and the main-thread packet execution path.

- [ ] **Step 1: Write failing tests.** Install a six-map snapshot, apply clothing upsert/replace/remove and hair+derived-style upsert/replace/remove; assert unrelated maps and eyes unchanged. Assert `Gender.NEUTRAL` survives; absent values truly remove keys rather than restoring client built-ins; malformed key, mismatched IDs and pre-bootstrap updates do nothing, including for one invalid member of a hair/style pair. Verify `clear()` returns to pre-sync state, a later full snapshot restores readiness, and further edits apply. In codec tests, round-trip present/empty values for both packet types (including an explicit hair-style override). Confirm the actual on-login hook calls `clear()` during implementation/runtime verification; do not invoke network-sending `MCAClient.onLogin()` in a pure unit test.
- [ ] **Step 2: Observe RED.** Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.client.resources.ClientAppearanceCatalogUpdatesTest' --tests 'net.conczin.mca.network.AppearanceCatalogUpdateCodecTest' --no-parallel --no-daemon --console=plain` (expected missing types/methods before implementation).
- [ ] **Step 3: Implement clients and codecs.** Use `ByteBufCodecs.optional(Clothing.STREAM_CODEC)`, corresponding `Hair`/`HairStyle` codecs, and the existing `HandleablePayload` route. Keep `installServerSnapshot` a full replace and mark ready; reset readiness in `clear()` and call it at login as well as logout. Validate all packet fields before updating either affected map, and publish copies on the client game thread; an invalid hair packet must not partially update its two maps. A datapack full snapshot replaces maps without leaving the client unready.
- [ ] **Step 4: Implement receive handling.** When the application returned true, call `DynamicSkinCache.invalidateSourceTexture(ResourceLocation.tryParse(identifier))` and notify `AppearanceCatalogUpdateListener` if an appropriate screen is open. Do not call `FaceLayer.clearGeneratedEyeTextureCache()` on these edit packets. Preserve the existing full-snapshot handler's broad reset.
- [ ] **Step 5: Observe GREEN.** Rerun the focused tests and inspect results; include a test for changed-ID targeted invalidation via the existing `DynamicSkinCacheInvalidationTest` where practical.

### Task 3: Produce server-resolved updates after authorized changes

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/AddCustomClothingMessage.java`
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/RemoveCustomClothingMessage.java`
- Modify: `common/src/main/java/net/conczin/mca/network/s2c/AppearanceClothingUpdate.java`
- Modify: `common/src/main/java/net/conczin/mca/network/s2c/AppearanceHairUpdate.java`
- Test/extend: `common/src/test/java/net/conczin/mca/network/c2s/AddCustomClothingMessageTest.java`
- Create: `common/src/test/java/net/conczin/mca/network/AppearanceCatalogEffectiveEntryTest.java`

**Interfaces:**
- Produce static `AppearanceClothingUpdate.current(MinecraftServer server, String identifier): AppearanceClothingUpdate` and `AppearanceHairUpdate.current(MinecraftServer server, String identifier): AppearanceHairUpdate`. Read existing server `CustomClothingManager`, `ClothingList.clothing`, `HairStyleList.styles`, `HairStyle.fromHair` only; no `BuiltInAppearanceCatalog` and no need to call `AppearanceCatalogSync.current` for every edit.
- Keep entry selection independently testable with package-private `AppearanceClothingUpdate.effective(Clothing custom, Clothing datapack): Optional<Clothing>` and `AppearanceHairUpdate.effectiveStyle(Hair custom, HairStyle explicit): Optional<HairStyle>`; `current(server, identifier)` delegates to these functions. The latter gives `explicit` precedence, then derives `HairStyle.fromHair(custom)`, then yields empty.
- Produce static `broadcast(MinecraftServer server, String identifier): void` on each update record: compute once **after** the mutation and send the same immutable metadata packet to every connected `ServerPlayer` through `Network.sendToPlayer`.
- Source the `MinecraftServer` from `player.serverLevel().getServer()` in **both** handlers, then use `CustomClothingManager.getHair(server)` or `getClothing(server)` and the same server for broadcasting. Use the existing `AddCustomClothingMessage.isValidLibraryId(identifier)` for removal input before storage lookup; do not add a duplicate validator.

- [ ] **Step 1: Add failing server-effective-resolution tests.** Exercise the `effective` / `effectiveStyle` selectors with direct metadata values: clothing custom override wins over same-ID datapack value, missing custom restores datapack or yields empty; explicit hair style wins over generated style, missing custom restores explicit style or yields empty. Assert neutral metadata is preserved. Extend the existing canonical ID test with valid/invalid deletion examples for the same shared predicate, including an out-of-range integer. This needs no parallel catalog or mock `MinecraftServer`.
- [ ] **Step 2: Observe RED.** Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.network.AppearanceCatalogEffectiveEntryTest' --no-parallel --no-daemon --console=plain` and inspect the actual failure.
- [ ] **Step 3: Implement server resolution + broadcasting.** Prefer narrow static value-resolution methods on the two packets; derive explicit-style precedence exactly like `HairStyleList.getAllStyles`. Broadcast the changed identifier only. In add/remove handlers, preserve permissions and size limits, validate both mutation IDs with the existing canonical predicate, resolve storage from the **player's** server, and send only when its mutation returned `true`. Do not send in `Storage#setDirty` or echo untrusted request JSON.
- [ ] **Step 4: Observe GREEN and inspect diff.** Run focused tests from Tasks 1-3 plus the existing `net.conczin.mca.network.c2s.AddCustomClothingMessageTest`; verify both insertion/replacement and deletion/fallback paths. Confirm neither handler touches storage for an invalid ID. Confirm no other direct storage writers by searching for direct storage mutations and accesses in common sources.

### Task 4: Validate multiplayer behavior, loader parity, and payload limits

**Files:**
- Modify tests from Tasks 1-3 only if required by actual regressions; no new production abstraction without a reproduced defect.
- Keep existing `AppearanceCatalogSync`, Fabric and NeoForge datapack hooks, and `GeneratedEyeTextureReloadListener` unchanged unless tests prove an owner-level error.

- [ ] **Step 1: Run relevant common unit tests.** `.\gradlew.bat :common:test --no-parallel --no-daemon --console=plain`; record executed tests and failures.
- [ ] **Step 2: Build both loader artifacts.** `.\gradlew.bat :fabric:build :neoforge:build --no-parallel --no-daemon --console=plain`; a green build does not prove runtime network or rendering behavior.
- [ ] **Step 3: Exercise Fabric and NeoForge clients (with a disposable test world/server).** Two clients, one in editor and one out: global clothing/hair add, replace, no-op replace, delete; invalid and absent deletion must not change state or emit an update; active edit screen refreshes; both clients receive final metadata; actual library image load still invalidates stitched textures. Join while an edit occurs, disconnect/rejoin or change servers, and interleave `/reload` with edits after adding/removing/replacing datapack entries in clothing/hair and eyes. Check packet order and that a later full snapshot never permanently overwrites a newer edit. Ensure no old-client-baseline ghost entries and generated eyes are only flushed by legitimate full sync/reload paths. Record exact tested modes; do not claim this from unit tests.
- [ ] **Step 4: Check packet budget.** In a disposable server, encode `AppearanceCatalogSync.current(server)` with its `STREAM_CODEC` into a temporary `FriendlyByteBuf` and record `writerIndex()` (release the backing buffer); likewise encode the two edit packets. Test ordinary and representative larger datapacks against NeoForge 1.21.1's 1 MiB clientbound payload ceiling; if an initial snapshot exceeds it, report a blocker and stop for a separately designed split/bootstrap change. Remove temporary instrumentation; avoid speculative chunking for this task.
- [ ] **Step 5: Final diff audit.** Run `git diff --check`, `git status --short`, and `git diff --stat`; also inspect untracked/new files explicitly because regular `git diff` excludes them. Separately inspect the previously dirty files to ensure none were overwritten. State tests actually executed, runtime gaps, and absence of commits/pushes.

## Execution status — 2026-10-10

The user explicitly authorized implementation on the eye-system branch, including Task 0's clothing precedence fix. Production code and regression tests for Tasks 0–3 are in the worktree; the full six-map snapshot and loader datapack hooks remain. The implementation is **uncommitted** and pre-existing dirty editor/renderer changes were left untouched.

- **Automated verification passed:** `.\gradlew.bat :common:test :fabric:build :neoforge:build --no-parallel --no-daemon --console=plain`. The final run reported **576 common JUnit tests, 0 failures/errors**, and both loader builds successful. Earlier failing compile-test runs captured missing interfaces before implementation, including an extra RED→GREEN check for the known library clothing gender fallback.
- **Not yet runtime-verified:** Task 4 Step 3 requires two-client Fabric/NeoForge join/edit/reload/disconnect checks, which a green JUnit or Gradle build cannot replace.
- **Not yet measured:** Task 4 Step 4 requires full snapshot and edit-packet serialized sizes against the 1 MiB clientbound budget with representative larger datapacks.
- The task checkboxes above are the original executable checklist; this section records observed completion and remaining acceptance gates without presenting those unrun runtime steps as passed. No commits, pushes, or subagents.
