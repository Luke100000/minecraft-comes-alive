# Family eye genetics and Diverse founder populations

Date: 2026-10-10

Status: The user approved production integration of the calibrated two-pair,
three-state model, DIVERSE only and no mutation. Production Tasks 2–6 are
implemented locally; the integration checks listed in the plan passed. Historical staging
descriptions below describe the earlier design stage, not a remaining approval gate.
See [measured calibration and proposed amendment](2026-10-10-eye-genetics-calibration.md)
for numerical evidence. The implementation contract is documented below.

## Implemented contract (2026-10-10)

- `EyeGenetics` owns two pairs of integer alleles in [0,2], stored as
  `PigmentA`, `PigmentB`, `ModifierA`, `ModifierB` in a compound with integer
  `Format=1`. MCA migration version is independently `MCADataVersion=4`.
- The inherited coordinates follow the calibrated functions in the linked
  report; the palette and local-expression sampler now follow the approved
  2026-10-14 amendment below. The founder sampler was previously rebalanced.
  Only `eyePopulationPreset="DIVERSE"` ships.
  This is a gameplay distribution, not human demographic data or offspring quotas.
- Inheritance copies one allele from each available parent at each locus. Only a
  missing contribution is synthesized from the joint founder sampler. A saved
  child's genotype remains authoritative after parents disappear.
- Identity seeding is UUID MSB XOR rotated-left UUID LSB (21 bits) XOR domain salt.
  Founder salt is `0x455945464F554E44`, inference `0x455945494E464552`,
  inheritance `0x455945494E484552`, local expression `0x4559454C4F43414C`.
  Seeded births use their supplied seed with the inheritance/local domain salt;
  unseeded births use the child's stable UUID. Eye draws do not consume the
  generic scalar genes' random stream or change its registered gene set.
- Local expression uses the approved bounded pigment/appearance offsets and is
  stored separately under `Local`. It is generated after traits, reused across
  trait removal/re-add and never inherited. It can produce a subtle same-family
  difference; it does not promise different named families for every villager.
- Legacy natural appearance is retained separately under `LegacyNatural`.
  Gradient saves retain the original scalar, endpoint wrapping and +0.43 legacy
  heterochromia. New children inherit inferred alleles, not those visible colours.
  Dye colours, albinism and rainbow effects are not inferred as genetic alleles.
- Recoverable raw 22-entry saves use representative colours sampled from the
  bundled textures at `f756d7bd20fb26ebbf371f93f9815349e6b1467a`: brightest
  chromatic iris pixel (chroma >25) at x=10/13, y=11..15, or brightest grayscale
  pixel if no chromatic sample exists. Male/female and heterochromatic variants
  are preserved separately. This is not pixel-identical historical shading,
  normal-eye asymmetry or custom resource-pack preservation. An index lost in a
  prior migration cannot be reconstructed; the current saved gradient is retained.
- Inverse inference searches 36 canonical unphased genotypes by squared RGB
  distance to the undyed, normal unaffected-eye colour; UUID-seeded ties follow
  lexicographic order. Partial repair preserves valid slots. Unsupported eye
  formats survive opaquely and are refused for breeding/editing, not randomized.
- `GeneEyeColor` is compatibility output, not a second inherited state owner.
  Explicit server-side scalar writes validate finite [0,1] and intentionally
  infer a replacement genotype, exiting legacy mode while preserving dyes.
  Ordinary editor scalar echoes and client-supplied eye compounds are ignored.
- The tracked compound has defensive load/save/default copies. Player appearance
  uses player identity and owner writeback; rendering/client previews never
  initialize genetics. No new appearance packet or renderer rewrite is needed.

Downgrading the new saves is unsupported. Back up worlds before adoption.

## Smooth natural shades and local heterochromia (2026-10-14)

The user approved the before/after preview for ordinary eyes and complete
heterochromia. Keep the existing allele pairs, inheritance, no-mutation policy,
founder weights, UUID/domain seeding and `Format=1`/MCA version 4 save shape.
This is an artistic gameplay approximation, not a human pigmentation simulator.

