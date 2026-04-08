# MCA 26.1.1 NeoForge Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port MCA `common` and `neoforge` from `1.21.1` to the NeoForge `26.1.1` line and restore a runnable NeoForge client baseline.

**Architecture:** Update the build and toolchain first, then drive compile failures down in focused API slices. Keep Fabric out of scope and use local 1.21.1 sources plus the 26.1 primer as the comparison baseline.

**Tech Stack:** Gradle, Java 25, NeoForge ModDev Gradle, NeoForm, NeoForge 26.1.1, MCA common/neoforge modules

---

### Task 1: Update Build And Version Wiring

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `common/build.gradle`
- Modify: `neoforge/build.gradle`
- Modify: `buildSrc/src/main/groovy/multiloader-common.gradle`

- [ ] Update Minecraft, NeoForge, NeoForm, Java, and related version wiring to the 26.1.1 target.
- [ ] Run `./gradlew :common:compileJava :neoforge:compileJava` and capture the first real API failures.

### Task 2: Repair Common Compile Breaks

**Files:**
- Modify: `common/src/main/java/**`

- [ ] Fix compile failures outside villager trades and custom advancement criteria.
- [ ] Re-run `./gradlew :common:compileJava :neoforge:compileJava` after each focused slice until the remaining errors cluster around trades or criteria.

### Task 3: Port Custom Advancement Criteria

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/advancement/criterion/*`
- Modify: `common/src/main/java/net/conczin/mca/registry/CriterionMCA.java`

- [ ] Adapt MCA custom triggers to the 26.1 advancement and validation model.
- [ ] Re-run `./gradlew :common:compileJava :neoforge:compileJava` and confirm criterion-related errors are gone.

### Task 4: Port Villager Trade Registration

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/registry/TradeOffersMCA.java`
- Modify: any new datagen or registry wiring required by the 26.1 trade system

- [ ] Replace direct legacy trade-table mutation with the 26.1-compatible trade path.
- [ ] Verify `common` and `neoforge` compile after the trade migration.

### Task 5: Restore NeoForge Runtime Baseline

**Files:**
- Modify: `neoforge/src/main/java/**`
- Modify: runtime resources if required

- [ ] Run `./gradlew :neoforge:runClient`.
- [ ] Fix launch-time regressions until the client starts successfully.
- [ ] Update docs if any port-specific runtime setup changed.
