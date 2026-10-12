# Unified conversation flow and relationship reactions — MCA 1.21.1

**Status:** Design for review, not implementation approval.

## Intent and scope

Make Talk intelligible and dependable: player-initiated dialogue reads as an actual exchange, relationship actions behave according to existing bonds, and topic sections mean what their labels say. The user chose **approach B, unified conversation flow**, over a content-only patch or a wholesale UI replacement. They chose **A, always positive responses for established romantic partners**, not random romantic rejection. Earlier screenshots are stale; the authoritative UI is the checked-in implementation.

Work only in the existing `dev/1.21.1-dialogue-system` worktree. Keep MCA's `DialogueEvent` engine, server authorization, existing parchment-style Talk assembly, villager status icons, datapack extension points, and Fabric/NeoForge support. Do not reintroduce the legacy dialogue engine, generate runtime dialogue, invent villager histories, or redesign unrelated MCA screens.

## Existing problems and source of truth

- `relationship/flirt.json` begins with an invisible automatic outcome, so the villager reacts before the player has actually said a flirt line. High hearts and existing partnerships do not increase success. `hug.json` and `kiss.json` likewise begin as automatic outcomes, and each can reject established partners.
- `DialogueEvent.Presentation.topic` is optional authoring metadata; the server's `InteractionDialogueOptionsResponse.EventOption` contains only mode, ID and prompt. The client currently groups all `ASK` events under **Ask about...**, regardless of whether the item is small talk, romance or a practical command.
- `InteractScreen` places continuation, highlighted, Ask and ambient options on the same menu; `Previous` and `Next` history entries can also appear in the active choice list. `Q` returns to topics or backs out according to context, while only acknowledging the terminal node commits pending effects.
- Existing UI history and pending effects are server/session-sensitive; making Back auto-complete could award hearts or execute commands without finishing a conversation. Preserve those guards.

## Relationship behavior

1. A valid adult villager with an established **player-specific** relationship (`romantic_partner`, `promised`, `engaged`, or `spouse`) **always responds positively to player-initiated Flirt**. No random insult, slap, negative hearts or romantic rejection is eligible in those states. Existing valid age and family restrictions take precedence: this is not a bypass for prohibited interactions. Different warm, personality-appropriate success lines can vary without varying the positive outcome.
2. An established partner's **Kiss** and affectionate **Hug** also avoid punitive rejection or slap. The positive path remains server-authoritative and only commits once. A Hug with an ordinary nonpartner can still be declined based on the authored interaction rules; romantic restrictions continue to govern Kiss.
3. Outside an established relationship, Flirt success rises in meaningful heart tiers. The initial design uses a tier at **30 hearts** (friendly), **60 hearts** (high), and a baseline below 30; additional positivity can come from personality. The high tier cannot still have the same probability as the baseline. Zero/negative hearts can remain strongly resistant. Authors use the existing `mca:hearts` and `mca:relationship` conditions and **highest-eligible-priority outcome selection**, not a new romance engine or hardcoded chance calculation.
4. Define the partner-success tier above every heart/personality tier, with outcomes that cannot reach a negative terminal. Document the actual authored weights and regression-test outcome eligibility under each combination. A flirty/odd personality must not override an established relationship into rejection.
5. Keep distinct wording for stranger, high-heart friend and established partner where needed. Do not imply exclusivity, previous romantic history or events not recorded by the game. Never expose private hidden heart-roll probabilities in the menu.

## Spoken exchange and interaction semantics

- Player-invoked social interactions (at least **Flirt, Kiss, Hug**) visibly establish what the player is saying or requesting **before** the villager's response. Use a brief authored villager invitation or setup and concrete first-person player reply choices, followed by the weighted/conditioned villager response. The selected choice must semantically match the response and reward; do not use a fake "Yes, continue" confirmation. Keep the choice text localized with distinct neutral/friendly/partner-appropriate wording where necessary.
- Preserve the difference between an **exchange** (Chat, Joke, Story, Flirt, Hug, Kiss, personal topics) and an **action/request** (hire, stay, divorce papers, adoption, procreation, etc.). Action requests may have concise explicit confirmation or feedback, but need not masquerade as multi-line conversations. The UI must label these correctly.
- Branch outcomes should have coherent dialogue in all affected English personality packs, with neutral fallback, unchanged existing non-English locale keys and preserved translation arguments/escape syntax. No unsupported claims about raids, families or village events.
- Keep existing session tokens, offer verification, cooldown/history behavior and server-side command validation. Reject stale or ineligible choices instead of granting effects. Never emit two reward commits from one terminal acknowledgement.

## Menu taxonomy and delivery

`presentation.mode` continues to describe **scheduling** (`highlighted`, `ask`, `ambient`), not a player-facing category. `presentation.topic` becomes the event's **semantic** group. Carry the topic through the existing server-authored `EventOption` and options packet as a bounded nonblank identifier with a safe default for older datapacks that omit it. Do not derive categories from filenames or translated prompt text on the client.

Present a **single scrollable list with collapsible sections** inside the existing Talk panel, not a new screen:

