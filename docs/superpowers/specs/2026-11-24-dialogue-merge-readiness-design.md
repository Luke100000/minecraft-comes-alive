# Dialogue system B simplification and dev/1.21.1 integration

Date: 2026-11-24
Status: Proposed design for review
Scope: Dialogue, interaction, and directly related integration changes on Minecraft 1.21.1

## Intent

Retain the data-driven dialogue system in B while making its server lifecycle, client presentation, and extension boundaries easier to maintain. Prepare it for integration into dev/1.21.1 without regressing fixes that landed on the target branch independently.

The result should have one authoritative owner for each piece of state, fewer overlapping transitions and checks, and a reviewable merge. Existing authored conversations, gameplay effects, visual design, addon extension points, and authorization contracts remain supported.

## Baselines and evidence

The following local Git refs were inspected on November 24, 2026.

| Ref | Commit | Role |
| --- | --- | --- |
| dev/1.21.1-dialogue-system | e249845f6 | B's original dialogue parent and its merge base |
| dev/1.21.1-dialogue-system-b | 7cda50286 | Feature branch under review |
| dev/1.21.1 | f51795ef4 | Intended eventual target, two commits ahead of its local remote-tracking ref |
| Common ancestor of B and dev | 928bd79a1 | Starting point for identifying independently changed files |

The B-to-parent dialogue-focused production Java diff covers 30 files, with 3,334 additions and 447 deletions. The original parent already implements data-driven events, conditions, weighted outcomes, and server sessions. B adds asynchronous Rumours, interaction ownership and guarded command dispatch, richer presentation and history, fatigue parity, and addon registration.

The earlier review identified DialogueEngine (1,380 to 1,843 lines) and InteractScreen (903 to 1,489 lines in the inspected working copy) as the main complexity hotspots. File size is indicative, not an acceptance metric. B also has local uncommitted docked-layout, texture, test, documentation and generator changes. Coordinate with their owner before starting the implementation; retain them and use the agreed docked layout as the presentation baseline.

Minecraft 1.21.1, Java 21, Fabric Loader 0.20.1 / Fabric API 0.116.17+1.21.1 and NeoForge 21.1.265 were read from B's gradle.properties. Recheck both branches and their build configuration at implementation time.

This design is based on static inspection. No merge, gameplay test or fresh build has been performed.

## Goals and constraints

- Keep B's event JSON, translations, event history, choices, weighted outcomes, conditions, actions, session resume semantics, fatigue and Rumours functionality.
- Keep the server as the authority for interaction ownership, permissions, dialogue progress and gameplay mutations.
- Keep registered Java actions, conditions, async resolvers and interaction commands addon-friendly, including supported namespaced identifiers and existing aliases. Datapacks may invoke registered actions; executors remain Java-defined.
- Preserve both Fabric and NeoForge and compatibility with existing world data, authored packs and established integration APIs. Determine compatibility requirements for legacy Java methods from actual callers before removal.
- Retain the currently agreed dialogue visuals, history, independent scrolling, rich text, input and inspector behavior.
- Limit changes to the dialogue/interaction surface and directly overlapping target-branch fixes. An independent whole-project dev review, protocol redesign, new content system, new rendering modes, navigation rewrite and arbitrary class splitting are outside this design.
- Keep work in the existing authorized checkouts. Coordinate concurrent changes; no new worktree or checkout without consent. GameTests require separate user authorization under the existing testing restriction.

## Approaches considered

1. Merge B immediately and clean up afterwards. This delivers features sooner, but intertwines refactoring failures with merge regressions and leaves duplicate state in the initial integration.
2. Perform a focused, behavior-preserving cleanup on B, merge dev into B and validate the integrated result before the eventual B-to-dev merge. This allows review of smaller changes and exposes target-branch semantic differences before release. **Recommended.**
3. Replace the dialogue engine or interaction protocol wholesale. This creates avoidable compatibility, gameplay and verification work, since the event model and guarded dispatcher already provide useful foundations.

The recommendation is a proposal for the user's review. Individual implementation choices below can be adjusted if source inspection or regression evidence identifies a simpler existing owner.

## Phase 0 — Establish the comparison and verification baseline

**Branch: B. Priority: prerequisite.**

- Record B, its parent and current dev tip, the dirty worktree, their merge bases, and the supported build/test tasks.
- Review the existing docked-layout design and current changes with their owner. Snapshot the intended gameplay and UI contracts using existing focused tests and representative client scenarios.
- Compare both branch deltas against their common ancestor. Identify overlapping behavioral changes, including changes Git can merge without reporting a textual conflict.
- Record existing failures separately from regressions introduced by this work. Use the current supported test source sets and existing fixtures. Do not manufacture clean state by resetting, stashing, discarding or overwriting unrelated changes.

