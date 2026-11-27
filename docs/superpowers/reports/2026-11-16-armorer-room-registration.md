# Armorer room registration on MCA 1.21.1

The profiled Builder NeoForge dependency was `mca-compatible-chore-3FA6C531.jar` (SHA-256 `3FA6C531D11250284623A41389A13066FB732D96BBE0EF6CDAB86507D0725291`). Its `VillageManager`, `RoomWorkflow`, `StructureScanner`, and `SelectedFloorScanner` classes, including the scanner’s nested classes, matched the pre-change local artifacts byte for byte. The earlier floor-index and connector work from `a49fb2c62` and the bounded-exterior and discovery work from `f9de24136` and `ddf28fd68` were already in use.

Both November 16 Spark captures show one reported-source registration. The scaffold-tail capture attributed 648 ms inclusive to MCA registration, including 40 ms to vertical connector collection. The full capture attributed 432 ms to MCA registration and 28 ms to connector collection. These are 4 ms sampled weights on the server thread. The full construction run failed its scaffold-required fixture assertion after registration published the room.

The stopped full-run `world/data/mca_villages.dat` (`DataVersion` 3955, 12:38:11) matches Builder’s restoration checkpoint for project `329a10aa-1033-461d-8eae-fe723c4bb964`. Structure 1 has its source at `(-383246,-57,-3736478)`, a supported interior cell, and its floor contains 6,238 cells across the enclosed 79×79 course interior. The test enclosure supplies the ceiling at Y=-33 for 6,196 cells. The selected `building` Room 2 contains 20 house cells. Builder’s separate outdoor Armorer control registered 21 floor cells and 21 room cells from the same template-relative `(0,1,0)` seed. Same-height interior seed selection does not remove the artificial enclosure from the physical floor scan. The earlier Taiga comparison documented the same cause for this test course in `a49fb2c62:docs/superpowers/reports/2026-10-11-floor-performance-runtime-verification.md`.

## MCA correction

Vertical connector collection already unions overlapping direct probes above the support layer. The short-gap probe at the floor support still ran once for each adjacent floor cell, up to five times for an interior support with four neighbors. Collection now visits each exact support position once within the existing scan. Different Y positions in an uneven column still receive independent short-gap checks. Connector validation, first-seen ordering, room selection and floor bounds use their existing owners.

The same compiled scanner and Minecraft block states executed the reported scan on each saved block volume before and after the correction. Each entry is an independent observation. Within each scene, the west seed and Builder seed selected the same canonical floor geometry and same room. For each seed, the selected and traversal positions, floor/room sizes and geometry checksums agreed across the change.

| Saved Armorer geometry | Floor / room cells | Block-state reads before | After |
| --- | ---: | ---: | ---: |
| Sealed course, Builder seed | 6,238 / 20 | 477,215 | 452,624 |
| Sealed course, one cell west | 6,238 / 20 | 477,231 | 452,640 |
| Outdoors, Builder seed | 21 / 21 | 1,201 | 1,143 |
| Outdoors, one cell west | 21 / 21 | 1,215 | 1,157 |

The durable tall-room regression recorded 708 support-layer reads before the change and 452 after it. All 256 cells and the empty connector set were preserved. The focused run passed all 18 connector tests and the archive diagnostic. The archive replay measures deterministic work on bounded saved block views. Live world reads, server timing and the earlier in-game samples have different measurement boundaries.

The diagnostics are in MCA’s ignored `build/` directory as `mca-armorer-archive-before-20261116.xml`, `mca-armorer-archive-after-20261116.xml`, `mca-armorer-support-red-20261116.xml`, and `mca-armorer-support-green-20261116.xml`. The archive replay code and input extracts are local diagnostic files.

## Verification artifact

On `dev/1.21.1` HEAD `4a8556e44d1163ac53bf9169a76bf4e74d037a88`, `:common:test :neoforge:jar --continue --offline --no-parallel` exited zero with Temurin JDK 25. The common suite executed 840 tests, including 430 world-data tests, with zero failures, errors or skips. Source and resource manifests matched before and after the build. MCA’s separate NeoForge runtime tests were outside this verification run.

The pinned output is `build/mca-armorer-scan-F6B45601.jar`, SHA-256 `F6B456014C0BA792E9FBEC03047529760B11EF615B29E79299FDF89ADF52E101`. The build includes the checkout’s existing family-tree, navigation and other work. It differs from the earlier Builder-pinned jar outside the scanner, so compare the matching archive workload for scanner-only read counts. The packaging log, dirty source manifests and full changed-entry list are in `build/` with `mca-armorer-` prefixes.

## Savanna Medium House 1 source control

Fresh normal construction `20261116-125909873-21c9a259` completed its 324 blueprint operations, restored the temporary supports and reached registration, which returned `INVALID_TYPE` for the forced `house` category. This differs from the earlier live saved venue’s `IDENTICAL` result. The live saved structure belongs to Snowy and spans Savanna’s physical footprint. `IDENTICAL` on shared floor cells does not establish that the Savanna room has registered.

The fresh Savanna world retained by Builder contains its source at `(-9834794,-57,-12037012)`. This cell is in the open courtyard between two huts. Its column has air from Y=-57 through Y=-34 and the test course’s artificial roof at Y=-33. The template’s two bed heads are at source-relative `(1,0,-5)` and `(1,0,5)`, behind bedroom doors at Z offsets -2 and 2. The declared `house` category requires a bed, while MCA intentionally selects the door-partitioned room at the requested source.

An independent common-lane diagnostic scanned the exact final saved block volume through the production `StructureScanner.scanReportedStructure`, partitioned through `BuildingRoomScanner.components`, and collected the selected room’s real POI candidates. All three scans found the same 6,220-cell physical floor. The original source selected a 6,202-cell courtyard component with zero bed heads. A source four blocks north selected its nine-cell bedroom and one bed head at `(-9834793,-57,-12037017)`. Four blocks south selected the other nine-cell bedroom with one bed head. The focused diagnostic exited zero. Its outputs are `build/mca-savanna-source-diagnostic-20261116.log` and `.xml`.

Builder independently ran its original-source open-sky native registration wrapper, `20261116-132806401-05a50ad5`. It physically materialized all 324 operations and failed registration with `INVALID_TYPE`, publishing zero rooms and zero new structures. The controlled result and the saved-state bedroom selection identify the caller’s trusted interior seed as the correction boundary. Root owns the catalogue-source fix and its native verification. This follow-up added no MCA production change.

The first saved-state diagnostic used the convenience `RoomPoiEvidence.candidates` collector and spent about 73 seconds constructing a large immutable set. The production materializer streams those positions through `Index.forEachCandidate`. The diagnostic eventually passed. Its thread dump localizes the extra collection cost to the diagnostic’s choice of entry point, and provides no timing claim for the production materializer. Later diagnostics should use the streaming entry point.