For inherited coordinates `P` and `S`, use the existing six RGB anchors. Define
`smooth(a,b,x)` as `t*t*(3-2*t)` where `t=clamp((x-a)/(b-a),0,1)`. Blend channels
without intermediate rounding:

- Cool anchor: grey to blue with `smooth(0,0.5,S)`.
- Middle anchor: green to hazel with `smooth(0,0.75,S)`.
- Warm anchor: brown to amber with `smooth(0.65,1,S)`.
- Below `P=0.5`, blend cool to middle with `smooth(0,0.5,P)`.
- From `P=0.5` to `0.8`, blend middle to warm with `smooth(0.5,0.8,P)`.
- Above `P=0.8`, blend warm to brown with `smooth(0.8,1,P)`.
- Multiply channels by `0.85+0.30*P`, round once, cap at 255, and use opaque alpha.

Children still copy alleles; parental RGB is never averaged. The finite allele
model yields 20 distinct ordinary undyed RGB shades before brightness, texture
tone overrides and lighting. This is not an unlimited continuous genetic range.

For newly initialized local heterochromia, choose either pigment direction with
equal probability, independently of inherited pigmentation. Its magnitude is
uniform in `[0.05,0.4)`; the secondary offset remains uniform in `[-0.25,0.25)`.
These are gameplay choices, not clinical frequencies. Apply the saved offsets to
the left eye only while the existing trait is active, clamp coordinates, and use
the same blended resolver. A clamped effect may be subtle or visually unchanged;
do not reroll until different. Keep trait inheritance and the affected side as-is.

Existing valid DNA and `Local` values are retained. New-model villagers change
shade under the new resolver, including those already saved. `LegacyNatural`
continues to use its prior gradient/baked resolver, preserving recoverable old
appearance. Dye, albinism, rainbow, texture selection, exporter and cache ownership
remain in their established paths; there is no new genetics editor or packet.
Missing alleles use constrained legacy inference only when natural legacy colour
exists; otherwise fill invalid/missing slots from a stable Diverse founder sample.

The former six-family frequency figures below describe the threshold palette.
They do not forecast perceived colour categories under the blended palette. The
new validation checks founder allele weights and shade variety directly; exact
RGB swatches are distinct from perceptual classification and client evidence.

### Historical founder rebalance (2026-10-10)

The user requested fewer brown eyes and more variety. `DIVERSE` retains the
same independent-allele founder model, with pigmentation allele weights
`(0.55, 0.225, 0.225)` and modifier weights `(0.45, 0.18, 0.37)` for values
`(0, 1, 2)`. The cumulative sampling thresholds are `(0.55, 0.775)` and
`(0.45, 0.63)`, respectively. With the unchanged phenotype resolver, expected
new-founder frequencies are approximately 35.16% brown, 24.12% blue, 18.95%
hazel, 10.87% green, 6.13% grey, and 4.77% amber. These are analytical
expectations, not fixed colour rolls or offspring quotas. Existing saved eye
genotypes and legacy appearance are not re-rolled or repainted by this change.
The original calibration report's measured frequencies remain valid for its
*earlier* sampler, not for this new population policy.

## 1. Outcome and scope

Eye colours should reflect inherited family information, not an average of the
parents' visible colours. Siblings may differ, relatives may resemble one
another, and inherited influences that are not obvious in a parent's appearance
may become visible in descendants. This is a simplified gameplay model, not a
simulation of named human genes.

Build on `feature/1.21.1-eye-system` for Minecraft 1.21.1 and Java 21. Preserve
the eye catalogue, independently inherited textures, layered rendering, editor,
manual dyes, and established save appearance wherever recoverable.

Use the repository's canonical engineering policy rather than duplicating it
here. No subagents, new checkouts, publishing, or unrelated edits. The separately
authorized local dev merge is committed; no implementation commits are implied.

### Agreed decisions

- Prefer two inherited allele pairs; compare a one-pair baseline before freezing
  the model and save representation.