## Phase 1 — Consolidate the server dialogue lifecycle

**Branch: B. Priority: P2, recommended before integration.**

### Current evidence

- DialogueEngine.java owns selection, progression, sessions, offers, asynchronous completion and packets alongside gameplay details. See its pause and release methods around lines 616–689, tick around 760–809, async completion around 1335–1449 and pauseUnavailable around 1497.
- EntityCommandHandler.interactAt explicitly calls DialogueEngine.pause during interaction replacement, then stops the old interaction. stopInteracting invokes DialogueEngine.onInteractionReleased, which already performs villager-scoped cleanup.
- DialogueSession and DialoguePendingRequests already represent session and async state. Keep these useful boundaries.

### Design

Make the engine's existing lifecycle transitions the single path for changing session and offer state. Use a small set of shared operations for temporary suspension and terminal invalidation, reusing current session and pending-request methods. The interaction handler remains responsible for the concrete interaction owner, interaction ID and screen/container closure. Its scoped release notification drives dialogue suspension; eliminate redundant broad pause calls only after tracing callers and preserving intended ordering. Avoid a second session registry or an independently mutable state machine.

Define the following observable transition contract using the current session identities, offer tokens, event generations and timeouts.

| Trigger | Required outcome |
| --- | --- |
| Begin, valid choice or advance | Validate current player, villager, interaction and offer/session token; consume permitted progression once. |
| Close, leave, loss of matching interaction, or temporarily unavailable villager | Revoke obsolete offers and cancel unfinished operations; retain eligible resumable session and completed results for the existing pause window. |
| Reopen an eligible session | Issue fresh authorization, retain prior completed result, and resume unfinished work only if the existing contract calls for restarting it. |
| Finish, expiry, reload, disconnect or terminal replacement | Apply existing terminal semantics and invalidate outstanding operations and retained state. |
| Late completion, old close or stale choice | Ignore or reject without changing a newer session, its screen, or its gameplay effects. |
| Command opens another container | Release only the named MCA interaction; preserve the replacement container. |

Distinguish switching to another villager from temporary suspension and eventual terminal replacement according to the existing session rules. Repeated release of an obsolete interaction must be harmless. An unrelated villager's release must not affect the active dialogue.

Keep selection, authored condition evaluation and event progression in DialogueEngine. Investigate moving delayed slap scheduling into the existing gameplay-effect owner and coordinate text formatting into the existing dialogue text/coordinate utility. Move the smallest cohesive responsibility only when the move actually removes coupling, duplicated work or owner ambiguity. Preserve server-thread execution, action ordering, timeouts, cancellation, error handling and one-time effects.

### Acceptance

Existing DialogueOwnershipReleaseTest, DialogueSessionTest, DialogueAsyncLifecycleTest and selection tests cover the relevant boundaries. Extend them only for missing scenarios, including duplicate release, stale tokens, replacement container, late async completion and completed result resume/terminal cleanup. Confirm no duplicate structure search or duplicate gameplay effect. The resulting lifecycle must be explainable from one set of transition operations.

## Phase 2 — Consolidate client presentation and render-derived state

**Branch: B. Priority: P2, recommended before integration.**

### Current evidence

InteractScreen tracks dialogueAwaitingReply and displayed/rendered node or token state while ClientHandlerImpl.DialoguePresentation already owns server responses and awaitingReply. Screen methods dialogueRows, replyRows and rendering around lines 632–778 rebuild rows and parse rich text. Some screen-local state is legitimately visual.

### Design

- Make the existing DialoguePresentation the owner of the current authorized interaction/session, request correlation, whether a reply is awaited, server response/rejection, and presentation history. Derive loading and available actions from that state and existing reveal/pending semantics.
- Trace and remove screen-level copies of protocol state when they express the same fact. Keep visual-only last-rendered identity, reveal/animation timing, geometry, focus, scrollbar and hit targets with the screen or existing widgets when their values serve distinct purposes.
- Preserve the existing data flow from packet handler to presentation to screen. No additional controller/store or general UI framework is needed.
- Reuse existing DialogueOverlayLayout, row layout, rich-text and scroll-widget helpers. Compute stable translated/parsed rows and wrapped metrics when their semantic inputs change if profiling or inspection confirms repeated expensive work. Keep one owner for derived content; prefer existing widget invalidation over another persistent cache.
- Invalidate derived rows on authoritative response or rejection, choice/history or availability changes, changed translation/resource state, GUI width/font/scale changes and relevant configuration changes. Keep frame-dependent reveal and animation dynamic. Draw, clip, scroll and hit-test using the same metrics.
- Preserve independent speech and options scrolling, history, copyable coordinates, loading indicators, disabled choices, terminal Finish, keyboard/focus behavior, and the current docked/corner layout. Reuse Minecraft's native widget contracts where applicable. Inspect version-matched Screen/widget sources before any geometry or input behavior change.

