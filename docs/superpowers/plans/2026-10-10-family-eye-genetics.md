# Family Eye Genetics Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement task-by-task in this session after user review. No subagents. Checkboxes track execution, not completed work.

**Goal:** Give MCA villagers persistent, family-inherited natural eye colours, a Diverse founder/fallback population, and stable complete heterochromia without repainting existing saves.

**Architecture:** An eye-specific owner in `common/` copies parental alleles and resolves natural appearance; the existing tracked-data pipeline persists and synchronizes one versioned compound. Cosmetic dyes and textures stay separate. Compare one and two allele pairs first, then review the calibrated model before introducing persistent genetics.

**Tech Stack:** Minecraft 1.21.1, Java 21, existing Fabric/NeoForge modules, Minecraft NBT and entity-data tracking, MCA DataFixerUpper pipeline, existing JUnit and NeoForge GameTest lanes. No new dependency.

**Spec:** [Family eye genetics design](../specs/2026-10-10-family-eye-genetics-design.md).

## Global Constraints

Execution amendment (2026-10-10): user approved production integration of the calibrated two-pair, three-state model with DIVERSE only, no mutation. Gate A model choice is settled; actual-client swatches remain an integrated verification requirement, not a new architecture approval pause. The earlier conditional wording below records the original staging decision.

- Work in the existing `C:/Users/mikol/IdeaProjects/minecraft-comes-alive-1.21.1-eye-system` checkout, branch `feature/1.21.1-eye-system`; original inspected HEAD `fccb7005a35dbd6bb320a8c88fab30a81a82d7f5`, execution baseline after the authorized dev merge `31c9179c1b4c121f4c81c3b0f7bb0688a2698fbf`.
- Follow the user-supplied canonical MCA engineering policy. Recheck Git state before execution; preserve unrelated dirty appearance-catalog/rendering/editor/test changes.
- No WSL, new worktree, subagent, commit, push or PR without direct authorization. The user authorized execution and merging local `dev/1.21.1` into the eye branch; only that merge was committed. No automatic implementation commits or publishing. Subsequent approval settled Gate A and authorized Tasks 2–7.
- Shared behavior stays in `common/`; Java source targets 21 even if the Gradle daemon uses JDK 25.
- Diverse is the selected default. The percentages are approximate founder targets, not exact quotas or human demographics. `NATURAL_INSPIRED` is deferred and is not a supported config value.
- No new purple/pink natural families, third locus, central/sectoral heterochromia, ancestry database, biome colour rules or unrelated gene redesign.
- Valid genetics survive missing parents. Generate unavailable information once on the server, then save it. Never initialize from a render pass or client preview.
- Use `apply_patch` for edits and the repository Gradle wrapper. Run Gradle checks serially with `--no-parallel --no-daemon`; use disposable runtime worlds.
- Tasks 2–7 were authorized by subsequent user approval of the numerical amendment.
- Do not release an intermediate schema/integration task by itself: migration, birth, editor, rendering and actual load/save preservation must all be complete.

## Review Focus

1. Raw historical 22-entry and nested saves: the version-3 fixer removes `GeneFace`; capture original appearance before it runs, not afterward. Tests in Task 3.
2. Ordinary editor patches echo `GeneEyeColor`: dye/reset/texture/trait changes must not rewrite hidden alleles or exit legacy appearance. Tests in Task 6.
3. Missing parents and temporary player proxies: use stable source identity, persist fallback, and never reroll on reload, config change or proxy recreation. Tests in Tasks 3–4.
4. Partial, malformed or unsupported eye data: distinguish absent fields from tracked defaults, keep valid alleles, and preserve unsupported payloads instead of treating them as founders. Tests in Tasks 2–3.
5. RNG and visual-state seams: preserve unrelated seeded genes, generate local heterochromia after traits, and invalidate/export all appearance-changing state. Tests in Tasks 4–5.

## File and ownership map

New production files, conditional on Gate A:

- `common/src/main/java/net/conczin/mca/entity/ai/EyeGenetics.java`: pure model, immutable domain carriers, bounded validation, founder/inheritance/inference, legacy/natural resolution and eye-payload serialization. Keep carriers/enums nested rather than adding a general genetics framework.
- `common/src/main/java/net/conczin/mca/datafix/fixes/FamilyEyeGeneticsFix.java`: schema-only promotion of captured legacy inputs into the new eye payload; no entity creation or random generation.

Existing production owners to change narrowly:

- `Genetics.java`: delegates eye operations; preserves legacy API and unrelated RNG draws.
- `VillagerLike.java`, `VillagerEntityMCA.java`, `ZombieVillagerEntityMCA.java`: tracked state and spawn/load/conversion integration.
- `McaDataFixers.java`: capture before the old chain, allocate the next unused version, register migration.
- `PlayerSaveData.java`, `Pregnancy.java`, `BabyItem.java`: stable player identity, birth/trait order, stored babies.
- `Config.java`: server-only founder preset and validation before config rewrite.
- `EyeTextureLayers.java`, `DynamicSkinCache.java`, `SkinExporter.java`: shared colour resolution, cache identity, export parity; inspect `FaceLayer.java` but do not rewrite its layering/catalogue work.
- `VillagerEditorSyncRequest.java`: preserve server-owned eye data across generic editor patches.

Paths above are under `common/src/main/java/net/conczin/mca/` unless fully specified. Exact test files appear in their owning tasks below. Reuse the current GameTest registration, not a new test launcher.

## Task 1 — Compare and calibrate before freezing the save model

**Files:** Create `common/src/test/java/net/conczin/mca/entity/ai/EyeGeneticsModelComparisonTest.java`; later add `docs/superpowers/specs/2026-10-10-eye-genetics-calibration.md` with measured results and amend the linked design/plan.

**Interfaces:** Test-local `ProbeModel.founder(RandomSource) -> ProbeGenotype`, `inherit(ProbeGenotype, ProbeGenotype, RandomSource) -> ProbeGenotype`, `express(ProbeGenotype) -> ProbePhenotype`. `ProbeGenotype` contains an immutable ordered allele list; `ProbePhenotype` reports ARGB, descriptive family and continuous model coordinates. These are experimental interfaces, not a production save schema.

- [x] Write `inheritanceCopiesOneAlleleFromEachParentAtEachLocus`: distinct parental allele values, fixed seeds, every child's maternal and paternal contribution belongs to the corresponding pair; repeated calls allow both contributions. Start with a failing averaging implementation to prove this test distinguishes copying from averaging.
- [x] Run `:common:test --tests 'net.conczin.mca.entity.ai.EyeGeneticsModelComparisonTest'` using the command conventions below. Record the assertion failure; a missing symbol or broken fixture is not the averaging reproduction.
- [x] Label the averaging counterexample as an inheritance-operator test, not a reproduction of the player's green-eye settlement report. Do not turn a probe's result into a claim about an uninspected world.
- [x] Implement literal Mendelian copying in the probes. Compare the existing scalar interpolation/mutation as a characterized control, one inherited pair plus stable non-inherited shade variation, and two inherited pairs. For candidate inheritance comparisons disable mutation; report the current algorithm's mutation separately.
- [x] Make expression and founder sampling explicit experimental parameters: document each candidate's exact equations, allele bounds, dominance/interactions, family classification, palette and joint sampler. Begin with a small parameter sweep; do not label arbitrary coefficients as human genetics or copy them into production.
- [x] Add `founderAndGenerationReportsAreReproducible`: identical seed produces identical counts and allele summaries. Use seeds `11, 29, 47, 83, 131, 197, 263, 347, 431, 557`; founders 100,000 per seed; randomly mating populations 10,000 at generations 0, 10, 50, 100; also finite populations 32 and 128. Report counts, shade variance, allele frequencies and drift without asserting that generations must maintain founder phenotype percentages.
- [x] Add explicit family experiments: hidden light-pigmentation contributions in visibly darker parents; equal visible parental colours with different hidden information; repeated sibling outcomes; descendants receiving a grandparent's traceable contribution. Show whether an independently inherited secondary modifier adds useful outcomes beyond the one-pair control, not merely more combinations.
- [x] Calibrate the Diverse proposal (45/20/15/10/5/5 percent brown/blue/hazel/green/grey/amber) against founder and descendant results. If inclusion is approved, evaluate Natural-inspired separately. Publish deviations and sampling uncertainty; do not force weighted colour rolls on children to make charts match.
- [x] Evaluate the numerical complete-heterochromia local expression rule on the same palette: bounded pigment/appearance offsets and no genetic contribution from the local offset. Record exact distribution/bounds in the amendment. Deterministic storage is specified, not yet implemented; persistence and visual usefulness remain pending below and in Tasks 3–5.
- [x] Review swatches through actual eye textures, including grey/blue and amber/hazel distinction at ordinary gameplay scale. Label numerical experiments separately from actual client checks. If client review is unavailable, record that gate as pending.
- [x] Run the probe tests GREEN, review their outputs, and write the calibration report with commands, seeds, results, equations and limitations. Do not invent results while drafting the report.

Numerical stage: 12 new probe tests; full common suite 592 tests with zero failures/errors/skips. [Measured calibration and proposed amendment](../specs/2026-10-10-eye-genetics-calibration.md) contains the actual results, equations, inference and limitations. Subsequent user approval settled Gate A; actual-client swatches were reviewed during integration. Task 1 is complete.

