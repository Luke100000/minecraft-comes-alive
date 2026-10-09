# Automatic ambient Talk introduction

**Status:** Proposed for user review; design only.
**Target:** Existing dev/1.21.1-dialogue-system worktree (shared Fabric/NeoForge).

## Goal

Pressing the existing **Talk** button immediately makes the villager say one
context-appropriate ambient opening line, selected by the server from eligible
DialogueEvents. The player can **always choose another option**: a highlighted
event, Ask topic, personalized paused continuation, another ambient
conversation, or Back. No automatic introduction can force the player to
finish the story.

Ambient content may be a standalone one-line remark or the start of a
multi-line conversation with natural player replies and branching outcomes.
Reuse existing event IDs, requirements, priority, weights, nodes, choices,
cooldowns, localization and completion/history semantics. No replacement
dialogue engine, new trigger type or wholesale UI redesign.

## Player experience

    Right-click -> normal MCA interaction -> Talk
      -> server offers eligible topics AND selects an ambient opening
      -> the villager begins speaking in the bottom passage panel
      -> the topics list remains reachable immediately
      -> player can ignore the line, select another topic, or engage
         -> Next for another passage / natural reply for a choice
         -> continue the authored event through normal server sessions
      -> explicit story completion commits effects/history
      -> refreshed topics; no automatically chained story

Opening right-click alone never triggers speech. Neither a highlighted/Ask
story nor a previously paused story automatically starts or resumes.
This is a Stardew-inspired hybrid presentation, not location-driven cutscenes.

## Chosen architecture: an ambient preview in the existing menu offer

Three approaches were considered:

1. Start a real ambient session on Talk: straightforward progression, but
   clashes with an already-paused conversation and implicitly interrupts it.
2. **Chosen: noncommitting server-pinned ambient preview.** The remark appears
   immediately, and the first deliberate Next/reply engages the same event.
3. Independent random greeting text: easy to render but duplicates authored
   event selection and can contradict datapack conditions.

The current DialogueEngine.begin() creates a DialogueOptions offer. Extend
that offer with one optional ambient preview, not another ACTIVE session or
separate event subsystem. The server pins the event ID, resource generation,
owner player/villager and initial localized Component to the offer token.
The client receives only the spoken text and permitted actions, never trusted
event-selection authority.

For preview selection first filter to eligible, safely previewable ambient
events, then take the highest remaining priority tier and choose by canonical
weighted selection. The start node must have a resolvable first passage with
no side-effectful transition needed to reveal it. A one-line terminal with no
engagement path is previewable only with scheduling-only history; a
story/once terminal otherwise cannot complete through this presentation and
must remain manually accessible rather than silently becoming repeatable.
For an event without a safe opening, skip it for automatic preview (it
remains manually selectable). The shipped baseline ambient event guarantees
an ordinary valid villager still says something.
Reuse existing DialogueType/translation-pool resolution; do not require a
second hardcoded greeting string for every event.

**Displaying the preview does not create a session, mark an event completed,
record a story choice, grant hearts, queue gameplay rewards or resume a paused
run.** It is a readable invitation to engage with that particular event.
Changing topics simply discards the preview.

For a preview with multiple lines, after the first line is revealed, Next
starts the pinned event and advances to its second passage. For a preview
whose first passage offers immediate replies, a selected reply starts that
event and applies the selected choice. That first engagement is **one
server-validated atomic operation**, not a client-triggered select packet
followed by a race-prone independent answer packet. The already-shown first
line is never replayed. Revalidate the offer token, target, generation,
eligibility and offered choice before beginning the normal DialogueSession;
reject and refresh if stale. Further nodes and actions use the existing
authoritative session and explicit-completion rules.

An ambient preview with exactly one line and no choices/next passage needs
no Next or Back-to-topics acknowledgement. Its line can remain visible until
the player selects something else. No story completion is fabricated.

## Cooldowns and avoiding repetition

- Existing per-event datapack cooldowns remain expressed in **seconds**.
  Do not invent a global fixed delay or make personality ownership global.
- The preview is pinned once per offer; GUI renders/scrolls cannot reroll it.
  When Talk is reopened quickly, do not allow repeated reroll farming. A
  server-side recent-preview cache per player should prefer a different
  equally eligible event for that villager when one exists. Bound it to
  the 32 most recent villager pairs per online player; if only one
  candidate exists, it may repeat. Clear transient records on logout,
  reload and server stop.
- Existing story history/choice state changes only on explicit completion.
  For a standalone one-line ambient greeting with scheduling-only history,
  sending its offer **starts its authored scheduling cooldown** on the
  server, without marking story completion or granting rewards. For any
  preview with an engagement path, scheduling starts only on actual event
  completion: otherwise clicking its already-delivered offer could fail
  the repeat check against its own newly activated cooldown. An event
  with story history never starts its completion cooldown just by previewing.
  Keep delivery bookkeeping distinct from story outcomes and test it.
- A manually available alternate ambient option may read **Anything else on
  your mind?** after an opening line. Select another eligible ambient event,
  preferably different from the current preview. Hide it if no meaningful
  alternative is available; never restart an active event simply by rerolling.

No new mandatory JSON fields are required for normal ambient authors.
Existing conditions, history/repeat policies and fully namespaced event IDs
remain authoritative.

## Hybrid screen behavior