- Use Diverse for founder generation and genuinely missing genetic information.
- Preserve valid stored genetics regardless of whether parents still exist.
- Preserve known legacy natural appearance and infer compatible hidden genetics.
- Inherit from an available parent and generate only the unavailable contribution.
- Generate fallback genetics once on the server and persist the result.
- Keep complete heterochromia expression and manual cosmetics separate from
  inherited colour genetics.
- The withdrawn purple/pink/blue rarity ordering is not a requirement.

### Not in scope

Three-locus or general-purpose genetics frameworks; new naturally generated
purple/pink families; central/sectoral heterochromia; settlement ancestry state;
biome-based colour selection; unrelated genes or trait inheritance redesign;
automatic reconstruction of unavailable ancestors; forced population percentages
in offspring; and transparent support for downgrading saves.

## 2. Current source baseline

The local eye worktree was inspected at
`fccb7005a35dbd6bb320a8c88fab30a81a82d7f5`, with existing uncommitted edits.
This is an evidence snapshot, not a claim that those unrelated edits are verified.

Execution baseline is now merge `31c9179c1b4c121f4c81c3b0f7bb0688a2698fbf`,
which includes local `dev/1.21.1` at `c127423c659d2a026ff2b0177cbdb88fccf6ec67`.
Original dirty work was retained. The numerical probe itself is test-local;
production integration now delegates to the separate `EyeGenetics` owner.

- `Genetics.java` owns `EYE_COLOR`, uniformly randomizes scalar genes, and
  combines colour using parental interpolation plus mutation. Texture inheritance
  separately selects one parent's eye and resolves the catalogue/gender.
- `EyeTextureLayers.java` uses blue/green/hazel/brown gradient anchors, `frac`,
  and the `+0.43` natural heterochromia offset. Dyes override natural colour;
  rainbow animation overrides static colour. Albinism overrides ordinary natural
  colour. Exact scalar 1 wraps to the blue endpoint.
- `VillagerLike.java` stores texture and the separate `EyeColor`/`EyeColorLeft`
  cosmetic values. Those keys are not the genetic `GeneEyeColor` field.
- `Pregnancy.java` and `BabyItem.java` combine genes before inheriting traits.
  Stored babies carry saved entity data and must be included in migration.
- `EyeGeneticsFix.java` copies `GeneFace` to `GeneEyeColor`, infers texture
  format from field presence, and removes `GeneFace`. The 22-entry conversion
  retains a shape identifier, not the original baked palette index.
- `McaDataFixers.java` currently uses MCA schema version 3, including reconciliation
  of earlier version-2 collisions. A subsequent migration needs a fresh version.
- `DynamicSkinCache.java` currently keys scalar eye colour, brightness, texture,
  dyes and traits. New appearance-affecting state needs equivalent coverage.
- `SkinExporter.java` uses the same eye colour/tone helpers as rendering.

Historical tone overrides in `data/mca/skins/eyes/male.json` and `female.json`
are useful compatibility inputs; they do not independently recover lost palette
choices. This document does not certify all historical migrations.

## 3. Genetic ownership and inheritance

The proposed genotype has two abstract loci: a major pigmentation-expression
influence and a secondary pigmentation/appearance influence. Each has two bounded
allele values. They are functional gameplay influences, not independent replicas
of HERC2 and OCA2.

At each locus, copy one allele chosen from each parent's pair. Resolve phenotype
from the child's resulting genotype. Never replace the child's genotype with an
average of parental RGB values, colour categories, or resolved phenotypes.

The parent's saved alleles carry family history forward. Birth does not require
walking the family tree or loading grandparents. Persisted children do not lose
their genetic information when a parent dies, unloads, or is deleted.

Use an eye-specific domain owner integrated with `Genetics`; do not put every
allele into the generic scalar `GENOMES` loop or change unrelated inheritance.
The owner handles validation, founder generation, inheritance and phenotype
resolution. Rendering/editor code consumes its results rather than owning a
second mutable version of natural colour.

The proposed representation uses four quantized allele values and a small
immutable carrier, persisted through the existing tracked-data mechanism. Choose
quantization and the final serialized shape only after the model comparison;
do not release an allele format that has not exercised its full range.