### Gate A — User review before production state

Present the report and amend the spec with: selected one/two-pair model, named expression functions/constants, allele quantization, complete joint founder sampler, compatible-genotype inference rule (including closest-supported inference for out-of-palette legacy colours), heterochromia distribution, accepted targets/tolerances, and whether the alternative preset is included.

If two pairs do not deliver an observable advantage, recommend one pair and revise the remaining contracts before proceeding. The following two-pair types are conditional, not evidence that this decision is already made. No persistent format, migration version or production palette is frozen before this gate.

## Task 2 — Implement the approved pure model and payload contract

**Files:** Create `common/src/main/java/net/conczin/mca/entity/ai/EyeGenetics.java`, `common/src/test/java/net/conczin/mca/entity/ai/EyeGeneticsTest.java`, `common/src/test/java/net/conczin/mca/entity/ai/EyeGeneticsCodecTest.java`, and `common/src/test/java/net/conczin/mca/EyePopulationConfigTest.java`; modify `common/src/main/java/net/conczin/mca/Config.java`.

**Interfaces, for an approved two-pair model:** Nested immutable `Locus(int first, int second)`, `Genotype(Locus pigmentation, Locus modifier)`, `LocalExpression(float pigmentationOffset, float appearanceOffset)`, and `PopulationPreset`. Allele/offset bounds are the exact Gate A constants, not independently chosen here. The locus is unphased: slots track the two caller contributions, not maternal imprinting; don't reorder existing combine arguments and alter non-eye genetics.

Produce these static contracts in `EyeGenetics`:

```java
PopulationPreset parsePreset(String value);
Genotype founder(PopulationPreset preset, RandomSource random);
Genotype inherit(Genotype mother, Genotype father, RandomSource random);
Genotype inferLegacy(CompoundTag legacyNatural, UUID identity);
int resolveNatural(Genotype genotype, LocalExpression local, boolean left);
CompoundTag encode(Genotype genotype, LocalExpression local, CompoundTag legacyNatural);
Genotype decodeGenotype(CompoundTag payload);
LocalExpression decodeLocalExpression(CompoundTag payload);
```

`local == null` means no stored local effect; absence is distinct from a valid zero effect. `legacyNatural` is an empty compound for new-model appearance. All NBT inputs/outputs are defensively copied; no caller owns an internal mutable reference. Codec methods reject unsupported formats and invalid required fields with contextual exceptions; supported partial repair is a separate server initialization operation in Task 3.

- [ ] Write `childAllelesAreParentalContributions` and `approvedFamilyFixturesKeepHiddenInfluences` using the exact Gate A examples, plus `darkestPhenotypeDoesNotWrapToBlue`. Write `genotypeRoundTripsAtEveryBound`, `rejectsWrongTypesAndOutOfRangeAlleles`, `unsupportedVersionIsNotMissing`, and `codecDoesNotAliasMutableTags`.
- [ ] Run the new focused tests RED against minimal stubs; do not claim a behavioral reproduction from unresolved imports. Add Minecraft bootstrap only where NBT/Minecraft test initialization needs the existing fixture pattern.
- [ ] Implement the approved functions, immutable carriers and versioned compound `EyeGenetics`; document its independent `Format` version. No generic `GeneType` additions and no mutation in the approved baseline unless Gate A explicitly accepts a separately measured rule.
- [ ] Add server-only `Config.eyePopulationPreset` defaulting to `"DIVERSE"`, parsed into `PopulationPreset`. Keep only the approved choices. Validate after Gson load and before `config.save()`; absence uses the field default, unknown/null values produce an actionable exception without overwriting the config or silently taking a different preset. Do not route that exception into the existing whole-config-reset fallback.
- [ ] Write `oldConfigDefaultsToDiverse`, `unknownOrNullPresetIsRejectedBeforeRewrite`, and `generationPresetDoesNotChangeSavedExpression`; use a disposable config fixture, never the user's live `mca.json`.
- [ ] Run all three focused test classes GREEN. Self-review the codec, server-only dependency graph and inverse-inference ambiguity: compatible hidden genotypes may differ by identity, but inference must be repeatable and preserve the separate legacy appearance.

## Task 3 — Preserve raw legacy appearance and actual persistent state

**Files:** Modify `common/src/main/java/net/conczin/mca/datafix/McaDataFixers.java`, `common/src/main/java/net/conczin/mca/entity/ai/Genetics.java`, `common/src/main/java/net/conczin/mca/entity/VillagerLike.java`, `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java`, `common/src/main/java/net/conczin/mca/entity/ZombieVillagerEntityMCA.java`, and `common/src/main/java/net/conczin/mca/server/world/data/PlayerSaveData.java`; create `common/src/main/java/net/conczin/mca/datafix/fixes/FamilyEyeGeneticsFix.java`; extend `common/src/test/java/net/conczin/mca/datafix/McaDataFixersTest.java`; create `common/src/test/java/net/conczin/mca/datafix/FamilyEyeGeneticsMigrationTest.java`.

