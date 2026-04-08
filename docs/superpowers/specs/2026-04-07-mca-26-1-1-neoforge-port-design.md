# MCA 26.1.1 NeoForge Port Design

## Goal

Port the MCA `1.21.1` branch forward to the NeoForge `26.1.1` line for `common` and `neoforge` only, leaving Fabric untouched for now.

## Scope

- Update build and toolchain wiring so `common` and `neoforge` target Java 25 and the 26.1.1 NeoForge line.
- Make the `common` and `neoforge` source sets compile against the new APIs.
- Reach a runnable NeoForge client baseline.
- Keep upgrade notes and local reference source workflow in-repo.

## Non-Goals

- Fabric parity in this phase.
- Large gameplay refactors unrelated to the port.
- Reworking MCA systems unless 26.1 API changes force it.

## Approach

1. Move Gradle, Java, Minecraft, NeoForm, and NeoForge version wiring to the 26.1.1 target.
2. Use compile failures to identify API breaks and fix them in focused slices.
3. Prioritize high-risk surfaces called out by the 26.1 primer:
   - Java 25 and toolchain changes
   - advancement trigger/validation changes
   - villager trade migration
   - codec and value-provider rewrites if MCA touches them
4. Verify with `:common:compileJava`, `:neoforge:compileJava`, and `:neoforge:runClient`.

## High-Risk Areas

- `common/src/main/java/net/conczin/mca/registry/TradeOffersMCA.java`
- `common/src/main/java/net/conczin/mca/advancement/criterion/*`
- `common/src/main/java/net/conczin/mca/registry/CriterionMCA.java`
- `gradle.properties`, `build.gradle`, `common/build.gradle`, `neoforge/build.gradle`, and `buildSrc`

## Definition Of Done

- `common` and `neoforge` target the 26.1.1 line.
- `./gradlew :common:compileJava :neoforge:compileJava` succeeds with Java 25.
- `./gradlew :neoforge:runClient` launches successfully.
- Fabric remains unchanged unless shared `common` edits incidentally affect it.
