# Dialogue Navigation Experiment

## Goal

Make MCA's existing Talk screen easier to navigate, without replacing the DialogueEvent engine or adding a separate submenu.

## Menu

- The existing `ASK` event category is an expandable **Ask about…** row in the current topic menu. It is selectable, not a grey disabled heading. Its eligible topics appear indented immediately below when expanded; other offered event categories and ambient Talk remain visible.
- Expansion is purely client-side presentation. The server still sends the complete offered event set and validates selections. Collapse/expand never requests another menu, changes availability, or advances a dialogue.
- Menus still scroll within their existing bounds. The panel and main interaction screen are unchanged in this experiment.

## Conversation history

- Show small **Previous** and **Next** controls in the existing active-dialogue footer, alongside **Topics**. They navigate **villager speech lines in the current conversation**, not earlier branches or the player's choice history.
- Previous opens an earlier line as read-only; Next moves toward the live line. All previously displayed lines appear **fully revealed immediately**. No animation, choices, action, game-state transitions or network requests occur while reading history.
- The live/latest line preserves its existing character reveal and progress, even if the player temporarily reviews an earlier line. On returning, already-revealed characters do not animate again. Next on the live line retains its existing server-authoritative progression, only after the line is fully revealed.
- On reaching the last earlier line, Next returns to the current line; it must not also advance the server in the same click. No Previous button on the first line. No history navigation while the Talk menu is active.
- The footer gives Topics, Previous and Next distinct, non-overlapping hit areas at narrower GUI scales. Switching reviewed lines starts displaying each line from its beginning.
- Keep at most 64 resolved villager lines, client-only, scoped to a single session. Repeated packets with the same offer do not duplicate a line. New dialogue sessions and dialogue end/clear discard the old history; Pause/Topics/Resume preserve it. If the player engages an ambient opener, its already-visible opening line belongs to that conversation's history.
- An old line must not expose its former choices, send completion/choice packets, or rerun rewards. Navigation between viewed lines must not change the authoritative current node.

## Non-goals / verification

- No new dialogue node type, protocol packet, history persistence, event selection rule, or entire InteractScreen redesign.
- Tests exercise token/session replacement, repeat and pause/resume history, read-only viewing and Unicode animation behavior. Validate both loader compilation and actual in-game high/low scale layout separately; a successful compile is not a visual acceptance result.