Mutation is disabled in the baseline comparison. A later rare, bounded allele
mutation requires its own documented rate and population/family results. Do not
reuse the current per-child +/-0.1 phenotype mutation merely for consistency.

## 4. Phenotype and model-selection gate

Resolve continuous pigmentation/appearance parameters from the genotype, with
explicit interactions between the two loci. Map those parameters to nuanced
natural iris colours. Brown, blue, hazel, green, grey and amber are descriptive
families used for calibration and visual review, not six independent colour genes.

The expression model must retain observable family resemblance while permitting
more than one sibling outcome for appropriate heterozygous parents. A hidden
influence can become visible through a different inherited combination; no claim
that every colour can skip generations in every family is intended.

Reuse brightness and only subtle stable cosmetic variation. A cosmetic modifier
must not secretly supply an additional inherited colour-family locus. New natural
colour resolution must not wrap its darkest endpoint to blue.

Before adopting the two-locus representation:

1. Compare one-pair and two-pair candidates under the same founder, family and
   multigenerational experiments, with mutation disabled.
2. Publish the exact expression functions, family definitions, seeds and results.
3. Demonstrate an observable family/appearance requirement that the second locus
   improves beyond the one-pair baseline. More genotype combinations alone are
   not sufficient justification.
4. Review colour swatches through the existing textures, not RGB values alone.
5. Amend this spec with the selected expression function, allele quantization,
   joint founder sampler, calibrated targets and complete-heterochromia modifier
   rule before implementing save migration. Review the amendment with the user.

This is an explicit calibration gate, not permission to invent biological
dominance rules or to silently add a third locus. If the one-pair baseline meets
the requirements just as well, return that evidence for a simpler-design decision.

## 5. Diverse generation and configuration

Diverse is the selected default population policy. The proposed server config is
`eyePopulationPreset`, with `DIVERSE` as its default. To make the requested preset
configuration useful, this draft proposes `NATURAL_INSPIRED` as a brown-weighted
alternative, rather than a single-option selector or precise per-colour sliders.
The alternative is a proposed review choice, not a second user-selected default.

The original Diverse calibration proposal (now superseded by the founder
rebalance above) was the illustrative distribution from
the evaluation handoff:

| Natural family | Diverse target | Natural-inspired target |
| --- | ---: | ---: |
| Brown | 45% | 75% |
| Blue | 20% | 10% |
| Hazel | 15% | 8% |
| Green | 10% | 3% |
| Grey | 5% | 2% |
| Amber | 5% | 2% |

Both columns are gameplay targets from the handoff, pending calibration and
written-spec review. The user selected Diverse, not these individual numbers or
the alternative's inclusion. Neither column is verified worldwide human
demographics, exact quotas in a small village, or guaranteed offspring frequencies.
Reject unknown or explicitly null preset values before rewriting the config,
with an explicit diagnostic; do not silently switch population policy. An absent
field in an old config uses Diverse. Neither preset requires new client config.

The policy generates genotypes and then derives appearance. It must not generate
an unrelated random cosmetic colour alongside nominal genetic data. Founder
sampling must be assessed after mating as well as at generation zero; a
colour-first sampler can alter genotype combinations and descendant frequencies.

Configuration applies when establishing missing founder information, including
an unavailable parent's contribution at birth. It does not change expression of
already saved alleles, repaint villagers, reroll hidden carriers on reload, or
replace normal inheritance with a new weighted colour roll for each child.

Changing generation settings affects later initializations only. A generated
genotype remains persisted even when the generation policy changes.

## 6. Initialization and missing-information rules

| Available information | Required behaviour |
| --- | --- |
| Valid saved genotype | Use it; ignore absence of parent entities. |
| No genotype, recoverable legacy natural appearance | Preserve that appearance and deterministically infer compatible genetics. |
| New child, both parental genotypes available | Inherit normally from both. |
| New child, one genotype available | Inherit from the known parent; sample a contribution from the configured founder policy for the missing parent. |
| No genotype and no recoverable natural appearance | Generate a complete genotype from the configured founder policy. |
| Partially malformed genotype in a supported format | Keep valid allele fields; infer missing values from recoverable natural appearance where possible, otherwise sample missing contributions once from the founder policy. |