| Visible section | Topic values and meaning |
| --- | --- |
| **Conversations** | `small_talk`, `location`, `game`: everyday chat, local area, stories/jokes as conversations, optional games; also includes the server-offered ambient "What's on your mind?" entry |
| **Relationships** | `relationship`: affection, reconciliation and relationship/family requests. Distinguish affectionate talk from consequential actions in labels and confirmations |
| **Personal stories** | `personal`, `life_event`, `introduction`: villager-led subjects, first meeting, important follow-ups |
| **Practical matters** | `service`: hiring, work/staying and other practical gameplay requests |
| **Other topics** | Missing, unknown or datapack-added topic IDs; do not lose access to otherwise eligible events |

One **Continue: [topic]** option for an unfinished conversation remains distinct and prominent. A currently highlighted event is visibly marked/placed first within its semantic group, without silently changing its eligibility or server-selected identity; allow genuinely urgent villager-led events to stay discoverable. Retain server event-order/priority information rather than client-invented priority. Collapsing a section is client-only and sends no new server request. Remember expansion within the current Talk session; reset appropriately on leaving. Headers must have unambiguous disclosure affordances; the actual choices must use natural action/question labels.

The server can keep the existing event count limit, single highlighted slot, and ambient pool semantics. This is a **wire-format update** for the available event option metadata, not a replacement selection algorithm. Update all packet codecs and callers/tests in common code consistently for both loaders. Preserve the existing pending offer/selection-token security contract.

## Navigation and completion contract

1. **Browse topics:** expand/collapse sections, select a conversation, or use **Back to interaction**. An ambient preview can coexist with choices without pretending a topic has started.
2. **Speak:** show the villager setup and natural player reply buttons only when they are actually offered by the server. On selection, hide old buttons, resolve the authored reaction and progress through dialogue in order. A parchment click can finish revealing text but cannot also advance it.
3. **Review text:** separate **Earlier line** / **Latest line** history controls from active player choices and from the **Continue** button. History is read-only; returning to the live line must not send a progression packet or replay an effect. Keep the bounded client history and reveal state behavior.
4. **Complete:** the terminal button explicitly says **Finish conversation**. The button commits the pending effects exactly once on the server, then returns to the Talk topics through the regular menu request/response path; do not commit on Q/Back or Escape and do not preempt the terminal acknowledgement.
5. **Back / Q:** from an active conversation goes to Topics while pausing, **without effects**; from Topics returns to MCA interaction. **Escape** closes the interaction. Use visible footer wording that reflects the actual destination. Make choice, review, advance, finish and back controls physically distinct at small GUI scales and keyboard navigable.
6. If finishing or selection fails server-side (stale token, changed eligibility, unloaded/reloaded data), display a harmless return/refreshed state, never optimistic reward or doubled network action. Preserve the player's ability to resume a genuinely paused session when available.

## Component boundaries and tests

- **Event JSON & localization:** author coherent starters, selectable player lines, relationship-weight tiers, matching replies, and appropriate menu text. First fix Flirt/Hug/Kiss, then audit other player-initiated conversation graphs for silent automatic reactions.
- **Server:** `DialogueEngine`, `DialogueEvent`, `DialogueCondition` retain ownership of eligibility, outcome selection, sessions, completion, history and effects. Add only data plumbing essential to expose semantic category and completion transition; do not place event-graph logic in the GUI.
- **Network:** `InteractionDialogueOptionsResponse` carries the validated semantic topic per option; ensure codec, stream bounds and round-trip tests stay in sync.
- **Client:** `InteractScreen` renders grouped topics and distinct navigation; `ClientHandlerImpl.DialoguePresentation` continues to own snapshot/reveal/review state. Extract a narrowly scoped, testable menu-grouping helper if needed; avoid a second UI state machine.
- **Regression tests:** partner-positive outcomes for each established state and both challenging personalities, hearts-tier probabilities/priority behavior, romance age/family exclusions, player-line-before-reaction graph structure, absence of stale choice availability, topic/mode separation with unknown/missing datapack topics, packet roundtrips and bounds, layout/hit areas/key behavior, and terminal-only exactly-once completion/Back/no-commit semantics.
- **Verification:** Gradle `:common:test`, `:fabric:build`, `:neoforge:build`, `git diff --check`; test actual in-game 1.21.1 GUI at multiple scales, bright/dark backgrounds, relationship/hearts states, resume/abort paths. Compilation alone is not visual/gameplay verification.

## Alternatives and delivery guardrails

- **Chosen B:** reuse the current DialogueEvent engine and existing parchment interface, while repairing authored logic, metadata plumbing and client flows.
- **A (not chosen):** change dialogue probabilities/text only; category confusion and navigation remain.
- **C (not chosen):** replace the whole UI/subsystem; higher migration and regression cost.

Stage changes so each behavior has its own tests and manageable review: relationship content, menu topic metadata/grouping, and navigation/completion. Preserve source and saved worlds; no new Git worktree, generated runtime dialogue, AI text service, or unrelated refactoring. Never push or merge without explicit instruction.