**Interfaces:** `EyeGenetics.captureLegacyNatural(CompoundTag raw) -> CompoundTag`, `EyeGenetics.initializeSavedPayload(CompoundTag payload, UUID identity, PopulationPreset preset) -> CompoundTag`; `Genetics.getEyePayload() -> CompoundTag` returns a copy and `Genetics.setEyePayload(CompoundTag payload) -> void` replaces the tracked compound. Normalization is pure; generation/repair runs only with server identity.

- [ ] Write RED fixtures for flat and nested `MCAData`, source versions 0/1/2/3, 22-entry gendered palettes, the later gradient format, and current eye-branch saves. Assert raw input unchanged, captured original scalar/palette evidence retained before `GeneFace` removal, idempotent second upgrade, and unrelated traits/genetics/NBT preserved.
- [ ] Write `missingScalarIsNotTrackedDefault`, `manualDyesAreNotNaturalGenetics`, `partialPayloadKeepsValidAlleles`, and `unsupportedPayloadIsRetainedWithoutInitialization`. Include wrong NBT numeric/string types, missing fields, endpoint 1 and legacy heterochromia/albinism. Do not manufacture a palette for a previously discarded index.
- [ ] Include a historical pink/purple palette fixture: preserve its natural legacy colour while documenting closest-supported hidden-genotype inference; offspring are not given invented pink/purple alleles. Include conflicting flat/nested fields to pin the existing nested-payload precedence.
- [ ] Run the migration tests RED against the existing updater to demonstrate the lost-source problem.
- [ ] Before changing tracked-state integration, inspect the checkout's version-matched `SynchedEntityData`, compound serializer and `ServerEntity` dirty-update path. Pin replacement/copy semantics and server-thread ownership; don't infer them from another Minecraft version.
- [ ] Capture raw source inputs in `McaDataFixers.updatePayload` before invoking the existing fixer chain, including nested payloads. Keep original flat/nested precedence. Register `FamilyEyeGeneticsFix` at the next unused MCA version (currently 4, recheck at execution). Do not reuse the colliding historical version 2 or rewrite its meaning.
- [ ] Register one compound parameter in `Genetics.createTrackedData`, outside `GENOMES`. Its empty default is uninitialized; never mutate the shared default or a tracked compound in place. `CDataManager` continues to normalize before parameter loading; prefer owner hooks over changing generic data-manager semantics.
- [ ] After load on the logical server, initialize/repair supported state using original field presence and stable identity. Keep valid state byte-for-byte where possible. A missing source is not a known natural colour of 0.5. Inference stores genotype plus separate legacy appearance, not dyes.
- [ ] In player data, normalize and initialize with the player's UUID and mark the saved owner dirty when changed. Proxy construction only consumes this initialized snapshot; it cannot generate and discard fresh hidden DNA. Stored baby NBT retains captured source until its server-side initialization/load with saved identity.
- [ ] Keep unsupported eye compounds opaquely in the registered field across load/save. Decoder/initialization/birth/edit must diagnose and decline reinterpretation; rendering may use a recoverable legacy preview without mutating it. Do not promise universal downgrade preservation or relabel a future MCA-wide schema as current.
- [ ] Run migration/codec tests GREEN, then extend the Task 4 GameTests with actual entity load/save/reload and conversion assertions. Pure DFU tests alone do not clear this task's preservation requirement.

## Task 4 — Wire founders, birth, missing contributions and trait order

**Files:** Modify `common/src/main/java/net/conczin/mca/entity/ai/Genetics.java`, `common/src/main/java/net/conczin/mca/entity/VillagerLike.java`, `common/src/main/java/net/conczin/mca/entity/ai/Pregnancy.java`, `common/src/main/java/net/conczin/mca/item/BabyItem.java`, and `common/src/main/java/net/conczin/mca/server/world/data/PlayerSaveData.java`; create `neoforge/src/main/java/net/conczin/mca/entity/ai/EyeGeneticsGameTests.java`; register it in `neoforge/src/main/java/net/conczin/mca/gametest/McaGameTestsRegistration.java`.

**Interfaces:** `Genetics.initializeEyesIfMissing() -> void`, `Genetics.finishEyeExpression(long seed) -> void`, and the existing `combine(Genetics, Genetics)` / seeded overload delegate to the approved model. Pure helper `EyeGenetics.inheritWithMissingParent(Genotype mother, Genotype father, PopulationPreset preset, RandomSource random) -> Genotype` accepts null only for genuinely unavailable supported data; unsupported formats are not null.