Preserve the existing left mood/personality/relationship information.
The bottom panel displays the villager's ambient introduction, then the
active spoken passage. The right panel retains highlighted, Ask, continuation,
other ambient topics and Back during the initial preview. If the opening
supports immediate player replies, show those in a clearly separate compact
reply group while retaining access to topics.

During an active conversation, the right panel may prioritize authored
replies, but a fixed **Topics** affordance must remain accessible, even during
the character reveal. On compact screens Topics may switch between the reply
list and topic list. The player is never required to progress the current
story in order to select another conversation. Do not crowd the HUD with two
unscrollable lists; preserve measured panel bounds and token-bound click areas.

Opening Topics mid-story pauses the current run through the server and offers
the personalized continuation alongside other eligible topics. It preserves
the existing 2400-overworld-tick pause deadline; browsing does not extend
that deadline. Only selecting a different *valid* conversation abandons its
unfinished effects. Invalid selections and preview display do not abandon
it. For a paused run already present when Talk opens, the automatic ambient
preview is independent of the run; exclude the paused event from new starts.

Keep the existing 40 ms/Unicode grapheme reveal without skip/instant-click.
The topic navigation control works even before reveal completes; Next and
replies become usable only when the relevant first passage has fully appeared.
Natural replies conceal heart rewards and outcome classifications. Keep
intentional chat/voice semantics, but avoid duplicating the on-screen line
into ordinary Minecraft chat by default.

## State transitions

| Action/state | Visible result | Server-owned state |
| --- | --- | --- |
| Talk with no paused run | Ambient line and all eligible topics | Menu offer with preview; no ACTIVE session |
| Talk with valid paused run | Ambient line, authored continuation, other topics | Paused run/deadline unchanged |
| One-line preview, no replies | Readable line and topics | No story completion; scheduling-only delivery exception |
| Preview Next | Second authored passage; Topics available | Atomically start pinned event and advance once |
| Preview natural reply | Appropriate response; Topics available | Atomically start pinned event and accept one valid choice |
| Select highlighted/Ask/alternate ambient | Selected story | Discard preview; start offered eligible event |
| Topics during active run | Topic browser plus continuation | Server pauses run; no commit or deadline refresh |
| Select another valid topic | New story | Drop old unfinished effects only on valid replacement |
| Select authored continuation | Previously saved node/line | Resume same run with fresh token and no reroll |
| Back/close without engaging | Normal MCA interaction/closed | Invalidate menu offer; no new run to pause |
| Explicit terminal acknowledgement | Back to refreshed topics | Apply queued effects and history exactly once |
| Stale/invalid offer or reload | Refresh or clear stale controls | Reject; no effects |

## Integration scope and safety

Anticipated shared-code owners are DialogueEngine.begin/planSelection/select,
its menu/session lifecycle; InteractionDialogueOptionsResponse's bounded
StreamCodec and a minimal server-validated preview engagement operation;
ClientHandlerImpl.DialoguePresentation; and InteractScreen's
requestDialogueMenu/drawDialogueEventState/drawDialogueOptions/drawDialogueNode.
The current UI's menu-versus-active-node rendering needs a small presentation
extension, not a second screen. A server-side Topics transition must pause an
active run before issuing a new menu offer; current active-run selection
rejection cannot simply be bypassed on the client.

Retain one authoritative event history and at most one active/paused run per
player. Menu offers and preview snapshots expire on resource reload,
disconnect, server stop, villager unload/death or invalid interaction.
Packets are checked against player, villager, generation and single-use
tokens; duplicate Next/reply selections must not grant duplicate rewards.
All effectful operations and scheduling writes run on the server thread.

No changes to Gift/Hug/Kiss mechanics, no extra event registry, no new
personality state or event-script format, no loader-specific GUI fork.

## Acceptance / regression checks

1. Talk on an ordinary villager always produces a valid, localized
   baseline-or-better automatic ambient line **and** accessible normal topics.
2. Weather, time, mood, personality, relationships, repeat policy, priority
   and weights constrain ambient selection; per-villager repetition/cooldowns
   work. Reopening the menu does not enable free random rerolls.
3. One-line preview finishes naturally without an extra click; multiline
   Next and first reply engage exactly once and do not repeat the opening.
4. Other topics can be chosen during the opening, during reveal and during
   an active event. No unchosen preview grants hearts or marks a story seen.
5. A paused story remains untouched by Talk/preview, can resume with its
   existing personalized prompt, and expires at the original 2400-tick
   boundary; choosing another valid story drops unfinished effects.
6. Forged, wrong-villager, replayed, expired and reload-stale preview
   tokens/choice IDs are rejected without history or reward mutations.
7. No Back-only regression, hidden/clipped options, duplicate chat speech,
   lost translations or broken focus at compact GUI scales. Verify real
   Fabric and NeoForge client behavior; builds alone are not visual tests.

## Relationship to existing specs and plans

Upon user approval, this document supersedes **only** the menu-first and
no-automatic-speech clauses in the October 6 conversation-engine spec and
the options-only preview / mutually-exclusive menu-versus-passage states
in the October 8 hybrid UI spec. The October 6 implementation plan then
needs a narrow follow-up update for this opening, Topics transition and
network response; it is **not** implementation authorization today.

The full DialogueEvents migration, existing authored conversations,
server authority, meaningful progression labels, previous-choice history,
datapack extensibility and explicit completion semantics remain unchanged.
