# Optional-FCP configuration review follow-up — 2026-10-17

The pre-review config foundation was committed at `d857d2988` after excluding
generated `.log` artifacts. This follow-up implements the targeted code review
fixes from the 2026-10-17 implementation plan. The earlier
`2026-10-15-optional-fcp-implementation-status.md` remains a historical
checkpoint rather than evidence of today's behavior.

## Review disposition and evidence to verify

| Review | Implementation / regression coverage | Remaining release evidence |
| --- | --- | --- |
| R1 Fabric ownership | FCP remains the sole active native backend when installed. The standalone writer emits native-compatible TOML; an offline migrator backs up/relocates recognized old paths. Peer-capability routing selects either FCP's configuration-phase SERVER sync or MCA's play-phase snapshot. | Real-world repeated FCP mode switches and all four client/server installation combinations |
| R2 Section metadata | Schema builder retains translations/comments set before `push()`; native spec builder restores them. `ConfigLayoutTest` asserts actual native section translations. | Interactive GUI section search/headings |
| R3 Pending SERVER values | Client play INIT sets pending state; `ConfigValue` refuses stale SERVER reads; validated MCA snapshot or native-sync acknowledgement completes it, then deferred client login runs. | Remote client join timing and gameplay rendering during the pending window |
| R4 Malformed world override | Uses validated global SERVER settings (including completed upgrades), not a previous world's state. Consecutive world regression tests leave malformed files unchanged. | Integrated server world transitions |
| R5 Existing TOML upgrades | Applies `defaultMapsVersion` migration after a valid existing-file load, persists successful upgrades, and avoids overwriting invalid input. | Filesystem failure injection and real-world migration fixtures |
| R6 Live SERVER change | FCP's native SERVER transport is login-only in the inspected 21.1.6 sources. Reload resends MCA snapshots to non-FCP clients and disconnects FCP peers with explicit reconnect instruction. | Real GUI edit/save/watch/reload with connected mixed clients |
| R7 Native tests | `ConfigDecodedMapCacheTest` reloads native `ModConfigSpec` through the native test loader with different SERVER documents. Existing pathless SERVER config test remains. | Actual Fabric/FCP and NeoForge reload event integration |
| R8 List copies | `ConfigValue` uses `List.copyOf`, reusing immutable JDK lists while copying mutable inputs. Test covers repeated-read identity, input aliasing, and unmodifiable return. | Optional representative hot-path allocation measurements |

## Verification boundaries

The following verification executed on 2026-10-17 after the implementation:

- `.\gradlew.bat :common:test :fabric:test :fabric:build :neoforge:build --console=plain -q`
  finished with **exit code 0**. JUnit XML reports **639 common tests**, with
  **0 failures and 1 skipped**, and **24 Fabric tests**, with **0 failures**.
- Generated COMMON/CLIENT/SERVER TOML was parsed and checked by the real
  native NeoForge `ModConfigSpec.isCorrect()` in Fabric's test lane, with
  all files accepted without correction. NeoForge and Fabric runtime GUI
  interactions were **not** exercised by that JVM test.
- `git diff --check` exited successfully (Git printed line-ending warnings,
  not whitespace failures).
- `mca-fabric-1.21.1-SNAPSHOT.jar` was inspected: no `fuzs/forgeconfigapiport`
  mod/classes or direct `net/neoforged` class package entries were bundled;
  `META-INF/jars/core-3.8.0.jar` and `META-INF/jars/toml-3.8.0.jar` are present.

The original 2026-10-15 report recorded previously successful Fabric client/
server startup probes and five NeoForge GameTests. These **do not** prove
the new changes work in live multiplayer or interactive FCP GUI scenarios.
Mixed-FCP multiplayer, FCP GUI edit/save/search/reset, runtime callback order,
FCP mode-switch persistence, and real NeoForge GameTests on this new commit
remain explicit acceptance gaps, not passing tests.

No generated logs or user run-directory configuration files are intended to
be committed by this follow-up.