- [ ] Add `founderSurvivesReloadAndPresetChange`, `oneMissingParentCopiesKnownContribution`, `bothMissingParentsGenerateOnce`, `conversionPreservesEyePayload`, and `playerProxyUsesPersistedPlayerIdentity`. Assert full saved state as well as resolved colours, not only non-null genes.
- [ ] Add `storedBabyKeepsBirthGeneticsAfterParentsDisappear` using real `BabyItem.createItem` and placement/load; remove/unload parental entities and recreate the child from stored data. Do not ask the family tree to regenerate already saved alleles.
- [ ] Add `birthGeneratesLocalExpressionAfterInheritedTraits`: cover `Pregnancy.createChild`, seeded baby items and fresh spawn. Local effect persists, trait removal suppresses it, re-enable restores it, and the child's local value is generated independently of the parent's local value. Ordinary genotype contributions still come from both parents.
- [ ] Before modifying generic gene loops, record fixed-seed expected values for every non-eye gene and texture choice in existing `combine(..., seed)`. Add `eyeInheritanceDoesNotChangeUnrelatedSeededGenes`; keep its baseline fixtures rather than updating them to hide RNG drift.
- [ ] Run focused GameTests RED through the registered selector. Use the existing small `minecraft:bastion/blocks/air` fixture pattern where suitable; use larger current fixtures for actual baby placement. Broken placement setup does not reproduce a genetics bug.
- [ ] Preserve old loop membership/order and RNG draw count: consume the old eye-slot draws without invoking the public genetic-edit adapter; discard that scalar result as an inheritance authority. Use a separate eye-domain stream derived from the supplied birth seed. Document seed mixing and preserve non-eye behavior; do not add four new `GeneType`s to the HashSet.
- [ ] Fresh spawn creates founder genotype on the server; conversion/load retains valid state. For a genuinely missing parent, sample one complete joint founder genotype and copy one allele per locus, then discard that temporary value. No phantom entity/family record or chunk forcing.
- [ ] Finish local expression after trait initialization/inheritance in all three birth/spawn paths and player defaults, before saving or exposing the completed appearance. Existing local state is never rerolled. Use approved constants and separate domain seeding; don't consume unrelated trait randomness.
- [ ] Run `:neoforge:runGameTestServer -PmcaGameTest=EyeGeneticsGameTests` GREEN and inspect actual executed methods/counts. Include Task 3 entity, zombie/cure, player and baby persistence checks in this class. Self-review preview/factory call sites for accidental client-side initialization.

## Task 5 — Resolve appearance once for rendering, export and caching

**Files:** Modify `common/src/main/java/net/conczin/mca/entity/ai/EyeGenetics.java`, `common/src/main/java/net/conczin/mca/client/resources/EyeTextureLayers.java`, `common/src/main/java/net/conczin/mca/client/render/DynamicSkinCache.java`, and `common/src/main/java/net/conczin/mca/client/resources/SkinExporter.java`; create `common/src/test/java/net/conczin/mca/entity/ai/EyeAppearanceResolutionTest.java` and `common/src/test/java/net/conczin/mca/client/resources/EyeTextureLayersTest.java`; extend the dirty existing `common/src/test/java/net/conczin/mca/client/render/DynamicSkinCacheInvalidationTest.java` without replacing its other tests.

**Interfaces:** `EyeGenetics.resolveLegacyNatural(CompoundTag legacyNatural, boolean left, boolean heterochromia, boolean albinism) -> int`; `Genetics.getNaturalEyeColor(boolean left) -> int` selects supported new or legacy mode. Existing `EyeTextureLayers.getStaticEyeColor` / `getBaseEyeColor` remain the cosmetic and rainbow entry points.