### Acceptance

Use DialoguePresentationTest and existing layout, rich-text, hit-test, ordering, animation, scroll and overlay tests. Exercise stale/rejected Choice/Continue, pending async completion, screen close/reopen, new tokens, resource reload and resizing. Verify in a client at normal and large GUI scales with long translations, partially clipped rows, history navigation and actual pointer/wheel/keyboard input. Idle frames should not repeatedly derive unchanged semantic rows; verify any claimed performance gain rather than assuming it from fewer lines.

## Phase 3 — Simplify async resolver and legacy command plumbing

**Branch: B. Priority: P3 for codec cleanup; required before merge for preservation of target security behavior.**

DialogueAsyncResolver has separate built-in and registered serialization paths with manual JsonOps handling and codec field inspection. Only the bundled Rumours event currently declares a built-in async resolver. DialogueAction already offers a registration precedent.

- Compare codec shapes and API use in DialogueAsyncResolver, DialogueAction and DialogueExtensionRegistrationTest. Prefer routing the built-in resolver through the existing typed registration/serialization path if it removes special cases. Retain built-in and addon JSON shapes, strict unknown-field handling, explicitly supplied defaults, identifiers, registration errors and existing non-MapCodec compatibility. If a common path requires a more complicated public API, defer the codec cleanup.
- Keep DialoguePendingRequests as the ticket/result owner, with distinct cancellation of unfinished operations and invalidation of retained results.
- Preserve InteractionCommands as the shared Java authorization and dispatch boundary for menu and dialogue sources. Keep aliases, Work eligibility, interaction matching, distance and same-level validation, explicit command outcomes, and token-scoped screen closure.
- Audit actual uses of the remaining location command and its public legacy Java entry point. B's VillagerCommandHandler location case accepts an explicit parseable registered structure whenever the configured pool is nonempty. Dev's resolveAllowedRumorStructure rejects explicit IDs outside its configured pool. Retain that target restriction for the legacy command. B already splits location arguments with a two-part split.
- Preserve explicitly authored structure IDs on the server-defined async resolver where that is its intentional datapack contract. Do not apply the legacy request allowlist indiscriminately to trusted authored content. Validate registry presence, cancellation and lifecycle consistently.
- If legacy and current lookup paths have identical search behavior after this audit, reuse their narrow shared validation or lookup implementation. Keep their distinct authorization, output and cancellation contracts. Delete a compatibility entry point only with evidence that supported callers have migrated; otherwise retain and document it.

Acceptance covers allowed and disallowed legacy IDs, empty pool, invalid IDs, namespaced IDs with dots, registered resolver round-trips including defaults and unknown fields, addon command permissions, and the bundled Rumours event. There is no confirmed remote exploit in the reviewed B packet path, which restricts the legacy location command to dialogue dispatch. The allowlist difference is a target-branch behavior regression risk.

## Phase 4 — Integrate dev/1.21.1 without losing independently landed fixes

**Branch: merge current dev into B when phases 1–2 are reviewed and the remaining changes are ready; eventual B into dev after acceptance. Priority: required for integration.**

The following were found by comparing the independent deltas from 928bd79a1. Recheck them against the actual refs at merge time; this is a semantic reconciliation checklist, not a complete textual conflict forecast.

