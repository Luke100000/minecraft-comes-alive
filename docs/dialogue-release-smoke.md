# DialogueEvent 1.21.1 release acceptance

Use this checklist **in a running Minecraft client and a dedicated server** for
each supported loader (Fabric and NeoForge). Successful compilation, JUnit,
FakePlayer GameTests, and direct packet-handler GameTests do **not** establish
these client/network results. Mark each case pass/fail with date, loader, server
and client versions, client resource packs, and the associated log or recording.

## 1. Ordinary UI and display

- Right-click a normal, awake MCA villager. The ordinary interaction controls
  open first; no dialogue automatically starts. Open **Talk** and confirm that
  eligible topic labels are visible, a grey `Ask about...` heading is not
  clickable, and an ambient option can be selected separately.
- Resize to a short client window and use a datapack with enough eligible Ask
  topics to overflow the menu. Scroll to the **last** topic, click it, and
  confirm no clipped/offscreen topic can be selected. Repeat with a node that
  has enough long replies to overflow the node view. Verify wheel scrolling
  affects the dialogue, not the hotbar, and new content resets scroll position.
- Use a dialogue with ordered lines (for example, `mca:personal/cured_zombies`
  under the required speaker/time/hearts conditions). Check the line reveals
  at **40 milliseconds per Unicode grapheme**. Clicking during reveal must
  neither skip nor queue a choice/Next; no instant-reveal shortcut may exist.
  Subsequent lines appear in authored order, and replies/Next become clickable
  only when the current line has fully appeared. Confirm hidden outcome effects
  are not exposed as choices.
- Finish a rewarding story only after clicking its terminal **Back to topics**.
  Confirm reward and history change exactly once and the Talk topics refresh
  without automatically starting another event. Close before final acknowledgment
  in another run and verify no partial reward or cooldown is committed.

## 2. Continuation and expiry

- Start a multi-line conversation; close the Talk view. Reopen the **ordinary**
  interaction screen, open Talk, and confirm a topic-specific continuation
  appears **alongside other eligible topics**, without auto-resume. Browsing
  those topics must not change the saved line, choices, or pause deadline.
- Select the continuation; confirm exactly the former line/choices return with
  no reroll. Separately, choose another valid topic (including another villager)
  and confirm the abandoned attempt cannot later apply its queued effects.
- Repeat and allow **2,400 actual overworld game ticks** (normally 120 seconds
  at 20 TPS) to elapse without resuming. The continuation expires, pending
  actions are discarded, and no remote/distant speech or automatic penalty
  occurs. `/time set` only changes day time; it does **not** substitute for
  advancing 2,400 game ticks.

## 3. Two authenticated clients

- Join one dedicated server with two **real** player accounts, A and B. Have
  each complete a five-second cooldown story with the same villager but choose
  different replies. Verify each player's availability, most recent completed
  choice, follow-up eligibility, hearts, and cooldown are independent.
- Rejoin after disconnect/reconnect. Completed story history and reply choices
  must persist; the unfinished active/paused conversation must not. Repeat
  after a server restart to check durable pair history versus transient runs.
- With A in an unfinished dialogue, let B interact with the same villager.
  A's final acknowledgment must never dismiss B's interaction or award B's
  rewards. Verify closing/reopening, choosing while another player interacts,
  and loss of interaction range cannot bypass server checks.
- Confirm a five-second authored cooldown lasts **100 server game ticks**,
  prevents the specific event before expiry, then permits it after expiry
  without erasing the earlier completed choice or blocking unrelated topics.

## 4. Addon and client resource mismatch

- Install a namespaced JSON-only dialogue datapack on the **server**, but do
  **not** install its optional language/resource pack on one client. Enable
  it using `/datapack list`, `/datapack enable ...` and `/reload`. The server
  must offer, select and complete the valid event regardless of a missing
  client translation; that client may display the raw key. A second client
  with the translation installed should display localized text while sharing
  exactly the same server-authoritative options and effect behavior.
- Alongside the valid addon event, include an intentionally malformed event.
  Verify the server log identifies the precise rejected namespaced ID, while
  the valid event and shipped MCA topics remain available.
- Start an addon run and queue an effect without final acknowledgment. Also
  pause an addon run. Disable/remove the addon datapack and `/reload` while
  connected. Neither stale dialogue may commit, resume or award rewards.
  Re-enable the pack and confirm only fresh menu/session tokens work.

## 5. Network and gameplay abuse checks

- In a **test server**, use a protocol test client capable of deliberately
  replaying/forging C2S packets (ordinary manual clicks cannot prove this).
  Attempt unoffered topics and choices, forged villager/session/interaction IDs,
  repeated select/choice/Next/final-ack tokens, and stale close/leave packets
  before and after pause, resume, reload, timeout and reconnect. The server
  must reject invalid transitions and permit at most one committed reward.
- While a dialogue is unfinished, trigger villager unload, death and
  conversion; verify unload pauses until expiry, while permanent removal
  discards the run. Test during disconnect/reconnect and after server restart.

**Release gate:** do not mark Task 13 or production readiness complete until
the applicable cases above have recorded successful runtime evidence for both
loaders. The automated NeoForge GameTests cover several server internals but
not graphical presentation, authenticated multiplayer, or actual network wire
delivery. See [developer guide](dialogue-system-guide.md) for the implemented
protocol and [migration plan](superpowers/plans/2026-10-06-dialogue-event-conversation-overhaul.md)
for the full requirements.