- [ ] Write `legacyGradientMatchesOldResolver` across anchors, intervals, scalar 1, shifted left-eye wrapping and albinism. Write `bakedPaletteMatchesRecoverableHistoricalInputs` against verified historical source/catalogue fixtures, documenting resource-pack/lost-index limits. New-model darkest endpoint must not wrap; legacy wrapping remains for preservation.
- [ ] Write `dyedAndRainbowEyesKeepExistingPrecedence`, `clearingDyeRestoresLegacyNaturalAppearance`, and `localExpressionDoesNotBecomeSecondGenotype`. Test disabled/enabled heterochromia and albinism for both source modes.
- [ ] Write `clientWithoutInitialSnapshotDoesNotInitializeGenetics`: use a read-only legacy/default preview until authoritative tracked data arrives, never persist that preview as genotype. Unsupported-format previews likewise cannot trigger random generation.
- [ ] Write `appearanceCacheChangesForLocalAndLegacyState` and `equalResolvedAppearanceCanReuseCache`: include new payload updates that the old scalar cache key cannot distinguish. Retain brightness, dyes, traits and texture/resource invalidation coverage; don't hash a mutable tag reference.
- [ ] Run focused JUnit tests RED; demonstrate a stale-key assertion or precedence mismatch, not an unrelated client/native-image fixture failure.
- [ ] Delegate only the natural colour calculation to the common owner. Leave marker alpha 254, fixed pixels and shadow/primary/highlight roles in `EyeTextureLayers`; preserve `EyeDefinition` tone overrides, brightness and `FaceLayer` catalogue behavior. Do not add new masks or a second phenotype owner.
- [ ] Key exported-skin caching by immutable resolved left/right natural colours or an equivalent complete immutable appearance snapshot; include all other existing visual inputs. Rendering and `SkinExporter` use the same colour/tone path. Test exported pixel parity using existing Minecraft/native-image facilities; if this cannot run in JUnit, verify in the client lane and report it there.
- [ ] Run JUnit GREEN and produce actual in-client swatches for the six families, dyed/legacy/new heterochromia, albinism, rainbow, missing/custom textures and resource reload. Inspect at ordinary gameplay size and inspect an exported skin. Screenshots/RGB-only tests do not substitute for one another.

## Task 6 — Preserve editor behavior and define the legacy scalar API

**Files:** Modify `common/src/main/java/net/conczin/mca/entity/ai/Genetics.java` and `common/src/main/java/net/conczin/mca/network/c2s/VillagerEditorSyncRequest.java`; create `common/src/test/java/net/conczin/mca/network/c2s/VillagerEditorEyeGeneticsPatchTest.java`; extend `EyeGeneticsGameTests.java`.

**Interfaces:** Keep `Genetics.EYE_COLOR` and both `getGene`/`setGene` and `getGenome(...).get()`/`set()` routes. `EyeGenetics.compatibilityScalar(CompoundTag payload) -> float` is a derived approximate read, or exact legacy scalar when available; it is not an invertible representation of four alleles. `Genetics.setNaturalEyeColorFromLegacyScalar(float scalar) -> void` is an explicit server-side genetic edit, using the approved inference rule and stable identity, exiting legacy mode. Raw tracked-field decoding is not that operation.

- [ ] Write `genericPatchCannotReplaceEyePayload`, `staleGeneEyeColorEchoDoesNotReinitializeEyes`, `dyeClearPreservesLegacyMode`, and `traitTextureAndUnrelatedGeneEditsPreserveEyeGenotype`. Exercise both the patch builder and server merge; removing only the new compound from the allowlist does not protect echoed scalar values.

  Minimum server-merge assertion, using a valid saved payload from Task 2:

  ```java
  CompoundTag patch = new CompoundTag();
  patch.putFloat("GeneEyeColor", 0.1F);
  patch.putInt("EyeColor", 0xFFFFFFFF);
  CompoundTag merged = VillagerEditorSyncRequest.mergeAllowedEditorPatch(serverData, patch);
  assertEquals(serverData.getCompound("EyeGenetics"), merged.getCompound("EyeGenetics"));
  assertEquals(serverData.getFloat("GeneEyeColor"), merged.getFloat("GeneEyeColor"));
  assertEquals(0xFFFFFFFF, merged.getInt("EyeColor"));
  ```

- [ ] Write `legacyGeneReadRoutesAgree`, `explicitScalarWriteHasOneOwner`, and `rawNbtLoadDoesNotInvokeGeneticEdit`. An explicit finite [0,1] write uses documented scalar inference, preserves dyes, and replaces the genotype/legacy mode as one operation. Reject NaN, infinity and out-of-range values rather than wrapping/clamping silently.
- [ ] Run JUnit and the actual editor GameTest RED against the current generic `Gene*` path; target the new eye-specific boundary, not a whole-editor security redesign.
- [ ] Exclude server-owned `EyeGenetics` and echoed `GeneEyeColor` from generic editor changes. Merge onto fresh server state, keeping dye/texture/trait/unrelated-gene behavior. Preserve player saved eye state too; a client-supplied legacy appearance payload is not trusted.
- [ ] After an authorized trait edit adds heterochromia to a new-model villager with no local effect, generate that effect once from stable identity and the approved rule. Preserve genotype and legacy mode; suppress/reuse a stored effect on trait removal/re-add. Add `editorTraitAdditionInitializesOnlyMissingLocalExpression` to the runtime tests, including player data writeback.
- [ ] Implement the compatibility adapter in the nested `Gene` accessors as well as the public convenience APIs. Internal randomization/inheritance uses a non-editing raw slot path to preserve old RNG consumption, never the public setter. Keep the serialized scalar only as compatibility output; it is not the authoritative source for descendants.
- [ ] Do not add a new genetics editor page or packet command in this scope. Existing cosmetics remain available; addon/server calls have the explicit validated operation. Document the approximate scalar-read limitation and the authorized write semantics.
- [ ] Run the patch tests and editor GameTests GREEN. Manually edit migrated and new villagers/players on a connected client; change dyes/texture, remove/re-add heterochromia, reconnect and verify server state/visible appearance.

