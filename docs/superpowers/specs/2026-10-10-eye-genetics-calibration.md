# Family eye genetics — measured calibration and Gate A proposal

Date: 2026-10-10

Status: Numerical comparison completed. The user subsequently approved the
two-pair model, DIVERSE-only sampler and no mutation. Production integration and
migration are implemented locally; final verification is recorded in the plan.
The measurements below remain numerical experiments, not in-game population counts.

The 2026-10-14 user-approved smooth-palette amendment supersedes the threshold
colour resolver and local-offset sampler measured here. Allele inheritance and
founder weights are unchanged. See the [current expression contract](2026-10-10-family-eye-genetics-design.md#smooth-natural-shades-and-local-heterochromia-2026-10-14).
This report is historical model-selection evidence; its colour-family counts and
identical-parent swatches do not describe the current blended palette.

The founder sampler was later rebalanced to reduce brown eyes; see the
[implemented contract](2026-10-10-family-eye-genetics-design.md#founder-rebalance-2026-10-10).
Founder frequencies and descendant measurements below describe the *original*
sampler. They are retained as historical calibration evidence, not current
population forecasts.

## Recommendation

Use two independent inherited allele pairs, with three states per allele, and
the product-distribution Diverse founder sampler below. Keep brightness and
complete-heterochromia local expression separate from the inherited genotype.
Do not add a third locus or force offspring colour percentages.

Both one-pair and two-pair copying retain variation far better than scalar
averaging. The reason to prefer two pairs is independent inherited family
information: visually identical blue parents can have different probabilities
of grey children, without their visible colour being used as their genotype.
The tested one-pair/cosmetic-reroll alternative loses that distinction. This does
not prove that every imaginable one-locus encoding is incapable of doing it.

Prefer near-target founders with stable initial allele independence over an exact
colour quota obtained by conditioning genotypes on a colour roll. The tested
colour-first alternative shifted markedly in descendants despite meeting its
founder targets. That result applies to the specific uniform-conditioned sampler
tested here, not every possible colour-first algorithm.

## Source, merge and scope

Existing checkout: `C:/Users/mikol/IdeaProjects/minecraft-comes-alive-1.21.1-eye-system`,
branch `feature/1.21.1-eye-system`. The user's merge request was executed locally:

- Eye parent: `fccb7005a35dbd6bb320a8c88fab30a81a82d7f5`.
- Incoming local `dev/1.21.1`: `c127423c659d2a026ff2b0177cbdb88fccf6ec67`.
- Merge: `31c9179c1b4c121f4c81c3b0f7bb0688a2698fbf`.

The merge was clean; unrelated dirty appearance-catalog/editor/rendering work
was retained and excluded from the merge commit. No new checkout, WSL,
subagents, push or PR. At the numerical stage the new model remained inside
`common/src/test/java/net/conczin/mca/entity/ai/EyeGeneticsModelComparisonTest.java`.
No production eye genetics, palette or save schema changed in that stage.

Controls were checked against `Genetics.Gene.mutate` and
`EyeTextureLayers.getGeneticEyeColor` in this checkout. Probe assertions pin
the old gradient anchors and exact scalar-1 wrapping behaviour. They are not
end-to-end tests of a villager's rendered pixels.

## Experimental models and method

Compared five models:

1. Existing scalar interpolation with its current uniform mutation in [-0.1, 0.1).
2. The same scalar interpolation with mutation disabled.
3. One inherited pigmentation pair, with a non-inherited modifier sampled once
   per individual and held stable for its simulated lifetime.
4. Two inherited pairs with independently sampled founder alleles.
5. Two inherited pairs with colour-first founders: roll a target family, then
   uniformly select one of the ordered, compatible genotypes.

Candidate inheritance copies one randomly chosen allele from each parent at
each locus; candidate mutation is disabled. One-pair births reroll their cosmetic
modifier from the same distribution used by its founders, not from parental
cosmetics. Models 3 and 4 use the same founder phenotype mapping/distribution.

Seeds: `11, 29, 47, 83, 131, 197, 263, 347, 431, 557`.
For each model and seed, sample 100,000 independent founders. Separately simulate
closed populations of 10,000, 32 and 128 for 100 generations; report generations
0, 10, 50 and 100. The independent founder samples total one million per model.

At every birth, select two distinct parent indices uniformly from the preceding
generation, with replacement across births. There is no self-parenting,
migration, selection, mating preference, mortality simulation or Minecraft
entity lifecycle. Sex is not modelled. Founder RNG uses the seed; mating and
inheritance use separate streams seeded with XOR salts `0x4D415445L` and
`0x45594553L`. Equal seeds therefore give the same mating schedule across models,
even when genetic RNG consumption differs.

Reports contain family counts, coordinate mean/variance, normalized RGB variance
and per-locus allele frequencies. RGB variance is the sum of the three channel
population variances after dividing channels by 255; it is not perceptual distance
or proof of distinguishability in game. Unavailable allele-frequency fields are
`NaN`, not measured zero frequencies. There are 653 population/sweep rows.

## Exact candidate expression and founder sampler

All four experimental alleles are in `{0, 0.5, 1}`. Proposed production encoding
is integers `{0, 1, 2}`, divided by two for expression. This encoding and the
versioned compound remain proposals until Gate A; no persistent format is frozen.

Let pigmentation alleles be `pA,pB`, secondary alleles `mA,mB`, and the lower
allele contribution be `r = 0.25`:

```text
P = (1-r) * max(pA,pB) + r * min(pA,pB)
S = (mA+mB) / 2
```

These are gameplay coordinates, not quantities measured from human DNA.

| Pigmentation coordinate | Secondary coordinate | Family |
| --- | --- | --- |
| P < 0.30 | S < 0.125 | Grey |
| P < 0.30 | S >= 0.125 | Blue |
| 0.30 <= P < 0.65 | S < 0.375 | Green |
| 0.30 <= P < 0.65 | S >= 0.375 | Hazel |
| 0.65 <= P < 0.90 | S >= 0.90 | Amber |
| Remaining P >= 0.65 | Any | Brown |

Experimental opaque ARGB palette bases: brown `FF6B442E`, blue `FF6699BE`, hazel
`FF8E9D4C`, green `FF70975D`, grey `FF81909C`, amber `FFC98A30`.
Scale each base RGB channel by `0.85 + 0.30*P`, round to the nearest integer,
and cap at 255. The darkest endpoint is brown `FF7B4E35`, not wrapped blue.
This is a small quantized palette with within-family shade steps, not continuous
human pigmentation. Existing MCA brightness, texture tone overrides, albinism,
dyes, rainbow animation and lighting are not simulated here.

Independently draw every founder allele using these probabilities:

| Locus | Value 0 | Value 0.5 | Value 1 |
| --- | ---: | ---: | ---: |
| Pigmentation | 0.5 | sqrt(0.5)-0.5 | 1-sqrt(0.5) |
| Secondary | sqrt(0.2) | sqrt(0.2)/2 | 1-1.5*sqrt(0.2) |

Approximate weights are `(0.50000000, 0.20710678, 0.29289322)` and
`(0.44721360, 0.22360680, 0.32917961)`. The joint sampler is the product of
these four independent draws. No independent final colour roll follows it.

Analytically, this produces 20% blue, 5% grey, 10% green, 15% hazel,
approximately 4.488% amber and 45.512% brown in independent founders. The
approximately 0.512 percentage-point trade between amber and brown is deliberate;
it is not Monte Carlo error. Proposed acceptance: each founder family is within
one percentage point of the illustrative Diverse targets on a million-founder
sample. Do not impose that tolerance on a small village or later generations.

### Founder measurements

Percentages aggregated over one million founders per model:

| Model | Brown | Blue | Hazel | Green | Grey | Amber |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Diverse target, illustrative | 45 | 20 | 15 | 10 | 5 | 5 |
| One pair / two pairs, independent founders | 45.499 | 20.050 | 14.945 | 10.028 | 4.996 | 4.483 |
| Two pairs, tested colour-first founders | 44.950 | 20.002 | 15.017 | 10.031 | 5.034 | 4.967 |
| Existing scalar, nearest-anchor reporting bands | 14.976 | 17.510 | 32.544 | 34.970 | 0 | 0 |

The old scalar family labels are reporting bands around its gradient anchors:
blue below 0.175, green below 0.525, hazel below 0.85, otherwise brown, after
`frac`. They are not the candidate model's family definition or measured player
perception. Do not directly interpret this row as observed settlement statistics.

For independent founders, binomial sampling standard error for the largest
family is approximately 0.050 percentage points at one million samples; a
rough 95% interval is approximately +/-0.10 percentage points. Descendant
observations are related and affected by drift, so that calculation does not
apply to pooled descendant counts.

### Generations: variance and frequencies

Mean pigmentation-coordinate population variance across ten runs of size 10,000:

| Model | Generation 0 | 10 | 50 | 100 |
| --- | ---: | ---: | ---: | ---: |
| Scalar, current mutation | 0.08312502 | 0.01131841 | 0.01007058 | 0.01004168 |
| Scalar, no mutation | 0.08312502 | 0.00144394 | 0.00000000 | 0.00000000 |
| One pair, cosmetic modifier | 0.12263678 | 0.12193342 | 0.11970371 | 0.11747489 |
| Two pairs, independent founders | 0.12263678 | 0.12319122 | 0.12358674 | 0.12447314 |
| Two pairs, colour-first founders | 0.12950768 | 0.10965096 | 0.10851004 | 0.10835139 |

Zero above means zero to eight decimal places, not a proof of exact mathematical
fixation of the floating scalar. Initial scale differs between mappings; the
useful comparison is loss within each model, not claiming their variance units
are interchangeable human measurements. At generation 100, normalized RGB
variance is 0.01700091 for current scalar mutation, 0.05573218 for one pair,
0.05909926 for two pairs, and 0.05416272 for colour-first two pairs.

Generation-100 family percentages, same ten populations of 10,000:

| Model | Brown | Blue | Hazel | Green | Grey | Amber |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Scalar, current mutation, reporting bands | 0.023 | 0.051 | 40.192 | 59.734 | 0 | 0 |
| Scalar, no mutation, reporting bands | 0 | 0 | 0 | 100 | 0 | 0 |
| One pair | 47.218 | 17.890 | 15.543 | 10.320 | 4.550 | 4.479 |
| Two pairs, independent founders | 44.783 | 21.708 | 14.565 | 9.325 | 5.078 | 4.541 |
| Two pairs, colour-first founders | 45.245 | 16.591 | 20.104 | 11.290 | 2.488 | 4.282 |

The scalar experiment establishes a tendency toward intermediate gradient values
under this mating model. It does not establish the cause of the player's actual
world report. Even uniform founders give broad green/hazel reporting bands;
rendering and the world's actual founder/breeding history need separate evidence.

Both copying candidates avoid the scalar operator's rapid variance contraction.
The second locus is not necessary merely to achieve that; its family-information
benefit is the stronger justification. Differences between the two copying
models' ten-run means are not evidence of one universally resisting drift better.

Finite populations demonstrate genuine loss of variation: with two pairs at
generation 100, population 32 and seed 11 had 32 grey villagers; seed 131 had
32 brown; seed 347 had 32 blue. Other runs remained mixed. Population 128 and
seed 47 had 123 brown, 2 blue and 3 amber. These are closed-model simulations,
not predictions of an ordinary Minecraft settlement's future. Saved family
genetics cannot guarantee colour diversity forever in a tiny closed population.

### Parameter sensitivity

Single-seed sweep, 100,000 independent founders, seed 47:

| Lower-allele contribution r | Brown | Blue | Hazel | Green | Grey | Amber |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 50.134 | 20.105 | 14.669 | 10.028 | 5.064 | 0 |
| 0.25 | 45.671 | 20.105 | 14.669 | 10.028 | 5.064 | 4.463 |
| 0.5 | 19.562 | 36.423 | 20.089 | 13.452 | 9.164 | 1.310 |

Changing expression without recalibrating founders materially changes frequencies.
The coefficient 0.25 is an explicit tested gameplay choice, not a biological fact.

## Family evidence

Tests exercise copied contributions, sibling segregation and a light grandparent
allele reappearing after a visibly darker intermediate generation. In the major
pair, two `(0,1)` carriers look brown but can produce `(0,0)` blue offspring;
two `(1,1)` parents cannot produce a lighter major allele in the no-mutation model.

Secondary-history comparison keeps the parents' displayed ARGB identical in both
models. Two blue major `(0,0)` parents have secondary `(0,1)` versus `(0.5,1)`;
each is paired with a grey `(0,0;0,0)` partner. Over 10,000 children, seed 347:

| Model | Cool blue parent: grey children | Warm blue parent: grey children |
| --- | ---: | ---: |
| Two inherited pairs | 4,975 | 0 |
| One inherited pair, equivalent stable parental cosmetics | 2,035 | 2,035 |

Both two-pair parents look blue, but the first carries a secondary zero it can
transmit. The one-pair control rerolls the secondary appearance at birth and
cannot convey that history. The result justifies an independent inherited
secondary influence for this requirement; it does not validate named human loci.

## Complete heterochromia proposal

Test-local candidate: resolve one shared genotype first; affect the left eye only
when the existing trait is active. Generate two independent uniform draws `U,V`
and store offsets once:

```text
direction = P < 0.5 ? +1 : -1
deltaP = direction * (0.2 + 0.2*U)
deltaS = (V - 0.5) * 0.5
affectedP = clamp(P + deltaP, 0, 1)
affectedS = clamp(S + deltaS, 0, 1)
```

Absolute bounds are 0.4 and 0.25. Resolve the affected eye through the same
family/palette function. Local offsets never become genetic contributions.
In 10,000 seeded founder/local samples (seed 263), 7,495 changed family. Every
sample retained identical alleles and bounded coordinates. A changed family is
not an in-game perceptual measurement, and the remaining samples may differ
only in shade or be visually unchanged after clamping/rounding.

This can shift low-pigment eyes toward intermediate colours and higher-pigment
eyes toward lighter ones. It does not routinely map extreme blue straight to
extreme brown, and is not a clinical heterochromia distribution. Do not force
category changes by rerolling until success. Actual-client swatches must decide
whether these offsets are useful or excessive before production constants freeze.
Persistent identity seeding, save/reload behaviour and trait-order integration
are future production tests, not established by this pure expression probe.

Keep existing trait inheritance for gameplay; children inherit genotype and
possibly the trait, not parental offsets. Keep rainbow/dye precedence, albinism,
and historical appearance via their existing or migration-compatible owners.
Central/sectoral forms remain out of scope.

## Proposed inference and configuration amendment

Inference is intentionally not part of the completed simulation. For production,
retain recoverable original natural appearance separately, then infer a valid
hidden genotype for offspring. Never infer from manual dye, rainbow animation,
trait-induced albinism colour or the affected heterochromatic eye when the
underlying natural input is available.

Proposed bounded rule: enumerate the 36 canonical unphased genotypes (six sorted
allele pairs per locus). Resolve each under the approved function; minimize
squared RGB-channel distance to the recoverable undyed base natural colour.
For exact supported matches, distance is zero. Choose among tied genotypes in
canonical lexicographic order using stable UUID-derived randomness with an
eye-inference domain salt. Pin that seed derivation with fixtures before writing
save data; do not use a temporary player proxy's UUID. The salt/bit-mixing is not
yet implemented or tested, so Gate A approval does not excuse those tests.

RGB distance is a small deterministic compatibility approximation, not perceptual
or biological reconstruction. Purple/pink and other unsupported legacy colours
stay visible through legacy appearance, while offspring use the closest supported
inferred genotype. Preserve valid fields when repairing partial supported payloads;
restrict inference to compatible candidates rather than rewriting valid alleles.
Unavailable natural inputs use the Diverse founder policy once. Unsupported
future payload formats remain opaque and are not randomized.

Recommend `DIVERSE` only for the first implementation scope. An alternative
`NATURAL_INSPIRED` preset has not been calibrated and should not be offered as
though its illustrative percentages are implemented. The user-selected default
and this founder sampler are independent of biome. If a second preset is wanted,
calibrate its joint alleles separately before adding it. Keep server ownership;
configuration affects founders/missing contributions only, never saved expression.

## Verification and remaining gate

Commands used, serially from the eye worktree:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.EyeGeneticsModelComparisonTest' --console=plain --no-parallel --no-daemon
.\gradlew.bat :common:test --console=plain --no-parallel --no-daemon
```

Merged baseline: 580 tests, zero failures/errors/skips. The completed numerical
stage adds 12 probe tests: full common suite 592 tests, zero failures/errors/skips.
Behavioural RED/GREEN evidence includes deliberate averaging instead of copying,
missing phenotype/simulation implementations, and self-parenting in the mating
sampler. RED failures were assertions, not unresolved imports. Test reproducibility
is asserted separately from merely printing reports. Results are emitted into the
fresh JUnit XML `common/build/test-results/test/TEST-net.conczin.mca.entity.ai.EyeGeneticsModelComparisonTest.xml`.
That XML is disposable build output; the measured summary is recorded here.

Self-review corrected self-parenting and made the one-pair family fixtures have
the same initial visible colours as the two-pair fixtures. No delegated review.

Still pending:

- User review of two pairs, quantization, expression, near-target Diverse weights,
  proposed inference and the decision to defer a second preset.
- Actual-client swatches through MCA textures at gameplay scale, particularly
  blue/grey, hazel/amber and complete heterochromia. Neither palette numbers nor
  JUnit success clear this visual gate; no client check was run in this stage.
- All conditional Tasks 2–7: production owner, codec, migration, births/fallbacks,
  tracking, cosmetics/editor, skin export/cache, GameTests, loader artifacts,
  dedicated-server and multiplayer/client evidence.

The `executing-plans` workflow stops at this planned calibration-review gate.
It must not silently treat the newly measured amendment as already approved or
describe the production feature as complete.