| Surface | Current target behavior to preserve | Integration decision |
| --- | --- | --- |
| InteractionVillagerMessage / InteractionCommands | Dev added living-entity and 25-block distance guards to its old packet. | Retain B's stronger shared owner/token, same-level, alive and vanilla interaction-reach authorization. Exercise the rejected cases that dev covers. Do not restore the old tokenless packet or its looser authorization. |
| VillagerCommandHandler | Dev validates explicit legacy Rumours structure IDs against the configured pool. | Carry the restriction into B's retained command, as described in phase 3. |
| VillagerBrain and Memories | Dev batches fatigue decay into an updated tracked NBT compound, skips malformed memories, and only publishes changes when needed. It also adds chore activity/retry-gate changes. | Preserve dev's memory update and AI behavior while retaining B's fatigue event effects and dialogue ownership rules. Verify the fatigue cooldown, save compatibility and no unrelated memory overwrite. |
| VillagerEntityMCA / interaction AI | Both branches changed entity/interaction behavior. Dev contains navigation, chore and broader gameplay fixes. | Keep current target navigation and activity behavior. Trace B's interaction start/stop, danger and release changes against those owners. Preserve walking/brain activity and container handoff. |
| DialogueType / MixinClientLanguage / MCAClient | bb3281767 decouples common fallback translation from MCAClient via BooleanSupplier and handles a null key at the client language boundary. | Preserve the side-safe supplier design, null fallback, existing B dialogue translation keys and caller coverage. Adapt B's callers/tests to the agreed signature. |
| Speech managers | ddaed6e0d fixes concurrent audio caching and stale voice requests, with focused tests. | Preserve these fixes and the B dialogue speech call sites; verify stale speech does not replay on a newer dialogue. |
| ClientHandlerImpl / FamilyTree | Dev changes FAMILY_TREE screen opening. | Preserve the target family tree behavior alongside B's DialoguePresentation and token-scoped close. |
| Client/server network and resources | Dev also has independent baby naming, family-call/editor validation and other changes. Both branches modify en_us.json. | Retain target packet validation and non-dialogue registrations/handlers; use B's intentional dialogue protocol and server/client packet pairing. Merge translation keys semantically and preserve resources, config and tests from both branches. |

Use the existing API ownership boundaries rather than duplicating checks inside every handler. Where old and new packet formats intentionally differ, record the supported client/server version policy and keep registration, serialization and all call sites internally consistent on both loaders. Check actual addon compatibility requirements before deleting old entry points or changing published packet/API contracts. Reconcile conflicting tests from their behavior and history, retaining negative cases.

Keep unrelated dev fixes in their owning systems. Do not recreate them inside dialogue. Review the integration diff against both pre-merge refs, including generated resources and tests, before moving toward the final merge. Avoid cherry-picking broad mixed commits solely to recover one behavior.

## Phase 5 — Acceptance and merge readiness

Use the existing Gradle tasks and test source sets. Reconfirm tasks on the integrated branch. For pure and Minecraft-aware state regressions, reuse the existing common tests. For production behavior changes, demonstrate a meaningful failing regression before a fix when feasible, then rerun the same case and affected suites after it.

Recommended checks:

| Check | Acceptance evidence |
| --- | --- |
| Automated common suite | Run :common:test on the selected integrated revision, inspect actual executed tests and failures. Include dialogue resources, serialization, lifecycle, client presentation, authorization, fatigue and retained dev regressions. |
| Loader wiring | Compile Fabric and NeoForge and validate resources on the same revision. |
| Server/client | A dedicated-server and matching-client smoke test covers valid interaction, wrong owner, stale token, multiple players, disconnect/reopen, pending and completed Rumours, command/container handoff and datapack reload. Check both loaders through their normal entry points where feasible. |
| Client visual acceptance | Normal/large GUI scale, long/localized lines, independent scrolling, history, partial clipping, keyboard/focus, progress and Finish. Preserve the agreed docked layout and texture generation. |
| Existing-world and extension acceptance | Use a safe test copy if runtime world data verification is needed. Load existing authored packs, register a sample supported Java addon command/resolver and verify translations, event history, fatigue persistence/decay and rejection of invalid requests. |
| Integration review | Review the complete diff against both current source branches. Verify target-only fixes and corresponding tests survived, no obsolete tokenless path reappeared, no generated-resource or documentation drift was introduced, and git diff --check is clean. |

The user previously asked to stop running GameTests. Keep GameTest execution subject to renewed authorization; if gameplay or dedicated-server checks remain pending, record the gap and ask for the appropriate runtime verification before declaring the branch merge-ready. Compilation and historical green tests do not prove current gameplay.

## Priority and handoff

| Order | Work | Destination |
| --- | --- | --- |
| 1 | Baseline and agree on current UI changes | B |
| 2 | Server lifecycle and owner cleanup | B |
| 3 | Client request/presentation simplification | B |
| 4 | Legacy Rumours restriction; optional resolver simplification | B, reconciled with dev |
| 5 | Merge current dev into B and resolve semantic overlaps | B |
| 6 | Verify integrated revision and address demonstrated regressions | B |
| 7 | Merge the accepted B revision into current dev | dev/1.21.1, when requested |

Keep each meaningful change independently reviewable. Defer optional class extraction, codec refactoring or performance optimizations if evidence shows they add complexity or if they threaten the integration schedule. Actual authorization regressions, lost target fixes, stale-session side effects, and broken supported gameplay are merge blockers; the earlier static review did not establish a critical defect in B.

Before implementation, convert this approved design into a focused implementation plan. Establish the current baseline and exact test commands, identify the smallest shared owners, and record any scope choices that require user input. This specification does not authorize a production change, commit, push or branch merge.