## Task 7 — Verify the integrated feature and review the complete diff

**Files:** Existing build/test lanes and the two design/calibration documents; no extra production mechanism.

- [ ] Recheck all Task 1–6 checkboxes and Gate A decisions against the spec. Ensure every new tracked key is registered for both villager and zombie data managers, and player/baby data survives owner writeback. Do not ship only the pure model or DFU patch as a completed feature.
- [ ] Run the common suite, then both loader builds, then the focused runtime suite serially. Inspect exit codes and fresh reports; record skipped or unexecuted tests, not just a green task name.
- [ ] On both relevant loaders, smoke-test fresh world spawning, save/restart, migration from copied worlds, natural/item births, editor/reconnect, and resource reload. Verify multiplayer clients see server state and never create missing alleles themselves. Include dedicated-server startup because common-side ownership must not pull in `NativeImage` or client renderer classes.
- [ ] Inspect version-matched Minecraft sources for entity-data notification and serializer behavior. Confirm a replaced compound dirties tracking and reconnect receives a full authoritative snapshot; do not introduce a new custom appearance packet just to carry alleles.
- [ ] Compare pre/post images and saved fields on migration fixtures; report unavoidable lost-index/custom-pack differences explicitly. Validate an unsupported eye-format payload inside an otherwise supported MCA schema survives opaque round-trip; keep rollback unsupported and require world backups.
- [ ] Self-review the diff for duplicate state owners, mutable-tag aliasing, RNG drift, accidental phenotype averaging, noisy helpers, unrelated dirty edits, and loader duplication. Remove task-owned scaffolding only when it no longer provides evidence.
- [ ] Update documentation with measured results, actual commands and runtime limitations. Hand off without commits or publishing unless the user separately requests them.

## Commands for execution, not checks already run

Run from the eye worktree root. Replace the focused JUnit class with the exact class named in the current task; use one Gradle invocation at a time.

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.EyeGeneticsModelComparisonTest' --console=plain --no-parallel --no-daemon
.\gradlew.bat :common:test --console=plain --no-parallel --no-daemon
.\gradlew.bat :fabric:build :neoforge:build --console=plain --no-parallel --no-daemon
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=EyeGeneticsGameTests --console=plain --no-parallel --no-daemon
```

For focused runtime RED/GREEN cycles use the registered `-PmcaGameTest=EyeGeneticsGameTests#methodName` selector. Verify the class registration before running it. Use the checkout's configured loader `runClient`/`runServer` tasks for client/dedicated-server checks; stop after an observed clean startup/check and keep worlds/logs out of source changes.

## Planning review and remaining decisions

The initial plan was checked against current source ownership and the linked spec. It corrects three integration hazards: source loss before appended migrations, stale scalar editor echoes, and temporary player identity during inference. Subsequent authorized execution completed Task 1 and production integration; numerical and runtime evidence are recorded separately.

The user subsequently approved the two-pair, three-state model, exact calibrated expression, near-target Diverse sampler, inverse inference and separate local heterochromia. The uncalibrated alternative preset was deferred. Execution stayed native/no-subagents, with no automatic implementation commits or publishing.

## Integration status (2026-10-10)

Tasks 2–6 are implemented locally. The detailed checkboxes above are the original
granular acceptance checklist, not a claim that every broader runtime scenario
was performed. The implemented save/inference/API contract is in the design's
"Implemented contract" section. Task 7's selected integration checks passed;
broader release-runtime gaps remain explicitly listed below.

Final common/build command:
`./gradlew.bat :common:test :fabric:build :neoforge:build --console=plain --no-parallel --no-daemon`.
Fresh common XML reports: 615 tests, zero failures/errors/skips; both loader builds
successful. Log: `.superpowers/sdd/2026-10-10-family-eye-genetics/final-build.log`.

Final NeoForge runtime commands (same console/no-parallel/no-daemon flags):
`./gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=EyeGeneticsGameTests`
and `./gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=VillagerChildSuffocationGameTests`.
All 12 eye and all 8 existing birth/placement GameTests passed; logs are
`final-eye-runtime.log` and `final-birth-runtime.log` in the evidence directory.
This includes fresh player dirty persistence and save-tag ownership regressions,
in addition to legacy reload, parent fallback, birth ordering, stored babies,
conversion, unsupported-format retention and scalar/editor boundaries.