If a parent's legacy data is available, initialize/infer its genetics before
treating that contribution as unknown. Do not fabricate parent entities, invent
family-tree records, or force-load chunks to satisfy this fallback.

Fallback generation runs once on the logical server. Seed legacy inference from
stable entity identity and a documented eye-specific salt; persist its result.
Initialization must distinguish unavailable data from temporarily unavailable
entities, and conversion/load must not overwrite valid genetics.

Check field presence and types in the original save input. A tracked scalar's
default value (currently 0.5) is not evidence that a natural colour was saved.
For a missing parent, sample a joint founder genotype, then inherit one allele
per locus from it; do not create or persist a phantom parent. Player appearance
proxies use the player's stable identity and write back to server-owned player
data, rather than repeatedly inferring from the proxy's temporary UUID.

An unsupported future genotype format is not a missing genotype. Do not silently
reinterpret or replace it with randomized data; surface an unsupported-format
diagnostic and keep this separate from ordinary legacy migration.
Retain an unsupported eye payload opaquely through the registered compound field;
do not initialize, edit or breed from it as if it were missing. Test the actual
load/save path, not only the decoder. This is not a guarantee that a downgraded
binary can preserve every field in an unknown future MCA-wide schema.

Known manual dyes are not evidence of the natural genotype. A locally affected
heterochromatic eye is not an independent parental genetic contribution.
Historical colours outside the new natural palette can be preserved visually
without claiming an exact matching genotype. Infer a documented closest supported
natural phenotype in those cases; do not create new purple/pink genetic families
to make an inverse mapping look exact.

## 7. Complete heterochromia and cosmetics

Keep the existing trait's gameplay inheritance initially. After both genotype and
traits are established, generate and save a bounded local expression difference
for the affected eye. Children inherit the genotype and possibly the predisposition
trait, not the parent's local expression value.

Do not regenerate that difference during rendering, reload, or reconnect. Removing
the trait can suppress its expression without corrupting the genotype; re-enabling
it on the same villager uses the stored difference where valid. Migrated natural
heterochromia retains its recoverable legacy colours through the compatibility
appearance path.

Preserve existing cosmetic precedence: rainbow animation, then explicit dye,
then natural appearance. Albinism modifies the ordinary natural path; it is not
a new inherited eye-colour allele in this work. A legacy appearance resolver must
respect the original representation's trait semantics, rather than applying a
new universal override that changes historical villagers.

Keep fixed pixels and shadow/primary/highlight tone roles. Central rings and
sector masks are not interchangeable with those roles and remain out of scope.

## 8. Migration and existing appearance

Separate appearance preservation from genotype reconstruction. Existing eye-branch
villagers keep a versioned legacy natural-appearance payload with the source
representation and colour inputs needed by its resolver, while receiving inferred
genetics for offspring. Store the old scalar for gradient formats and recoverable
palette/eye identifiers for baked formats, including both eyes where they differ.
This compatibility state is not written into manual-dye fields or inherited by
children. New children use the new phenotype model.

The proposed editor semantics are explicit: changing or clearing a dye does not
erase legacy natural appearance. Replacing a migrated villager's natural genetics
is a distinct authorized genetic edit and exits legacy appearance mode. A new
implicit conversion behind an existing dye-reset action is not allowed.

Handle historical 22-texture saves, later scalar/gradient saves, and eye-branch
saves separately. Capture recoverable source appearance before destructive
normalization removes the original face index. Historical tone definitions can
assist reconstruction; custom resource packs and already discarded indices can
limit exact recovery. Document those limits without inventing original colours.

In the current pipeline, a newly appended fixer runs after the version-3
`EyeGeneticsFix` has removed `GeneFace`. Capture source appearance in the raw
payload before that chain runs, for both flat and nested MCA data, and carry it
into the new migration. Already-upgraded saves cannot recover an index that was
discarded earlier. Keep pure schema normalization separate from identity-seeded
server initialization so baby-item and player-data readers share the same rules.

Allocate a fresh shared MCA schema version at implementation time, newer than
the then-current version. Keep migration idempotent and preserve unrelated data.
Cover stored babies, villagers, zombie/conversion paths and other existing readers
of MCA entity data. A standalone fixer pass is not proof of load/save preservation.

Downgrade compatibility is not promised: older binaries can drop unknown fields
when saving. Users need a backup before upgrading worlds.

## 9. Integration boundaries

Shared genotype/phenotype logic stays in `common/` and cannot depend on client
rendering classes. Existing `CParameter`/`CDataManager` and Minecraft entity-data
sync are the first integration mechanism; no new appearance packet family or
loader-specific genetics storage is required by this design.

Replace mutable compound payloads when updating tracked state. Validate allele
bounds and the server's edit authority at existing input boundaries. The client
does not initialize genotypes or mutate inherited state.

The editor currently accepts all `Gene*` keys. Ordinary dye, texture, trait and
unrelated-gene edits must preserve server-owned eye genetics and legacy appearance;
ignore stale echoed `GeneEyeColor` values rather than interpreting them as a
genetic edit. Do not accept the new eye compound via that generic patch path.

Rendering, editor previews and exported skins share natural appearance resolution.
Update `DynamicSkinCache` identity/invalidation for all new appearance inputs.
Preserve catalogue reload/texture fallback behaviour, including existing user edits.

Define the old `Genetics.EYE_COLOR` read/write compatibility contract before
removing it. A deprecated read adapter may expose a derived value; a write must
route through an explicit genetic-edit operation, not create a second owner.
Retaining legacy serialized fields for compatibility is not permission to continue
averaging them in new inheritance. Preserve unrelated seeded genetics behaviour.
Apply that contract to both `getGene`/`setGene` and `getGenome(...).get()`/`set()`.
Preserve the existing generic loop's random draws for unrelated genes; use a
separate eye-specific random stream instead of adding allele types to its HashSet.

## 10. Acceptance evidence

Required evidence before adopting calibrated genetics:

- Large founder samples and multiple fixed seeds, with allele distributions,
  family classification rules, colour variation and generation-zero frequencies.
- Generations 10, 50 and 100 under random mating, with no-mutation controls.
- Small settlement runs that distinguish ordinary finite-population drift from
  systematic variance loss caused by the inheritance operator.
- Selected family fixtures exercising sibling variation, hidden influences and
  grandparent-like appearances with copied, traceable alleles.
- One-parent and both-parent fallback fixtures, config changes after initialization,
  and parent death/unload/deletion without genotype changes or colour rerolls.
- Legacy normal, dyed, heterochromatic, albinism and baby-item migration fixtures;
  repeated upgrade and load/save/reload must preserve relevant state.
- Shared resolver, texture fallback, cache/export and client/server synchronization
  checks, followed by visual swatches and dedicated-server evidence.

Use existing test lanes according to the canonical repository policy. Do not
claim gameplay or visual validation from compilation alone. The initial draft
had no simulation evidence. Subsequent numerical calibration is recorded in the
linked amendment: 12 probe tests and 592 common tests passing at that stage.
Subsequent integration evidence and remaining runtime gaps are recorded in the plan.

## 11. Review and next stage

The user requested source double-checking, an implementation plan, then staged
execution on the eye branch with a local dev merge. See
[the staged implementation plan](../plans/2026-10-10-family-eye-genetics.md).
The subsequent user approval settled the calibrated amendment: near-target Diverse,
deferred Natural-inspired, missing-information rules and migration/editor semantics.
No PR or publishing is authorized.

The plan separates model comparison and calibration from production integration
and save migration. Production work is now implemented locally; completed checks
and remaining runtime gaps are listed separately in the plan.

Scientific context: [MedlinePlus eye-colour genetics](https://medlineplus.gov/genetics/understanding/traits/eyecolor/).
It supports complex inheritance, not this particular gameplay model or the
proposed population percentages.