Self-review: no additional gene types or unrelated RNG changes, client-side
initialization, duplicate hereditary owner, loader implementation or new packet.
`git diff --check` passed. Temporary Gradle modifications were removed and the
final NeoForge jar contains neither verification harness class. HEAD remains the
authorized merge `31c9179c1b4c121f4c81c3b0f7bb0688a2698fbf`; implementation and
documentation are uncommitted, with no staged changes, push or PR. No subagents.

Actual NeoForge client evidence: all 12 server snapshots and static colours matched;
six natural families rendered with the existing layers at ordinary small-face scale;
legacy/local/dyed/albino/rainbow/missing-texture fixtures rendered; eleven static
skin exports remained pixel-identical after resource reload (rainbow excluded as
time-dependent). Packet dye edits, ignored scalar/payload echoes and heterochromia
removal/re-add preserved DNA. The disposable world saved, restarted and reconnected
with the same local-expression payload. Dedicated-server normal startup and founder
load/save also passed. Temporary harness sources/worlds/artifacts remain ignored;
their Gradle wiring was removed before final packaging.

Evidence: `.superpowers/sdd/2026-10-10-family-eye-genetics/client-server-pass.log`
and `client-artifacts/{natural-families,legacy-cosmetics}.png`. Earlier failed
pause/disconnect harness runs are superseded, not used as passing evidence.

Not performed: Fabric client/dedicated runtime, remote multiplayer, full copied
player-world migration, arbitrary addon/resource-pack compatibility, or manual
player-model UI reconnect. Historical representative palette recovery is not
pixel-identical artwork preservation; existing missing-texture fallback can select
a closed-eye style. No rollback guarantee: back up worlds before adoption.

### Later `DIVERSE` founder rebalance (2026-10-10)

Requested reduction of brown-eyed founders, while increasing blue, hazel, green,
grey and amber. Only the joint allele sampling thresholds were adjusted; natural
expression, inheritance, traits, migration and saved-eye ownership are unchanged.
The current weights and analytical expectations are in the design's founder
rebalance section. The earlier million-founder/descendant calibration figures
remain evidence for the *previous* sampler, not predictions for the rebalanced
population. Rebalanced founder frequencies have a focused reproducible test.

## Approved shade and heterochromia amendment (2026-10-14)

The user approved implementing the before/after visual for ordinary eyes and
complete heterochromia. This bounded follow-up changes `EyeGenetics` expression:
smooth blends through the existing palette, and genotype-independent positive/
negative local pigment offsets ranging from weak to strong. The exact functions
and compatibility behavior are in the design's 2026-10-14 amendment. Founder
weights, copied alleles, save shape, cosmetics and trait inheritance stay in their
existing owners. Existing new-model DNA/local values are retained but render
through the new palette; the legacy natural resolver is unchanged.

Three focused tests failed on the intended old behavior before the change:
repeated modifier swatches, abrupt colour jumps at old family cutoffs, and
heterochromia constrained to one direction/no subtle pigment differences. All
passed after implementation. The founder regression now checks actual allele
weights and production RGB shade variety, rather than duplicating historical
six-family thresholds. A reproducible sample of 100,000 Diverse founders produces
20 ordinary RGB shades before brightness and texture/lighting.

Validation:

- `:common:test :fabric:build :neoforge:build` passed; 619 common tests with no
  failures, errors or skips. Existing compiler/Javadoc warnings remain.
- `:neoforge:runGameTestServer -PmcaGameTest=EyeGeneticsGameTests` passed all 12
  required tests. Log: `.superpowers/sdd/2026-10-14-eye-shades/eye-runtime.log`.
- A disposable NeoForge client harness passed all 18 authoritative DNA/static
  colour snapshots, actual rendering, resource reload and pixel-identical static
  exports (17 fixtures; animated rainbow excluded). Actual editor packet
  dye/trait edits, world save/restart and reconnect retained DNA/local effects.
  Log: `.superpowers/sdd/2026-10-14-eye-shades/client-runtime.log`; screenshots
  and exports are in that directory's `client-artifacts`.
- The temporary harness uses an ignored Gradle init script and ignored source/
  world directories. Production build files were not changed. Normal packaging
  was rerun without the init script; no harness is included in the jar.
- Final `:common:test :neoforge:jar` passed after removing temporary harness
  sources from the build inputs. Log: `final-common-packaging.log` in the same
  evidence directory. All 619 common tests passed again.

The original implementation was committed externally during this follow-up as
`c70c0c937`; this amendment remains local and uncommitted. No commit/push was
performed by this task. Fabric runtime and remote multiplayer were not rerun;
the client evidence above is NeoForge integrated-server evidence. Historical
colour-family percentages and simulation fixtures remain historical evidence,
not population predictions for the blended palette.
